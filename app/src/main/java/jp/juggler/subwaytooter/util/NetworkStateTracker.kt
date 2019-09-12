package jp.juggler.subwaytooter.util

import android.content.Context
import android.net.*
import android.os.Build
import jp.juggler.util.LogCategory
import java.lang.RuntimeException

class NetworkStateTracker(
	val context : Context

) : ConnectivityManager.NetworkCallback() {
	
	
	companion object {
		private val log = LogCategory("NetworkStateTracker")
		
		private val NetworkCapabilities?.isConnected : Boolean
			get() = if(this == null) {
				log.e("isConnected: missing NetworkCapabilities.")
				false
			} else {
				this.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			}
		
	}
	
	private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
		as? ConnectivityManager
	
	private var lastNetwork : Network? = null
	
	init {
		if(Build.VERSION.SDK_INT >= 28) {
			cm?.registerDefaultNetworkCallback(this)
			lastNetwork = cm?.activeNetwork
		}
	}
	
//	private fun <T> tryOrNull(block : () -> T?) : T? = try {
//		block()
//	} catch(ex : Throwable) {
//		null
//	}
	
//	@Suppress("DEPRECATION")
//	private fun NetworkInfo?.getStateString() =
//		if(this == null) {
//			"null"
//		} else {
//			// API 28 以上で typeName と state がdeprecated になっている
//			"${tryOrNull { this.typeName }} ${tryOrNull { this.subtypeName }} ${tryOrNull { this.state }} ${tryOrNull { this.detailedState }}"
//		}
	
	////////////////////////////////////////////////////////////////
	// NetworkCallback
	
	//	Called when the framework connects and has declared a new network ready for use.
	// 準備ができた
	override fun onAvailable(network : Network) {
		super.onAvailable(network)
		val nc = try {
			cm?.getNetworkCapabilities(network)?.toString()
		} catch(ex : Throwable) {
			log.e(ex, "getNetworkCapabilities failed.")
		}
		log.d("onAvailable $network $nc")
		if( cm?.getNetworkCapabilities(network).isConnected ){
			this.lastNetwork = network
		}
	}
	
	//	Called when the network the framework connected to for this request changes capabilities but still satisfies the stated need.
	//  接続完了し、ネットワークが変わったあと
	override fun onCapabilitiesChanged(
		network : Network,
		networkCapabilities : NetworkCapabilities
	) {
		super.onCapabilitiesChanged(network, networkCapabilities)
		log.d("onCapabilitiesChanged $network, $networkCapabilities")
		if(networkCapabilities.isConnected) {
			this.lastNetwork = network
		}
	}
	
	//	Called when the network the framework connected to for this request changes LinkProperties.
	override fun onLinkPropertiesChanged(network : Network, linkProperties : LinkProperties) {
		super.onLinkPropertiesChanged(network, linkProperties)
		log.d("onLinkPropertiesChanged $network, $linkProperties")
		if( cm?.getNetworkCapabilities(network).isConnected ){
			this.lastNetwork = network
		}
	}
	
	override fun onLosing(network : Network, maxMsToLive : Int) {
		super.onLosing(network, maxMsToLive)
		log.d("onLosing $network, $maxMsToLive")
		if(lastNetwork == network) lastNetwork = null
	}
	
	//	Called when the framework has a hard loss of the network or when the graceful failure ends.
	override fun onLost(network : Network) {
		super.onLost(network)
		log.d("onLost $network")
		if(lastNetwork == network) lastNetwork = null
	}
	
	////////////////////////////////////////////////////////////////
	
	val isConnected : Boolean
		get() = when(cm) {
			null -> {
				log.e("isConnected: missing ConnectivityManager")
				true
			}
			else -> if(Build.VERSION.SDK_INT >= 23) {
				val activeNetwork = cm.activeNetwork
				if(activeNetwork == null) {
					log.e("isConnected: missing activeNetwork")
					false
				} else {
					cm.getNetworkCapabilities(activeNetwork).isConnected
				}
			} else {
				@Suppress("DEPRECATION")
				val ani = cm.activeNetworkInfo
				if(ani == null) {
					log.e("isConnected: missing activeNetworkInfo")
					false
				} else {
					@Suppress("DEPRECATION")
					ani.isConnected
				}
			}
		}
	
	fun checkNetworkState() {
		if(!isConnected) {
			throw RuntimeException("checkNetworkState: not connected.")
		}
	}
}