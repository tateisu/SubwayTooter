package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.CheckBox
import android.widget.TextView

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.EmptyCallback

object DlgConfirm {
	
	interface Callback {
		var isConfirmEnabled : Boolean
		
		fun onOK()
	}
	
	@SuppressLint("InflateParams")
	fun open(activity : Activity, message : String, callback : Callback) {
		
		if(! callback.isConfirmEnabled) {
			callback.onOK()
			return
		}
		
		val view = activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
		val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
		val cbSkipNext = view.findViewById<CheckBox>(R.id.cbSkipNext)
		tvMessage.text = message
		
		AlertDialog.Builder(activity)
			.setView(view)
			.setCancelable(true)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.ok) { _, _ ->
				if(cbSkipNext.isChecked) {
					callback.isConfirmEnabled = false
				}
				callback.onOK()
			}
			.show()
	}
	
	@SuppressLint("InflateParams")
	fun openSimple(activity : Activity, message : String, callback : ()->Unit ) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
		val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
		val cbSkipNext = view.findViewById<CheckBox>(R.id.cbSkipNext)
		tvMessage.text = message
		cbSkipNext.visibility = View.GONE
		
		AlertDialog.Builder(activity)
			.setView(view)
			.setCancelable(true)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.ok) { _, _ -> callback() }
			.show()
	}
}

