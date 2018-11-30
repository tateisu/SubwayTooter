package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import jp.juggler.util.showToast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

object LoginForm {
	private val log = LogCategory("LoginForm")
	
	private class StringArray : ArrayList<String>()
	
	@SuppressLint("InflateParams")
	fun showLoginForm(
		activity : Activity,
		instanceArg : String?,
		onClickOk : (
			dialog : Dialog,
			instance : String,
			bPseudoAccount : Boolean,
			bInputAccessToken : Boolean
		) -> Unit
	) {
		val view = activity.layoutInflater.inflate(R.layout.dlg_account_add, null, false)
		val etInstance : AutoCompleteTextView = view.findViewById(R.id.etInstance)
		val btnOk : View = view.findViewById(R.id.btnOk)
		val cbPseudoAccount : CheckBox = view.findViewById(R.id.cbPseudoAccount)
		val cbInputAccessToken : CheckBox = view.findViewById(R.id.cbInputAccessToken)
		
		cbPseudoAccount.setOnCheckedChangeListener { _, _ ->
			cbInputAccessToken.isEnabled = ! cbPseudoAccount.isChecked
		}
		
		if(instanceArg != null && instanceArg.isNotEmpty()) {
			etInstance.setText(instanceArg)
			etInstance.inputType = InputType.TYPE_NULL
			etInstance.isEnabled = false
			etInstance.isFocusable = false
		} else {
			etInstance.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
				if(actionId == EditorInfo.IME_ACTION_DONE) {
					btnOk.performClick()
					return@OnEditorActionListener true
				}
				false
			})
		}
		val dialog = Dialog(activity)
		dialog.setContentView(view)
		btnOk.setOnClickListener { _ -> // 警告がでるが、パラメータ名の指定を削ってはいけない
			val instance = etInstance.text.toString().trim { it <= ' ' }
			
			when {
				instance.isEmpty() -> showToast(activity, true, R.string.instance_not_specified)
				instance.contains("/") || instance.contains("@") -> showToast(
					activity,
					true,
					R.string.instance_not_need_slash
				)
				else -> onClickOk(
					dialog,
					instance,
					cbPseudoAccount.isChecked,
					cbInputAccessToken.isChecked
				)
			}
		}
		view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }
		
		val instance_list = ArrayList<String>()
		try {
			activity.resources.openRawResource(R.raw.server_list).use { inStream ->
				val br = BufferedReader(InputStreamReader(inStream, "UTF-8"))
				while(true) {
					val s : String = br.readLine()?.trim { it <= ' ' }?.toLowerCase() ?: break
					if(s.isNotEmpty()) instance_list.add(s)
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		val adapter = object : ArrayAdapter<String>(
			activity, R.layout.lv_spinner_dropdown, ArrayList()
		) {
			
			val nameFilter : Filter = object : Filter() {
				override fun convertResultToString(value : Any) : CharSequence {
					return value as String
				}
				
				override fun performFiltering(constraint : CharSequence?) : Filter.FilterResults {
					val result = Filter.FilterResults()
					if(constraint?.isNotEmpty() == true) {
						val key = constraint.toString().toLowerCase()
						// suggestions リストは毎回生成する必要がある。publishResultsと同時にアクセスされる場合がある
						val suggestions = StringArray()
						for(s in instance_list) {
							if(s.contains(key)) {
								suggestions.add(s)
								if(suggestions.size >= 20) break
							}
						}
						result.values = suggestions
						result.count = suggestions.size
					}
					return result
				}
				
				override fun publishResults(
					constraint : CharSequence?,
					results : Filter.FilterResults?
				) {
					clear()
					val values = results?.values
					if(values is StringArray) {
						for(s in values) {
							add(s)
						}
					}
					notifyDataSetChanged()
				}
			}
			
			override fun getFilter() : Filter {
				return nameFilter
			}
		}
		adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
		etInstance.setAdapter<ArrayAdapter<String>>(adapter)
		
		
		dialog.window?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.WRAP_CONTENT
		)
		dialog.show()
	}
	
}
