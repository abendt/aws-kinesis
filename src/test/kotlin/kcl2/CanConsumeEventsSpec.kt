package kcl2

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainAll
import kotlin.time.Duration.Companion.seconds

class CanConsumeEventsSpec : KinesisConsumerBase({

    "can consume events" {
        withKinesisStream {
            withKinesisConsumer {
                sendEvent(listOf("1", "2", "3"))

                eventually(30.seconds) {
                    eventsReceived shouldContainAll listOf("1", "2", "3")
                }
            }
        }
    }
})
