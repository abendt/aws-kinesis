package kcl2

import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import java.time.Duration
import java.time.Instant
import mu.KotlinLogging
import software.amazon.kinesis.exceptions.KinesisClientLibRetryableException
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput
import software.amazon.kinesis.processor.RecordProcessorCheckpointer

class CheckpointStrategyFactory(
    val batchSizeMax: Int = 500,
    val maxAge: Duration = Duration.ofMinutes(1),
    maxRetries: Int = 3,
    intervalFunction: IntervalFunction = IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 2.0),
) {
    val logger = KotlinLogging.logger {}

    private val retryConfig =
        RetryConfig.custom<Any>()
            .maxAttempts(maxRetries)
            .intervalFunction(intervalFunction)
            .retryExceptions(KinesisClientLibRetryableException::class.java)
            .failAfterMaxAttempts(true)
            .build()

    private val retryRegistry = RetryRegistry.of(retryConfig)

    fun checkpointStrategy(): CheckpointStrategy {
        val retry =
            retryRegistry.retry("retry-checkpointer").also {
                it.eventPublisher.onRetry {
                    logger.warn { "Retrying checkpoint because of: ${it.lastThrowable}" }
                }
            }

        return CheckpointStrategy(batchSizeMax, maxAge, retry)
    }
}

class CheckpointStrategy(
    private val batchSizeMax: Int,
    private val maxAge: Duration,
    private val retry: Retry,
) {
    private val logger = KotlinLogging.logger {}

    private var lastCommit: Instant = Instant.now()
    private var uncommitedCount: Int = 0
    private var lastSequence: String? = null

    fun checkpointRecords(processRecords: ProcessRecordsInput) {
        uncommitedCount += processRecords.records().size
        lastSequence = processRecords.records().last().sequenceNumber()

        val commitOnSize = uncommitedCount > batchSizeMax
        val commitOnAge =
            Duration.between(lastCommit, Instant.now()).minus(maxAge).isPositive

        if (commitOnSize || commitOnAge) {
            logger.debug {
                "committing pending size: $commitOnSize ($uncommitedCount > $batchSizeMax), age: $commitOnAge ($lastCommit > $maxAge)"
            }

            commitPending(processRecords.checkpointer(), lastSequence!!)
            uncommitedCount = 0
            lastCommit = Instant.now()
        }
    }

    fun commitPending(checkpointer: RecordProcessorCheckpointer) {
        lastSequence?.let {
            commitPending(checkpointer, it)
        }
    }

    private fun commitPending(
        checkpointer: RecordProcessorCheckpointer,
        sequenceNumber: String,
    ) {
        val commitWithRetry =
            Retry.decorateCallable(retry) {
                checkpointer.checkpoint(sequenceNumber)
                logger.debug { "checkpointed pending sequenceNumber $sequenceNumber" }
            }

        commitWithRetry.call()
    }
}
