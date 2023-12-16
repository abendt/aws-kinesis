package kinesis

import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.StringSpec
import kotlin.time.Duration.Companion.seconds
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisFixture

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

    fun createKinesisClient() = KinesisClient.builder().configureForLocalstack(localstack).build()

    val fixture: KinesisFixture by lazy {
        KinesisFixture(createKinesisClient())
    }

    val minute =
        eventuallyConfig {
            duration = 60.seconds
            interval = 1.seconds
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
