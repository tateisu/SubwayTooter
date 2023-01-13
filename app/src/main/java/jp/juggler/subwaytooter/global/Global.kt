package jp.juggler.subwaytooter.global

import android.content.Context
import androidx.startup.Initializer
import jp.juggler.util.log.LogCategory
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

val appDatabase by lazy { getKoin().get<AppDatabaseHolder>().database }

val appPref by lazy { getKoin().get<AppPrefHolder>().pref }

fun getKoin(): Koin = KoinPlatformTools.defaultContext().get()

object Global {
    private val log = LogCategory("Global")

    private var isPrepared = false

    fun prepare(contextArg: Context, caller: String): Global {
        // double check befort/after lock
        if (!isPrepared) {
            synchronized(this) {
                if (!isPrepared) {
                    isPrepared = true
                    log.i("prepare. caller=$caller")
                    startKoin {
                        androidContext(contextArg)
                        modules(module {
                            single<AppPrefHolder> {
                                val context: Context = get()
                                log.i("AppPrefHolderImpl: context=$context")
                                AppPrefHolderImpl(context)
                            }
                            single<AppDatabaseHolder> {
                                val context: Context = get()
                                log.i("AppDatabaseHolderImpl: context=$context")
                                AppDatabaseHolderImpl(context)
                            }
                        })
                    }
                    getKoin().get<AppDatabaseHolder>().afterGlobalPrepare()
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
        return Global.prepare(context, "GlobalInitializer")
    }
}
