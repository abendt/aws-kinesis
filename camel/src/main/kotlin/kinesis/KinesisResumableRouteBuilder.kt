package kinesis

import mu.KotlinLogging
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.aws2.kinesis.consumer.KinesisConsumerOffsetProcessor
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategyConfiguration
import org.apache.camel.resume.cache.ResumeCache
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.kinesis.KinesisClient

class KinesisResumableRouteBuilder(
    @Value("\${stream-name:my-stream}") val streamName: String,
    val kinesisClient: KinesisClient,
    val myService: MyService,
    val resumeCache: ResumeCache<*>,
) : RouteBuilder() {
    val logger = KotlinLogging.logger {}

    override fun configure() {
        logger.info { "configure KinesisConsumeRoute" }
        context.registry.bind("amazonKinesisClient", kinesisClient)

        from("aws2-kinesis://$streamName?amazonKinesisClient=#amazonKinesisClient")
            // without this resume strategy the consumer will re-consume the whole stream after re-start
            .resumable().configuration(KinesisResumeStrategyConfiguration.builder().withResumeCache(resumeCache))
            .process(KinesisConsumerOffsetProcessor())
            .log(LoggingLevel.INFO, logger, "received '\${body}'")
            .bean(myService, "processEvent")
    }
}
