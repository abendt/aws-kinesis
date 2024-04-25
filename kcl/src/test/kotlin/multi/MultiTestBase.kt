package multi

import config.KinesisTestBase
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import software.amazon.awssdk.core.SdkBytes
import utils.KinesisStreamTestScope

abstract class MultiTestBase(block: MultiTestBase.() -> Unit) : KinesisTestBase() {
    init {
        block()
    }

    suspend fun withKinesisStreams(block: suspend (Pair<KinesisStreamTestScope, KinesisStreamTestScope>) -> Unit) {
        withKinesisStream {
            val firstScope = this
            withKinesisStream {
                block(firstScope to this)
            }
        }
    }

    interface KinesisConsumerTestScope {
        val processorInvoked: Int
        val eventsReceived: List<String>
    }

    suspend fun withKinesisConsumer(
        streams: List<String>,
        block: suspend KinesisConsumerTestScope.() -> Unit,
    ) {
        var processRecordsCount = 0

        val eventsReceived = mutableListOf<String>()
        val processorsReady = CountDownLatch(streams.size)

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
                    eventsReceived.add(payload)
                }
            }

        val consumer =
            KinesisConsumer("test-application", streams, kinesisClient, dynamoClient, cloudWatchClient, config)

        consumer.start()

        try {
            logger.info { "awaiting ${processorsReady.count} processors" }
            val success = processorsReady.await(2, TimeUnit.MINUTES)

            if (!success) {
                throw IllegalStateException("processors did not start!")
            }

            val context =
                object : KinesisConsumerTestScope {
                    override val processorInvoked: Int
                        get() = processRecordsCount
                    override val eventsReceived: List<String>
                        get() = eventsReceived
                }

            block(context)
        } finally {
            consumer.stop()
        }
    }
}
