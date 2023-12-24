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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.regions.Region
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
        fun kinesisAsyncClient() = KinesisAsyncClient.builder().configureForLocalstack(localstack).build()

        @Bean
        fun kinesisFixture() = KinesisFixture(KinesisClient.builder().configureForLocalstack(localstack).build())

        @Bean
        fun dynamoDbAsyncClient() = DynamoDbAsyncClient.builder().configureForLocalstack(localstack).build()

        @Bean
        fun awsCredentialsProvider(): AwsCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey))
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

fun <B : AwsClientBuilder<B, C>, C> AwsClientBuilder<B, C>.configureForLocalstack(localstack: LocalStackContainer) =
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
