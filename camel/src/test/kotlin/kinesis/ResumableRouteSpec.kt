package kinesis

import io.mockk.mockk
import io.mockk.verify
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.processor.resume.TransientResumeStrategy
import org.apache.camel.resume.cache.ResumeCache
import utils.KinesisStreamTestScope

class ResumableRouteSpec : LocalstackBase({

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
