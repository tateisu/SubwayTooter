package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class LongPref(key: String, defVal: Long) : BasePref<Long>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Long =
        pref.getLong(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Long) {
        if (v == defVal) editor.remove(key) else editor.putLong(key, v)
    }
}