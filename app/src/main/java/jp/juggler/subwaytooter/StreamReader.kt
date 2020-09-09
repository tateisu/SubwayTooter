package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootAnnouncement.Reaction
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ProtocolException
import java.net.SocketException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal class StreamReader(
	val context : Context,
	private val handler : Handler,
	val pref : SharedPreferences
) {
	
	internal interface StreamCallback {
		
		fun channelId() : String?
		
		fun onTimelineItem(item : TimelineItem)
		fun onListeningStateChanged(bListen : Boolean)
		fun onNoteUpdated(ev : MisskeyNoteUpdate)
		fun onAnnouncementUpdate(item : TootAnnouncement)
		fun onAnnouncementDelete(id : EntityId)
		fun onAnnouncementReaction(reaction : Reaction)
	}
	
	companion object {
		
		val log = LogCategory("StreamReader")
		
		const val MISSKEY_ALIVE_INTERVAL = 60000L
		
		val reAuthorizeError = "authorize".asciiPattern(Pattern.CASE_INSENSITIVE)
	}
	
	private val reader_list = LinkedList<Reader>()
	
	internal inner class Reader(
		internal val access_info : SavedAccount,
		internal val end_point : String,
		highlight_trie : WordTrieTree?
	) : WebSocketListener() {
		
		private val bDisposed = AtomicBoolean()
		internal val bListening = AtomicBoolean()
		internal val socket = AtomicReference<WebSocket>(null)
		internal val callback_list = LinkedList<StreamCallback>()
		internal val parser : TootParser =
			TootParser(context, access_info, highlightTrie = highlight_trie, fromStream = true)
		
		internal fun dispose() {
			bDisposed.set(true)
			socket.get()?.cancel()
			socket.set(null)
		}
		
		private val proc_reconnect : Runnable = Runnable {
			if(bDisposed.get()) return@Runnable
			startRead()
		}
		
		private val proc_alive : Runnable = Runnable {
			fireAlive()
		}
		
		private fun fireAlive() {
			handler.removeCallbacks(proc_alive)
			if(bDisposed.get()) return
			try {
				if(socket.get()?.send("""{"type":"alive"}""") == true) {
					handler.postDelayed(proc_alive, MISSKEY_ALIVE_INTERVAL)
				}
			} catch(ex : Throwable) {
				log.d(ex.withCaption("fireAlive failed."))
			}
		}
		
		@Synchronized
		internal fun setHighlightTrie(highlight_trie : WordTrieTree) {
			this.parser.highlightTrie = highlight_trie
		}
		
		@Synchronized
		internal fun addCallback(stream_callback : StreamCallback) {
			for(c in callback_list) {
				if(c === stream_callback) return
			}
			callback_list.add(stream_callback)
		}
		
		@Synchronized
		internal fun removeCallback(stream_callback : StreamCallback) {
			val it = callback_list.iterator()
			while(it.hasNext()) {
				val c = it.next()
				if(c === stream_callback) it.remove()
			}
		}
		
		fun containsCallback(streamCallback : StreamCallback) : Boolean {
			return callback_list.contains(streamCallback)
		}
		
		@Synchronized
		fun fireListeningChanged(bListen : Boolean) {
			for(c in callback_list) {
				try {
					c.onListeningStateChanged(bListen)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		/**
		 * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
		 * messages.
		 */
		override fun onOpen(webSocket : WebSocket, response : Response) {
			log.d("WebSocket onOpen. url=%s .", webSocket.request().url)
			if(access_info.isMisskey) {
				handler.removeCallbacks(proc_alive)
				handler.postDelayed(proc_alive, MISSKEY_ALIVE_INTERVAL)
			}
		}
		
		private inline fun eachCallback(block : (callback : StreamCallback) -> Unit) {
			synchronized(this) {
				if(bDisposed.get()) return@synchronized
				for(callback in callback_list) {
					try {
						block(callback)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
				}
			}
		}
		
		private fun fireTimelineItem(item : TimelineItem?, channelId : String? = null) {
			item ?: return
			eachCallback { callback ->
				if(channelId != null && channelId != callback.channelId()) return@eachCallback
				callback.onTimelineItem(item)
			}
		}
		
		private fun fireDeleteId(id : EntityId) {
			
			val tl_host = access_info.apiHost
			runOnMainLooper {
				synchronized(this) {
					if(bDisposed.get()) return@synchronized
					if(Pref.bpDontRemoveDeletedToot(App1.getAppState(context).pref)) return@synchronized
					for(column in App1.getAppState(context).column_list) {
						try {
							column.onStatusRemoved(tl_host, id)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
					}
				}
			}
		}
		
		private fun fireNoteUpdated(ev : MisskeyNoteUpdate, channelId : String? = null) {
			runOnMainLooper {
				eachCallback { callback ->
					if(channelId != null && channelId != callback.channelId()) return@eachCallback
					callback.onNoteUpdated(ev)
				}
			}
		}
		
		private fun handleMisskeyMessage(obj : JsonObject, channelId : String? = null) {
			val type = obj.string("type")
			when(type) {
				null, "" -> {
					log.d("handleMisskeyMessage: missing type parameter")
					return
				}
				
				"channel" -> {
					// ストリーミングのchannelイベントにチャネルIDが含まれない場合がある
					// https://github.com/syuilo/misskey/issues/4801
					val body = obj.jsonObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: channel body is null")
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
			
			when(type) {
				
				"note" -> {
					val body = obj.jsonObject("body")
					fireTimelineItem(parser.status(body), channelId)
				}
				
				"noteUpdated" -> {
					val body = obj.jsonObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: noteUpdated body is null")
						return
					}
					fireNoteUpdated(MisskeyNoteUpdate(body), channelId)
				}
				
				"notification" -> {
					val body = obj.jsonObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: notification body is null")
						return
					}
					log.d("misskey notification: ${parser.apiHost} ${body}")
					fireTimelineItem(parser.notification(body), channelId)
				}
				
				else -> {
					log.v("ignore streaming event $type")
				}
			}
			
		}
		
		private fun handleMastodonMessage(obj : JsonObject, text : String) {
			
			when(val event = obj.string("event")) {
				null, "" -> log.d("onMessage: missing event parameter")
				
				"filters_changed" ->
					Column.onFiltersChanged(context, access_info)
				
				else -> {
					val payload = TootPayload.parsePayload(parser, event, obj, text)
					
					when(event) {
						"delete" -> when(payload) {
							is Long -> fireDeleteId(EntityId(payload.toString()))
							is String -> fireDeleteId(EntityId(payload.toString()))
							else -> log.d("unsupported payload type. $payload")
						}
						
						// {"event":"announcement","payload":"{\"id\":\"3\",\"content\":\"<p>追加</p>\",\"starts_at\":null,\"ends_at\":null,\"all_day\":false,\"mentions\":[],\"tags\":[],\"emojis\":[],\"reactions\":[]}"}
						"announcement" -> {
							if(payload is TootAnnouncement) {
								runOnMainLooper {
									eachCallback { it.onAnnouncementUpdate(payload) }
								}
							}
						}
						
						// {"event":"announcement.delete","payload":"2"}
						"announcement.delete" -> {
							val id = EntityId.mayNull(payload?.toString())
							if(id != null) {
								runOnMainLooper {
									eachCallback { it.onAnnouncementDelete(id) }
								}
							}
						}
						
						// {"event":"announcement.reaction","payload":"{\"name\":\"hourglass_gif\",\"count\":1,\"url\":\"https://m2j.zzz.ac/...\",\"static_url\":\"https://m2j.zzz.ac/...\",\"announcement_id\":\"9\"}"}
						"announcement.reaction" -> {
							if(payload is Reaction) {
								runOnMainLooper {
									eachCallback { it.onAnnouncementReaction(payload) }
								}
							}
						}
						
						else -> when(payload) {
							is TimelineItem -> fireTimelineItem(payload)
							else -> log.d("unsupported payload type. $payload")
						}
					}
				}
			}
		}
		
		/**
		 * Invoked when a text (type `0x1`) message has been received.
		 */
		override fun onMessage(webSocket : WebSocket, text : String) {
			// warning.d( "WebSocket onMessage. url=%s, message=%s", webSocket.request().url(), text );
			try {
				val obj = text.decodeJsonObject()
				
				if(access_info.isMisskey) {
					handleMisskeyMessage(obj)
				} else {
					handleMastodonMessage(obj, text)
					
				}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("data=$text")
			}
		}
		
		/**
		 * Invoked when the peer has indicated that no more incoming messages will be transmitted.
		 */
		override fun onClosing(webSocket : WebSocket, code : Int, reason : String) {
			log.d(
				"WebSocket onClosing. code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url
			)
			webSocket.cancel()
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged(false)
		}
		
		/**
		 * Invoked when both peers have indicated that no more messages will be transmitted and the
		 * connection has been successfully released. No further calls to this listener will be made.
		 */
		override fun onClosed(webSocket : WebSocket, code : Int, reason : String) {
			log.d(
				"WebSocket onClosed.  code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url
			)
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged(false)
		}
		
		/**
		 * Invoked when a web socket has been closed due to an error reading from or writing to the
		 * network. Both outgoing and incoming messages may have been lost. No further calls to this
		 * listener will be made.
		 */
		override fun onFailure(webSocket : WebSocket, t : Throwable, response : Response?) {
			if(t is SocketException && t.message == "Socket is closed") {
				log.w("WebSocket is closed. url=${webSocket.request().url}")
			} else {
				log.e(t, "WebSocket onFailure. url=${webSocket.request().url}")
			}
			
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			handler.removeCallbacks(proc_alive)
			fireListeningChanged(false)
			
			if(t is ProtocolException) {
				val msg = t.message
				if(msg != null && reAuthorizeError.matcher(msg).find()) {
					log.e("seems old instance that does not support streaming public timeline without access token. don't retry...")
					return
				}
			}
			handler.postDelayed(proc_reconnect, 10000L)
			
		}
		
		internal fun startRead() {
			if(bDisposed.get()) {
				log.d("startRead: disposed.")
				return
			} else if(bListening.get()) {
				log.d("startRead: already listening.")
				return
			}
			
			socket.set(null)
			bListening.set(true)
			synchronized(capturedId) {
				capturedId.clear()
			}
			fireListeningChanged(false)
			
			TootTaskRunner(context).run(access_info, object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					val (result, ws) = client.webSocket(end_point, this@Reader)
					
					when {
						result == null -> {
							log.d("startRead: cancelled.")
							bListening.set(false)
							fireListeningChanged(false)
						}
						
						ws == null -> {
							val error = result.error
							log.d("startRead: error. $error")
							bListening.set(false)
							fireListeningChanged(false)
							// this may network error.
							handler.removeCallbacks(proc_reconnect)
							handler.postDelayed(proc_reconnect, 5000L)
						}
						
						else -> {
							socket.set(ws)
							fireListeningChanged(true)
						}
					}
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
				}
			})
		}
		
		// Misskeyの投稿キャプチャ
		private val capturedId = HashSet<EntityId>()
		
		fun capture(list : ArrayList<EntityId>) {
			val socket = socket.get()
			when {
				bDisposed.get() -> return
				socket == null -> return
				
				else -> {
					for(id in list) {
						if(id.isDefault) continue
						synchronized(capturedId) {
							if(capturedId.contains(id)) return
							try {
								if(socket.send("""{"type":"subNote","body": {"id":"$id"}}""")) {
									capturedId.add(id)
								} else {
									log.w("capture failed.")
								}
							} catch(ex : Throwable) {
								log.d(ex.withCaption("capture failed."))
							}
						}
					}
				}
			}
		}
		
		fun registerMisskeyChannel(channelArg : JsonObject?) {
			channelArg ?: return
			try {
				if(bDisposed.get()) return
				socket.get()?.send(channelArg.toString())
			} catch(ex : Throwable) {
				log.e(ex, "registerMisskeyChannel failed.")
			}
		}
		
		fun removeChannel(channelId : String?) {
			channelId ?: return
			try {
				if(bDisposed.get()) return
				socket.get()?.send(
					JsonObject().apply {
						put("type", "disconnect")
						put("body", JsonObject().apply {
							put("id", channelId)
						})
					}.toString()
				)
			} catch(ex : Throwable) {
				log.e(ex, "registerMisskeyChannel failed.")
			}
		}
	}
	
	private fun prepareReader(
		accessInfo : SavedAccount,
		endPoint : String,
		highlightTrie : WordTrieTree?
	) : Reader {
		synchronized(reader_list) {
			// アカウントとエンドポイントが同じリーダーがあればそれを使う
			for(reader in reader_list) {
				if(reader.access_info.db_id == accessInfo.db_id && reader.end_point == endPoint) {
					if(highlightTrie != null) reader.setHighlightTrie(highlightTrie)
					return reader
				}
			}
			// リーダーを作成する
			val reader = Reader(accessInfo, endPoint, highlightTrie)
			reader_list.add(reader)
			return reader
		}
	}
	
	// onResume や ロード完了ののタイミングで登録される
	fun register(
		accessInfo : SavedAccount,
		endPoint : String,
		highlightTrie : WordTrieTree?,
		streamCallback : StreamCallback
	) : Reader {
		
		val reader = prepareReader(accessInfo, endPoint, highlightTrie)
		reader.addCallback(streamCallback)
		if(! reader.bListening.get()) {
			reader.startRead()
		} else {
			streamCallback.onListeningStateChanged(true)
		}
		return reader
	}
	
	// カラム破棄やリロードのタイミングで呼ばれる
	fun unregister(
		accessInfo : SavedAccount,
		endPoint : String,
		streamCallback : StreamCallback
	) {
		synchronized(reader_list) {
			val it = reader_list.iterator()
			while(it.hasNext()) {
				val reader = it.next()
				if(reader.access_info.db_id == accessInfo.db_id && reader.end_point == endPoint) {
					log.d("unregister: removeCallback $endPoint")
					reader.removeCallback(streamCallback)
					if(reader.callback_list.isEmpty()) {
						log.d("unregister: dispose $endPoint")
						reader.dispose()
						it.remove()
					} else {
						reader.removeChannel(streamCallback.channelId())
					}
				}
			}
		}
	}
	
	// onPauseのタイミングで全てのStreaming接続を破棄する
	fun stopAll() {
		synchronized(reader_list) {
			for(reader in reader_list) {
				reader.dispose()
			}
			reader_list.clear()
		}
	}
	
	fun getStreamingStatus(
		accessInfo : SavedAccount,
		endPoint : String,
		streamCallback : StreamCallback
	) : StreamingIndicatorState {
		synchronized(reader_list) {
			val reader = reader_list.find {
				it.access_info.db_id == accessInfo.db_id &&
					it.end_point == endPoint &&
					it.containsCallback(streamCallback)
			}
			return when {
				reader == null -> StreamingIndicatorState.NONE
				reader.bListening.get() &&
					reader.socket.get() != null ->
					StreamingIndicatorState.LISTENING
				else -> StreamingIndicatorState.REGISTERED
			}
		}
	}
}
