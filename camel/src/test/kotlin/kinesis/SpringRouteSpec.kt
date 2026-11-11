package kinesis

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.extensions.ApplyExtension
import io.kotest.extensions.spring.SpringExtension
import io.mockk.verify
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ApplyExtension(SpringExtension::class)
class SpringRouteSpec : SpringLocalstackBase() {
    @MockkBean(relaxed = true)
    lateinit var myService: MyService

    @TestConfiguration
    @Import(KinesisBasicRouteBuilder::class)
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
