package sample

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisFixture

@Import(LocalstackBase.LocalstackConfiguration::class)
abstract class LocalstackBase : StringSpec() {
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

    @Configuration
    class LocalstackConfiguration {
        @Bean
        fun kinesisAsyncClient() = KinesisAsyncClient.builder().configureForLocalstack(localstack).build()

        @Bean
        fun kinesisFixture() = KinesisFixture(KinesisClient.builder().configureForLocalstack(localstack).build())

        @Bean
        fun dynamoDbAsyncClient() = DynamoDbAsyncClient.builder().configureForLocalstack(localstack).build()

        @Bean
        fun awsCredentialsProvider(): AwsCredentialsProvider =
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
            )

        @Bean
        fun awsRegionProvider(): AwsRegionProvider = AwsRegionProvider { Region.AP_EAST_1 }
    }

    override fun extensions() = listOf(SpringExtension)
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
