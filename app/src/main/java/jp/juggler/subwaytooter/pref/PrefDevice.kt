package jp.juggler.subwaytooter.pref

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect

object PrefDevice {

    private const val file_name = "device"

    internal const val KEY_DEVICE_TOKEN = "device_token"
    internal const val KEY_INSTALL_ID = "install_id"
    private const val KEY_POST_WINDOW_W = "postWindowW"
    private const val KEY_POST_WINDOW_H = "postWindowH"

    const val LAST_AUTH_INSTANCE = "lastAuthInstance"
    const val LAST_AUTH_SECRET = "lastAuthSecret"
    const val LAST_AUTH_DB_ID = "lastAuthDbId"

    fun from(context: Context): SharedPreferences {
        return context.getSharedPreferences(file_name, Context.MODE_PRIVATE)
    }

    fun savePostWindowBound(context: Context, w: Int, h: Int) {
        if (w < 64 || h < 64) return
        from(context).edit().putInt(KEY_POST_WINDOW_W, w).putInt(KEY_POST_WINDOW_H, h).apply()
    }

    fun loadPostWindowBound(context: Context): Rect? {
        val pref = from(context)
        val w = pref.getInt(KEY_POST_WINDOW_W, 0)
        val h = pref.getInt(KEY_POST_WINDOW_H, 0)
        return if (w <= 0 || h <= 0) null else Rect(0, 0, w, h)
    }
}
