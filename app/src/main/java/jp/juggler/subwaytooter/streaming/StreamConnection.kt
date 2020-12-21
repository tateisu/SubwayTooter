package jp.juggler.subwaytooter.streaming

import android.os.SystemClock
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ProtocolException
import java.net.SocketException
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class StreamConnection(
    private val manager: StreamManager,
    private val server: StreamGroupAcct,
    val name: String,
    val streamKey: String? = null // null if merged connection
) : WebSocketListener() {

    companion object{
        private val log = LogCategory("StreamConnection")

        private const val misskeyAliveInterval = 60000L

        val reAuthorizeError = "authorize".asciiPattern(Pattern.CASE_INSENSITIVE)
    }

    private val isDisposed = AtomicBoolean(false)

    val client = TootApiClient(manager.context, callback = object : TootApiCallback {
        override val isApiCancelled: Boolean
            get() = isDisposed.get()

    }).apply {
        account = server.accessInfo
    }

    private val _status = AtomicReference(StreamStatus.Closed)

    private var status: StreamStatus
        get() = _status.get()
        set(value) {
            _status.set(value)
            eachCallback { it.onListeningStateChanged(value) }
        }

    private val socket = AtomicReference<WebSocket>(null)

    private var lastAliveSend = 0L

    private val subscription = ConcurrentHashMap<String, StreamGroupKey>()

    // Misskeyの投稿キャプチャ
    private val capturedId = HashSet<EntityId>()

    //        internal val callback_list = LinkedList<StreamCallback>()
    //        internal val parser : TootParser =
    //            TootParser(context, access_info, highlightTrie = highlight_trie, fromStream = true)

    ///////////////////////////////////////////////////////////////////
    // methods

    fun dispose() {
        isDisposed.set(true)
        socket.get()?.cancel()
        socket.set(null)
    }

    fun getStreamingStatus(streamKey: String?) = when {
        streamKey == null -> null

        status != StreamStatus.Open || null == socket.get() ->
            StreamIndicatorState.REGISTERED

        subscription[streamKey] == null ->
            StreamIndicatorState.REGISTERED

        else -> StreamIndicatorState.LISTENING
    }

    private inline fun eachCallback(channelId: String? = null, block: (callback: StreamCallback) -> Unit) {
        synchronized(this) {
            if (isDisposed.get()) return@synchronized
            if (streamKey == null) {
                server.groups.values.forEach { group ->
                    if (channelId?.isNotEmpty() == true && channelId != group.channelId) {
                        // skip if channel id is provided and not match
                    } else {
                        group.values.forEach { spec ->
                            try {
                                block(spec.streamCallback)
                            } catch (ex: Throwable) {
                                log.trace(ex)
                            }
                        }
                    }
                }
            } else {
                try {
                    val group = server.groups[streamKey]
                    if (group == null) {
                        // skip if missing group for streamKey
                    } else if (channelId?.isNotEmpty() == true && channelId != group.channelId) {
                        // skip if channel id is provided and not match
                    } else {
                        group.values.forEach { spec ->
                            try {
                                block(spec.streamCallback)
                            } catch (ex: Throwable) {
                                log.trace(ex)
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
        }
    }


    private fun fireTimelineItem(item: TimelineItem?, channelId: String? = null) {
        item ?: return
        eachCallback(channelId) { it.onTimelineItem(item, channelId) }
    }

    // コールバックによってrunOnMainLooperの有無が異なるの直したい！

    private fun fireNoteUpdated(ev: MisskeyNoteUpdate, channelId: String? = null) {
        runOnMainLooper { eachCallback(channelId) { it.onNoteUpdated(ev, channelId) } }
    }

    private fun fireDeleteId(id: EntityId) {
        val tl_host = server.accessInfo.apiHost
        runOnMainLooper {
            synchronized(this) {
                if (isDisposed.get()) return@synchronized
                if (Pref.bpDontRemoveDeletedToot(manager.appState.pref)) return@synchronized

                manager.appState.columnList.forEach {
                    runCatching { it.onStatusRemoved(tl_host, id) }
                        .onFailure { log.trace(it) }
                }
            }
        }
    }

    private fun handleMisskeyMessage(obj: JsonObject, channelId: String? = null) {
        val type = obj.string("type")
        when (type) {
            null, "" -> {
                log.d("$name handleMisskeyMessage: missing type parameter")
                return
            }

            "channel" -> {
                // ストリーミングのchannelイベントにチャネルIDが含まれない場合がある
                // https://github.com/syuilo/misskey/issues/4801
                val body = obj.jsonObject("body")
                if (body == null) {
                    log.e("$name handleMisskeyMessage: channel body is null")
                    return
                }
                val id = body.string("id")
                // ストリーミングのchannelイベントにチャネルIDが含まれない場合がある
                // https://github.com/syuilo/misskey/issues/4801
                handleMisskeyMessage(body, id)
                return
            }

            // 通知IDも日時もないイベントを受け取っても通知TLに反映させられないから無視するしかない
            // https://github.com/syuilo/misskey/issues/4802
            "followed", "renote", "mention", "meUpdated", "follow", "unfollow" -> return

            // 特にすることはない
            "readAllNotifications",
            "readAllUnreadMentions",
            "readAllUnreadSpecifiedNotes" -> return

        }

        when (type) {

            "note" -> {
                val body = obj.jsonObject("body")
                fireTimelineItem(server.parser.status(body), channelId)
            }

            "noteUpdated" -> {
                val body = obj.jsonObject("body")
                if (body == null) {
                    log.e("$name handleMisskeyMessage: noteUpdated body is null")
                    return
                }
                fireNoteUpdated(MisskeyNoteUpdate(body), channelId)
            }

            "notification" -> {
                val body = obj.jsonObject("body")
                if (body == null) {
                    log.e("$name handleMisskeyMessage: notification body is null")
                    return
                }
                log.d("$name misskey notification: ${server.parser.apiHost} ${body}")
                fireTimelineItem(server.parser.notification(body), channelId)
            }

            else -> log.v("$name ignore streaming event $type")
        }

    }

    private fun handleMastodonMessage(obj: JsonObject, text: String) {

        when (val event = obj.string("event")) {
            null, "" ->
                log.d("$name onMessage: missing event parameter")

            "filters_changed" ->
                Column.onFiltersChanged(manager.context, server.accessInfo)

            else -> {
                val payload = TootPayload.parsePayload(server.parser, event, obj, text)

                when (event) {
                    "delete" -> when (payload) {
                        is Long -> fireDeleteId(EntityId(payload.toString()))
                        is String -> fireDeleteId(EntityId(payload.toString()))
                        else -> log.d("$name unsupported payload type. $payload")
                    }

                    // {"event":"announcement","payload":"{\"id\":\"3\",\"content\":\"<p>追加</p>\",\"starts_at\":null,\"ends_at\":null,\"all_day\":false,\"mentions\":[],\"tags\":[],\"emojis\":[],\"reactions\":[]}"}
                    "announcement" -> {
                        if (payload is TootAnnouncement) {
                            runOnMainLooper {
                                eachCallback { it.onAnnouncementUpdate(payload) }
                            }
                        }
                    }

                    // {"event":"announcement.delete","payload":"2"}
                    "announcement.delete" -> {
                        val id = EntityId.mayNull(payload?.toString())
                        if (id != null) {
                            runOnMainLooper {
                                eachCallback { it.onAnnouncementDelete(id) }
                            }
                        }
                    }

                    // {"event":"announcement.reaction","payload":"{\"name\":\"hourglass_gif\",\"count\":1,\"url\":\"https://m2j.zzz.ac/...\",\"static_url\":\"https://m2j.zzz.ac/...\",\"announcement_id\":\"9\"}"}
                    "announcement.reaction" -> {
                        if (payload is TootAnnouncement.Reaction) {
                            runOnMainLooper {
                                eachCallback { it.onAnnouncementReaction(payload) }
                            }
                        }
                    }

                    else -> when (payload) {
                        is TimelineItem -> fireTimelineItem(payload)
                        else -> log.d("$name unsupported payload type. $payload")
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    // implements okhttp3 WebSocketListener

    override fun onOpen(webSocket: WebSocket, response: Response) {
        manager.enqueue {
            log.d("$name WebSocket onOpen.")
            status = StreamStatus.Open
            checkSubscription()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        manager.enqueue {
            log.d("$name WebSocket onMessage.")
            try {
                val obj = text.decodeJsonObject()
                when {
                    server.accessInfo.isMisskey -> handleMisskeyMessage(obj)
                    else -> handleMastodonMessage(obj, text)
                }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e("data=$text")
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        manager.enqueue {
            log.d("$name WebSocket onClosing code=$code, reason=$reason")
            webSocket.cancel()
            status = StreamStatus.Closed
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        manager.enqueue {
            log.d("$name WebSocket onClosed code=$code, reason=$reason")
            status = StreamStatus.Closed
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        manager.enqueue {
            if (t is SocketException && t.message == "Socket is closed") {
                log.w("$name ${t.message}")
            } else {
                log.e(t, "$name WebSocket onFailure.")
            }
            status = StreamStatus.Closed

            if (t is ProtocolException) {
                val msg = t.message
                if (msg != null && reAuthorizeError.matcher(msg).find()) {
                    log.e("$name seems this server don't support public TL streaming. don't retry…")
                }
            }
        }
    }

    private fun postMisskeyAlive() {
        if (isDisposed.get()) return
        if (server.accessInfo.isMisskey) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAliveSend >= misskeyAliveInterval) {
                try {
                    socket.get()?.send("""{"type":"alive"}""")
                } catch (ex: Throwable) {
                    log.d(ex.withCaption("$name postMisskeyAlive failed."))
                }
            }
        }
    }

    private fun unsubscribe(group: StreamGroupKey) {
        subscription.remove(group.streamKey)
        try {
            val jsonObject = if (server.accessInfo.isMastodon) {
                /*
                Mastodonの場合
                     {  "stream": "hashtag:local", "tag": "foo" }
                等に後から "type": "unsubscribe" を足す
                 */
                group.streamKey.decodeJsonObject().apply {
                    this["type"] = "unsubscribe"
                }
            } else {
                /*
                Misskeyの場合
                    { "type":"disconnect", "body": { "id": "foobar" } }
                */

                jsonObject {
                    put("type", "disconnect")
                    put("body", jsonObject("id" to group.channelId))
                }
            }
            socket.get()?.send(jsonObject.toString())
        } catch (ex: Throwable) {
            log.e(ex, "unsubscribe failed.")
        }
    }

    private fun subscribe(group: StreamGroupKey) {
        try {
            var jsonObject = group.streamKey.decodeJsonObject()
            if (server.accessInfo.isMastodon) {
                /*
                    マストドンの場合
                     {  "stream": "hashtag:local", "tag": "foo" }
                     等に後から "type": "subscribe" を足す
                 */
                jsonObject["type"] = "subscribe"
            } else {
                /*
                   Misskeyの場合
                   渡されたデータをbodyとして
                   後から body.put("id", "xxx")して
                   さらに外側を {"type": "connect", "body": body} でラップする
                   */

                jsonObject["id"] = group.channelId
                jsonObject = jsonObject("type" to "connect", "body" to jsonObject)
            }
            socket.get()?.send(jsonObject.toString())
        } catch (ex: Throwable) {
            log.e(ex, "send failed.")
        } finally {
            subscription[group.streamKey] = group
        }
    }

    private fun subscribeIfChanged(newGroup: StreamGroupKey, oldGroup: StreamGroupKey?) {
        if (oldGroup == null) subscribe(newGroup)
    }

    private fun checkSubscription() {
        postMisskeyAlive()
        if (streamKey != null) {
            val group = server.groups[streamKey]
            if (group != null) subscribeIfChanged(group, subscription[streamKey])
        } else {
            val existsIds = HashSet<String>()

            // 購読するべきものを購読する
            server.groups.entries.forEach {
                existsIds.add(it.key)
                subscribeIfChanged(it.value, subscription[it.key])
            }

            // 購読するべきでないものを購読解除する
            subscription.entries.toList().forEach {
                if (!existsIds.contains(it.key)) unsubscribe(it.value)
            }
        }
    }

    private fun StreamSpec.canStartStreaming(): Boolean {
        val column = refColumn.get()
        return when {
            column == null -> {
                log.w("$name updateConnection: missing column.")
                false
            }
            !column.canStartStreaming() -> {
                log.w("$name updateConnection: canStartStreaming returns false.")
                false
            }
            else -> true
        }
    }

    internal suspend fun updateConnection() {
        if (isDisposed.get()) {
            log.w("$name updateConnection: disposed.")
            return
        }

        val group = server.groups[streamKey]
        if (group != null) {
            if (!group.values.any { it.canStartStreaming() }) {
                // 準備できたカラムがまったくないなら接続開始しない
                log.w("$name updateConnection: column is not prepared.")
                return
            }
        } else {
            // merged connection ではないのにgroupがなくなってしまったら再接続しない
            if (streamKey != null) {
                log.w("$name updateConnection: missing group.")
                return
            }
        }

        when (status) {

            StreamStatus.Connecting -> return

            StreamStatus.Open -> {
                checkSubscription()
                return
            }

            else -> Unit //fall thru
        }

        subscription.clear()

        socket.set(null)
        synchronized(capturedId) {
            capturedId.clear()
        }

        status = StreamStatus.Connecting

        val path = group?.spec?.streamPath ?: when {
            server.accessInfo.isMisskey -> "/streaming"
            else -> "/api/v1/streaming/"
        }

        val (result, ws) = try {
            client.webSocket(path, this)
        } catch (ex: Throwable) {
            Pair(TootApiResult(ex.withCaption("can't create WebSocket.")), null)
        }

        when {
            result == null -> {
                log.d("$name updateConnection: cancelled.")
                status = StreamStatus.Closed
            }

            ws == null -> {
                val error = result.error
                log.d("$name updateConnection: $error")
                status = StreamStatus.Closed
            }

            else -> socket.set(ws)
        }
    }

    fun misskeySetCapture(list: ArrayList<EntityId>) {
        val socket = socket.get()
        if (isDisposed.get() || socket == null) return

        for (id in list) {
            if (id.isDefault) continue
            synchronized(capturedId) {
                if (capturedId.contains(id)) return
                try {
                    if (socket.send("""{"type":"subNote","body": {"id":"$id"}}""")) {
                        capturedId.add(id)
                    } else {
                        log.w("capture failed.")
                    }
                } catch (ex: Throwable) {
                    log.d(ex.withCaption("capture failed."))
                }
            }
        }
    }
}
