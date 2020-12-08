package jp.juggler.subwaytooter.util

import android.content.Context
import okhttp3.*
import jp.juggler.subwaytooter.App1
import jp.juggler.util.LogCategory
import ru.gildor.coroutines.okhttp.await

// okhttpそのままだとモックしづらいので
// リクエストを投げてレスポンスを得る部分をインタフェースにまとめる

interface SimpleHttpClient {

    var onCallCreated: (Call) -> Unit

    fun getResponse(
		request: Request,
		tmpOkhttpClient: OkHttpClient? = null
	): Response

	suspend fun getResponseAsync(
		request: Request,
		tmpOkhttpClient: OkHttpClient? = null
	): Response

    fun getWebSocket(
		request: Request,
		webSocketListener: WebSocketListener
	): WebSocket
}

class SimpleHttpClientImpl(
	val context: Context,
	private val okHttpClient: OkHttpClient
) : SimpleHttpClient {

    companion object {
        val log = LogCategory("SimpleHttpClientImpl")
    }

    override var onCallCreated: (Call) -> Unit = {}

    override fun getResponse(
		request: Request,
		tmpOkhttpClient: OkHttpClient?
	): Response {
        App1.getAppState(context).networkTracker.checkNetworkState()
        val call = (tmpOkhttpClient ?: this.okHttpClient).newCall(request)
		onCallCreated(call)
        return call.execute()
    }

	override suspend fun getResponseAsync(
		request: Request,
		tmpOkhttpClient: OkHttpClient?
	): Response {
		App1.getAppState(context).networkTracker.checkNetworkState()
		val call = (tmpOkhttpClient ?: this.okHttpClient).newCall(request)
		onCallCreated(call)
		return call.await()
	}

    override fun getWebSocket(
		request: Request,
		webSocketListener: WebSocketListener
	): WebSocket {
        App1.getAppState(context).networkTracker.checkNetworkState()
        return okHttpClient.newWebSocket(request, webSocketListener)
    }


}
