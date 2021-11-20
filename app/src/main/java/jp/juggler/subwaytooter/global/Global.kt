package jp.juggler.subwaytooter.global

import android.content.Context
import androidx.startup.Initializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

val appDatabase by lazy {
    getKoin().get<AppDatabaseHolder>().database
}

val appPref by lazy {
    getKoin().get<AppPrefHolder>().pref
}

fun getKoin(): Koin = KoinPlatformTools.defaultContext().get()

object Global {

    private var isPrepared = false

    fun prepare(context: Context): Global {
        if (!isPrepared) {
            synchronized(this) {
                if (!isPrepared) {
                    isPrepared = true
                    startKoin {
                        androidContext(context)
                        modules(module {
                            single<AppDatabaseHolder> { AppDatabaseHolderImpl(get()) }
                            single<AppPrefHolder> { AppPrefHolderImpl(get()) }
                        })
                    }
                }
            }
        }
        return this
    }
}

class GlobalInitializer : Initializer<Global> {
    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }

    override fun create(context: Context): Global {
        return Global.prepare(context)
    }
}
