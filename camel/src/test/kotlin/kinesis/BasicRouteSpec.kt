package kinesis

import io.mockk.mockk
import io.mockk.verify
import org.apache.camel.impl.DefaultCamelContext
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisStreamTestScope

class BasicRouteSpec :
    LocalstackBase({

        "can consume event" {
            val myService = mockk<MyService>(relaxed = true)

            fixture.withKinesisStream {
                sendEvent("First")
                sendEvent("Second")

                withBasicCamelConsumer(this@withKinesisStream, ::createKinesisClient, myService) {
                    verify(timeout = 5000) { myService.processEvent("First") }
                    verify(timeout = 5000) { myService.processEvent("Second") }
                }
            }
        }
    })

private fun withBasicCamelConsumer(
    testScope: KinesisStreamTestScope,
    kinesisClientSupplier: () -> KinesisClient,
    myService: MyService,
    block: () -> Unit,
) {
    val context = DefaultCamelContext()

    context.addRoutes(KinesisBasicRouteBuilder(testScope.streamName, kinesisClientSupplier(), myService))

    context.start()
    try {
        block()
    } finally {
        context.stop()
    }
}
