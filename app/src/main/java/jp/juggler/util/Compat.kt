package jp.juggler.util

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle

/**
 * API 33 でget() が deprecatedになる？
 */
fun Bundle.getRaw(key: String) = get(key)

fun Intent.decodeRingtonePickerResult() =
    extras?.getRaw(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri

fun Bundle.intOrNull(key: String) =
    when (val v = getRaw(key)) {
        null -> null
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

fun Bundle.longOrNull(key: String) =
    when (val v = getRaw(key)) {
        null -> null
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

fun Intent.intOrNull(key: String) = extras?.intOrNull(key)
fun Intent.longOrNull(key: String) = extras?.longOrNull(key)
