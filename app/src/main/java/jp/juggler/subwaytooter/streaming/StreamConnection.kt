package jp.juggler.subwaytooter.streaming

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.onStatusRemoved
import jp.juggler.subwaytooter.column.reloadFilter
import jp.juggler.subwaytooter.column.replaceStatus
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ProtocolException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class StreamConnection(
    private val manager: StreamManager,
    private val acctGroup: StreamGroupAcct,
    val spec: StreamSpec? = null, // null if merged connection
    val name: String,
) : WebSocketListener(), TootApiCallback {

    companion object {
        private val log = LogCategory("StreamConnection")

        private const val misskeyAliveInterval = 60000L

        val reAuthorizeError = "authorize".asciiPattern(Pattern.CASE_INSENSITIVE)
    }

    private val isDisposed = AtomicBoolean(false)

    override suspend fun isApiCancelled() = isDisposed.get()

    val client = TootApiClient(manager.context, callback = this)
        .apply { account = acctGroup.account }

    private val _status = AtomicReference(StreamStatus.Closed)

    private var status: StreamStatus
        get() = _status.get()
        set(value) {
            _status.set(value)
            eachCallback { it.onStreamStatusChanged(value) }
        }

    private val socket = AtomicReference<WebSocket>(null)

    private var lastAliveSend = 0L

    private val subscriptions = ConcurrentHashMap<StreamSpec, StreamGroup>()

    // Misskeyの投稿キャプチャ
    private val capturedId = HashSet<EntityId>()

    ///////////////////////////////////////////////////////////////////
    // methods

    private fun eachCallbackForSpec(
        spec: StreamSpec,
        channelId: String? = null,
        stream: JsonArray? = null,
        item: TimelineItem? = null,
        block: (callback: StreamCallback) -> Unit,
    ) {
        if (isDisposed.get()) return
        acctGroup.keyGroups[spec]?.eachCallback(channelId, stream, item, block)
    }

    private fun eachCallbackForAcct(
        item: TimelineItem? = null,
        block: (callback: StreamCallback) -> Unit,
    ) {
        if (isDisposed.get()) return
        acctGroup.keyGroups.values.forEach {
            it.eachCallback(null, null, item, block)
        }
    }

    private fun eachCallback(
        channelId: String? = null,
        stream: JsonArray? = null,
        item: TimelineItem? = null,
        block: (callback: StreamCallback) -> Unit,
    ) {
        if (StreamManager.traceDelivery) log.v("$name eachCallback spec=${spec?.name}")
        if (spec != null) {
            eachCallbackForSpec(spec, channelId, stream, item, block)
        } else {
            if (isDisposed.get()) return
            acctGroup.keyGroups.values.forEach { it.eachCallback(channelId, stream, item, block) }
        }
    }

    fun dispose() {
        status = StreamStatus.Closed
        isDisposed.set(true)
        socket.get()?.cancel()
        socket.set(null)
    }

    fun getStreamStatus(streamSpec: StreamSpec): StreamStatus = when {
        subscriptions[streamSpec] != null -> StreamStatus.Subscribed
        else -> status
    }

    private fun fireTimelineItem(
        item: TimelineItem?,
        channelId: String? = null,
        stream: JsonArray? = null,
    ) {
        item ?: return
        if (StreamManager.traceDelivery) log.v("$name fireTimelineItem")
        eachCallback(channelId, stream, item = item) { it.onTimelineItem(item, channelId, stream) }
    }

    // fedibird emoji reaction noti
    private fun fireEmojiReactionNotification(item: TootNotification) {
        if (StreamManager.traceDelivery) log.v("$name fireTimelineItem")
        eachCallbackForAcct { it.onEmojiReactionNotification(item) }
    }

    private fun fireEmojiReactionEvent(item: TootReaction) {
        if (StreamManager.traceDelivery) log.v("$name fireTimelineItem")
        eachCallbackForAcct { it.onEmojiReactionEvent(item) }
    }

    private fun fireNoteUpdated(ev: MisskeyNoteUpdate, channelId: String? = null) {
        eachCallback(channelId) { it.onNoteUpdated(ev, channelId) }
    }

    private fun fireDeleteId(id: EntityId) {
        if (PrefB.bpDontRemoveDeletedToot.invoke(manager.appState.pref)) return
        val timelineHost = acctGroup.account.apiHost
        manager.appState.columnList.forEach {
            runOnMainLooper {
                try {
                    if (!it.isDispose.get()) it.onStatusRemoved(timelineHost, id)
                } catch (ex: Throwable) {
                    log.e(ex, "onStatusRemoved failed.")
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
            "readAllUnreadSpecifiedNotes",
            -> return
        }

        when (type) {

            "note" -> {
                val body = obj.jsonObject("body")
                fireTimelineItem(acctGroup.parser.status(body), channelId)
            }

            "noteUpdated" -> {
                val body = obj.jsonObject("body")
                if (body == null) {
                    log.e("$name handleMisskeyMessage: noteUpdated body is null")
                    return
                }
                fireNoteUpdated(MisskeyNoteUpdate(acctGroup.account.apDomain, body), channelId)
            }

            "notification" -> {
                val body = obj.jsonObject("body")
                if (body == null) {
                    log.e("$name handleMisskeyMessage: notification body is null")
                    return
                }
                log.d("$name misskey notification: ${acctGroup.parser.apiHost} $body")
                fireTimelineItem(acctGroup.parser.notification(body), channelId)
            }

            else -> log.w("$name ignore streaming event $type")
        }
    }

    private fun handleMastodonMessage(obj: JsonObject, text: String) {

        val stream = obj.jsonArray("stream")

        when (val event = obj.string("event")) {
            null, "" ->
                log.d("$name handleMastodonMessage: missing event parameter")

            "filters_changed" ->
                reloadFilter(manager.context, acctGroup.account)

            else -> {
                val payload = TootPayload.parsePayload(acctGroup.parser, event, obj, text)

                when (event) {
                    "delete" -> when (payload) {
                        is Long -> fireDeleteId(EntityId(payload.toString()))
                        is String -> fireDeleteId(EntityId(payload.toString()))
                        else -> log.d("$name unsupported payload type. $payload")
                    }

                    // {"event":"announcement","payload":"{\"id\":\"3\",\"content\":\"<p>追加</p>\",\"starts_at\":null,\"ends_at\":null,\"all_day\":false,\"mentions\":[],\"tags\":[],\"emojis\":[],\"reactions\":[]}"}
                    "announcement" -> {
                        if (payload is TootAnnouncement) {
                            eachCallback { it.onAnnouncementUpdate(payload) }
                        }
                    }

                    // {"event":"announcement.delete","payload":"2"}
                    "announcement.delete" -> {
                        val id = EntityId.mayNull(payload?.toString())
                        if (id != null) {
                            eachCallback { it.onAnnouncementDelete(id) }
                        }
                    }

                    // {"event":"announcement.reaction","payload":"{\"name\":\"hourglass_gif\",\"count\":1,\"url\":\"https://m2j.zzz.ac/...\",\"static_url\":\"https://m2j.zzz.ac/...\",\"announcement_id\":\"9\"}"}
                    "announcement.reaction" -> {
                        if (payload is TootReaction) {
                            eachCallback { it.onAnnouncementReaction(payload) }
                        }
                    }

                    "emoji_reaction" -> {
                        if (payload is TootReaction && payload.status_id != null) {
                            log.d("emoji_reaction ${payload.status_id} ${payload.name} ${payload.count}")
                            fireEmojiReactionEvent(payload)
                        }
                    }

                    "status.update" -> {
                        if (payload is TootStatus) {
                            log.d("status.update ${payload.uri}")
                            val account = acctGroup.account
                            // homeストリームから来るが、該当アカウントの全カラムに反映させたい
                            launchMain {
                                try {
                                    manager.appState.columnList.forEach { column ->
                                        if (column.accessInfo.acct != account.acct) return@forEach
                                        column.replaceStatus(payload.id, payload.json)
                                    }
                                } catch (ex: Throwable) {
                                    log.e(ex, "replaceStatus failed.")
                                }
                            }
                        }
                    }

                    else -> when (payload) {
                        is TimelineItem -> {

                            if (payload is TootNotification &&
                                (payload.type == TootNotification.TYPE_EMOJI_REACTION ||
                                        payload.type == TootNotification.TYPE_EMOJI_REACTION_PLEROMA)

                            ) {
                                log.d("emoji_reaction (notification) ${payload.status?.id}")
                                fireEmojiReactionNotification(payload)
                            }

                            fireTimelineItem(payload, stream = stream)
                        }
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
            log.v("$name WebSocket onOpen.")
            status = StreamStatus.Open
            checkSubscription()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        manager.enqueue {
            if (StreamManager.traceDelivery) log.v("$name WebSocket onMessage.")
            try {
                val obj = text.decodeJsonObject()
                when {
                    acctGroup.account.isMisskey -> handleMisskeyMessage(obj)
                    else -> handleMastodonMessage(obj, text)
                }
            } catch (ex: Throwable) {
                log.e(ex, "$name onMessage error. data=$text")
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        manager.enqueue {
            log.v("$name WebSocket onClosing code=$code, reason=$reason")
            webSocket.cancel()
            status = StreamStatus.Closed
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        manager.enqueue {
            log.i("$name WebSocket onClosed code=$code, reason=$reason")
            status = StreamStatus.Closed
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        manager.enqueue {
            if (t is SocketException && t.message?.contains("closed") == true) {
                log.v("$name socket closed.")
            } else {
                log.w(t, "$name WebSocket onFailure.")
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

    ///////////////////////////////////////////////////////////////////////////////

    private fun postMisskeyAlive() {
        if (isDisposed.get()) return
        if (acctGroup.account.isMisskey) {
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

    private fun unsubscribe(spec: StreamSpec) {
        try {
            subscriptions.remove(spec)
            eachCallbackForSpec(spec) { it.onStreamStatusChanged(getStreamStatus(spec)) }

            val jsonObject = if (acctGroup.account.isMastodon) {
                /*
                Mastodonの場合
                     {  "stream": "hashtag:local", "tag": "foo" }
                等に後から "type": "unsubscribe" を足す
                 */
                spec.paramsClone().apply { put("type", "unsubscribe") }
            } else {
                /*
                Misskeyの場合
                    { "type":"disconnect", "body": { "id": "foobar" } }
                */

                jsonObject {
                    put("type", "disconnect")
                    put("body", jsonObjectOf("id" to spec.channelId))
                }
            }
            socket.get()?.send(jsonObject.toString())
        } catch (ex: Throwable) {
            log.e(ex, "unsubscribe failed.")
        }
    }

    private fun subscribe(group: StreamGroup) {
        val spec = group.spec
        try {
            val jsonObject = if (acctGroup.account.isMastodon) {
                /*
                    マストドンの場合
                     {  "stream": "hashtag:local", "tag": "foo" }
                     等に後から "type": "subscribe" を足す
                 */
                spec.paramsClone().apply { put("type", "subscribe") }
            } else {
                /*
                   Misskeyの場合
                   渡されたデータをbodyとして
                   後から body.put("id", "xxx")して
                   さらに外側を {"type": "connect", "body": body} でラップする
                   */
                jsonObjectOf(
                    "type" to "connect",
                    "body" to spec.paramsClone().apply { put("id", spec.channelId) }
                )
            }
            socket.get()?.send(jsonObject.toString())
        } catch (ex: Throwable) {
            log.e(ex, "send failed.")
        } finally {
            subscriptions[spec] = group
            eachCallbackForSpec(spec) { it.onStreamStatusChanged(getStreamStatus(spec)) }
        }
    }

    private fun subscribeIfChanged(newGroup: StreamGroup, oldGroup: StreamGroup?) {
        if (oldGroup == null) subscribe(newGroup)
    }

    private fun checkSubscription() {
        postMisskeyAlive()
        if (spec != null) {
            val group = acctGroup.keyGroups[spec]
            if (group != null) subscribeIfChanged(group, subscriptions[spec])
        } else {
            val existsIds = HashSet<StreamSpec>()

            // 購読するべきものを購読する
            acctGroup.keyGroups.entries.forEach {
                existsIds.add(it.key)
                subscribeIfChanged(it.value, subscriptions[it.key])
            }

            // 購読するべきでないものを購読解除する
            subscriptions.entries.toList().forEach {
                if (!existsIds.contains(it.key)) unsubscribe(it.key)
            }
        }
    }

    internal suspend fun updateConnection() {
        if (isDisposed.get()) {
            log.w("$name updateConnection: disposed.")
            return
        }

        val group = spec?.let { acctGroup.keyGroups[it] }
        if (group != null) {
            // 準備できたカラムがまったくないなら接続開始しない
            if (!group.destinations.values.any { it.canStartStreaming() }) return
        } else {
            // merged connection ではないのにgroupがなくなってしまったら再接続しない
            if (spec != null) {
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

        subscriptions.clear()

        socket.set(null)
        synchronized(capturedId) {
            capturedId.clear()
        }

        status = StreamStatus.Connecting

        val path = group?.spec?.path ?: when {
            acctGroup.account.isMisskey -> "/streaming"
            else -> "/api/v1/streaming/"
        }

        val (result, ws) = try {
            client.webSocket(path, this)
        } catch (ex: Throwable) {
            Pair(TootApiResult(ex.withCaption("can't create WebSocket.")), null)
        }

        when {
            result == null -> {
                log.w("$name updateConnection: cancelled.")
                status = StreamStatus.Closed
            }

            ws == null -> {
                val error = result.error
                log.w("$name updateConnection: $error")
                status = StreamStatus.Closed
            }

            else -> socket.set(ws)
        }
    }

    fun misskeySetCapture(list: ArrayList<EntityId>) {
        val socket = socket.get()
        if (isDisposed.get() || socket == null) return

        val type = when {
            acctGroup.ti.versionGE(TootInstance.MISSKEY_VERSION_12_75_0) -> "sr"
            else -> "subNote"
        }

        for (id in list) {
            if (id.isDefault) continue
            synchronized(capturedId) {
                if (capturedId.contains(id)) return
                try {
                    if (socket.send("""{"type":"$type","body":{"id":"$id"}}""")) {
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
