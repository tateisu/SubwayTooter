package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.util.JsonArray
import jp.juggler.util.LogCategory
import jp.juggler.util.decodeJsonObject
import java.util.concurrent.ConcurrentHashMap

// 同じ種類のストリーミングを複数のカラムで受信する場合がある
// subscribe/unsubscribe はまとめて行いたい
class StreamGroupKey(val spec: StreamSpec) {
    companion object {
        private val log = LogCategory("StreamGroupKey")
    }

    val keyString = spec.keyString
    val channelId = spec.channelId

    val destinations = ConcurrentHashMap<Int, StreamDestination>()

    override fun hashCode(): Int = keyString.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other is StreamGroupKey) return keyString == keyString
        return false
    }

    fun paramsClone() =
        spec.params.toString().decodeJsonObject()

    fun eachCallback(channelId: String?, stream: JsonArray?, item: TimelineItem?, block: (callback: StreamCallback) -> Unit) {
        // skip if channel id is provided and not match
        if (channelId?.isNotEmpty() == true && channelId != this.channelId) return

        val strStream = stream?.joinToString { "," }

        destinations.values.forEach { dst ->
            try {
                if (strStream != null && item != null) {
                    val column = dst.refColumn.get() ?: return@forEach
                    if (!dst.spec.streamFilter(column, strStream, item)) return@forEach
                }
                dst.refCallback.get()?.let { block(it) }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }
    }
}
