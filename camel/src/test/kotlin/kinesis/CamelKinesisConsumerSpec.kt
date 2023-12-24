package kinesis

import io.kotest.assertions.nondeterministic.continually
import io.kotest.engine.spec.tempdir
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.processor.resume.TransientResumeStrategy
import org.apache.camel.resume.cache.ResumeCache
import utils.KinesisStreamTestScope

class CamelKinesisConsumerSpec : LocalstackBase({
    val tempfile = tempdir().path

    "can consume event" {
        val myService = mockk<MyService>(relaxed = true)

        fixture.withKinesisStream {
            sendEvent("First")
            sendEvent("Second")

            withBasicCamelConsumer(myService) {
                verify(timeout = 5000) { myService.processEvent("First") }
                verify(timeout = 5000) { myService.processEvent("Second") }
            }
        }
    }

    "can consume event (threaded)" {
        val myService = mockk<MyService>(relaxed = true)
        val latch = CountDownLatch(1)

        fixture.withKinesisStream {
            sendEvent("First")
            sendEvent("Second")

            thread {
                withBasicCamelConsumer(myService) {
                    latch.await()
                }
            }

            verify(timeout = 5000) { myService.processEvent("First") }
            verify(timeout = 5000) { myService.processEvent("Second") }

            latch.countDown()
        }
    }

    "can resume kinesis consumer" {
        // we need to use the same cache instance across restarts of the Camel context
        val cache = TransientResumeStrategy.createSimpleCache()
        val myService = mockk<MyService>(relaxed = true)

        fixture.withKinesisStream {
            withResumableCamelConsumer(cache, myService) {
                sendEvent("First")

                verify(timeout = 5000) { myService.processEvent("First") }
            }

            withResumableCamelConsumer(cache, myService) {
                sendEvent("Second")

                verify(timeout = 5000) { myService.processEvent("Second") }
            }

            // we should have received the first event only once!
            verify(exactly = 2) { myService.processEvent(any()) }
        }
    }

    "can use clustered route" {
        val myService = mockk<MyService>(relaxed = true)
        val latch = CountDownLatch(1)

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
private fun withResumableCamelConsumer(
    cache: ResumeCache<Any>,
    myService: MyService,
    block: () -> Unit,
) {
    val context = DefaultCamelContext()

    context.addRoutes(KinesisResumableRouteBuilder(streamName, createKinesisClient(), myService, cache))

    context.start()
    try {
        block()
    } finally {
        context.stop()
    }
}

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

context(KinesisStreamTestScope, LocalstackBase)
private fun withBasicCamelConsumer(
    myService: MyService,
    block: () -> Unit,
) {
    val context = DefaultCamelContext()

    context.addRoutes(KinesisBasicRouteBuilder(streamName, createKinesisClient(), myService))

    context.start()
    try {
        block()
    } finally {
        context.stop()
    }
}
