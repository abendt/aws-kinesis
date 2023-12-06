package kcl2

import config.configureForLocalstack
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
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry
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
                        payload: List<String>,
                        partitionKey: String,
                    ) {
                        kinesisClient.putRecords(
                            PutRecordsRequest.builder().streamName(name).records(
                                payload.map {
                                    PutRecordsRequestEntry.builder().partitionKey(partitionKey).data(SdkBytes.fromUtf8String(it)).build()
                                },
                            ).build(),
                        )
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

            logger.info { "stream $name is available" }
        }
    }

    interface KinesisStreamTestScope {
        val streamName: String
        val shardCount: Int

        fun sendEvent(
            payload: List<String>,
            partitionKey: String = "partition-1",
        )
    }

    interface KinesisConsumerTestScope {
        val processorInvoked: Int
        val eventsReceived: List<String>
    }

    suspend fun KinesisStreamTestScope.withKinesisConsumer(
        shouldFailPermanently: Boolean = false,
        shouldFailPermanentlyOn: Set<String> = emptySet(),
        // shouldFailTemporaryOn: Map<String, Int> = emptyMap(),
        block: suspend KinesisConsumerTestScope.() -> Unit,
    ) {
        var processRecordsCount = 0

        val eventsReceived = mutableListOf<String>()
        val processorsReady = CountDownLatch(shardCount)
        // val tempFails = shouldFailTemporaryOn.mapValues { AtomicInteger(it.value) }

        val config =
            object : ConsumerConfiguration<String> {
                override fun processorInitialized() {
                    logger.info { "processor started" }
                    processorsReady.countDown()
                }

                override fun convertPayload(buffer: ByteBuffer): String {
                    return SdkBytes.fromByteBuffer(buffer).asUtf8String()
                }

                override fun processPayload(payload: List<Pair<String, String>>) {
                    try {
                        logger.info { "got $payload" }

                        if (shouldFailPermanently) {
                            throw RuntimeException("fail always")
                        }

                        if (payload.any { shouldFailPermanentlyOn.contains(it.second) }) {
                            throw RuntimeException("fail on $payload")
                        }

                        eventsReceived.addAll(payload.map { it.second })

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
