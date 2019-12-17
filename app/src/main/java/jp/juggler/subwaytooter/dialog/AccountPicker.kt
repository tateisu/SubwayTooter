package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DialogInterfaceCallback
import jp.juggler.subwaytooter.util.SavedAccountCallback
import jp.juggler.util.dismissSafe
import jp.juggler.util.showToast
import org.jetbrains.anko.textColor
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object AccountPicker {
	
	@SuppressLint("InflateParams")
	fun pick(
		activity : AppCompatActivity,
		bAllowPseudo : Boolean = false,
		bAllowMisskey : Boolean = true,
		bAllowMastodon: Boolean = true,
		bAuto : Boolean = false,
		message : String? = null,
		accountListArg : ArrayList<SavedAccount>? = null,
		dismiss_callback : DialogInterfaceCallback? = null,
		extra_callback : (LinearLayout,Int,Int)->Unit = {_,_,_->},
		callback : SavedAccountCallback
	) {
		var removedMisskey =0
		var removedPseudo =0
		var removeMastodon = 0
		val account_list : MutableList<SavedAccount> = accountListArg ?: {
			val l = SavedAccount.loadAccountList(activity).filter { a->
				var bOk = true
				
				if( !bAllowMastodon && !a.isMisskey ){
					++removeMastodon
					bOk=false
				}
				
				if( !bAllowMisskey && a.isMisskey ){
					++removedMisskey
					bOk=false
				}

				if( !bAllowPseudo && a.isPseudo ){
					++removedPseudo
					bOk=false
				}

				bOk
			}.toMutableList()
			SavedAccount.sort(l)
			l
		}()
		
		if(account_list.isEmpty()) {

			val sb=StringBuilder()

			if( removedPseudo > 0 ){
				sb.append(activity.getString(R.string.not_available_for_pseudo_account))
			}

			if( removedMisskey > 0 ){
				if(sb.isNotEmpty() ) sb.append('\n')
				sb.append(activity.getString(R.string.not_available_for_misskey_account))
			}
			if( removeMastodon > 0 ){
				if(sb.isNotEmpty() ) sb.append('\n')
				sb.append(activity.getString(R.string.not_available_for_mastodon_account))
			}

			if( sb.isEmpty() ){
				sb.append(activity.getString(R.string.account_empty))
			}
			
			showToast(activity, false,sb.toString())
			return
		}

		if(bAuto && account_list.size == 1) {
			callback(account_list[0])
			return
		}
		
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_account_picker, null, false)
		
		val dialog = Dialog(activity)
		val isDialogClosed = AtomicBoolean(false)
		
		dialog.setOnDismissListener {
			if(dismiss_callback != null) dismiss_callback(it)
		}
		
		dialog.setContentView(viewRoot)
		if(message != null && message.isNotEmpty()) {
			val tv = viewRoot.findViewById<TextView>(R.id.tvMessage)
			tv.visibility = View.VISIBLE
			tv.text = message
		}
		viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener {
			isDialogClosed.set(true)
			dialog.cancel()
		}
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(true)
		dialog.setOnCancelListener { isDialogClosed.set(true) }
		
		val density = activity.resources.displayMetrics.density
		
		val llAccounts :LinearLayout= viewRoot.findViewById(R.id.llAccounts)
		val pad_se = (0.5f + 12f * density).toInt()
		val pad_tb = (0.5f + 6f * density).toInt()
		
		for(a in account_list) {
			val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
			
			val ac = AcctColor.load(a.acct)
			
			val b = Button(activity)
			
			if(AcctColor.hasColorBackground(ac)) {
				b.setBackgroundColor(ac.color_bg)
			} else {
				b.setBackgroundResource(R.drawable.btn_bg_transparent)
			}
			if(AcctColor.hasColorForeground(ac)) {
				b.textColor = ac.color_fg
			}
			
			b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
			b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
			b.isAllCaps = false
			b.layoutParams = lp
			b.minHeight = (0.5f + 32f * density).toInt()
			
			val sb = SpannableStringBuilder(if(AcctColor.hasNickname(ac)) ac.nickname else a.acct)
			if( a.last_notification_error?.isNotEmpty() == true) {
				sb.append("\n")
				val start = sb.length
				sb.append(a.last_notification_error)
				val end = sb.length
				sb.setSpan(RelativeSizeSpan(0.7f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			}else if(a.last_subscription_error?.isNotEmpty() == true) {
				sb.append("\n")
				val start = sb.length
				sb.append(a.last_subscription_error)
				val end = sb.length
				sb.setSpan(RelativeSizeSpan(0.7f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			b.text = sb
			
			b.setOnClickListener {
				isDialogClosed.set(true)
				callback(a)
				dialog.dismissSafe()
			}
			llAccounts.addView(b)
		}
		
		extra_callback(llAccounts,pad_se, pad_tb)
		
		dialog.show()
	}
}
