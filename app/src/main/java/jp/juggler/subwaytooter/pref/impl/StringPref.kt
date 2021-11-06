package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences
import jp.juggler.util.optInt

class StringPref(
    key: String,
    defVal: String,
    val skipImport: Boolean = false,
) : BasePref<String>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): String =
        pref.getString(key, defVal) ?: defVal

    override fun put(editor: SharedPreferences.Editor, v: String) {
        if (v == defVal) editor.remove(key) else editor.putString(key, v)
    }

    fun toInt(pref: SharedPreferences) = invoke(pref).optInt() ?: defVal.toInt()
}