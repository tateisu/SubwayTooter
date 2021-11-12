package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences
import jp.juggler.subwaytooter.App1

class BooleanPref(key: String, defVal: Boolean) : BasePref<Boolean>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Boolean =
        pref.getBoolean(key, defVal)

    // put if value is not default, remove if value is same to default
    override fun put(editor: SharedPreferences.Editor, v: Boolean) {
        if (v == defVal) editor.remove(key) else editor.putBoolean(key, v)
    }
}