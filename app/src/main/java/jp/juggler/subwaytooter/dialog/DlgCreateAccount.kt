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
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.*

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
			agreement : Boolean,
			reason : String?
		) -> Unit
	) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_account_create, null, false)
		
		view.findViewById<TextView>(R.id.tvInstance).text = instance
		
		val etUserName : EditText = view.findViewById(R.id.etUserName)
		val etEmail : EditText = view.findViewById(R.id.etEmail)
		val etPassword : EditText = view.findViewById(R.id.etPassword)
		val cbAgreement : CheckBox = view.findViewById(R.id.cbAgreement)
		val tvDescription : TextView = view.findViewById(R.id.tvDescription)
		val etReason : EditText = view.findViewById(R.id.etReason)
		val tvReasonCaption : TextView = view.findViewById(R.id.tvReasonCaption)
		
		val dialog = Dialog(activity)
		dialog.setContentView(view)
		
		val instanceInfo = TootInstance.getCached(instance)
		val options = DecodeOptions(
			activity,
			LinkHelper.newLinkHelper(instance, misskeyVersion = instanceInfo?.misskeyVersion ?: 0)
		)
		tvDescription.text = options.decodeHTML(
			instanceInfo?.short_description?.notBlank()
				?: instanceInfo?.description?.notBlank()
				?: TootInstance.DESCRIPTION_DEFAULT
		).neatSpaces()
		
		val showReason = instanceInfo?.approval_required ?: false
		tvReasonCaption.vg(showReason)
		etReason.vg(showReason)
		
		val listener : View.OnClickListener = View.OnClickListener { v ->
			when(v.id) {
				R.id.btnRules ->
					App1.openCustomTab(activity, "https://$instance/about/more")
				
				R.id.btnTerms ->
					App1.openCustomTab(activity, "https://$instance/terms")
				
				R.id.btnCancel ->
					dialog.cancel()
				
				R.id.btnOk -> {
					val username = etUserName.text.toString().trim()
					val email = etEmail.text.toString().trim()
					val password = etPassword.text.toString().trim()
					val agreement = cbAgreement.isChecked
					val reason =
						if(etReason.visibility == View.VISIBLE) etReason.text.toString().trim() else null
					
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
							agreement,
							reason
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
