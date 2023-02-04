package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences
import jp.juggler.subwaytooter.pref.lazyPref

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
    abstract fun readFrom(pref: SharedPreferences): T

    var value : T
        get()= readFrom(lazyPref)
        set(value){
            val e = lazyPref.edit()
            put(e,value)
            e.apply()
        }

    fun removeValue(pref:SharedPreferences = lazyPref){
        pref.edit().remove(key).apply()
    }

    override fun equals(other: Any?) =
        this === other

    override fun hashCode(): Int = key.hashCode()

    fun remove(e: SharedPreferences.Editor): SharedPreferences.Editor =
        e.remove(key)

    fun removeDefault(pref: SharedPreferences, e: SharedPreferences.Editor) =
        if (pref.contains(key) && this.value == defVal) {
            e.remove(key)
            true
        } else {
            false
        }

    abstract fun hasNonDefaultValue(pref: SharedPreferences= lazyPref): Boolean
}