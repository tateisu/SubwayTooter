package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences
import jp.juggler.subwaytooter.App1

class FloatPref(key: String, defVal: Float) : BasePref<Float>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Float =
        pref.getFloat(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Float) {
        if (v == defVal) editor.remove(key) else editor.putFloat(key, v)
    }
}