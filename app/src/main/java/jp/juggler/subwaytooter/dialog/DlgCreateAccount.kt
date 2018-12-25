package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import jp.juggler.util.showToast

object DlgCreateAccount {
	private val log = LogCategory("DlgCreateAccount")
	
	@SuppressLint("InflateParams")
	fun showCreateAccountForm(
		activity : Activity,
		instance : String,
		onClickOk : (
			dialog : Dialog,
			username : String,
			email : String,
			password : String,
			agreement : Boolean
		) -> Unit
	) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_account_create, null, false)
		
		view.findViewById<TextView>(R.id.tvInstance).text = instance
		
		val etUserName : EditText = view.findViewById(R.id.etUserName)
		val etEmail : EditText = view.findViewById(R.id.etEmail)
		val etPassword : EditText = view.findViewById(R.id.etPassword)
		val cbAgreement : CheckBox = view.findViewById(R.id.cbAgreement)
		
		val dialog = Dialog(activity)
		dialog.setContentView(view)
		
		val listener : View.OnClickListener = View.OnClickListener { v ->
			when(v.id) {
				R.id.btnRules ->
					App1.openCustomTab(activity, "https://$instance/about/more")
				
				R.id.btnTerms ->
					App1.openCustomTab(activity, "https://$instance/terms")
				
				R.id.btnCancel ->
					dialog.cancel()
				
				R.id.btnOk -> {
					val username = etUserName.text.toString().trim { it <= ' ' }
					val email = etEmail.text.toString().trim { it <= ' ' }
					val password = etPassword.text.toString().trim { it <= ' ' }
					val agreement = cbAgreement.isChecked
					
					when {
						
						username.isEmpty() ->
							showToast(activity, true, R.string.username_empty)
						
						email.isEmpty() ->
							showToast(activity, true, R.string.email_empty)
						
						password.isEmpty() ->
							showToast(activity, true, R.string.password_empty)
						
						username.contains("/") || username.contains("@") ->
							showToast(activity, true, R.string.username_not_need_atmark)
						
						else -> onClickOk(
							dialog,
							username,
							email,
							password,
							agreement
						)
						
					}
				}
				
			}
		}
		
		arrayOf(
			R.id.btnRules,
			R.id.btnTerms,
			R.id.btnCancel,
			R.id.btnOk
		).forEach {
			view.findViewById<Button>(it)?.setOnClickListener(listener)
		}
		
		dialog.window?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.WRAP_CONTENT
		)
		dialog.show()
	}
	
}
