package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.util.JsonArray
import jp.juggler.util.LogCategory
import java.util.concurrent.ConcurrentHashMap

// 同じ種類のストリーミングを複数のカラムで受信する場合がある
// subscribe/unsubscribe はまとめて行いたい
class StreamGroup(val spec: StreamSpec) {
    companion object {
        private val log = LogCategory("StreamGroup")
    }

    val destinations = ConcurrentHashMap<Int, StreamRelation>()

    override fun hashCode(): Int = spec.keyString.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is StreamGroup) return spec.keyString == other.spec.keyString
        return false
    }

    fun eachCallback(
        channelId: String?,
        stream: JsonArray?,
        item: TimelineItem?,
        block: (callback: StreamCallback) -> Unit,
    ) {
        // skip if channel id is provided and not match
        if (channelId?.isNotEmpty() == true && channelId != spec.channelId) {
            if (StreamManager.traceDelivery) log.v("${spec.name} channelId not match.")
            return
        }

        destinations.values.forEach { dst ->
            try {
                if (stream != null && item != null) {
                    val column = dst.refColumn.get() ?: return@forEach
                    if (!dst.spec.streamFilter(column, stream, item)) {
                        if (StreamManager.traceDelivery) log.v("${spec.name} streamFilter not match. stream=$stream")
                        return@forEach
                    }
                }
                dst.refCallback.get()?.let { block(it) }
            } catch (ex: Throwable) {
                log.e(ex, "eachCallback failed.")
            }
        }
    }
}
