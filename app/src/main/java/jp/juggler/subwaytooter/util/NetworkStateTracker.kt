package jp.juggler.subwaytooter.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import jp.juggler.util.LogCategory

class NetworkStateTracker(
    val context: Context,
    val onConnectionStateChanged: () -> Unit,

    ) : ConnectivityManager.NetworkCallback() {

    companion object {
        private val log = LogCategory("NetworkStateTracker")

//		private val NetworkCapabilities?.isConnected : Boolean
//			get() = if(this == null) {
//				log.e("isConnected: missing NetworkCapabilities.")
//				false
//			} else {
//				this.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//			}
    }

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager

    init {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                cm.registerDefaultNetworkCallback(this)
            } catch (ex: Throwable) {
                // android.net.ConnectivityManager$TooManyRequestsException:
                log.e(ex, "registerDefaultNetworkCallback failed.")
            }
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
    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        val nc = try {
            cm.getNetworkCapabilities(network)?.toString()
        } catch (ex: Throwable) {
            log.e(ex, "getNetworkCapabilities failed.")
        }
        log.d("onAvailable $network $nc")
        onConnectionStateChanged()
    }

    //	Called when the network the framework connected to for this request changes capabilities but still satisfies the stated need.
    //  接続完了し、ネットワークが変わったあと
    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
    ) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        log.d("onCapabilitiesChanged $network, $networkCapabilities")
        onConnectionStateChanged()
    }

    //	Called when the network the framework connected to for this request changes LinkProperties.
    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties)
        log.d("onLinkPropertiesChanged $network, $linkProperties")
        onConnectionStateChanged()
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
        super.onLosing(network, maxMsToLive)
        log.d("onLosing $network, $maxMsToLive")
        onConnectionStateChanged()
    }

    //	Called when the framework has a hard loss of the network or when the graceful failure ends.
    override fun onLost(network: Network) {
        super.onLost(network)
        log.d("onLost $network")
        onConnectionStateChanged()
    }

    ////////////////////////////////////////////////////////////////

    // null if connected, else error status.
    val connectionState: String?
        get() = if (Build.VERSION.SDK_INT >= 29) {
            val activeNetwork = cm.activeNetwork
            if (activeNetwork == null) {
                "activeNetwork is null"
            } else {
                val capabilities = cm.getNetworkCapabilities(activeNetwork)

                // log.d("connectionState: $activeNetwork $capabilities")
                // connectionState: 103 [ Transports: WIFI Capabilities: NOT_METERED&INTERNET&NOT_RESTRICTED&TRUSTED&NOT_VPN&VALIDATED&NOT_ROAMING&FOREGROUND&NOT_CONGESTED&NOT_SUSPENDED LinkUpBandwidth>=1048576Kbps LinkDnBandwidth>=1048576Kbps SignalStrength: -48]

                // capabilities.isConnected でも cm.isDefaultNetworkActive でもなく
                // インターネット接続ありかどうかを確認する
                when {
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> null
                    else -> "activeNetwork.capabilities?.hasCapability(internet) is not true. $activeNetwork $capabilities"
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = cm.activeNetworkInfo

            @Suppress("DEPRECATION")
            when {
                activeNetworkInfo == null -> "connectionState: activeNetworkInfo is null"
                !activeNetworkInfo.isConnected -> "connectionState: not connected. $activeNetworkInfo"
                else -> null
            }
        }

    fun checkNetworkState() {
        connectionState?.let { error(it) }
    }
}
