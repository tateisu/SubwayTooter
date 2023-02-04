package jp.juggler.subwaytooter.push

import android.content.Context
import androidx.startup.AppInitializer
import androidx.startup.Initializer
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.EmptyScope
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import jp.juggler.subwaytooter.BuildConfig
import jp.juggler.subwaytooter.pref.prefDevice

private val log = LogCategory("FcmHandler")

class FcmHandler(
    private val context: Context,
) {
    companion object {
        val reNoFcm = """noFcm""".toRegex(RegexOption.IGNORE_CASE)
    }

    val fcmToken = MutableStateFlow(context.prefDevice.fcmToken)

    val noFcm :Boolean
        get()= reNoFcm.containsMatchIn(BuildConfig.FLAVOR)

    val hasFcm get() = !noFcm

    fun onTokenChanged(token: String?) {
        context.prefDevice.fcmToken = token
        EmptyScope.launch(AppDispatchers.IO) { fcmToken.emit(token) }
    }

    suspend fun onMessageReceived(data: Map<String, String>) {
        try {
            context.pushRepo.handleFcmMessage(data)
        } catch (ex: Throwable) {
            log.e(ex, "onMessage failed.")
        }
    }

    suspend fun deleteFcmToken() =
        withContext(AppDispatchers.IO) {
            // 古いトークンを覚えておく
            context.prefDevice.fcmToken
                ?.takeIf { it.isNotEmpty() }
                ?.let { context.prefDevice.fcmTokenExpired = it }
            // FCMから削除する
            log.i("deleteFcmToken: start")
            FcmTokenLoader().deleteToken()
            log.i("deleteFcmToken: end")
            onTokenChanged(null)
            log.i("deleteFcmToken complete")
        }

    suspend fun loadFcmToken(): String? = try {
        withContext(AppDispatchers.IO) {
            log.i("loadFcmToken start")
            val token = FcmTokenLoader().getToken()
            log.i("loadFcmToken onTokenChanged")
            onTokenChanged(token)
            log.i("loadFcmToken end")
            token
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

/**
 * AndroidManifest.xml で androidx.startup.InitializationProvider から参照される
 */
@Suppress("unused")
class FcmHandlerInitializer : Initializer<FcmHandler> {
    override fun dependencies(): List<Class<out Initializer<*>>> =
        emptyList()

    override fun create(context: Context): FcmHandler {
        val newHandler = FcmHandler(context.applicationContext)
        log.i("FcmHandlerInitializer hasFcm=${newHandler.hasFcm}, BuildConfig.FLAVOR=${BuildConfig.FLAVOR}")
        EmptyScope.launch{
            newHandler.loadFcmToken()
        }
        return newHandler
    }
}

val Context.fcmHandler: FcmHandler
    get() = AppInitializer.getInstance(this)
        .initializeComponent(FcmHandlerInitializer::class.java)
