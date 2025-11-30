package jp.juggler.subwaytooter.push

import android.content.Context
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.withContext

val fcmHandler = FcmHandler

object FcmHandler {
    private val log = LogCategory("FcmHandler")

    fun hasFcm(context: Context): Boolean =
        FcmTokenLoader.isPlayServiceAvailavle(context)

    fun noFcm(context: Context): Boolean =
        !hasFcm(context)

    suspend fun deleteFcmToken(context: Context) =
        withContext(AppDispatchers.IO) {
            // 古いトークンを覚えておく
            loadFcmToken()?.notEmpty()?.let {
                context.prefDevice.fcmTokenExpired = it
            }
            // FCMにトークン変更を依頼する
            FcmTokenLoader.deleteToken()
        }

    suspend fun loadFcmToken(): String? = try {
        withContext(AppDispatchers.IO) {
            FcmTokenLoader.getToken()
        }
    } catch (ex: Throwable) {
        // https://github.com/firebase/firebase-android-sdk/issues/4053
        // java.io.IOException: java.util.concurrent.ExecutionException: java.io.IOException: SERVICE_NOT_AVAILABLE

        //
        // java.lang.IllegalStateException: Default FirebaseApp is not initialized in this process jp.juggler.pushreceiverapp. Make sure to call FirebaseApp.initializeApp(Context) first.
        // at com.google.firebase.FirebaseApp.getInstance(FirebaseApp.java:186)

        log.w(ex, "loadFcmToken failed")
        null
    }
}
