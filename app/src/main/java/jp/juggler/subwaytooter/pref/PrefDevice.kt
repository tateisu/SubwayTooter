package jp.juggler.subwaytooter.pref

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import androidx.startup.AppInitializer
import androidx.startup.Initializer
import jp.juggler.util.data.mayUri
import jp.juggler.util.os.applicationContextSafe
import java.util.UUID

class PrefDevice(context: Context) {

    companion object {
        // この設定ファイルはバックアップ対象から除外するべき
        const val SHARED_PREFERENCE_NAME = "device"

        // 認証開始時の状況を覚える
        private const val PREF_AUTH_SERVER_TYPE = "authServerType"
        private const val PREF_AUTH_API_HOST = "authApiHost"
        private const val PREF_AUTH_SESSION_ID = "authSessionId"

        private const val PREF_FCM_TOKEN = "fcmToken"
        private const val PREF_FCM_TOKEN_EXPIRED = "fcmTokenExpired"
        private const val PREF_INSTALL_ID_V2 = "installIdV2"
        private const val PREF_UP_ENDPOINT = "upEndpoint"
        private const val PREF_UP_ENDPOINT_EXPIRED = "upEndpointExpired"
        private const val PREF_PUSH_DISTRIBUTOR = "pushDistributor"
        private const val PREF_TIME_LAST_ENDPOINT_REGISTER = "timeLastEndpointRegister"
        private const val PREF_SUPRESS_REQUEST_NOTIFICATION_PERMISSION =
            "supressRequestNotificationPermission"
        private const val PREF_MEDIA_PICKER_MULTIPLE = "mediaPickerMultiple"
        private const val PREF_CAMERA_OPENER_LAST_URI = "cameraOpenerLastUri"
        private const val PREF_CAPTURE_ACTION = "captureAction"
        private const val PREF_CAPTURE_ERROR_CAPTION = "captureErrorCaption"

        const val PUSH_DISTRIBUTOR_FCM = "fcm"
        const val PUSH_DISTRIBUTOR_NONE = "none"

        // 以下は古いキー
        // private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_POST_WINDOW_W = "postWindowW"
        private const val KEY_POST_WINDOW_H = "postWindowH"

        private const val KEY_POLLING_WORKER2_INTERVAL = "pollingworker2Interval"
        private const val LAST_AUTH_INSTANCE = "lastAuthInstance"
        private const val LAST_AUTH_SECRET = "lastAuthSecret"
        private const val LAST_AUTH_DB_ID = "lastAuthDbId"

        fun SharedPreferences.Editor.putLongNullable(key: String, value: Long?) = apply {
            if (value == null) remove(key) else putLong(key, value)
        }

        fun SharedPreferences.Editor.putIntNullable(key: String, value: Int?) = apply {
            if (value == null) remove(key) else putInt(key, value)
        }

        fun SharedPreferences.Editor.putBooleanNullable(key: String, value: Boolean?) = apply {
            if (value == null) remove(key) else putBoolean(key, value)
        }
    }

    private val sp = context.getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

    private fun edit(block: (SharedPreferences.Editor) -> Unit) {
        val e = sp.edit()
        block(e)
        e.apply()
    }

    @Suppress("SameParameterValue")
    private fun string(key: String) = sp.getString(key, null)

    @Suppress("SameParameterValue")
    private fun long(key: String) = if (sp.contains(key)) sp.getLong(key, 0L) else null

    @Suppress("SameParameterValue")
    private fun int(key: String) = if (sp.contains(key)) sp.getInt(key, 0) else null

    @Suppress("SameParameterValue")
    private fun boolean(key: String) = if (sp.contains(key)) sp.getBoolean(key, false) else null

    @Suppress("SameParameterValue")
    private fun String?.saveTo(key: String) =
        edit { it.putString(key, this) }

    @Suppress("SameParameterValue")
    private fun Long?.saveTo(key: String) =
        edit { it.putLongNullable(key, this) }

    @Suppress("SameParameterValue")
    private fun Int?.saveTo(key: String) =
        edit { it.putIntNullable(key, this) }

    @Suppress("SameParameterValue")
    private fun Boolean?.saveTo(key: String) =
        edit { it.putBooleanNullable(key, this) }

    // 認証開始時の状態を覚えておく
    val authServerType: String? get() = string(PREF_AUTH_SERVER_TYPE)
    val authApiHost: String? get() = string(PREF_AUTH_API_HOST)
    val authSessionId: String? get() = string(PREF_AUTH_SESSION_ID)
    fun saveAuthStart(apiHost: String, sessionId: String) {
        edit {
            it.putString(PREF_AUTH_API_HOST, apiHost)
            it.putString(PREF_AUTH_SESSION_ID, sessionId)
        }
    }

