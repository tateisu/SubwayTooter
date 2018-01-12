package jp.juggler.subwaytooter.util

import okhttp3.*

// okhttpそのままだとモックしづらいので
// リクエストを投げてレスポンスを得る部分をインタフェースにまとめる

interface CurrentCallCallback {
	fun onCallCreated(call : Call)
}

interface SimpleHttpClient{
	var currentCallCallback : CurrentCallCallback?
	fun getResponse(request: Request) : Response
	fun getWebSocket(request: Request, webSocketListener : WebSocketListener): WebSocket
}

class SimpleHttpClientImpl(val okHttpClient:OkHttpClient): SimpleHttpClient{
	override var currentCallCallback : CurrentCallCallback? = null
	
	override fun getResponse(request : Request) : Response {
		val call = okHttpClient.newCall(request)
		currentCallCallback?.onCallCreated(call)
		return call.execute()
	}
	
	override fun getWebSocket(request : Request, webSocketListener : WebSocketListener) : WebSocket {
		return okHttpClient.newWebSocket(request,webSocketListener)
	}
}

