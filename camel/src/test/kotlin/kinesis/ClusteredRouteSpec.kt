package kinesis

import io.kotest.assertions.nondeterministic.continually
import io.kotest.engine.spec.tempdir
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import org.apache.camel.impl.DefaultCamelContext
import utils.KinesisStreamTestScope

class ClusteredRouteSpec : LocalstackBase({

    "can use clustered route" {
        val myService = mockk<MyService>(relaxed = true)
        val latch = CountDownLatch(1)
        val tempfile = tempdir().path

        fixture.withKinesisStream {
            sendEvent("First")
            sendEvent("Second")

            thread {
                withClusteredCamelConsumer(tempfile, myService) {
                    latch.await()
                }
            }

            thread {
                withClusteredCamelConsumer(tempfile, myService) {
                    latch.await()
                }
            }

            verify(timeout = 5000) { myService.processEvent("Second") }

            continually(10.seconds) {
                verify(exactly = 2) { myService.processEvent(any()) }
            }

            latch.countDown()
        }
    }
})

context(KinesisStreamTestScope, LocalstackBase)
private fun withClusteredCamelConsumer(
    root: String,
    myService: MyService,
    block: () -> Unit,
) {
    val context = DefaultCamelContext()

    context.addRoutes(KinesisClusteredRouteBuilder(root, streamName, createKinesisClient(), myService))

    context.start()
    try {
        block()
    } finally {
        context.stop()
    }
}
