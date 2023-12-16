package kinesis

import io.mockk.mockk
import io.mockk.verify
import org.apache.camel.impl.DefaultCamelContext
import utils.KinesisStreamTestScope

class CamelKinesisConsumerSpec : LocalstackBase() {
    fun KinesisStreamTestScope.withCamelConsumer(
        myService: MyService,
        block: () -> Unit,
    ) {
        val context = DefaultCamelContext()

        context.addRoutes(KinesisRouteBuilder(streamName, createKinesisClient(), myService))

        context.start()
        try {
            block()
        } finally {
            context.stop()
        }
    }

    init {
        "can consume kinesis event" {

            val myService = mockk<MyService>(relaxed = true)

            fixture.withKinesisStream {

                withCamelConsumer(myService) {
                    sendEvent("First")

                    verify(timeout = 30_000) { myService.processEvent("First") }
                }
            }
        }
    }
}
