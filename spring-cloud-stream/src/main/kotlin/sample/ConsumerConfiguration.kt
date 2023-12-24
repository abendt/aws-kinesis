package sample

import java.util.function.Consumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ConsumerConfiguration {
    @Bean
    fun input(myService: MyService): Consumer<String> =
        Consumer {
            myService.processEvent(it)
        }
}
