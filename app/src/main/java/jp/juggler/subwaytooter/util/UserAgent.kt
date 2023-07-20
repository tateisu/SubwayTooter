package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.Build
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.getPackageInfoCompat

val reNotAllowedInUserAgent = "[^\\x21-\\x7e]+".asciiPattern()

fun Context.userAgentDefault(): String {
    val versionName = try {
        packageManager.getPackageInfoCompat(packageName)!!.versionName
    } catch (ex: Throwable) {
        App1.log.e(ex, "can't get versionName.")
        "0.0.0"
    }
    return "SubwayTooter/${versionName} Android/${Build.VERSION.RELEASE}"
}

fun Context.getUserAgent(): String {
    val userAgentCustom = PrefS.spUserAgent.value
    return when {
        userAgentCustom.isNotEmpty() && !reNotAllowedInUserAgent.matcher(userAgentCustom)
            .find() -> userAgentCustom

        else -> userAgentDefault()
    }
}
