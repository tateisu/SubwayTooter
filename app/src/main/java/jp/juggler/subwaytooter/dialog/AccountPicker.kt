package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.dismissSafe
import jp.juggler.util.getAdaptiveRippleDrawableRound
import jp.juggler.util.showToast
import org.jetbrains.anko.textColor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("InflateParams")
suspend fun AppCompatActivity.pickAccount(
    bAllowPseudo: Boolean = false,
    bAllowMisskey: Boolean = true,
    bAllowMastodon: Boolean = true,
    bAuto: Boolean = false,
    message: String? = null,
    accountListArg: MutableList<SavedAccount>? = null,
    dismiss_callback:  (dialog : DialogInterface) -> Unit ={},
    extra_callback: (LinearLayout, Int, Int) -> Unit = { _, _, _ -> },
):SavedAccount?{
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

    val account_list: MutableList<SavedAccount> = accountListArg
        ?: SavedAccount.loadAccountList(activity)
            .filter { 0 == it.checkMastodon() + it.checkMisskey() + it.checkPseudo() }
            .toMutableList()
            .also { SavedAccount.sort(it) }

    if (account_list.isEmpty()) {

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

    if (bAuto && account_list.size == 1) {
        return account_list[0]
    }

    return suspendCoroutine { continuation ->
        val viewRoot = layoutInflater.inflate(R.layout.dlg_account_picker, null, false)
        val dialog = Dialog(activity)
        val isResumed = AtomicBoolean(false)

        dialog.setOnDismissListener {
            dismiss_callback(it)
            if(isResumed.compareAndSet(false,true)){
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
        val pad_se = (0.5f + 12f * density).toInt()
        val pad_tb = (0.5f + 6f * density).toInt()

        for (a in account_list) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val ac = AcctColor.load(a)

            val b = Button(activity)

            if (AcctColor.hasColorBackground(ac)) {
                b.background = getAdaptiveRippleDrawableRound(activity, ac.color_bg, ac.color_fg)
            } else {
                b.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
            }
            if (AcctColor.hasColorForeground(ac)) {
                b.textColor = ac.color_fg
            }

            b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.isAllCaps = false
            b.layoutParams = lp
            b.minHeight = (0.5f + 32f * density).toInt()

            val sb = SpannableStringBuilder(ac.nickname)
            if (a.last_notification_error?.isNotEmpty() == true) {
                sb.append("\n")
                val start = sb.length
                sb.append(a.last_notification_error)
                val end = sb.length
                sb.setSpan(RelativeSizeSpan(0.7f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (a.last_subscription_error?.isNotEmpty() == true) {
                sb.append("\n")
                val start = sb.length
                sb.append(a.last_subscription_error)
                val end = sb.length
                sb.setSpan(RelativeSizeSpan(0.7f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            b.text = sb

            b.setOnClickListener {
                if(isResumed.compareAndSet(false,true)){
                    continuation.resume(a)
                }
                dialog.dismissSafe()
            }
            llAccounts.addView(b)
        }

        extra_callback(llAccounts, pad_se, pad_tb)

        dialog.show()
    }
}
