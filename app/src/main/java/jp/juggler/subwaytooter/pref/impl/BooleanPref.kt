package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class BooleanPref(key: String, defVal: Boolean) : BasePref<Boolean>(key, defVal) {

    override fun readFrom(pref: SharedPreferences): Boolean =
        pref.getBoolean(key, defVal)

    // put if value is not default, remove if value is same to default
    override fun put(editor: SharedPreferences.Editor, v: Boolean) {
        if (v == defVal) editor.remove(key) else editor.putBoolean(key, v)
    }

    override fun hasNonDefaultValue(pref: SharedPreferences) =
        defVal != pref.getBoolean(key, defVal)
}
