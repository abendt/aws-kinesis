package sample

import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import utils.KinesisFixture

@SpringBootTest
class SpringCloudKinesisConsumerSpec : LocalstackBase() {
    @MockkBean(relaxed = true)
    lateinit var myService: MyService

    @Autowired
    lateinit var kinesisFixture: KinesisFixture

    init {
        "can send event" {
            kinesisFixture.withKinesisStream(name = "my-stream") {

                sendEvent("First")

                verify(timeout = 120_000) { myService.processEvent("First") }
            }
        }
    }
}
