package sample

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class MyService {
    val logger = KotlinLogging.logger {}

    fun processEvent(payload: String) {
        logger.info { "processEvent $payload" }
    }
}
