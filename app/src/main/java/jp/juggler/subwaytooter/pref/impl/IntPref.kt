package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class IntPref(key: String, defVal: Int) : BasePref<Int>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Int =
        pref.getInt(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Int) {
        if (v == defVal) editor.remove(key) else editor.putInt(key, v)
    }
}