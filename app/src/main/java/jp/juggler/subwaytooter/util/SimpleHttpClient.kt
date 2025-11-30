package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.App1
import jp.juggler.util.log.LogCategory
import okhttp3.*
import ru.gildor.coroutines.okhttp.await

/**
 * okhttpClientをラップしたクラス。
 * - モック用途
 * - onCallCreated ラムダ
 * - networkTracker.checkNetworkState
 */
interface SimpleHttpClient {

    var onCallCreated: (Call) -> Unit

    suspend fun getResponse(
        request: Request,
        overrideClient: OkHttpClient? = null,
    ): Response

    fun getWebSocket(
        request: Request,
        webSocketListener: WebSocketListener,
    ): WebSocket
}

class SimpleHttpClientImpl(
    val context: Context,
    private val okHttpClient: OkHttpClient,
) : SimpleHttpClient {

    companion object {
        val log = LogCategory("SimpleHttpClientImpl")
    }

    override var onCallCreated: (Call) -> Unit = {}

    override suspend fun getResponse(
        request: Request,
        overrideClient: OkHttpClient?,
    ): Response {
        App1.getAppState(context).networkTracker.checkNetworkState()
        val call = (overrideClient ?: this.okHttpClient).newCall(request)
        onCallCreated(call)
        return call.await()
    }

    override fun getWebSocket(
        request: Request,
        webSocketListener: WebSocketListener,
    ): WebSocket {
        App1.getAppState(context).networkTracker.checkNetworkState()
        return okHttpClient.newWebSocket(request, webSocketListener)
    }
}
