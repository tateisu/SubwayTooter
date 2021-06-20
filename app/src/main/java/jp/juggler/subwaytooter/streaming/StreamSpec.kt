package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.TimelineItem
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
    val streamFilter: Column.(JsonArray, TimelineItem) -> Boolean = { _, _ -> true }
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

    fun paramsClone() =
        params.toString().decodeJsonObject()
}

private fun encodeStreamNameMastodon(root: JsonObject) = StringWriter()
    .also { sw ->
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
    }.toString()

private fun Column.streamSpecMastodon(): StreamSpec? {

    val root = type.streamKeyMastodon(this) ?: return null

    return StreamSpec(
        params = root,
        path = "/api/v1/streaming/?${root.encodeQuery()}",
        name = encodeStreamNameMastodon(root),
        streamFilter = type.streamFilterMastodon
    )
}

private fun encodeStreamNameMisskey(root: JsonObject) =
    StringWriter().also { sw ->
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
    }.toString()

fun Column.streamSpecMisskey(): StreamSpec? {

    // 認証トークンがないなら認証トークン必須のカラムにはアクセスできない
    if (accessInfo.misskeyApiToken == null &&
        when (type) {
            ColumnType.HOME,
            ColumnType.MISSKEY_HYBRID,
            ColumnType.NOTIFICATIONS,
            ColumnType.LIST_TL,
            ColumnType.MISSKEY_ANTENNA_TL -> true
            else -> false
        }
    ) return null

    val channelName = type.streamNameMisskey ?: return null

    val path = when {
        misskeyVersion >= 10 -> "/streaming"
        else -> type.streamPathMisskey9(this)
    } ?: return null

    val channelParam = type.streamParamMisskey(this) ?: JsonObject()
    val root = jsonObjectOf(CHANNEL to channelName, PARAMS to channelParam)

    return StreamSpec(
        params = root,
        path = path,
        name = encodeStreamNameMisskey(root),
        // no stream filter
    )
}

val Column.streamSpec: StreamSpec?
    get() = when {
        // 疑似アカウントではストリーミングAPIを利用できない
        // 2.1 では公開ストリームのみ利用できるらしい
        (accessInfo.isNA || accessInfo.isPseudo && !isPublicStream) -> null
        accessInfo.isMastodon -> streamSpecMastodon()
        accessInfo.isMisskey -> streamSpecMisskey()
        else -> null
    }
