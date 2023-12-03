package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import kotlin.time.Duration.Companion.seconds

class ConsumeKinesisEventSpec : KinesisConsumerBase({
    "can consume kinesis events" {
        withKinesisStream {
            sendEvent("First")
            withKinesisConsumer {
                sendEvent("Second")

                eventually(60.seconds) {
                    eventsReceived shouldContain "First"
                    eventsReceived shouldContain "Second"
                }
            }
        }
    }
})
