package jp.juggler.subwaytooter.global

import android.content.Context
import android.content.SharedPreferences
import jp.juggler.subwaytooter.pref.pref

interface AppPrefHolder {
    val pref: SharedPreferences
}

class AppPrefHolderImpl(context: Context) : AppPrefHolder {
    override val pref = context.pref()
}
