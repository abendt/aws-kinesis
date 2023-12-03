package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.time.Duration.Companion.seconds

class ConsumeMultipleShardsSpec : KinesisConsumerBase({
    "can consume from multiple shards" {
        withKinesisStream(withShards = 2) {
            withKinesisConsumer {
                sendEvent("Hello Kinesis!", "1")
                sendEvent("Hello Kinesis!", "2")
                sendEvent("Hello Kinesis!", "3")
                sendEvent("Hello Kinesis!", "4")

                eventually(60.seconds) {
                    eventsReceived shouldHaveSize 4
                }
            }
        }
    }
})
