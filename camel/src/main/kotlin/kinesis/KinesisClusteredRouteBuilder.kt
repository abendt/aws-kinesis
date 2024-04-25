package kinesis

import mu.KotlinLogging
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.file.cluster.FileLockClusterService
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.kinesis.KinesisClient

class KinesisClusteredRouteBuilder(
    val fileLockDirectory: String,
    @Value("\${stream-name:my-stream}") val streamName: String,
    val kinesisClient: KinesisClient,
    val myService: MyService,
) : RouteBuilder() {
    val logger = KotlinLogging.logger {}

    override fun configure() {
        logger.info { "configure KinesisClusteredRoute" }
        context.registry.bind("amazonKinesisClient", kinesisClient)

        val lockClusterService =
            FileLockClusterService().apply {
                root = fileLockDirectory
            }
        context.addService(lockClusterService)

        from("master:my-ns:aws2-kinesis://$streamName?amazonKinesisClient=#amazonKinesisClient")
            // from("aws2-kinesis://$streamName?amazonKinesisClient=#amazonKinesisClient")
            .log(LoggingLevel.INFO, logger, "received '\${body}'")
            .bean(myService, "processEvent")
    }
}
