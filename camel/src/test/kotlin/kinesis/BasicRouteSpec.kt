package kinesis

import io.mockk.mockk
import io.mockk.verify
import org.apache.camel.impl.DefaultCamelContext
import utils.KinesisStreamTestScope

class BasicRouteSpec : LocalstackBase({

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
})

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