    // アプリサーバV2用のインストールID
    val installIdv2: String
        get() = synchronized(this) {
            string(PREF_INSTALL_ID_V2)
                ?: UUID.randomUUID().toString()
                    .apply { saveTo(PREF_INSTALL_ID_V2) }
        }

    var fcmToken: String?
        get() = string(PREF_FCM_TOKEN)
        set(value) {
            value.saveTo(PREF_FCM_TOKEN)
        }

    var fcmTokenExpired: String?
        get() = string(PREF_FCM_TOKEN_EXPIRED)
        set(value) {
            value.saveTo(PREF_FCM_TOKEN_EXPIRED)
        }

    var upEndpoint: String?
        get() = string(PREF_UP_ENDPOINT)
        set(value) {
            value.saveTo(PREF_UP_ENDPOINT)
        }

    var upEndpointExpired: String?
        get() = string(PREF_UP_ENDPOINT_EXPIRED)
        set(value) {
            value.saveTo(PREF_UP_ENDPOINT_EXPIRED)
        }

    var pushDistributor: String?
        get() = string(PREF_PUSH_DISTRIBUTOR)
        set(value) {
            value.saveTo(PREF_PUSH_DISTRIBUTOR)
        }

    var timeLastEndpointRegister: Long
        get() = long(PREF_TIME_LAST_ENDPOINT_REGISTER) ?: 0L
        set(value) {
            value.saveTo(PREF_TIME_LAST_ENDPOINT_REGISTER)
        }

    var supressRequestNotificationPermission: Boolean
        get() = boolean(PREF_SUPRESS_REQUEST_NOTIFICATION_PERMISSION) ?: false
        set(value) {
            value.saveTo(PREF_SUPRESS_REQUEST_NOTIFICATION_PERMISSION)
        }

    var mediaPickerMultiple: Boolean
        get() = boolean(PREF_MEDIA_PICKER_MULTIPLE) ?: false
        set(value) {
            value.saveTo(PREF_MEDIA_PICKER_MULTIPLE)
        }

    var cameraOpenerLastUri: Uri?
        get() = string(PREF_CAMERA_OPENER_LAST_URI)?.mayUri()
        set(value) {
            (value?.toString() ?: "").saveTo(PREF_CAMERA_OPENER_LAST_URI)
        }

    val captureAction
        get() = string(PREF_CAPTURE_ACTION)

    val captureErrorCaption
        get() = string(PREF_CAPTURE_ERROR_CAPTION)

    fun setCaptureParams(action: String, errorCaption: String) {
        edit {
            it.putString(PREF_CAPTURE_ACTION, action)
            it.putString(PREF_CAPTURE_ERROR_CAPTION, errorCaption)
        }
    }

    //////////////////////////////////
    // 以下は古い

    fun savePostWindowBound(w: Int, h: Int) {
        if (w < 64 || h < 64) return
        edit {
            it.putInt(KEY_POST_WINDOW_W, w)
            it.putInt(KEY_POST_WINDOW_H, h)
        }
    }

    fun loadPostWindowBound(): Rect? {
        val w = int(KEY_POST_WINDOW_W) ?: 0
        val h = int(KEY_POST_WINDOW_H) ?: 0
        return if (w <= 0 || h <= 0) null else Rect(0, 0, w, h)
    }

    var pollingWorker2Interval: Long?
        get() = long(KEY_POLLING_WORKER2_INTERVAL)
        set(value) {
            value.saveTo(KEY_POLLING_WORKER2_INTERVAL)
        }

    /**
     * Misskey 10 の認証開始時に状態を覚える
     */
    fun saveLastAuth(host: String, secret: String, dbId: Long?) =
        edit {
            it.putString(LAST_AUTH_INSTANCE, host)
            it.putString(LAST_AUTH_SECRET, secret)
            it.putLongNullable(LAST_AUTH_DB_ID, dbId)
        }

    fun removeLastAuth() {
        edit {
            it.remove(LAST_AUTH_INSTANCE)
            it.remove(LAST_AUTH_SECRET)
            it.remove(LAST_AUTH_DB_ID)
        }
    }

    val lastAuthInstance: String?
        get() = string(LAST_AUTH_INSTANCE)

    val lastAuthSecret: String?
        get() = string(LAST_AUTH_SECRET)

    val lastAuthDbId: Long?
        get() = long(LAST_AUTH_DB_ID)

    /**
     * アプリサーバV1で使っていたインストールID
     */
    val installIdV1 get() = string(KEY_INSTALL_ID)
}

class PrefDeviceInitializer : Initializer<PrefDevice> {
    override fun dependencies(): List<Class<out Initializer<*>>> =
        emptyList()

    override fun create(context: Context) =
        PrefDevice(context.applicationContextSafe)
}

val Context.prefDevice: PrefDevice
    get() = AppInitializer.getInstance(this)
        .initializeComponent(PrefDeviceInitializer::class.java)
