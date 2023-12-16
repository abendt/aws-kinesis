package kinesis

import mu.KotlinLogging
import org.apache.camel.Exchange
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.aws2.kinesis.Kinesis2Component
import org.apache.camel.support.resume.Resumables
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.kinesis.KinesisClient

@Component
class KinesisRouteBuilder(
    @Value("\${stream-name:my-stream}") val streamName: String,
    val kinesisClient: KinesisClient,
    val myService: MyService,
) : RouteBuilder() {
    val logger = KotlinLogging.logger {}

    override fun configure() {
        logger.info { "configure" }
        val kinesis = camelContext.getComponent("aws2-kinesis", Kinesis2Component::class.java)

        val configuration = kinesis.configuration

        kinesis.configuration =
            configuration.apply {
                amazonKinesisClient = kinesisClient
            }

        from("aws2-kinesis://$streamName")
            .process { ex ->
                ex.message.setHeader(
                    Exchange.OFFSET,
                    Resumables.of(
                        ex.message.getHeader("CamelAwsKinesisShardId"),
                        ex.message.getHeader("CamelAwsKinesisSequenceNumber"),
                    ),
                )
            }
            .log(LoggingLevel.INFO, logger, "received '\${body}'")
            .bean(myService, "processEvent")
    }
}
