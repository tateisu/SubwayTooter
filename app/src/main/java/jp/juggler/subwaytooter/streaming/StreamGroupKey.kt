package jp.juggler.subwaytooter.streaming

import jp.juggler.util.digestSHA256Base64Url
import java.util.concurrent.ConcurrentHashMap

// 同じ種類のストリーミングを複数のカラムで受信する場合がある
// subscribe/unsubscribe はまとめて行いたい
class StreamGroupKey(val streamKey: String) : ConcurrentHashMap<Int, StreamSpec>() {

    val channelId = streamKey.digestSHA256Base64Url()

    override fun hashCode(): Int = streamKey.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other is StreamGroupKey) return streamKey == other.streamKey
        return false
    }

    val spec: StreamSpec
        get() = this.values.first()
}
