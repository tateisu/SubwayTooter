package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.showToast

object ReportForm {
	
	@SuppressLint("InflateParams")
	fun showReportForm(
		activity : Activity,
		access_info : SavedAccount,
		who : TootAccount,
		status : TootStatus?,
		onClickOk : (dialog : Dialog, comment : String,forward:Boolean) -> Unit
	) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_report_user, null, false)
		
		val tvUser :TextView = view.findViewById(R.id.tvUser)
		val tvStatusCaption :TextView = view.findViewById(R.id.tvStatusCaption)
		val tvStatus :TextView = view.findViewById(R.id.tvStatus)
		val etComment :EditText = view.findViewById(R.id.etComment)
		
		val cbForward : CheckBox = view.findViewById(R.id.cbForward)
		val tvForwardDesc:TextView = view.findViewById(R.id.tvForwardDesc)
		val canForward = access_info.host != who.host
		
		
		cbForward.isChecked = false
		if(!canForward){
			cbForward.visibility = View.GONE
			tvForwardDesc.visibility = View.GONE
		}else{
			cbForward.visibility = View.VISIBLE
			tvForwardDesc.visibility = View.VISIBLE
			cbForward.text = activity.getString(R.string.report_forward_to,who.host)
		}
		
		
		tvUser.text = who.acct
		
		
		if( status == null){
			tvStatusCaption.visibility = View.GONE
			tvStatus.visibility = View.GONE
		}else{
			tvStatus.text = status.decoded_content
		}
		
		val dialog = Dialog(activity)
		dialog.setContentView(view)
		view.findViewById<View>(R.id.btnOk).setOnClickListener(View.OnClickListener {
			val comment = etComment.text.toString().trim()
			if(comment.isEmpty()) {
				showToast(activity, true, R.string.comment_empty)
				return@OnClickListener
			}
			
			onClickOk(dialog, comment,cbForward.isChecked)
		})
		view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }
		
		
		dialog.window?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.MATCH_PARENT
		)
		dialog.show()
	}
}
