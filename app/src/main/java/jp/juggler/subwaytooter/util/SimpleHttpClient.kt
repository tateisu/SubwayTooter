package jp.juggler.subwaytooter.util

import android.content.Context
import okhttp3.*
import android.net.ConnectivityManager
import android.net.NetworkInfo

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
	context : Context,
	private val okHttpClient : OkHttpClient
) : SimpleHttpClient {
	
	
	companion object {
		val log = LogCategory("SimpleHttpClientImpl")
		var connectivityManager : ConnectivityManager? = null
	}
	
	init {
		if(connectivityManager == null) {
			connectivityManager =
				context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
		}
	}
	
	override var currentCallCallback : CurrentCallCallback? = null
	
	override fun getResponse(
		request : Request,
		tmpOkhttpClient : OkHttpClient?
	) : Response {
		checkNetworkState()
		val call = (tmpOkhttpClient ?: this.okHttpClient).newCall(request)
		currentCallCallback?.onCallCreated(call)
		return call.execute()
	}
	
	override fun getWebSocket(
		request : Request,
		webSocketListener : WebSocketListener
	) : WebSocket {
		checkNetworkState()
		return okHttpClient.newWebSocket(request, webSocketListener)
	}
	
	private fun checkNetworkState() {
		
		val cm = connectivityManager
		if(cm == null) {
			log.d("missing ConnectivityManager")
		} else {
			val networkInfo = cm.activeNetworkInfo
				?: throw RuntimeException("missing ActiveNetwork")
			
			val state = networkInfo.state
			val detailedState = networkInfo.detailedState
			if(! networkInfo.isConnected) {
				throw RuntimeException("network not ready. state=$state detail=$detailedState")
			}
			if(state == NetworkInfo.State.CONNECTED && detailedState == NetworkInfo.DetailedState.CONNECTED) {
				// no logging
			} else {
				log.d("checkNetworkState state=$state detail=$detailedState")
			}
		}
		
	}
	
}


