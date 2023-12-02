package kcl

import java.nio.ByteBuffer
import java.util.UUID
import mu.KotlinLogging
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.kinesis.common.ConfigsBuilder
import software.amazon.kinesis.common.InitialPositionInStream
import software.amazon.kinesis.common.InitialPositionInStreamExtended
import software.amazon.kinesis.coordinator.Scheduler
import software.amazon.kinesis.coordinator.WorkerStateChangeListener
import software.amazon.kinesis.lifecycle.events.InitializationInput
import software.amazon.kinesis.lifecycle.events.LeaseLostInput
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput
import software.amazon.kinesis.lifecycle.events.ShardEndedInput
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput
import software.amazon.kinesis.processor.ShardRecordProcessor
import software.amazon.kinesis.processor.ShardRecordProcessorFactory
import software.amazon.kinesis.processor.SingleStreamTracker
import software.amazon.kinesis.retrieval.polling.PollingConfig

interface KinesisConsumerConfiguration<T> {
    fun processorInitialized()

    fun convertPayload(buffer: ByteBuffer): T

    fun processPayload(payload: T)
}

class KinesisConsumer<T>(
    val applicationName: String,
    val streamName: String,
    val kinesisClient: KinesisAsyncClient,
    val dynamoDbClient: DynamoDbAsyncClient,
    val cloudWatchClient: CloudWatchAsyncClient,
    val config: KinesisConsumerConfiguration<T>,
) {
    val logger = KotlinLogging.logger {}

    @Volatile
    private var scheduler: Scheduler? = null

    fun start() {
        val workerIdentifier = UUID.randomUUID().toString()

        val configsBuilder =
            ConfigsBuilder(
                streamName,
                applicationName,
                kinesisClient,
                dynamoDbClient,
                cloudWatchClient,
                workerIdentifier,
                MyShardRecordProcessorFactory(config),
            )

        var workerState: WorkerStateChangeListener.WorkerState? = null
        val delegatingListener =
            WorkerStateChangeListener {
                if (workerState == null) {
                    logger.info { "worker state $it" }
                } else {
                    logger.info { "worker state $workerState => $it" }
                }

                workerState = it
            }

        scheduler =
            Scheduler(
                configsBuilder.checkpointConfig(),
                configsBuilder.coordinatorConfig().workerStateChangeListener(delegatingListener),
                configsBuilder.leaseManagementConfig(),
                configsBuilder.lifecycleConfig(),
                configsBuilder.metricsConfig(),
                configsBuilder.processorConfig(),
                configsBuilder
                    .retrievalConfig().retrievalSpecificConfig(PollingConfig(streamName, kinesisClient))
                    .streamTracker(
                        SingleStreamTracker(
                            streamName,
                            InitialPositionInStreamExtended.newInitialPosition(InitialPositionInStream.TRIM_HORIZON),
                        ),
                    ),
            )

        logger.info { "starting scheduler" }
        Thread(scheduler).apply {
            isDaemon = true
            start()
        }
        logger.info { "started scheduler" }
    }

    fun stop() {
        scheduler?.let {
            logger.info { "stopping scheduler" }
            val stopped = it.startGracefulShutdown().get()
            logger.info { "stopped gracefully $stopped" }
        }
    }
}

class MyShardRecordProcessorFactory<T>(val config: KinesisConsumerConfiguration<T>) :
    ShardRecordProcessorFactory {

    val logger = KotlinLogging.logger {}

    override fun shardRecordProcessor(): ShardRecordProcessor {
        logger.info { "create record processor" }

        return MyShardProcessor(config)
    }
}

class MyShardProcessor<T>(val config: KinesisConsumerConfiguration<T>) : ShardRecordProcessor {

    val logger = KotlinLogging.logger {}
    var lastSequenceNumber: String? = null

    override fun initialize(initialization: InitializationInput) {
        logger.info { "initialize $initialization" }

        config.processorInitialized()
    }

    override fun processRecords(processRecords: ProcessRecordsInput) {
        logger.info { "processRecords $processRecords" }

        processRecords.records().asSequence().map {
            config.convertPayload(it.data())
        }.forEach { config.processPayload(it) }

        lastSequenceNumber = processRecords.records().last().sequenceNumber()
        processRecords.checkpointer().checkpoint()
    }

    override fun leaseLost(leaseLost: LeaseLostInput) {
        logger.info { "leaseLost $leaseLost" }
    }

    override fun shardEnded(shardEnded: ShardEndedInput) {
        logger.info { "shardEnded $shardEnded" }

        lastSequenceNumber?.let {
            shardEnded.checkpointer().checkpoint(it)
        }
    }

    override fun shutdownRequested(shutdownRequested: ShutdownRequestedInput) {
        logger.info { "shutdownRequested $shutdownRequested" }

        lastSequenceNumber?.let {
            shutdownRequested.checkpointer().checkpoint(it)
        }
    }
}
