package kinesis

import mu.KotlinLogging
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.aws2.kinesis.Kinesis2Component
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.kinesis.KinesisClient

@Component
class KinesisRouteBuilder(val kinesisClient: KinesisClient, val myService: MyService) : RouteBuilder() {
    val logger = KotlinLogging.logger {}

    override fun configure() {
        logger.info { "configure" }
        val kinesis = camelContext.getComponent("aws2-kinesis", Kinesis2Component::class.java)

        val configuration = kinesis.configuration

        kinesis.configuration =
            configuration.apply {
                amazonKinesisClient = kinesisClient
            }

        from("aws2-kinesis://my-stream")
            .log(LoggingLevel.INFO, logger, "received \${body}")
            .bean(myService, "processEvent")
    }
}
