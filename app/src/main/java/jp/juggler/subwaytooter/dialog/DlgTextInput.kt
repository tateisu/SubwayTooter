package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

import jp.juggler.subwaytooter.R

object DlgTextInput {

    interface Callback {
        fun onOK(dialog: Dialog, text: String)

        fun onEmptyError()
    }

    @SuppressLint("InflateParams")
    fun show(
        activity: Activity,
        caption: CharSequence,
        initialText: CharSequence?,
        allowEmpty: Boolean = false,
        callback: Callback,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dlg_text_input, null, false)
        val etInput = view.findViewById<EditText>(R.id.etInput)
        val btnOk = view.findViewById<View>(R.id.btnOk)
        val tvCaption = view.findViewById<TextView>(R.id.tvCaption)

        tvCaption.text = caption
        if (initialText != null && initialText.isNotEmpty()) {
            etInput.setText(initialText)
            etInput.setSelection(initialText.length)
        }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnOk.performClick()
                true
            } else {
                false
            }
        }

        val dialog = Dialog(activity)
        dialog.setContentView(view)
        btnOk.setOnClickListener {
            val token = etInput.text.toString().trim { it <= ' ' }

            if (token.isEmpty() && !allowEmpty) {
                callback.onEmptyError()
            } else {
                callback.onOK(dialog, token)
            }
        }

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }

        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}
