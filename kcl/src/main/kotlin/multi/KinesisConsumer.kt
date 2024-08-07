package multi

import java.nio.ByteBuffer
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import mu.KotlinLogging
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.kinesis.common.ConfigsBuilder
import software.amazon.kinesis.common.InitialPositionInStream
import software.amazon.kinesis.common.InitialPositionInStreamExtended
import software.amazon.kinesis.common.StreamConfig
import software.amazon.kinesis.common.StreamIdentifier
import software.amazon.kinesis.coordinator.Scheduler
import software.amazon.kinesis.coordinator.WorkerStateChangeListener
import software.amazon.kinesis.lifecycle.events.InitializationInput
import software.amazon.kinesis.lifecycle.events.LeaseLostInput
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput
import software.amazon.kinesis.lifecycle.events.ShardEndedInput
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput
import software.amazon.kinesis.processor.FormerStreamsLeasesDeletionStrategy
import software.amazon.kinesis.processor.MultiStreamTracker
import software.amazon.kinesis.processor.ShardRecordProcessor
import software.amazon.kinesis.processor.ShardRecordProcessorFactory
import software.amazon.kinesis.retrieval.polling.PollingConfig

interface KinesisConsumerConfiguration<T> {
    fun processorInitialized()

    fun convertPayload(buffer: ByteBuffer): T

    fun processPayload(payload: T)
}

class KinesisConsumer<T>(
    val applicationName: String,
    val streamName: List<String>,
    val kinesisClient: KinesisAsyncClient,
    val dynamoDbClient: DynamoDbAsyncClient,
    val cloudWatchClient: CloudWatchAsyncClient,
    val config: KinesisConsumerConfiguration<T>,
) {
    val logger = KotlinLogging.logger {}

    @Volatile
    private var scheduler: Scheduler? = null

    @Volatile
    private var thread: Thread? = null

    fun start() {
        val workerIdentifier = UUID.randomUUID().toString()

        val streamConfigs =
            streamName.map {
                kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(it).build())
            }.map {
                val response = it.get()

                StreamConfig(
                    StreamIdentifier.multiStreamInstance(
                        Arn.fromString(response.streamDescription().streamARN()),
                        response.streamDescription().streamCreationTimestamp().epochSecond,
                    ),
                    InitialPositionInStreamExtended.newInitialPosition(InitialPositionInStream.TRIM_HORIZON),
                )
            }

        val tracker: MultiStreamTracker =
            object : MultiStreamTracker {
                override fun streamConfigList(): List<StreamConfig> = streamConfigs

                override fun formerStreamsLeasesDeletionStrategy(): FormerStreamsLeasesDeletionStrategy {
                    return object : FormerStreamsLeasesDeletionStrategy.AutoDetectionAndDeferredDeletionStrategy() {
                        override fun waitPeriodToDeleteFormerStreams(): Duration {
                            return Duration.of(30, ChronoUnit.MINUTES)
                        }
                    }
                }
            }

        val configsBuilder =
            ConfigsBuilder(
                tracker,
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
                configsBuilder.leaseManagementConfig()
                    .shardSyncIntervalMillis(2000)
                    .failoverTimeMillis(2000)
                    .listShardsBackoffTimeInMillis(2000),
                configsBuilder.lifecycleConfig(),
                configsBuilder.metricsConfig(),
                configsBuilder.processorConfig(),
                configsBuilder
                    .retrievalConfig().retrievalSpecificConfig(PollingConfig(kinesisClient)),
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
        logger.info { "checkpointed ${processRecords.records().last().sequenceNumber()}" }
    }

    override fun leaseLost(leaseLost: LeaseLostInput) {
        logger.info { "leaseLost" }
    }

    override fun shardEnded(shardEnded: ShardEndedInput) {
        logger.info { "shardEnded" }

        lastSequenceNumber?.let {
            shardEnded.checkpointer().checkpoint(it)
            logger.info { "Checkpointed previous sequence" }
        }
    }

    override fun shutdownRequested(shutdownRequested: ShutdownRequestedInput) {
        logger.info { "shutdownRequested" }

        lastSequenceNumber?.let {
            shutdownRequested.checkpointer().checkpoint(it)
            logger.info { "Checkpointed previous sequence" }
        }
    }
}
