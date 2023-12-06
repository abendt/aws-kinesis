package kinesis

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.StreamStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LocalstackBase.LocalstackConfiguration::class)
abstract class LocalstackBase : StringSpec() {
    val logger = KotlinLogging.logger {}

    companion object {
        val localstack: LocalStackContainer by lazy {
            LocalStackContainer(DockerImageName.parse("localstack/localstack")).withLogConsumer(
                Slf4jLogConsumer(
                    LoggerFactory.getLogger("localstack.\u26C8"),
                    true,
                ),
            ).also { it.start() }
        }
    }

    override fun extensions() = listOf(SpringExtension)

    override suspend fun afterSpec(spec: Spec) {
        localstack.stop()
    }

    @TestConfiguration
    class LocalstackConfiguration {
        @Bean
        fun kinesisClient() = KinesisClient.builder().configureForLocalstack(localstack).build()
    }

    @Autowired
    lateinit var kinesisClient: KinesisClient

    @LocalServerPort
    protected val port: Int = 0

    suspend fun createKinesisStream(
        name: String,
        withShardCount: Int = 1,
    ) {
        logger.info { "creating stream $name" }
        kinesisClient.createStream(CreateStreamRequest.builder().shardCount(withShardCount).streamName(name).build())

        eventually(10.seconds) {
            val response = kinesisClient.describeStream(DescribeStreamRequest.builder().streamName(name).build())

            response.streamDescription().streamStatus() shouldBe StreamStatus.ACTIVE

            logger.info { "stream $name is available" }
        }
    }

    fun sendEvent(
        streamName: String,
        key: String,
        payload: String,
    ) {
        kinesisClient.putRecord(
            PutRecordRequest.builder()
                .streamName(streamName)
                .partitionKey(key)
                .data(SdkBytes.fromUtf8String(payload)).build(),
        )

        logger.info { "sent event" }
    }
}

private fun <B : AwsClientBuilder<B, C>, C> AwsClientBuilder<B, C>.configureForLocalstack(localstack: LocalStackContainer) =
    endpointOverride(localstack.endpoint)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.accessKey,
                    localstack.secretKey,
                ),
            ),
        )
        .region(Region.of(localstack.region))
