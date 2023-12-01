package kcl

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import mu.KotlinLogging
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.StreamStatus
import software.amazon.kinesis.coordinator.WorkerStateChangeListener

class KinesisConsumerSpec : StringSpec() {
    val logger = KotlinLogging.logger {}

    val localstack =
        install(ContainerExtension(LocalStackContainer(DockerImageName.parse("localstack/localstack")))) {
        }

    val kinesisClient =
        KinesisAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    val dynamoClient =
        DynamoDbAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    val cloudWatchClient =
        CloudWatchAsyncClient.builder()
            .configureForLocalstack(localstack)
            .build()

    init {
        "can consume kinesis events" {
            givenStreamWithName("my-stream")

            val received = mutableListOf<String>()
            val consumerStarted = CountDownLatch(1)
            var workerState: WorkerStateChangeListener.WorkerState? = null
            val workerListener =
                WorkerStateChangeListener {
                    if (workerState == null) {
                        logger.info { "worker state $it" }
                    } else {
                        logger.info { "worker state $workerState => $it" }
                    }

                    workerState = it

                    if (it == WorkerStateChangeListener.WorkerState.STARTED) {
                        consumerStarted.countDown()
                    }
                }

            val consumer =
                KinesisConsumer("my-stream", kinesisClient, dynamoClient, cloudWatchClient, workerListener) {
                    received.add(it)
                }

            consumer.start()

            consumerStarted.await()

            kinesisClient.putRecord(
                PutRecordRequest.builder()
                    .streamName("my-stream")
                    .partitionKey("1")
                    .data(SdkBytes.fromUtf8String("Hello Kinesis!")).build(),
            ).get()

            eventually(60.seconds) {
                received shouldHaveSize 1
            }

            consumer.stop()
        }
    }

    suspend fun givenStreamWithName(name: String) {
        kinesisClient.createStream(CreateStreamRequest.builder().shardCount(1).streamName(name).build()).get()
        logger.info { "created stream $name" }

        eventually(10.seconds) {
            val response = kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(name).build()).get()

            response.streamDescription().streamStatus() shouldBe StreamStatus.ACTIVE

            logger.info { "stream $name is available ${response.streamDescription().streamARN()}" }
        }
    }
}

private fun <B : AwsClientBuilder<B, C>, C> AwsClientBuilder<B, C>.configureForLocalstack(localstack: LocalStackContainer) =
    also {
        it.endpointOverride(localstack.endpoint)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.accessKey,
                        localstack.secretKey,
                    ),
                ),
            )
            .region(Region.of(localstack.region))
    }
