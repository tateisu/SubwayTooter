package jp.juggler.subwaytooter.util

import android.content.Context
import okhttp3.*
import jp.juggler.subwaytooter.App1
import jp.juggler.util.LogCategory

// okhttpそのままだとモックしづらいので
// リクエストを投げてレスポンスを得る部分をインタフェースにまとめる

interface CurrentCallCallback {
	fun onCallCreated(call : Call)
}

interface SimpleHttpClient {
	var currentCallCallback : CurrentCallCallback?
	
	fun getResponse(
		request : Request,
		tmpOkhttpClient : OkHttpClient? = null
	) : Response
	
	fun getWebSocket(
		request : Request,
		webSocketListener : WebSocketListener
	) : WebSocket
}

class SimpleHttpClientImpl(
	val context : Context,
	private val okHttpClient : OkHttpClient
) : SimpleHttpClient {
	
	
	companion object {
		val log = LogCategory("SimpleHttpClientImpl")
	}
	
	override var currentCallCallback : CurrentCallCallback? = null
	
	override fun getResponse(
		request : Request,
		tmpOkhttpClient : OkHttpClient?
	) : Response {
		App1.getAppState(context).networkTracker.checkNetworkState()
		val call = (tmpOkhttpClient ?: this.okHttpClient).newCall(request)
		currentCallCallback?.onCallCreated(call)
		return call.execute()
	}
	
	override fun getWebSocket(
		request : Request,
		webSocketListener : WebSocketListener
	) : WebSocket {
		App1.getAppState(context).networkTracker.checkNetworkState()
		return okHttpClient.newWebSocket(request, webSocketListener)
	}
	
	
}
