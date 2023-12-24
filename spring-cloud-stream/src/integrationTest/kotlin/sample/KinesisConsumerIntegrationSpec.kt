package sample

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.verify
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisFixture

@SpringBootTest
class KinesisConsumerIntegrationSpec : StringSpec() {
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

    @TestConfiguration
    class MyConfiguration {
        @Bean
        fun kinesisAsyncClient() =
            KinesisAsyncClient.builder()
                .endpointOverride(localstack.endpoint)
                .build()

        @Bean
        fun kinesisFixture() =
            KinesisFixture(
                KinesisClient.builder()
                    .endpointOverride(localstack.endpoint)
                    .build(),
            )

        @Bean
        fun dynamoDbAsyncClient() =
            DynamoDbAsyncClient.builder()
                .endpointOverride(localstack.endpoint)
                .build()

        @Bean
        fun awsCredentialsProvider() =
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
            )

        @Bean
        fun awsRegionProvider() = AwsRegionProvider { Region.of(localstack.region) }
    }

    override fun extensions() = listOf(SpringExtension)

    @MockkBean(relaxed = true)
    lateinit var myService: MyService

    @Autowired
    lateinit var kinesisFixture: KinesisFixture

    init {
        "can send event" {
            kinesisFixture.withKinesisStream(name = "my-stream") {

                sendEvent("First")

                verify(timeout = 60_000) { myService.processEvent("First") }
            }
        }
    }
}
