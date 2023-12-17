package sample

import java.util.function.Consumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ApplicationConfiguration {
    @Bean
    fun input(myService: MyService): Consumer<String> =
        Consumer {
            myService.processEvent(it)
        }
}
