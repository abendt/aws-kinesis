package kcl2

import config.KinesisTestBase
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import software.amazon.awssdk.core.SdkBytes
import utils.KinesisStreamTestScope

abstract class Kcl2TestBase(block: Kcl2TestBase.() -> Unit) : KinesisTestBase() {
    init {
        block()
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
