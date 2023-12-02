package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import mu.KotlinLogging
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.StreamStatus

class KinesisConsumerSpec : StringSpec() {
    val logger = KotlinLogging.logger {}

    val localstack =
        install(ContainerExtension(LocalStackContainer(DockerImageName.parse("localstack/localstack")))) {
        }

    val kinesisClient =
        KinesisAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    val dynamoClient =
        DynamoDbAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    val cloudWatchClient =
        CloudWatchAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    init {
        "can consume kinesis events" {
            withKinesisStream {
                sendEvent("First")
                withConsumer {
                    sendEvent("Second")

                    eventually(60.seconds) {
                        eventsReceived shouldContain "First"
                        eventsReceived shouldContain "Second"
                    }
                }
            }
        }

        "can consume from multiple shards" {
            withKinesisStream(withShards = 2) {
                withConsumer {
                    sendEvent("Hello Kinesis!", "1")
                    sendEvent("Hello Kinesis!", "2")
                    sendEvent("Hello Kinesis!", "3")
                    sendEvent("Hello Kinesis!", "4")

                    eventually(60.seconds) {
                        eventsReceived shouldHaveSize 4
                    }
                }
            }
        }

        "need to restart consumer after exception" {
            withKinesisStream {
                withConsumer(shouldFail = true) {
                    sendEvent("Event")

                    eventually(60.seconds) {
                        processorInvoked shouldBeGreaterThan 0
                    }
                }

                withConsumer {
                    eventually(60.seconds) {
                        eventsReceived shouldHaveSize 1
                    }
                }
            }
        }

        "event is not reconsumed after restart" {
            withKinesisStream {
                withConsumer {
                    sendEvent("First")

                    eventually(60.seconds) {
                        eventsReceived shouldContain "First"
                    }
                }

                sendEvent("Second")

                withConsumer {
                    eventually(60.seconds) {
                        eventsReceived shouldContain "Second"
                    }

                    eventsReceived shouldNotContain "First"
                }
            }
        }
    }

    private suspend fun withKinesisStream(
        withShards: Int = 1,
        block: suspend KinesisStreamContext.() -> Unit,
    ) {
        val name = UUID.randomUUID().toString()
        createKinesisStream(name, withShards)

        try {
            block(
                object : KinesisStreamContext {
                    override val streamName: String
                        get() = name
                    override val shardCount: Int
                        get() = withShards

                    override fun sendEvent(
                        payload: String,
                        partitionKey: String,
                    ) {
                        kinesisClient.putRecord(
                            PutRecordRequest.builder()
                                .streamName(name)
                                .partitionKey(partitionKey)
                                .data(SdkBytes.fromUtf8String(payload)).build(),
                        ).get()
                    }
                },
            )
        } finally {
            kinesisClient.deleteStream(DeleteStreamRequest.builder().streamName(name).build()).get()
        }
    }

    private suspend fun createKinesisStream(
        name: String,
        withShardCount: Int = 1,
    ) {
        logger.info { "creating stream $name" }
        kinesisClient.createStream(CreateStreamRequest.builder().shardCount(withShardCount).streamName(name).build()).get()

        eventually(10.seconds) {
            val response = kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(name).build()).get()

            response.streamDescription().streamStatus() shouldBe StreamStatus.ACTIVE

            logger.info { "stream $name is available ${response.streamDescription().streamARN()}" }
        }
    }

    interface KinesisStreamContext {
        val streamName: String
        val shardCount: Int

        fun sendEvent(
            payload: String,
            partitionKey: String = "partition-1",
        )
    }

    interface KinesisConsumerContext {
        val processorInvoked: Int
        val eventsReceived: List<String>
    }

    suspend fun KinesisStreamContext.withConsumer(
        shouldFail: Boolean = false,
        block: suspend KinesisConsumerContext.() -> Unit,
    ) {
        var counter = 0

        val events = mutableListOf<String>()
        val processorReady = CountDownLatch(shardCount)

        val config =
            object : KinesisConsumerConfiguration<String> {
                override fun processorInitialized() {
                    processorReady.countDown()
                }

                override fun convertPayload(buffer: ByteBuffer): String {
                    return SdkBytes.fromByteBuffer(buffer).asUtf8String()
                }

                override fun processPayload(payload: String) {
                    try {
                        if (shouldFail) {
                            throw RuntimeException("for test")
                        }

                        events.add(payload)
                    } finally {
                        counter += 1
                    }
                }
            }

        val consumer =
            KinesisConsumer(streamName, kinesisClient, dynamoClient, cloudWatchClient, config)

        consumer.start()

        try {
            processorReady.await()

            val context =
                object : KinesisConsumerContext {
                    override val processorInvoked: Int
                        get() = counter
                    override val eventsReceived: List<String>
                        get() = events
                }

            block(context)
        } finally {
            consumer.stop()
        }
    }
}

private fun <B : AwsClientBuilder<B, C>, C> AwsClientBuilder<B, C>.configureForLocalstack(localstack: LocalStackContainer) =
    endpointOverride(localstack.endpoint)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.accessKey,
                    localstack.secretKey,
                ),
            ),
        )
        .region(Region.of(localstack.region))
