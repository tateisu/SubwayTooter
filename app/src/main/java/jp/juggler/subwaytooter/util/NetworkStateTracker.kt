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
	
	private fun <T> tryOrNull(block : () -> T?) : T? = try {
		block()
	} catch(ex : Throwable) {
		null
	}
	
	@Suppress("DEPRECATION")
	private fun NetworkInfo?.getStateString() =
		if(this == null) {
			"null"
		} else {
			// API 28 以上で typeName と state がdeprecated になっている
			"${tryOrNull { this.typeName }} ${tryOrNull { this.subtypeName }} ${tryOrNull { this.state }} ${tryOrNull { this.detailedState }}"
		}
	
	////////////////////////////////////////////////////////////////
	// NetworkCallback
	
	//	Called when the framework connects and has declared a new network ready for use.
	// 準備ができた
	override fun onAvailable(network : Network?) {
		val ni : NetworkInfo? = try {
			cm?.getNetworkInfo(network)
		} catch(ex : Throwable) {
			null
		}
		log.d("onAvailable ${ni.getStateString()}")
		super.onAvailable(network)
		this.lastNetwork = network
	}
	
	//	Called when the network the framework connected to for this request changes capabilities but still satisfies the stated need.
	//  接続完了し、ネットワークが変わったあと
	override fun onCapabilitiesChanged(
		network : Network?,
		networkCapabilities : NetworkCapabilities?
	) {
		val ni : NetworkInfo? = try {
			cm?.getNetworkInfo(network)
		} catch(ex : Throwable) {
			null
		}
		log.d("onCapabilitiesChanged ${ni.getStateString()}")
		super.onCapabilitiesChanged(network, networkCapabilities)
		this.lastNetwork = network
	}
	
	//	Called when the network the framework connected to for this request changes LinkProperties.
	override fun onLinkPropertiesChanged(network : Network?, linkProperties : LinkProperties?) {
		val ni : NetworkInfo? = try {
			cm?.getNetworkInfo(network)
		} catch(ex : Throwable) {
			null
		}
		log.d("onLinkPropertiesChanged ${ni.getStateString()}")
		super.onLinkPropertiesChanged(network, linkProperties)
		this.lastNetwork = network
	}
	
	override fun onLosing(network : Network?, maxMsToLive : Int) {
		val ni : NetworkInfo? = try {
			cm?.getNetworkInfo(network)
		} catch(ex : Throwable) {
			null
		}
		log.d("onLosing ${ni.getStateString()}")
		super.onLosing(network, maxMsToLive)
		this.lastNetwork = null
	}
	
	//	Called when the framework has a hard loss of the network or when the graceful failure ends.
	override fun onLost(network : Network?) {
		val ni : NetworkInfo? = try {
			cm?.getNetworkInfo(network)
		} catch(ex : Throwable) {
			null
		}
		log.d("onLost ${ni.getStateString()}")
		super.onLost(network)
		this.lastNetwork = null
	}
	
	////////////////////////////////////////////////////////////////
	
	val isConnected : Boolean
		get() {
			return if(cm == null) {
				log.e("isConnected: missing ConnectivityManager")
				true
				
			} else {
				val activeNetworkInfo = cm.activeNetworkInfo
				if(activeNetworkInfo == null) {
					log.e("isConnected: missing activeNetworkInfo")
					false
				} else if(! activeNetworkInfo.isConnected) {
					log.e("not connected: ${activeNetworkInfo.getStateString()}")
					false
				} else {
					true
				}
			}
		}
	
	fun checkNetworkState() {
		
		if(cm == null) {
			log.e("isConnected: missing ConnectivityManager")
		} else {
			val activeNetworkInfo = cm.activeNetworkInfo
				?: throw RuntimeException("missing activeNetworkInfo")
			
			if(activeNetworkInfo.isConnected) return
			
			throw RuntimeException("not connected. ${activeNetworkInfo.getStateString()}")
			
		}
	}
}