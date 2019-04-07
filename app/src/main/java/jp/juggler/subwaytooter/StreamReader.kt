package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.WordTrieTree
import jp.juggler.util.LogCategory
import jp.juggler.util.runOnMainLooper
import jp.juggler.util.toJsonObject
import jp.juggler.util.withCaption
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ProtocolException
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
		fun onTimelineItem(item : TimelineItem)
		fun onListeningStateChanged()
		fun onNoteUpdated(ev:MisskeyNoteUpdate)
	}
	
	companion object {
		val log = LogCategory("StreamReader")
		
		const val MISSKEY_ALIVE_INTERVAL = 60000L
		
		@Suppress("HasPlatformType")
		val reAuthorizeError = Pattern.compile("authorize", Pattern.CASE_INSENSITIVE)
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
			TootParser(context, access_info, highlightTrie = highlight_trie)
		
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
		fun fireListeningChanged() {
			for(c in callback_list) {
				try {
					c.onListeningStateChanged()
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
			log.d("WebSocket onOpen. url=%s .", webSocket.request().url())
			if(access_info.isMisskey) {
				handler.removeCallbacks(proc_alive)
				handler.postDelayed(proc_alive, MISSKEY_ALIVE_INTERVAL)
			}
		}
		
		private fun fireTimelineItem(item : TimelineItem?) {
			item ?: return
			synchronized(this) {
				if(bDisposed.get()) return@synchronized
				for(callback in callback_list) {
					try {
						callback.onTimelineItem(item)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
				}
			}
		}
		
		private fun fireDeleteId(id : EntityId) {
			val tl_host = access_info.host
			runOnMainLooper {
				synchronized(this) {
					if(bDisposed.get()) return@runOnMainLooper
					if( Pref.bpDontRemoveDeletedToot(App1.getAppState(context).pref)) return@runOnMainLooper
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
		
		private fun fireNoteUpdated(ev:MisskeyNoteUpdate) {
			runOnMainLooper {
				synchronized(this) {
					if(bDisposed.get()) return@runOnMainLooper
					for(callback in callback_list) {
						try {
							callback.onNoteUpdated(ev)
						} catch(ex : Throwable) {
							log.trace(ex)
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
				
				if(text.isEmpty() || text[0] != '{') {
					log.d("onMessage: text is not JSON: $text")
					return
				}
				
				val obj = text.toJsonObject()
				
				if(access_info.isMisskey) {
					val type = obj.optString("type")
					if(type?.isEmpty() != false) {
						log.d("onMessage: missing type parameter")
						return
					}
					when(type) {
						
						"note" -> {
							val body = obj.optJSONObject("body")
							fireTimelineItem(parser.status(body))
						}

						"noteUpdated" -> {
							val body = obj.optJSONObject("body")
							fireNoteUpdated( MisskeyNoteUpdate(body))
						}

						"notification" -> {
							val body = obj.optJSONObject("body")
							fireTimelineItem(parser.notification(body))
						}
						
						"readAllNotifications"->{
							// nothing to do
						}
						
						else -> {
							log.v("ignore streaming event $type")
						}
					}
					
				} else {
					
					val event = obj.optString("event")
					
					if(event == null || event.isEmpty()) {
						log.d("onMessage: missing event parameter")
						return
					}
					
					if(event == "filters_changed") {
						Column.onFiltersChanged(context, access_info)
						return
					}
					
					val payload = TootPayload.parsePayload(parser, event, obj, text)
					
					when(event) {
						
						"delete" -> when(payload){
							is Long -> fireDeleteId(EntityId(payload.toString()))
							is String ->fireDeleteId(EntityId(payload.toString()))
							else -> log.d("unsupported payload type. $payload")
						}
						
						else ->  when(payload){
							is TimelineItem -> fireTimelineItem(payload)
							else -> log.d("unsupported payload type. $payload")
						}
					}
					
				}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("data=$text")
			}
		}
		
		/**
		 * Invoked when the peer has indicated that no more incoming messages will be transmitted.
		 */
		override fun onClosing(webSocket : WebSocket, code : Int, reason : String?) {
			log.d(
				"WebSocket onClosing. code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url()
			)
			webSocket.cancel()
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged()
		}
		
		/**
		 * Invoked when both peers have indicated that no more messages will be transmitted and the
		 * connection has been successfully released. No further calls to this listener will be made.
		 */
		override fun onClosed(webSocket : WebSocket, code : Int, reason : String?) {
			log.d(
				"WebSocket onClosed.  code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url()
			)
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged()
		}
		
		/**
		 * Invoked when a web socket has been closed due to an error reading from or writing to the
		 * network. Both outgoing and incoming messages may have been lost. No further calls to this
		 * listener will be made.
		 */
		override fun onFailure(webSocket : WebSocket, ex : Throwable, response : Response?) {
			log.e(ex, "WebSocket onFailure. url=%s .", webSocket.request().url())
			
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			handler.removeCallbacks(proc_alive)
			fireListeningChanged()
			
			if(ex is ProtocolException) {
				val msg = ex.message
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
			synchronized(capturedId){
				capturedId.clear()
			}
			fireListeningChanged()
			
			TootTaskRunner(context).run(access_info, object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					val( result ,ws) = client.webSocket(end_point, this@Reader)
					
					when {
						result == null -> {
							log.d("startRead: cancelled.")
							bListening.set(false)
							fireListeningChanged()
						}
						ws == null -> {
							val error = result.error
							log.d("startRead: error. $error")
							bListening.set(false)
							fireListeningChanged()
							// this may network error.
							handler.removeCallbacks(proc_reconnect)
							handler.postDelayed(proc_reconnect, 5000L)
						}
						else->{
							socket.set(ws)
							fireListeningChanged()
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
			when{
				bDisposed.get() -> return
				socket == null -> return
				else->{
					for( id in list ){
						if(id.isDefault) continue
						synchronized(capturedId){
							if( capturedId.contains(id) ) return
							try {
								if(socket.send("""{"type":"subNote","body": {"id":"$id"}}""")) {
									capturedId.add(id)
								}else{
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
	) :Reader {

		val reader = prepareReader(accessInfo, endPoint, highlightTrie)
		reader.addCallback(streamCallback)
		if(! reader.bListening.get()) {
			reader.startRead()
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
			for(reader in reader_list) {
				if(reader.access_info.db_id == accessInfo.db_id
					&& reader.end_point == endPoint
					&& reader.containsCallback(streamCallback)
				) {
					return if(reader.bListening.get() && reader.socket.get() != null) {
						StreamingIndicatorState.LISTENING
					} else {
						StreamingIndicatorState.REGISTERED
					}
				}
			}
		}
		return StreamingIndicatorState.NONE
	}
}
