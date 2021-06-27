package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RawRes
import jp.juggler.util.decodeUTF8
import jp.juggler.util.digestSHA256
import jp.juggler.util.encodeBase64Url
import jp.juggler.util.loadRawResource

// 利用規約
// 同意済みかどうか調べる
// 関連データを提供する
class PrivacyPolicyChecker(
    val context: Context,
    val pref: SharedPreferences = context.pref(),
) {
    val bytes by lazy {
        @RawRes val resId = when (context.getString(R.string.language_code)) {
            "ja" -> R.raw.privacy_policy_ja
            "fr" -> R.raw.privacy_policy_fr
            else -> R.raw.privacy_policy_en
        }
        context.loadRawResource(resId)
    }

    val text by lazy { bytes.decodeUTF8() }
    val digest by lazy { bytes.digestSHA256().encodeBase64Url() }

    val agreed: Boolean
        get() = when {
            bytes.isEmpty() -> true
            else -> digest == PrefS.spAgreedPrivacyPolicyDigest(pref)
        }
}
