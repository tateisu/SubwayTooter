package jp.juggler.subwaytooter.pref

import jp.juggler.subwaytooter.pref.impl.LongPref

object PrefL {

    // long
    val lpDefaultPostAccount = LongPref("tablet_toot_default_account", -1L)

    // long
    val lpThemeDefaultChangedWarnTime = LongPref("lpThemeDefaultChangedWarnTime", -1L)
}
