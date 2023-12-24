package kinesis

import mu.KotlinLogging
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.kinesis.KinesisClient

class KinesisBasicRouteBuilder(
    @Value("\${stream-name:my-stream}") val streamName: String,
    val kinesisClient: KinesisClient,
    val myService: MyService,
) : RouteBuilder() {
    val logger = KotlinLogging.logger {}

    override fun configure() {
        logger.info { "configure KinesisClusteredRoute" }
        context.registry.bind("amazonKinesisClient", kinesisClient)

        from("aws2-kinesis://$streamName?amazonKinesisClient=#amazonKinesisClient")
            .log(LoggingLevel.INFO, logger, "received '\${body}'")
            .bean(myService, "processEvent")
    }
}
