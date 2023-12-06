package kcl2

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import software.amazon.kinesis.exceptions.ThrottlingException
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput
import software.amazon.kinesis.processor.RecordProcessorCheckpointer
import software.amazon.kinesis.retrieval.KinesisClientRecord

class CheckpointStrategySpec : StringSpec({
    "should checkpoint on size limit" {

        val sut = CheckpointStrategy(1, Duration.ofMinutes(1), aRetry())
        val checkpointer: RecordProcessorCheckpointer = mockk(relaxed = true)

        sut.checkpointRecords(
            ProcessRecordsInput.builder()
                .records(listOf(KinesisClientRecord.builder().sequenceNumber("1").build()))
                .checkpointer(checkpointer)
                .build(),
        )

        verify(exactly = 0) { checkpointer.checkpoint(any(String::class)) }

        sut.checkpointRecords(
            ProcessRecordsInput.builder()
                .records(listOf(KinesisClientRecord.builder().sequenceNumber("2").build()))
                .checkpointer(checkpointer)
                .build(),
        )

        verify { checkpointer.checkpoint("2") }
    }

    "should retry checkpoint on throttling exception" {
        val sut = CheckpointStrategy(0, Duration.ofMinutes(1), aRetry())
        val checkpointer: RecordProcessorCheckpointer = mockk(relaxed = true)

        every { checkpointer.checkpoint(any(String::class)) }
            .throws(ThrottlingException("for test"))
            .andThenJust(Runs)

        sut.checkpointRecords(
            ProcessRecordsInput.builder()
                .records(listOf(KinesisClientRecord.builder().sequenceNumber("1").build()))
                .checkpointer(checkpointer)
                .build(),
        )

        verify(atLeast = 2) { checkpointer.checkpoint("1") }
    }

    "retry checkpoint will eventually fail" {
        val sut =
            CheckpointStrategy(
                0,
                Duration.ofMinutes(1),
                Retry.of("retry", RetryConfig.custom<Any>().waitDuration(Duration.ofMillis(10)).build()),
            )
        val checkpointer: RecordProcessorCheckpointer = mockk(relaxed = true)

        every { checkpointer.checkpoint(any(String::class)) }
            .throws(ThrottlingException("for test"))

        shouldThrow<ThrottlingException> {
            sut.checkpointRecords(
                ProcessRecordsInput.builder()
                    .records(listOf(KinesisClientRecord.builder().sequenceNumber("1").build()))
                    .checkpointer(checkpointer)
                    .build(),
            )
        }
    }

    "should checkpoint on age" {
        val sut = CheckpointStrategy(10, Duration.ofMillis(10), aRetry())
        val checkpointer: RecordProcessorCheckpointer = mockk(relaxed = true)

        sut.checkpointRecords(
            ProcessRecordsInput.builder()
                .records(listOf(KinesisClientRecord.builder().sequenceNumber("1").build()))
                .checkpointer(checkpointer)
                .build(),
        )

        Thread.sleep(500)

        sut.checkpointRecords(
            ProcessRecordsInput.builder()
                .records(listOf(KinesisClientRecord.builder().sequenceNumber("2").build()))
                .checkpointer(checkpointer)
                .build(),
        )

        verify { checkpointer.checkpoint("2") }
    }
})

private fun aRetry() = Retry.of("retry", RetryConfig.ofDefaults())
