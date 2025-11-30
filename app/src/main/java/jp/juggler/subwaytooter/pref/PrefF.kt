package jp.juggler.subwaytooter.pref

import jp.juggler.subwaytooter.pref.impl.FloatPref

object PrefF {
    // float

    val fpTimelineFontSize = FloatPref("timeline_font_size", Float.NaN)
    val fpAcctFontSize = FloatPref("acct_font_size", Float.NaN)
    val fpNotificationTlFontSize = FloatPref("notification_tl_font_size", Float.NaN)
    val fpHeaderTextSize = FloatPref("HeaderTextSize", Float.NaN)
    internal const val default_timeline_font_size = 14f
    internal const val default_acct_font_size = 12f
    internal const val default_notification_tl_font_size = 14f
    internal const val default_header_font_size = 14f
}