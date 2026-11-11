package kinesis

import org.apache.camel.component.aws2.kinesis.consumer.KinesisOffset
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeAction
import org.apache.camel.resume.Cacheable
import org.apache.camel.resume.Offset
import org.apache.camel.resume.OffsetKey
import org.apache.camel.resume.ResumeAction
import org.apache.camel.resume.ResumeActionAware
import org.apache.camel.resume.cache.ResumeCache

/**
 * Resume adapter for the Camel Kinesis consumer: each shard starts after the last sequence number in the resume cache.
 *
 * Camel finds this class via META-INF/services/org/apache/camel/adapter-factory and sets the cache from the
 * resume strategy configuration.
 */
class KinesisCacheResumeAdapter :
    ResumeActionAware,
    Cacheable {
    // the consumer polls shards in parallel and calls setResumeAction() and resume() on the same thread
    private val resumeAction = ThreadLocal<KinesisResumeAction>()

    private lateinit var cache: ResumeCache<String>

    override fun setResumeAction(resumeAction: ResumeAction) {
        this.resumeAction.set(resumeAction as KinesisResumeAction)
    }

    override fun resume() {
        val action = resumeAction.get()
        resumeAction.remove()

        val offset = cache.get(action.shardId, KinesisOffset::class.java) ?: return
        action.evalEntry(action.shardId, checkNotNull(offset.value) { "empty offset for shard ${action.shardId}" })
    }

    @Suppress("UNCHECKED_CAST")
    override fun setCache(cache: ResumeCache<*>) {
        // KinesisResumeStrategy stores the offsets with the shard id as key
        this.cache = cache as ResumeCache<String>
    }

    override fun getCache(): ResumeCache<*> = cache

    // KinesisResumeStrategy writes the offsets to the cache itself
    override fun add(
        key: OffsetKey<*>,
        offset: Offset<*>,
    ): Boolean = throw UnsupportedOperationException("offsets are written by KinesisResumeStrategy")
}
