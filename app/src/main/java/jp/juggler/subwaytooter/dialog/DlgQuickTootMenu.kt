package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import jp.juggler.util.dismissSafe
import java.lang.ref.WeakReference
import android.view.Gravity
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.TootVisibility

class DlgQuickTootMenu(
	internal val activity : ActMain,
	internal val callback : Callback
) : View.OnClickListener {
	
	companion object {
		val etTextIds = arrayOf(
			R.id.etText0,
			R.id.etText1,
			R.id.etText2,
			R.id.etText3,
			R.id.etText4,
			R.id.etText5
		)
		val btnTextIds = arrayOf(
			R.id.btnText0,
			R.id.btnText1,
			R.id.btnText2,
			R.id.btnText3,
			R.id.btnText4,
			R.id.btnText5
		)
		
		val visibilityList = arrayOf(
			TootVisibility.AccountSetting,
			TootVisibility.WebSetting,
			TootVisibility.Public,
			TootVisibility.UnlistedHome,
			TootVisibility.PrivateFollowers,
			TootVisibility.DirectSpecified
		)
		
	}
	
	interface Callback {
		fun onMacro(text:String)
		
		var visibility : TootVisibility
	}
	
	private var refDialog : WeakReference<Dialog>? = null
	
	fun toggle() {
		val dialog = refDialog?.get()
		if(dialog != null && dialog.isShowing) {
			dialog.dismissSafe()
		} else {
			show()
		}
	}
	
	private val etText = arrayOfNulls<EditText>(6)
	private lateinit var btnVisibility :Button
	
	@SuppressLint("InflateParams")
	fun show(){
		val view = activity.layoutInflater.inflate(R.layout.dlg_quick_toot_menu, null, false)

		view.findViewById<Button>(R.id.btnCancel).setOnClickListener(this)
		
		val btnListener :View.OnClickListener = View.OnClickListener{ v ->
			val text = etText[v.tag as? Int ?: 0]?.text?.toString()
			if(text!= null) {
				refDialog?.get()?.dismissSafe()
				callback.onMacro(text)
			}
		}

		val strings = loadStrings()
		val size = etText.size
		for(i in 0 until size ){
			val et :EditText = view.findViewById(etTextIds[i])
			val btn : Button = view.findViewById(btnTextIds[i])
			btn.tag = i
			btn.setOnClickListener(btnListener)
			etText[i] = et
			et.setText( if( i >= strings.size){
				""
			}else{
				strings[i]
			})
		}
		
		btnVisibility = view.findViewById(R.id.btnVisibility)
		btnVisibility.setOnClickListener(this)
		
		showVisibility()
		
		val dialog = Dialog(activity)
		this.refDialog = WeakReference(dialog)
		dialog.setCanceledOnTouchOutside(true)
		dialog.setContentView(view)
		
		dialog.setOnDismissListener { saveStrings() }
		
		dialog.window?.apply{
			val wlp = attributes
			wlp.gravity = Gravity.BOTTOM or Gravity.START
			wlp.flags = wlp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
			attributes = wlp
			setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
		}
		dialog.show()
	}
	
	
	private fun loadStrings() =
		Pref.spQuickTootMacro(activity.pref).split("\n")
		
	private fun saveStrings() = activity.pref
			.edit()
			.put(
				Pref.spQuickTootMacro,
				etText.joinToString("\n") {
					(it?.text?.toString() ?: "").replace("\n", " ")
				}
			)
			.apply()
	
	
	override fun onClick(v : View?) { // TODO
		when(v?.id){

			R.id.btnCancel-> refDialog?.get()?.dismissSafe()

			R.id.btnVisibility -> performVisibility()
		}
	}
	
	
	private fun performVisibility() {

		val caption_list = visibilityList
			.map { Styler.getVisibilityCaption(activity, false, it) }
			.toTypedArray()
		
		AlertDialog.Builder(activity)
			.setTitle(R.string.choose_visibility)
			.setItems(caption_list) { _, which ->
				if(which in visibilityList.indices) {
					callback.visibility = visibilityList[which]
					showVisibility()
				}
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
		
	}

	private fun showVisibility() {
		btnVisibility.text = Styler.getVisibilityCaption(activity,false,callback.visibility)
	}
}
