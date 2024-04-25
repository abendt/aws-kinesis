package multi

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import kotlin.time.Duration.Companion.seconds

class ConsumeKinesisEventSpec : MultiTestBase({
    "can consume kinesis events" {
        withKinesisStreams { (scopeA, scopeB) ->
            withKinesisConsumer(listOf(scopeA.streamName, scopeB.streamName)) {
                scopeA.sendEvent("First")
                scopeB.sendEvent("Second")

                logger.info { "sent events" }

                eventually(60.seconds) {
                    eventsReceived shouldContain "First"
                    eventsReceived shouldContain "Second"
                }

                logger.info { "test complete!" }
            }
        }
    }
})
