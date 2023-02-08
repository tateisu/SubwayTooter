package jp.juggler.subwaytooter.pref

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.startup.Initializer
import jp.juggler.subwaytooter.BuildConfig
import jp.juggler.util.os.applicationContextSafe
import java.util.concurrent.atomic.AtomicReference

var lazyContextOverride = AtomicReference<Context>()
var lazyPrefOverride = AtomicReference<SharedPreferences>()

val lazyContext
    get() = lazyContextOverride.get()
        ?: LazyContextHolder.contextNullable
        ?: error("LazyContextHolder not initialized")

val lazyPref
    get() = lazyPrefOverride.get()
        ?: LazyContextHolder.prefNullable
        ?: error("LazyContextHolder not initialized")

const val FILE_PROVIDER_AUTHORITY ="${BuildConfig.APPLICATION_ID}.FileProvider"

@SuppressLint("StaticFieldLeak")
object LazyContextHolder {
    var contextNullable: Context? = null
    var prefNullable: SharedPreferences? = null

    fun init(context: Context) {
        contextNullable = context
        prefNullable = context.getSharedPreferences(
            "${context.packageName}_preferences",
            Context.MODE_PRIVATE
        )
    }
}

class LazyContextInitializer : Initializer<LazyContextHolder> {
    override fun dependencies(): List<Class<out Initializer<*>>> =
        emptyList()

    override fun create(context: Context): LazyContextHolder {
        LazyContextHolder.init(context.applicationContextSafe)
        return LazyContextHolder
    }
}

