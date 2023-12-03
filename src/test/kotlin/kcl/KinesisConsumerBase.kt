package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

abstract class KinesisConsumerBase(block: KinesisConsumerBase.() -> Unit) : StringSpec() {

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
        block()
    }

    suspend fun withKinesisStream(
        withShards: Int = 1,
        block: suspend KinesisStreamTestScope.() -> Unit,
    ) {
        val name = UUID.randomUUID().toString()
        createKinesisStream(name, withShards)

        try {
            block(
                object : KinesisStreamTestScope {
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

    interface KinesisStreamTestScope {
        val streamName: String
        val shardCount: Int

        fun sendEvent(
            payload: String,
            partitionKey: String = "partition-1",
        )
    }

    interface KinesisConsumerTestScope {
        val processorInvoked: Int
        val eventsReceived: List<String>
    }

    suspend fun KinesisStreamTestScope.withKinesisConsumer(
        shouldFail: Boolean = false,
        block: suspend KinesisConsumerTestScope.() -> Unit,
    ) {
        var processRecordsCount = 0

        val eventsReceived = mutableListOf<String>()
        val processorsReady = CountDownLatch(shardCount)

        val config =
            object : KinesisConsumerConfiguration<String> {
                override fun processorInitialized() {
                    logger.info { "processor started" }
                    processorsReady.countDown()
                }

                override fun convertPayload(buffer: ByteBuffer): String {
                    return SdkBytes.fromByteBuffer(buffer).asUtf8String()
                }

                override fun processPayload(payload: String) {
                    try {
                        logger.info { "got $payload" }

                        if (shouldFail) {
                            throw RuntimeException("for test")
                        }

                        eventsReceived.add(payload)

                        logger.info { "processed $payload" }
                    } finally {
                        processRecordsCount += 1
                    }
                }
            }

        val consumer =
            KinesisConsumer("test-application", streamName, kinesisClient, dynamoClient, cloudWatchClient, config)

        consumer.start()

        try {
            logger.info { "awaiting $shardCount processors" }
            val success = processorsReady.await(2, TimeUnit.MINUTES)

            if (!success) {
                logger.error { "processors did not start!" }
            } else {
                val context =
                    object : KinesisConsumerTestScope {
                        override val processorInvoked: Int
                            get() = processRecordsCount
                        override val eventsReceived: List<String>
                            get() = eventsReceived
                    }

                block(context)
            }
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
