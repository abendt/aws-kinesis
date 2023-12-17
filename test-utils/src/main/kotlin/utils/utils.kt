package utils

import java.util.UUID
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import org.awaitility.kotlin.await
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException
import software.amazon.awssdk.services.kinesis.model.StreamStatus

interface KinesisStreamTestScope {
    val streamName: String
    val shardCount: Int

    fun sendEvent(
        payload: String,
        partitionKey: String = "partition-1",
    )

    fun sendEvents(
        payload: List<String>,
        partitionKey: String = "partition-1",
    )
}

class KinesisFixture(val kinesisClient: KinesisClient) {
    val logger = KotlinLogging.logger {}

    suspend fun withKinesisStream(
        name: String = UUID.randomUUID().toString(),
        withShards: Int = 1,
        block: suspend KinesisStreamTestScope.() -> Unit,
    ) {
        try {
            createKinesisStream(name, withShards)
        } catch (e: ResourceInUseException) {
        }

        try {
            block(
                object : KinesisStreamTestScope {
                    override val streamName: String
                        get() = name
                    override val shardCount: Int
                        get() = withShards

                    override fun sendEvent(
                        payload: String,
                        partitionKey: String,
                    ) {
                        kinesisClient.putRecord(
                            PutRecordRequest.builder()
                                .streamName(name)
                                .partitionKey(partitionKey)
                                .data(SdkBytes.fromUtf8String(payload)).build(),
                        )

                        logger.info { "sent event $payload" }
                    }

                    override fun sendEvents(
                        payload: List<String>,
                        partitionKey: String,
                    ) {
                        kinesisClient.putRecords(
                            PutRecordsRequest.builder().streamName(name).records(
                                payload.map {
                                    PutRecordsRequestEntry.builder().partitionKey(partitionKey).data(SdkBytes.fromUtf8String(it)).build()
                                },
                            ).build(),
                        )

                        logger.info { "sent events $payload" }
                    }
                },
            )
        } finally {
            kinesisClient.deleteStream(DeleteStreamRequest.builder().streamName(name).build())
        }
    }

    private fun createKinesisStream(
        name: String,
        withShardCount: Int = 1,
    ) {
        logger.info { "creating stream $name" }
        kinesisClient.createStream(CreateStreamRequest.builder().shardCount(withShardCount).streamName(name).build())

        await.atMost(10, TimeUnit.SECONDS).until {
            val response = kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(name).build())

            response.streamDescription().streamStatus() == StreamStatus.ACTIVE
        }

        logger.info { "stream $name is available" }
    }
}
