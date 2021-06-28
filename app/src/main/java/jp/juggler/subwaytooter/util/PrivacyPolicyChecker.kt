package jp.juggler.subwaytooter.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RawRes
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.util.decodeUTF8
import jp.juggler.util.digestSHA256
import jp.juggler.util.encodeBase64Url
import jp.juggler.util.loadRawResource
import java.lang.ref.WeakReference

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

fun ActMain.checkPrivacyPolicy() {

    // 既に表示中かもしれない
    if (dlgPrivacyPolicy?.get()?.isShowing == true) return

    val checker = PrivacyPolicyChecker(this, pref)

    // 同意ずみなら表示しない
    if (checker.agreed) return

    AlertDialog.Builder(this)
        .setTitle(R.string.privacy_policy)
        .setMessage(checker.text)
        .setOnCancelListener { finish() }
        .setNegativeButton(R.string.cancel) { _, _ -> finish() }
        .setPositiveButton(R.string.agree) { _, _ ->
            pref.edit().put(PrefS.spAgreedPrivacyPolicyDigest, checker.digest).apply()
        }
        .create()
        .also { dlgPrivacyPolicy = WeakReference(it) }
        .show()
}
