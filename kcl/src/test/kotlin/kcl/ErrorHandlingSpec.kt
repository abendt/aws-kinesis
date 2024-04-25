package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class ErrorHandlingSpec : KclTestBase({
    "failing events are not retried" {
        withKinesisStream {
            withKinesisConsumer(shouldFailPermanentlyOn = setOf("First")) {
                sendEvent("First")

                eventually(60.seconds) {
                    processorInvoked shouldBe 1
                }

                sendEvent("Second")

                eventually(30.seconds) {
                    eventsReceived shouldContain "Second"
                }

                eventsReceived shouldNotContain "First"
            }
        }
    }
})
