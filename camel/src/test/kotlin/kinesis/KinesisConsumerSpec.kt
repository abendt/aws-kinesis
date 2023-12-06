package kinesis

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.nondeterministic.eventually
import kotlin.time.Duration.Companion.seconds

class KinesisConsumerSpec : LocalstackBase() {
    @MockkBean(relaxed = true)
    lateinit var myService: MyService

    init {
        "can consume kinesis event" {
            createKinesisStream("my-stream")

            sendEvent(
                "my-stream",
                "key",
                "hello",
            )

            eventually(5.seconds) {
                myService.processEvent("hello")
            }
        }
    }
}
