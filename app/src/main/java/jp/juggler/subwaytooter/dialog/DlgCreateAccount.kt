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

class DlgCreateAccount(
	val activity : Activity,
	val instance : String,
	val onClickOk : (
		dialog : Dialog,
		username : String,
		email : String,
		password : String,
		agreement : Boolean,
		reason : String?
	) -> Unit
) : View.OnClickListener {
	
	companion object {
		// private val log = LogCategory("DlgCreateAccount")
	}
	
	@SuppressLint("InflateParams")
	private val viewRoot = activity.layoutInflater
		.inflate(R.layout.dlg_account_create, null, false)
	
	private val etUserName : EditText = viewRoot.findViewById(R.id.etUserName)
	private val etEmail : EditText = viewRoot.findViewById(R.id.etEmail)
	private val etPassword : EditText = viewRoot.findViewById(R.id.etPassword)
	private val cbAgreement : CheckBox = viewRoot.findViewById(R.id.cbAgreement)
	private val tvDescription : TextView = viewRoot.findViewById(R.id.tvDescription)
	private val etReason : EditText = viewRoot.findViewById(R.id.etReason)
	private val tvReasonCaption : TextView = viewRoot.findViewById(R.id.tvReasonCaption)
	
	private val dialog = Dialog(activity)
	
	init {
		viewRoot.findViewById<TextView>(R.id.tvInstance).text = instance
		
		arrayOf(
			R.id.btnRules,
			R.id.btnTerms,
			R.id.btnCancel,
			R.id.btnOk
		).forEach {
			viewRoot.findViewById<Button>(it)?.setOnClickListener(this)
		}
		
		val instanceInfo = TootInstance.getCached(instance)
		
		tvDescription.text =
			DecodeOptions(
				activity,
				LinkHelper.newLinkHelper(
					instance, misskeyVersion = instanceInfo?.misskeyVersion ?: 0
				)
			)
				.decodeHTML(
					instanceInfo?.short_description?.notBlank()
						?: instanceInfo?.description?.notBlank()
						?: TootInstance.DESCRIPTION_DEFAULT
				)
				.neatSpaces()
		
		val showReason = instanceInfo?.approval_required ?: false
		tvReasonCaption.vg(showReason)
		etReason.vg(showReason)
		
	}
	
	fun show() {
		dialog.setContentView(viewRoot)
		
		dialog.window?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.WRAP_CONTENT
		)
		dialog.show()
	}
	
	override fun onClick(v : View?) {
		when(v?.id) {
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
						cbAgreement.isChecked,
						when(etReason.visibility) {
							View.VISIBLE -> etReason.text.toString().trim()
							else -> null
						}
					)
				}
			}
		}
	}
}
