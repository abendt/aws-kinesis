package kinesis

import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import software.amazon.awssdk.services.kinesis.KinesisClient

abstract class SpringLocalstackBase : LocalstackBase() {
    open class LocalstackConfiguration {
        @Bean
        open fun kinesisClient() = KinesisClient.builder().configureForLocalstack(localstack).build()
    }

    @LocalServerPort
    protected val port: Int = 0
}
