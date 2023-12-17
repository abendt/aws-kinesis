package sample

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.GenericMessage

@SpringBootTest
@Import(TestChannelBinderConfiguration::class)
class KinesisConsumerSpec : StringSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var input: InputDestination

    @MockkBean
    lateinit var myService: MyService

    init {
        "can send event" {
            input.send(GenericMessage("hello".toByteArray()))

            verify(timeout = 5000) { myService.processEvent("hello") }
        }
    }
}
