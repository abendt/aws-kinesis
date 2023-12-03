package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.time.Duration.Companion.seconds

class ReconsumeAfterRestartSpec: KinesisConsumerBase( {
    "not committed is re-delivered after restart" {
        withKinesisStream {
            withKinesisConsumer(shouldFailPermanently = true) {
                sendEvent("Event")

                eventually(60.seconds) {
                    processorInvoked shouldBeEqual 1
                }
            }

            logger.info { "restarting consumer" }

            withKinesisConsumer {
                eventually(60.seconds) {
                    eventsReceived shouldHaveSize 1
                }
            }
        }
    }

    "committed is not re-delivered after restart" {
        withKinesisStream {
            withKinesisConsumer {
                sendEvent("First")

                eventually(60.seconds) {
                    eventsReceived shouldContain "First"
                }
            }

            sendEvent("Second")

            withKinesisConsumer {
                eventually(60.seconds) {
                    eventsReceived shouldContain "Second"
                }

                eventsReceived shouldNotContain "First"
            }
        }
    }
})
