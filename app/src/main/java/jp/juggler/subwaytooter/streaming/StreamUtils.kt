package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.encodeQuery
import jp.juggler.util.JsonObject
import jp.juggler.util.jsonObject

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



fun Column.getStreamingStatus() =
    app_state.streamManager.getStreamingStatus(access_info, internalId)
        ?: StreamIndicatorState.NONE

fun Column.getStreamSpec(): StreamSpec? {

    // 疑似アカウントではストリーミングAPIを利用できない
    // 2.1 では公開ストリームのみ利用できるらしい
    if (access_info.isNA || access_info.isPseudo && !isPublicStream) return null

    val streamParam = when {
        access_info.isMastodon -> streamArgMastodon()
        access_info.isMisskey -> streamArgMisskey()
        else ->null
    }

    return StreamSpec(
        column = this,
        streamPath = streamPath ?: return null,
        streamParam= streamParam ?: return null,
        streamCallback = streamCallback
    )
}
// "/api/v1/streaming/?stream=user"
// "/api/v1/streaming/?stream=public:local"
fun Column.streamArgMastodon() = when (type) {
    ColumnType.HOME, ColumnType.NOTIFICATIONS ->
        jsonObject(Column.STREAM to "user")

    ColumnType.LOCAL ->
        jsonObject(Column.STREAM to "public:local")

    ColumnType.FEDERATE ->
        jsonObject(Column.STREAM to if(remote_only) "public:remote" else "public" )

    ColumnType.LIST_TL ->
        jsonObject(Column.STREAM to "list",
            "list" to profile_id.toString() )

    ColumnType.DOMAIN_TIMELINE ->
        jsonObject(Column.STREAM to if (with_attachment) "public:domain:media" else "public:domain",
            "domain" to instance_uri )

    ColumnType.DIRECT_MESSAGES ->
        jsonObject(Column.STREAM to "direct")

    ColumnType.HASHTAG ->
        makeHashtagQueryParams().apply{
            put(Column.STREAM,if(instance_local) "hashtag:local" else "hashtag")
        }

    else -> null
}

fun Column.streamArgMisskey(): JsonObject?{
    fun x( channel: String, params: JsonObject = JsonObject() ) = JsonObject().apply {
        // put("type", "connect")
        put("body", JsonObject().apply {
            put("channel", channel)
            put("id", internalId.toString())
            put("params", params)
        })
    }

    return if (access_info.misskeyVersion < 11) {
        null
    } else {
        val misskeyApiToken = access_info.misskeyApiToken
        if (misskeyApiToken == null) {
            when (type) {
                ColumnType.LOCAL -> x("localTimeline")
                else -> null
            }
        } else {
            when (type) {
                ColumnType.HOME -> x("homeTimeline")
                ColumnType.LOCAL -> x("localTimeline")
                ColumnType.MISSKEY_HYBRID -> x("hybridTimeline")
                ColumnType.FEDERATE -> x("globalTimeline")
                ColumnType.NOTIFICATIONS -> x("main")

                ColumnType.MISSKEY_ANTENNA_TL ->
                    x(
                        "antenna",
                        jsonObject { put("antennaId", profile_id.toString()) }
                    )

                ColumnType.LIST_TL ->
                    x(
                        "userList",
                        jsonObject { put("listId", profile_id.toString()) }
                    )

                ColumnType.HASHTAG ->
                    x(
                        "hashtag",
                        jsonObject { put("q", hashtag) }
                    )

                else -> null
            }
        }
    }
}

fun Column.canSpeech(): Boolean {
    return canStreaming() && !isNotificationColumn
}

fun Column.canStreaming() = when {
    access_info.isNA -> false
    access_info.isMisskey -> streamPath != null
    access_info.isPseudo -> isPublicStream
    else -> streamPath != null
}

fun Column.canAutoRefresh() = streamPath != null

val Column.streamPath: String?
    get() = if (isMisskey) {
        val misskeyApiToken = access_info.misskeyApiToken
        when {

            // Misskey 11以降
            access_info.misskeyVersion >= 11 -> when {
                streamArgMisskey() == null -> null
                else -> "/streaming"
            }

            // Misskey 10 認証なし
            // Misskey 8.25 からLTLだけ認証なしでも見れるようになった
            misskeyApiToken ==null -> when (type) {
                ColumnType.LOCAL -> "/local-timeline"
                else -> null
            }

            // Misskey 10 認証あり
            else -> when (type) {
                ColumnType.HOME, ColumnType.NOTIFICATIONS -> "/"
                ColumnType.LOCAL -> "/local-timeline"
                ColumnType.MISSKEY_HYBRID -> "/hybrid-timeline"
                ColumnType.FEDERATE -> "/global-timeline"
                ColumnType.LIST_TL -> "/user-list?listId=$profile_id"
                ColumnType.MISSKEY_ANTENNA_TL -> "/antenna?listId=$profile_id"
                else -> null
            }

        }
    } else {
        when (val params = streamArgMastodon()) {
            null -> null
            else -> "/api/v1/streaming/?${params.encodeQuery()}"
        }
    }
