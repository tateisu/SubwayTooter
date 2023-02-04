package jp.juggler.subwaytooter.pref.impl

import android.content.SharedPreferences

class StringPref(
    key: String,
    defVal: String,
    val skipImport: Boolean = false,
) : BasePref<String>(key, defVal) {

    override fun readFrom(pref: SharedPreferences): String =
        pref.getString(key, defVal) ?: defVal

    override fun put(editor: SharedPreferences.Editor, v: String) {
        if (v == defVal) editor.remove(key) else editor.putString(key, v)
    }

    override fun hasNonDefaultValue(pref: SharedPreferences) =
        defVal != pref.getString(key, defVal)

    fun toInt() = value.toIntOrNull() ?: defVal.toInt()
}
