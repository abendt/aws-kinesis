package config

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.TestContainerSpecExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisClient
import utils.KinesisFixture
import utils.LOCALSTACK_IMAGE
import utils.KinesisStreamTestScope

abstract class KinesisTestBase(
    block: KinesisTestBase.() -> Unit = {},
) : StringSpec() {
    val logger = KotlinLogging.logger {}

    val localstack =
        install(TestContainerSpecExtension(LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))))

    val kinesisClient =
        KinesisAsyncClient
            .builder()
            .configureForLocalstack(localstack)
            .build()

    val dynamoClient =
        DynamoDbAsyncClient
            .builder()
            .configureForLocalstack(localstack)
            .build()

    val cloudWatchClient =
        CloudWatchAsyncClient
            .builder()
            .configureForLocalstack(localstack)
            .build()

    init {
        block()
    }

    val kinesisFixture = KinesisFixture(KinesisClient.builder().configureForLocalstack(localstack).build())

    suspend fun withKinesisStream(
        withShards: Int = 1,
        block: suspend KinesisStreamTestScope.() -> Unit,
    ) {
        kinesisFixture.withKinesisStream(withShards = withShards, block = block)
    }
}
