package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler

import org.json.JSONObject

import java.net.ProtocolException
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootPayload
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.util.WordTrieTree
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class StreamReader(
	val context : Context,
	private val handler : Handler,
	val pref : SharedPreferences
) {
	
	companion object {
		val log = LogCategory("StreamReader")
		
		@Suppress("HasPlatformType")
		val reAuthorizeError = Pattern.compile("authorize", Pattern.CASE_INSENSITIVE)
	}
	
	private val reader_list = LinkedList<Reader>()
	
	private inner class Reader(
		internal val access_info : SavedAccount,
		internal val end_point : String,
		highlight_trie : WordTrieTree?
	) : WebSocketListener() {
		
		internal val bDisposed = AtomicBoolean()
		internal val bListening = AtomicBoolean()
		internal val socket = AtomicReference<WebSocket>(null)
		internal val callback_list = LinkedList< (event_type : String, item : Any?)->Unit >()
		internal val parser : TootParser
		
		init {
			this.parser = TootParser(context, access_info,highlightTrie = highlight_trie)
		}
		
		internal fun dispose() {
			bDisposed.set(true)
			socket.get()?.cancel()
		}
		
		internal val proc_reconnect : Runnable = Runnable {
			if(bDisposed.get()) return@Runnable
			startRead()
		}
		
		@Synchronized internal fun setHighlightTrie(highlight_trie : WordTrieTree) {
			this.parser.highlightTrie = highlight_trie
		}
		
		@Synchronized internal fun addCallback(stream_callback : (event_type : String, item : Any?)->Unit ) {
			for(c in callback_list) {
				if(c === stream_callback) return
			}
			callback_list.add(stream_callback)
		}
		
		@Synchronized internal fun removeCallback(stream_callback : (event_type : String, item : Any?)->Unit) {
			val it = callback_list.iterator()
			while(it.hasNext()) {
				val c = it.next()
				if(c === stream_callback) it.remove()
			}
		}
		
		/**
		 * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
		 * messages.
		 */
		override fun onOpen(webSocket : WebSocket, response : Response) {
			log.d("WebSocket onOpen. url=%s .", webSocket.request().url())
		}
		
		/**
		 * Invoked when a text (type `0x1`) message has been received.
		 */
		override fun onMessage(webSocket : WebSocket, text : String) {
			// log.d( "WebSocket onMessage. url=%s, message=%s", webSocket.request().url(), text );
			try {
				val obj = JSONObject(text)
				
				val event = obj.optString("event")
				if(event == null || event.isEmpty()) {
					log.d("onMessage: missing event parameter")
					return
				}
				
				val payload = TootPayload.parsePayload(parser, event, obj, text)
				if(payload == null) {
					log.d("onMessage: payload is null")
					return
				}
				
				Utils.runOnMainThread {
					synchronized(this) {
						if(bDisposed.get()) return@runOnMainThread
						for(callback in callback_list) {
							try {
								callback(event, payload)
							} catch(ex : Throwable) {
								log.trace(ex)
							}
						}
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		/**
		 * Invoked when the peer has indicated that no more incoming messages will be transmitted.
		 */
		override fun onClosing(webSocket : WebSocket, code : Int, reason : String?) {
			log.d("WebSocket onClosing. code=%s,reason=%s,url=%s .", code, reason, webSocket .request().url())
			webSocket.cancel()
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
		}
		
		/**
		 * Invoked when both peers have indicated that no more messages will be transmitted and the
		 * connection has been successfully released. No further calls to this listener will be made.
		 */
		override fun onClosed(webSocket : WebSocket, code : Int, reason : String?) {
			log.d("WebSocket onClosed.  code=%s,reason=%s,url=%s .", code, reason, webSocket .request().url())
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
		}
		
		/**
		 * Invoked when a web socket has been closed due to an error reading from or writing to the
		 * network. Both outgoing and incoming messages may have been lost. No further calls to this
		 * listener will be made.
		 */
		override fun onFailure(webSocket : WebSocket, ex : Throwable, response : Response?) {
			log.e(ex , "WebSocket onFailure. url=%s .", webSocket .request().url())
			
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			
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
			
			bListening.set(true)
			TootTaskRunner(context).run(access_info, object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					val result = client.webSocket(end_point,  this@Reader)
					if(result == null) {
						log.d("startRead: cancelled.")
						bListening.set(false)
					}else {
						val ws = result.data as? WebSocket
						if(ws != null) {
							socket.set(ws)
						} else {
							val error = result.error
							log.d("startRead: error. $error")
						}
					}
					return result
				}
				override fun handleResult(result : TootApiResult?) {
				}
			})
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
		streamCallback : (event_type : String, item : Any?)->Unit
	) {
		
		val reader = prepareReader(accessInfo, endPoint, highlightTrie)
		reader.addCallback(streamCallback)
		
		if(! reader.bListening.get()) {
			reader.startRead()
		}
	}
	
	
	// カラム破棄やリロードのタイミングで呼ばれる
	fun unregister(
		accessInfo : SavedAccount,
		endPoint : String,
		streamCallback : (event_type : String, item : Any?)->Unit
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
}
