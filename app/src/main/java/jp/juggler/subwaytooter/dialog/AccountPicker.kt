package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.getAdaptiveRippleDrawableRound
import org.jetbrains.anko.textColor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val log = LogCategory("pickAccount")

@SuppressLint("InflateParams")
suspend fun AppCompatActivity.pickAccount(
    bAllowPseudo: Boolean = false,
    bAllowMisskey: Boolean = true,
    bAllowMastodon: Boolean = true,
    bAuto: Boolean = false,
    message: String? = null,
    accountListArg: List<SavedAccount>? = null,
    dismissCallback: (dialog: DialogInterface) -> Unit = {},
    extraCallback: (LinearLayout, Int, Int) -> Unit = { _, _, _ -> },
): SavedAccount? {
    val activity = this
    var removeMastodon = 0
    var removedMisskey = 0
    var removedPseudo = 0

    fun SavedAccount.checkMastodon() = when {
        !bAllowMastodon && !isMisskey -> ++removeMastodon
        else -> 0
    }

    fun SavedAccount.checkMisskey() = when {
        !bAllowMisskey && isMisskey -> ++removedMisskey
        else -> 0
    }

    fun SavedAccount.checkPseudo() = when {
        !bAllowPseudo && isPseudo -> ++removedPseudo
        else -> 0
    }

    val accountList = accountListArg
        ?: daoSavedAccount.loadAccountList()
            .filter { 0 == it.checkMastodon() + it.checkMisskey() + it.checkPseudo() }
            .sortedByNickname()

    if (accountList.isEmpty()) {

        val sb = StringBuilder()

        if (removedPseudo > 0) {
            sb.append(activity.getString(R.string.not_available_for_pseudo_account))
        }

        if (removedMisskey > 0) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(activity.getString(R.string.not_available_for_misskey_account))
        }
        if (removeMastodon > 0) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(activity.getString(R.string.not_available_for_mastodon_account))
        }

        if (sb.isEmpty()) {
            sb.append(activity.getString(R.string.account_empty))
        }

        activity.showToast(false, sb.toString())
        return null
    }

    if (bAuto && accountList.size == 1) {
        return accountList[0]
    }

    return suspendCoroutine { continuation ->
        val viewRoot = layoutInflater.inflate(R.layout.dlg_account_picker, null, false)
        val dialog = Dialog(activity)
        val isResumed = AtomicBoolean(false)

        dialog.setOnDismissListener {
            dismissCallback(it)
            if (isResumed.compareAndSet(false, true)) {
                continuation.resume(null)
            }
        }

        dialog.setContentView(viewRoot)
        if (message != null && message.isNotEmpty()) {
            val tv = viewRoot.findViewById<TextView>(R.id.tvMessage)
            tv.visibility = View.VISIBLE
            tv.text = message
        }

        viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.cancel()
        }

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val density = activity.resources.displayMetrics.density

        val llAccounts: LinearLayout = viewRoot.findViewById(R.id.llAccounts)
        val padX = (0.5f + 12f * density).toInt()
        val padY = (0.5f + 6f * density).toInt()

        for (a in accountList) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val ac = daoAcctColor.load(a)

            val b = AppCompatButton(activity)

            if (daoAcctColor.hasColorBackground(ac)) {
                b.background = getAdaptiveRippleDrawableRound(
                    activity,
                    ac.colorBg,
                    ac.colorFg
                )
            } else {
                b.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
            }
            if (daoAcctColor.hasColorForeground(ac)) {
                b.textColor = ac.colorFg
            }

            b.setPaddingRelative(padX, padY, padX, padY)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.isAllCaps = false
            b.layoutParams = lp
            b.minHeight = (0.5f + 32f * density).toInt()

            val sb = SpannableStringBuilder(ac.nickname)
            try {
                val status = daoAccountNotificationStatus.load(a.acct)
                val lastNotificationError = status?.lastNotificationError?.notEmpty()
                val lastSubscriptionError = status?.lastSubscriptionError?.notEmpty()
                (lastNotificationError ?: lastSubscriptionError)?.let { message ->
                    sb.append("\n")
                    val start = sb.length
                    sb.append(message)
                    val end = sb.length
                    sb.setSpan(
                        RelativeSizeSpan(0.7f),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } catch (ex: Throwable) {
                log.e(ex, "can't get notification status for ${a.acct}")
            }
            b.text = sb

            b.setOnClickListener {
                if (isResumed.compareAndSet(false, true)) {
                    continuation.resume(a)
                }
                dialog.dismissSafe()
            }
            llAccounts.addView(b)
        }

        extraCallback(llAccounts, padX, padY)

        dialog.show()
    }
}
