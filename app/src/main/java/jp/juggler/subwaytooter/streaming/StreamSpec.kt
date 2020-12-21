package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.encodeQuery
import jp.juggler.subwaytooter.makeHashtagQueryParams
import jp.juggler.subwaytooter.streaming.StreamSpec.Companion.CHANNEL
import jp.juggler.subwaytooter.streaming.StreamSpec.Companion.PARAMS
import jp.juggler.subwaytooter.streaming.StreamSpec.Companion.STREAM
import jp.juggler.util.*
import java.io.StringWriter

private fun StringWriter.appendValue(v: Any?) {
    when (v) {
        is JsonArray -> {
            append('[')
            v.forEachIndexed { i, child ->
                if (i > 0) append(',')
                appendValue(child)
            }
            append(']')
        }
        is JsonObject -> {
            append('{')
            v.entries.sortedBy { it.key }.forEachIndexed { i, child ->
                if (i > 0) append(',')
                append(child.key)
                append('=')
                appendValue(child)
            }
            append('}')
        }
        else -> append(v.toString())
    }
}


class StreamSpec(
    val params: JsonObject,
    val path: String,
    val name: String,
    val streamFilter: Column.(String?,TimelineItem)->Boolean = { _, _ -> true }
) {
    companion object {
        const val STREAM = "stream"
        const val CHANNEL = "channel"
        const val PARAMS = "params"
    }

    val keyString = "$path?${params.toString(indentFactor = 0, sort = true)}"

    val channelId = keyString.digestSHA256Base64Url()

    override fun hashCode(): Int = keyString.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is StreamSpec) return keyString == other.keyString
        return false
    }

    fun match(stream: JsonArray): Boolean {
        return true
    }
}

private fun Column.streamKeyMastodon(): StreamSpec? {
    val root = type.streamKeyMastodon(this) ?: return null
    val filter = type.streamFilterMastodon

    val path = "/api/v1/streaming/?${root.encodeQuery()}"

    val sw = StringWriter()
    synchronized(sw.buffer) {
        sw.append(root.string(STREAM)!!)
        root.entries.sortedBy { it.key }.forEach { pair ->
            val (k, v) = pair
            if (k != STREAM && v !is JsonArray && v !is JsonObject) {
                sw.append(',').append(k).append('=').appendValue(v)
            }
        }
        root.entries.sortedBy { it.key }.forEach { pair ->
            val (k, v) = pair
            if (v is JsonArray || v is JsonObject) {
                sw.append(',').append(k).append('=').appendValue(v)
            }
        }
    }

    return StreamSpec(root, path, sw.toString(),streamFilter=filter)
}


fun Column.streamKeyMisskey(): StreamSpec? {

    // 使われ方は StreamConnection.subscribe を参照のこと
    fun x(channel: String, params: JsonObject = JsonObject()) =
        jsonObject(CHANNEL to channel, PARAMS to params)

    val misskeyApiToken = access_info.misskeyApiToken

    val root = when (misskeyApiToken) {
        null -> when (type) {
            ColumnType.LOCAL -> x("localTimeline")
            else -> null
        }

        else -> when (type) {
            ColumnType.HOME ->
                x("homeTimeline")
            ColumnType.LOCAL ->
                x("localTimeline")
            ColumnType.MISSKEY_HYBRID ->
                x("hybridTimeline")
            ColumnType.FEDERATE ->
                x("globalTimeline")
            ColumnType.NOTIFICATIONS ->
                x("main")

            ColumnType.MISSKEY_ANTENNA_TL ->
                x("antenna", jsonObject { put("antennaId", profile_id.toString()) })

            ColumnType.LIST_TL ->
                x("userList", jsonObject { put("listId", profile_id.toString()) })

            ColumnType.HASHTAG ->
                x("hashtag", jsonObject { put("q", hashtag) })

            else -> null
        }
    } ?: return null

    val path = when {
        // Misskey 11以降は統合されてる
        misskeyVersion >= 11 -> "/streaming"

        // Misskey 10 認証なし
        // Misskey 8.25 からLTLだけ認証なしでも見れるようになった
        access_info.isPseudo -> when (type) {
            ColumnType.LOCAL -> "/local-timeline"
            else -> null
        }

        // Misskey 10 認証あり
        // Misskey 8.25 からLTLだけ認証なしでも見れるようになった
        else -> when (type) {
            ColumnType.HOME, ColumnType.NOTIFICATIONS -> "/"
            ColumnType.LOCAL -> "/local-timeline"
            ColumnType.MISSKEY_HYBRID -> "/hybrid-timeline"
            ColumnType.FEDERATE -> "/global-timeline"
            ColumnType.LIST_TL -> "/user-list?listId=${profile_id.toString()}"
            // タグやアンテナには対応しない
            else -> null
        }
    } ?: return null

    val sw = StringWriter()
    synchronized(sw.buffer) {
        sw.append(root.string(CHANNEL)!!)
        val params = root.jsonObject(PARAMS)!!
        params.entries.sortedBy { it.key }.forEach { pair ->
            val (k, v) = pair
            if (v !is JsonArray && v !is JsonObject) {
                sw.append(',').append(k).append('=').appendValue(v)
            }
        }
        params.entries.sortedBy { it.key }.forEach { pair ->
            val (k, v) = pair
            if (v is JsonArray || v is JsonObject) {
                sw.append(',').append(k).append('=').appendValue(v)
            }
        }
    }

    return StreamSpec(root, path, sw.toString())
}

// 公開ストリームなら真
val Column.isPublicStream: Boolean
    get() {
        return when (type) {
            ColumnType.LOCAL,
            ColumnType.FEDERATE,
            ColumnType.HASHTAG,
            ColumnType.LOCAL_AROUND,
            ColumnType.FEDERATED_AROUND,
            ColumnType.DOMAIN_TIMELINE -> true

            else -> false
        }
    }

val Column.streamSpec: StreamSpec?
    get() = when {
        // 疑似アカウントではストリーミングAPIを利用できない
        // 2.1 では公開ストリームのみ利用できるらしい
        (access_info.isNA || access_info.isPseudo && !isPublicStream) -> null
        access_info.isMastodon -> streamKeyMastodon()
        access_info.isMisskey -> streamKeyMisskey()
        else -> null
    }


fun Column.canStreaming() = when {
    access_info.isNA -> false
    access_info.isPseudo -> isPublicStream && streamSpec != null
    else -> streamSpec != null
}

fun Column.canAutoRefresh() = canStreaming()
