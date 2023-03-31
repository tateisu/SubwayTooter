package jp.juggler.subwaytooter.util

import android.content.Context
import androidx.annotation.RawRes
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.digestSHA256
import jp.juggler.util.data.encodeBase64Url
import jp.juggler.util.data.loadRawResource
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resumeWithException

// 利用規約
// 同意済みかどうか調べる
// 関連データを提供する
class PrivacyPolicyChecker(val context: Context) {
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
            else -> digest == PrefS.spAgreedPrivacyPolicyDigest.value
        }
}

suspend fun ActMain.checkPrivacyPolicy(): Boolean {
    // 既に表示中かもしれない
    if (dlgPrivacyPolicy?.get()?.isShowing == true) {
        throw CancellationException()
    }

    // 同意ずみなら表示しない
    val checker = PrivacyPolicyChecker(this)
    if (checker.agreed) return true

    return suspendCancellableCoroutine { cont ->
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.privacy_policy)
            .setMessage(checker.text)
            .setOnCancelListener { finish() }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
                if (cont.isActive) cont.resume(false) {}
            }
            .setPositiveButton(R.string.agree) { _, _ ->
                PrefS.spAgreedPrivacyPolicyDigest.value = checker.digest
                if (cont.isActive) cont.resume(true) {}
            }
            .setOnDismissListener {
                if (cont.isActive) cont.resumeWithException(CancellationException())
            }
            .create()
        dlgPrivacyPolicy = WeakReference(dialog)
        cont.invokeOnCancellation { dialog.dismissSafe() }
        dialog.show()
    }
}
