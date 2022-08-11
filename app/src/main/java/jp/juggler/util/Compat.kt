package jp.juggler.util

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult

/**
 * API 33 で Bundle.get() が deprecatedになる。
 * type safeにするべきだが、過去の使い方にもよるかな…
 */
fun Bundle.getRaw(key: String) = get(key)

/**
 * Ringtone pickerの処理結果のUriまたはnull
 */
fun ActivityResult.decodeRingtonePickerResult() =
    when {
        isNotOk -> null
        else -> data?.extras?.getRaw(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
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
        null -> null
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
