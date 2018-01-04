package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.Utils

typealias ReportFormCallback = (dialog : Dialog, comment : String) -> Unit

object ReportForm {
	
	@SuppressLint("InflateParams")
	fun showReportForm(activity : Activity, who : TootAccount, status : TootStatus?, callback : ReportFormCallback) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_report_user, null, false)
		
		val tvUser = view.findViewById<TextView>(R.id.tvUser)
		val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
		val etComment = view.findViewById<EditText>(R.id.etComment)
		
		tvUser.text = who.acct
		tvStatus.text = status?.decoded_content ?: ""
		
		val dialog = Dialog(activity)
		dialog.setContentView(view)
		view.findViewById<View>(R.id.btnOk).setOnClickListener(View.OnClickListener {
			val comment = etComment.text.toString().trim { it <= ' ' }
			if(comment.isEmpty()) {
				Utils.showToast(activity, true, R.string.comment_empty)
				return@OnClickListener
			}
			
			callback(dialog, comment)
		})
		view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }
		
		
		dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
		dialog.show()
	}
}
