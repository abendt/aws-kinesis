package sample

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.GenericMessage
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisClient

@SpringBootTest
@Import(TestChannelBinderConfiguration::class)
class KinesisConsumerSpec : StringSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var input: InputDestination

    @MockkBean
    lateinit var myService: MyService

    @TestConfiguration
    class MyConfiguration {

        /**
         * We need to provide some dummy credentials as auto config will otherwise fail on GitHub
         */
        @Bean
        fun awsCredentialsProvider(): AwsCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy"))
    }

    init {
        "can send event" {
            input.send(GenericMessage("hello".toByteArray()))

            verify(timeout = 5000) { myService.processEvent("hello") }
        }
    }
}
