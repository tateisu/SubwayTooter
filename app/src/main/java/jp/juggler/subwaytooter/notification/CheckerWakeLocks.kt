package jp.juggler.subwaytooter.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import jp.juggler.subwaytooter.App1
import jp.juggler.util.coroutine.AppDispatchers.withTimeoutSafe
import jp.juggler.util.log.*
import jp.juggler.util.systemService
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

class CheckerWakeLocks(contextArg: Context) {
    companion object {
        private val log = LogCategory("CheckerWakeLocks")

        private var checkerWakeLocksNullable: CheckerWakeLocks? = null

        fun checkerWakeLocks(context: Context): CheckerWakeLocks {
            // double-check before/after lock
            checkerWakeLocksNullable?.let { return it }
            return synchronized(this) {
                checkerWakeLocksNullable
                    ?: CheckerWakeLocks(context.applicationContext)
                        .also { checkerWakeLocksNullable = it }
            }
        }
    }

    // クラッシュレポートによると App1.onCreate より前にここを通る場合がある
    // データベースへアクセスできるようにする
    val appState = App1.prepare(contextArg, "PollingNotificationChecker")

    val connectivityManager: ConnectivityManager by lazy {
        systemService(contextArg)
            ?: error("missing ConnectivityManager system service")
    }

    val notificationManager: NotificationManager by lazy {
        systemService(contextArg)
            ?: error("missing NotificationManager system service")
    }

    private val powerManager: PowerManager by lazy {
        systemService(contextArg)
            ?: error("missing PowerManager system service")
    }
    private val wifiManager: WifiManager by lazy {
        // WifiManagerの取得時はgetApplicationContext を使わないとlintに怒られる
        systemService(contextArg.applicationContext)
            ?: error("missing WifiManager system service")
    }

    private val powerLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            PollingChecker::class.java.name
        ).apply { setReferenceCounted(false) }
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        if (Build.VERSION.SDK_INT >= 34) {
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                PollingChecker::class.java.name
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                PollingChecker::class.java.name
            )
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(PollingChecker::class.java.name)
        }.apply { setReferenceCounted(false) }
    }

    @SuppressLint("WakelockTimeout")
    fun acquireWakeLocks() {
        log.d("acquire power lock...")
        try {
            if (!powerLock.isHeld) {
                powerLock.acquire()
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't update powerLock.")
        }

        try {
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't update wifiLock.")
        }
    }

    fun releasePowerLocks() {
        log.d("release power lock...")
        try {
            if (powerLock.isHeld) {
                powerLock.release()
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't release powerLock.")
        }

        try {
            if (wifiLock.isHeld) {
                wifiLock.release()
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't release wifiLock.")
        }
    }

    /**
     * ネットワーク接続があるか一定時間まち、タイムアウトしたら例外を投げる
     */
    suspend fun checkConnection() {
        var connectionState: String? = null
        try {
            withTimeoutSafe(10000L) {
                while (true) {
                    connectionState = appState.networkTracker.connectionState
                        ?: break // null if connected
                    delay(333L)
                }
            }
        } catch (ignored: TimeoutCancellationException) {
            error("network state timeout. $connectionState")
        }
    }
}
