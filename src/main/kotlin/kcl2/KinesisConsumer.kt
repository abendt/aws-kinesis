package kcl2

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

interface ConsumerConfiguration<T> {
    fun processorInitialized()

    fun convertPayload(buffer: ByteBuffer): T

    fun processPayload(payload: List<Pair<String, T>>)
}

interface ConsumerControl {
    fun shutdown()
}

class LoggingWorkerStateChangeListener : WorkerStateChangeListener {
    val logger = KotlinLogging.logger {}
    var workerState: WorkerStateChangeListener.WorkerState? = null

    override fun onWorkerStateChange(newState: WorkerStateChangeListener.WorkerState) {
        if (workerState == null) {
            logger.info { "worker state $newState" }
        } else {
            logger.info { "worker state $workerState => $newState" }
        }

        workerState = newState
    }
}

class KinesisConsumer<T>(
    val applicationName: String,
    val streamName: String,
    val kinesisClient: KinesisAsyncClient,
    val dynamoDbClient: DynamoDbAsyncClient,
    val cloudWatchClient: CloudWatchAsyncClient,
    val config: ConsumerConfiguration<T>,
) {
    val logger = KotlinLogging.logger {}

    @Volatile
    private var scheduler: Scheduler? = null

    @Volatile
    private var thread: Thread? = null

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
                MyShardRecordProcessorFactory(
                    config,
                    object : ConsumerControl {
                        override fun shutdown() {
                            stop()
                        }
                    },
                ),
            )

        scheduler =
            Scheduler(
                configsBuilder.checkpointConfig(),
                configsBuilder.coordinatorConfig().workerStateChangeListener(LoggingWorkerStateChangeListener()),
                configsBuilder.leaseManagementConfig()
                    .shardSyncIntervalMillis(2000)
                    .failoverTimeMillis(2000)
                    .listShardsBackoffTimeInMillis(2000),
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
        thread =
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

        thread?.join()
    }
}

class MyShardRecordProcessorFactory<T>(
    val config: ConsumerConfiguration<T>,
    val control: ConsumerControl,
) : ShardRecordProcessorFactory {
    val logger = KotlinLogging.logger {}

    private val checkpointStrategyFactory = CheckpointStrategyFactory()

    override fun shardRecordProcessor(): ShardRecordProcessor {
        logger.info { "create record processor" }

        return MyShardProcessor(config, control, checkpointStrategyFactory.checkpointStrategy())
    }
}

class MyShardProcessor<T>(
    private val config: ConsumerConfiguration<T>,
    private val control: ConsumerControl,
    private val checkpointStrategy: CheckpointStrategy,
) : ShardRecordProcessor {
    private val logger = KotlinLogging.logger {}
    private var processorFailed = false

    override fun initialize(initialization: InitializationInput) {
        logger.info { "initialize $initialization" }

        config.processorInitialized()
    }

    override fun processRecords(processRecords: ProcessRecordsInput) {
        logger.info { "processRecords $processRecords" }

        try {
            if (!processorFailed) {
                val mapped =
                    processRecords.records().map {
                        it.partitionKey() to config.convertPayload(it.data())
                    }

                config.processPayload(mapped)

                checkpointStrategy.checkpointRecords(processRecords)
            }
        } catch (e: Exception) {
            processorFailed = true
            logger.error { "processor is not supposed to fail here. Will stop accepting events and shutdown the worker!" }
            control.shutdown()
        }
    }

    override fun leaseLost(leaseLost: LeaseLostInput) {
        logger.info { "leaseLost" }
    }

    override fun shardEnded(shardEnded: ShardEndedInput) {
        logger.info { "shardEnded" }

        checkpointStrategy.commitPending(shardEnded.checkpointer())
    }

    override fun shutdownRequested(shutdownRequested: ShutdownRequestedInput) {
        logger.info { "shutdownRequested" }

        checkpointStrategy.commitPending(shutdownRequested.checkpointer())
    }
}
