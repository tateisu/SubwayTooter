package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class IntPref(key: String, defVal: Int, val noRemove:Boolean = false) : BasePref<Int>(key, defVal) {

    override fun readFrom(pref: SharedPreferences): Int =
        pref.getInt(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Int) {
        if (!noRemove && v == defVal) editor.remove(key) else editor.putInt(key, v)
    }

    override fun hasNonDefaultValue(pref: SharedPreferences) =
        defVal != pref.getInt(key, defVal)
}
