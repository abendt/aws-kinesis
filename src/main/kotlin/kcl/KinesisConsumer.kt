package kcl

import java.util.UUID
import mu.KotlinLogging
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.kinesis.common.ConfigsBuilder
import software.amazon.kinesis.common.InitialPositionInStream
import software.amazon.kinesis.common.InitialPositionInStreamExtended
import software.amazon.kinesis.coordinator.NoOpWorkerStateChangeListener
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

typealias KinesisCallback = (String) -> Unit

class KinesisConsumer(
    val streamName: String,
    val kinesisClient: KinesisAsyncClient,
    val dynamoDbClient: DynamoDbAsyncClient,
    val cloudWatchClient: CloudWatchAsyncClient,
    val workerListener: WorkerStateChangeListener = NoOpWorkerStateChangeListener(),
    val callback: KinesisCallback,
) {
    val logger = KotlinLogging.logger {}

    @Volatile
    private var scheduler: Scheduler? = null

    fun start() {
        val workerIdentifier = UUID.randomUUID().toString()

        val configsBuilder =
            ConfigsBuilder(
                streamName,
                streamName,
                kinesisClient,
                dynamoDbClient,
                cloudWatchClient,
                workerIdentifier,
                MyShardRecordProcessorFactory(callback),
            )

        // This will prohably only work with one worker ...
        var workerState: WorkerStateChangeListener.WorkerState? = null
        val delegatingListener =
            WorkerStateChangeListener {
                if (workerState == null) {
                    logger.info { "worker state $it" }
                } else {
                    logger.info { "worker state $workerState => $it" }
                }

                workerState = it

                workerListener.onWorkerStateChange(it)
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

class MyShardRecordProcessorFactory(val callback: KinesisCallback) : ShardRecordProcessorFactory {
    val logger = KotlinLogging.logger {}

    override fun shardRecordProcessor(): ShardRecordProcessor {
        logger.info { "create record processor" }

        return MyShardProcessor(callback)
    }
}

class MyShardProcessor(val callback: KinesisCallback) : ShardRecordProcessor {
    val logger = KotlinLogging.logger {}

    override fun initialize(initialization: InitializationInput) {
        logger.info { "initialize $initialization" }
    }

    override fun processRecords(processRecords: ProcessRecordsInput) {
        logger.info { "processRecords $processRecords" }

        processRecords.records().asSequence().map {
            SdkBytes.fromByteBuffer(it.data()).asUtf8String()
        }.forEach(callback)
    }

    override fun leaseLost(leaseLost: LeaseLostInput) {
        logger.info { "leaseLost $leaseLost" }
    }

    override fun shardEnded(shardEnded: ShardEndedInput) {
        logger.info { "shardEnded $shardEnded" }
    }

    override fun shutdownRequested(shutdownRequested: ShutdownRequestedInput) {
        logger.info { "shutdownRequested $shutdownRequested" }
    }
}
