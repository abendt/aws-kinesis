package kinesis

import io.kotest.assertions.nondeterministic.continually
import io.kotest.engine.spec.tempdir
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import org.apache.camel.impl.DefaultCamelContext
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisStreamTestScope

class ClusteredRouteSpec :
    LocalstackBase({

        "can use clustered route" {
            val myService = mockk<MyService>(relaxed = true)
            val latch = CountDownLatch(1)
            val tempfile = tempdir().path

            fixture.withKinesisStream {
                sendEvent("First")
                sendEvent("Second")

                thread {
                    withClusteredCamelConsumer(this@withKinesisStream, ::createKinesisClient, tempfile, myService) {
                        latch.await()
                    }
                }

                thread {
                    withClusteredCamelConsumer(this@withKinesisStream, ::createKinesisClient, tempfile, myService) {
                        latch.await()
                    }
                }

                // leader election in FileLockClusterService can take up to its acquireLockInterval (10s)
                verify(timeout = 20_000) { myService.processEvent("Second") }

                continually(10.seconds) {
                    verify(exactly = 2) { myService.processEvent(any()) }
                }

                latch.countDown()
            }
        }
    })

private fun withClusteredCamelConsumer(
    testScope: KinesisStreamTestScope,
    kinesisClientSupplier: () -> KinesisClient,
    root: String,
    myService: MyService,
    block: () -> Unit,
) {
    val context = DefaultCamelContext()

    context.addRoutes(KinesisClusteredRouteBuilder(root, testScope.streamName, kinesisClientSupplier(), myService))

    context.start()
    try {
        block()
    } finally {
        context.stop()
    }
}
