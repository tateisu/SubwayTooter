package jp.juggler.subwaytooter.pref.impl

import android.content.Context
import android.content.SharedPreferences
import jp.juggler.subwaytooter.global.appPref
import jp.juggler.subwaytooter.pref.pref

@Suppress("EqualsOrHashCode")
abstract class BasePref<T>(val key: String, val defVal: T) {

    companion object {
        // キー名と設定項目のマップ。インポートやアプリ設定で使う
        val allPref = HashMap<String, BasePref<*>>()
    }

    init {
        when {
            allPref[key] != null -> error("Preference key duplicate: $key")
            else -> {
                @Suppress("LeakingThis")
                allPref[key] = this
            }
        }
    }

    abstract fun put(editor: SharedPreferences.Editor, v: T)
    abstract operator fun invoke(pref: SharedPreferences): T

    override fun equals(other: Any?) =
        this === other

    override fun hashCode(): Int = key.hashCode()

    open operator fun invoke(context: Context): T =
        invoke(context.pref())

    operator fun invoke(): T = invoke(appPref)

    fun remove(e: SharedPreferences.Editor): SharedPreferences.Editor =
        e.remove(key)

    fun removeDefault(pref: SharedPreferences, e: SharedPreferences.Editor) =
        if (pref.contains(key) && this.invoke(pref) == defVal) {
            e.remove(key)
            true
        } else {
            false
        }
}