package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button

import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.Utils

import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.util.DialogInterfaceCallback
import jp.juggler.subwaytooter.util.SavedAccountCallback

object AccountPicker {
	
	fun pick(
		activity : AppCompatActivity, bAllowPseudo : Boolean, bAuto : Boolean, message : String?, callback : SavedAccountCallback
	) {
		
		val account_list = SavedAccount.loadAccountList(activity)
		SavedAccount.sort(account_list)
		pick(activity, bAllowPseudo, bAuto, message, account_list, true, callback, null)
	}
	
	fun pick(
		activity : AppCompatActivity, bAllowPseudo : Boolean, bAuto : Boolean, message : String?,
		callback : SavedAccountCallback,
		dismiss_callback : DialogInterfaceCallback?
	) {
		val account_list = SavedAccount.loadAccountList(activity)
		pick(activity, bAllowPseudo, bAuto, message, account_list, true, callback, dismiss_callback)
	}
	
	fun pick(
		activity : AppCompatActivity, bAllowPseudo : Boolean, bAuto : Boolean, message : String?, account_list : ArrayList<SavedAccount>, callback : SavedAccountCallback
	) {
		pick(activity, bAllowPseudo, bAuto, message, account_list, false, callback, null)
	}
	
	@SuppressLint("InflateParams")
	private fun pick(
		activity : AppCompatActivity, bAllowPseudo : Boolean, bAuto : Boolean, message : String?, account_list : ArrayList<SavedAccount>, bSort : Boolean, callback : SavedAccountCallback, dismiss_callback : DialogInterfaceCallback?
	) {
		if(account_list.isEmpty()) {
			Utils.showToast(activity, false, R.string.account_empty)
			return
		}
		
		if(! bAllowPseudo) {
			val tmp_list = ArrayList<SavedAccount>()
			for(a in account_list) {
				if(a.isPseudo) continue
				tmp_list.add(a)
			}
			account_list.clear()
			account_list.addAll(tmp_list)
			if(account_list.isEmpty()) {
				Utils.showToast(activity, false, R.string.not_available_for_pseudo_account)
				return
			}
		}
		
		if(bSort) {
			SavedAccount.sort(account_list)
		}
		
		if(bAuto && account_list.size == 1) {
			callback(account_list[0])
			return
		}
		
		
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_account_picker, null, false)
		
		val dialog = Dialog(activity)
		val isDialogClosed = AtomicBoolean(false)
		
		dialog.setOnDismissListener{
			if( dismiss_callback != null ) dismiss_callback(it)
		}
		
		dialog.setContentView(viewRoot)
		if( message != null && message.isNotEmpty() ) {
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
		
		
		val llAccounts = viewRoot.findViewById<LinearLayout>(R.id.llAccounts)
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
				b.setTextColor(ac.color_fg)
			}
			
			b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
			b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
			b.setAllCaps(false)
			b.layoutParams = lp
			b.minHeight = (0.5f + 32f * density).toInt()
			b.text = if(AcctColor.hasNickname(ac)) ac.nickname else a.acct
			
			b.setOnClickListener {
				isDialogClosed.set(true)
				callback(a)
				dialog.dismiss()
			}
			llAccounts.addView(b)
		}
		
		dialog.show()
	}
	
}
