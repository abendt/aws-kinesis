package kinesis

import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringKinesisConsumerSpec : SpringLocalstackBase() {
    @MockkBean(relaxed = true)
    lateinit var myService: MyService

    @TestConfiguration
    class MyConfiguration : LocalstackConfiguration()

    init {
        "can consume kinesis event" {
            fixture.withKinesisStream("my-stream") {

                sendEvent(
                    "my event",
                )

                verify(timeout = 30_000) { myService.processEvent("my event") }
            }
        }
    }
}
