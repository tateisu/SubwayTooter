package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class FloatPref(key: String, defVal: Float) : BasePref<Float>(key, defVal) {

    override fun readFrom(pref: SharedPreferences): Float =
        pref.getFloat(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Float) {
        if (v == defVal) editor.remove(key) else editor.putFloat(key, v)
    }

    override fun hasNonDefaultValue(pref: SharedPreferences) =
        defVal != pref.getFloat(key, defVal)
}