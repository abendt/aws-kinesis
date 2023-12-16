package kcl

import config.configureForLocalstack
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import mu.KotlinLogging
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisFixture
import utils.KinesisStreamTestScope

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

    val kinesisFixture = KinesisFixture(KinesisClient.builder().configureForLocalstack(localstack).build())

    suspend fun withKinesisStream(
        withShards: Int = 1,
        block: suspend KinesisStreamTestScope.() -> Unit,
    ) {
        kinesisFixture.withKinesisStream(withShards = withShards, block = block)
    }

    interface KinesisConsumerTestScope {
        val processorInvoked: Int
        val eventsReceived: List<String>
    }

    suspend fun KinesisStreamTestScope.withKinesisConsumer(
        shouldFailPermanently: Boolean = false,
        shouldFailPermanentlyOn: Set<String> = emptySet(),
        shouldFailTemporaryOn: Map<String, Int> = emptyMap(),
        block: suspend KinesisConsumerTestScope.() -> Unit,
    ) {
        var processRecordsCount = 0

        val eventsReceived = mutableListOf<String>()
        val processorsReady = CountDownLatch(shardCount)
        val tempFails = shouldFailTemporaryOn.mapValues { AtomicInteger(it.value) }

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

                        if (shouldFailPermanently) {
                            throw RuntimeException("fail always")
                        }

                        if (shouldFailPermanentlyOn.contains(payload)) {
                            throw RuntimeException("fail on $payload")
                        }

                        tempFails[payload]?.let {
                            val count = it.getAndDecrement()
                            if (count > 0) {
                                throw RuntimeException("fail #$count")
                            }
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
