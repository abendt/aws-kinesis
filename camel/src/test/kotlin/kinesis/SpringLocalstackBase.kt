package kinesis

import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import software.amazon.awssdk.services.kinesis.KinesisClient

abstract class SpringLocalstackBase : LocalstackBase() {
    override fun extensions() = listOf(SpringExtension)

    open class LocalstackConfiguration {
        @Bean
        open fun kinesisClient() = KinesisClient.builder().configureForLocalstack(localstack).build()
    }

    @LocalServerPort
    protected val port: Int = 0
}
