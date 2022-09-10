package jp.juggler.util

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ResolveInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity

/**
 * API 33 で Bundle.get() が deprecatedになる。
 * type safeにするべきだが、過去の使い方にもよるかな…
 */
private fun Bundle.getRaw(key: String) =
    @Suppress("DEPRECATION")
    get(key)

fun Intent.getUriExtra(key: String) =
    extras?.getRaw(key) as? Uri

fun Intent.getStreamUriExtra() =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri?
    }

fun Intent.getStreamUriListExtra() =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(Intent.EXTRA_STREAM)
    }

fun Intent.getIntentExtra(key: String) =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

/**
 * Ringtone pickerの処理結果のUriまたはnull
 */
fun ActivityResult.decodeRingtonePickerResult() =
    when {
        isNotOk -> null
        else -> data?.getUriExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

/**
 * Bundleからキーを指定してint値またはnullを得る
 */
fun Bundle.int(key: String) =
    when (val v = getRaw(key)) {
        null -> null
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

/**
 * Bundleからキーを指定してlong値またはnullを得る
 */
fun Bundle.long(key: String) =
    when (val v = getRaw(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

/**
 * IntentのExtrasからキーを指定してint値またはnullを得る
 */
fun Intent.int(key: String) = extras?.int(key)

/**
 * IntentのExtrasからキーを指定してlong値またはnullを得る
 */
fun Intent.long(key: String) = extras?.long(key)

fun PackageManager.getPackageInfoCompat(
    pakageName: String,
    flags: Int = 0,
): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
    getPackageInfo(pakageName, PackageInfoFlags.of(flags.toLong()))
} else {
    @Suppress("DEPRECATION")
    getPackageInfo(pakageName, flags)
}

fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
    queryFlag: Int = 0,
): List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
    queryIntentActivities(intent, ResolveInfoFlags.of(queryFlag.toLong()))
} else {
    @Suppress("DEPRECATION")
    queryIntentActivities(intent, queryFlag)
}

fun PackageManager.resolveActivityCompat(
    intent: Intent,
    queryFlag: Int = 0,
): ResolveInfo? = if (Build.VERSION.SDK_INT >= 33) {
    resolveActivity(intent, ResolveInfoFlags.of(queryFlag.toLong()))
} else {
    @Suppress("DEPRECATION")
    resolveActivity(intent, queryFlag)
}

fun AppCompatActivity.backPressed(block: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = block()
    })
}
