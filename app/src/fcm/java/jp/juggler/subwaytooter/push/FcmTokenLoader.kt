package jp.juggler.subwaytooter.push

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.messaging.FirebaseMessaging
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.tasks.await

/*
// ビルド要求
// com.google.firebase:firebase-messaging.20.3.0 以降
// implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$kotlinx_coroutines_version"


> Default FirebaseApp failed to initialize because no default options were found.
> This usually means that com.google.gms:google-services was not applied to your gradle project.
> FirebaseApp initialization unsuccessful
とか
> FcmHandler/loadFcmToken failed :IllegalStateException
> Default FirebaseApp is not initialized in this process ***.
> Make sure to call FirebaseApp.initializeApp(Context) first.
とか
が出る場合はビルド設定を確認すること

 */
object FcmTokenLoader {

    private val log = LogCategory("FcmTokenLoader")

    private fun connectionResultString(i: Int) = when (i) {
        ConnectionResult.UNKNOWN -> "UNKNOWN"
        ConnectionResult.SUCCESS -> "SUCCESS"
        ConnectionResult.SERVICE_MISSING -> "SERVICE_MISSING"
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "SERVICE_VERSION_UPDATE_REQUIRED"
        ConnectionResult.SERVICE_DISABLED -> "SERVICE_DISABLED"
        ConnectionResult.SIGN_IN_REQUIRED -> "SIGN_IN_REQUIRED"
        ConnectionResult.INVALID_ACCOUNT -> "INVALID_ACCOUNT"
        ConnectionResult.RESOLUTION_REQUIRED -> "RESOLUTION_REQUIRED"
        ConnectionResult.NETWORK_ERROR -> "NETWORK_ERROR"
        ConnectionResult.INTERNAL_ERROR -> "INTERNAL_ERROR"
        ConnectionResult.SERVICE_INVALID -> "SERVICE_INVALID"
        ConnectionResult.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
        ConnectionResult.LICENSE_CHECK_FAILED -> "LICENSE_CHECK_FAILED"
        ConnectionResult.CANCELED -> "CANCELED"
        ConnectionResult.TIMEOUT -> "TIMEOUT"
        ConnectionResult.INTERRUPTED -> "INTERRUPTED"
        ConnectionResult.API_UNAVAILABLE -> "API_UNAVAILABLE"
        ConnectionResult.SIGN_IN_FAILED -> "SIGN_IN_FAILED"
        ConnectionResult.SERVICE_UPDATING -> "SERVICE_UPDATING"
        ConnectionResult.SERVICE_MISSING_PERMISSION -> "SERVICE_MISSING_PERMISSION"
        ConnectionResult.RESTRICTED_PROFILE -> "RESTRICTED_PROFILE"
        ConnectionResult.RESOLUTION_ACTIVITY_NOT_FOUND -> "RESOLUTION_ACTIVITY_NOT_FOUND"
        ConnectionResult.API_DISABLED -> "API_DISABLED"
        ConnectionResult.API_DISABLED_FOR_CONNECTION -> "API_DISABLED_FOR_CONNECTION"
        else -> "Unknown($i)"
    }

    fun isPlayServiceAvailavle(context: Context): Boolean {
        val code = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)
        if (code == ConnectionResult.SUCCESS) return true
        log.w("isPlayServiceAvailavle=${connectionResultString(code)}")
        return false
    }

    //
    suspend fun getToken(): String? =
        FirebaseMessaging.getInstance().token.await()

    suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}
