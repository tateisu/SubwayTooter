package jp.juggler.subwaytooter.dialog

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.postDelayed
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.DlgOpenUrlBinding
import jp.juggler.util.LogCategory
import jp.juggler.util.isEnabledAlpha
import jp.juggler.util.showToast
import jp.juggler.util.systemService

object DlgOpenUrl {
    private val log = LogCategory("DlgOpenUrl")

    fun show(
        activity: Activity,
        onEmptyError: () -> Unit = { activity.showToast(false, R.string.url_empty) },
        onOK: (Dialog, String) -> Unit
    ) {

        val allowEmpty = false

        val clipboard: ClipboardManager? = systemService(activity)
        val viewBinding = DlgOpenUrlBinding.inflate(activity.layoutInflater)

        viewBinding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewBinding.btnOk.performClick()
                true
            } else {
                false
            }
        }

        val dialog = Dialog(activity)
        dialog.setContentView(viewBinding.root)
        viewBinding.btnCancel.setOnClickListener { dialog.cancel() }
        viewBinding.btnPaste.setOnClickListener { pasteTo(clipboard, viewBinding.etInput) }
        viewBinding.btnOk.setOnClickListener {
            val token = viewBinding.etInput.text.toString().trim { it <= ' ' }
            if (token.isEmpty() && !allowEmpty) {
                onEmptyError()
            } else {
                onOK(dialog, token)
            }
        }

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            showPasteButton(clipboard, viewBinding)
        }
        clipboard?.addPrimaryClipChangedListener(clipboardListener)
        dialog.setOnDismissListener {
            clipboard?.removePrimaryClipChangedListener(clipboardListener)
        }
        viewBinding.root.postDelayed(100L) {
            showPasteButton(clipboard, viewBinding)
            pasteTo(clipboard, viewBinding.etInput)
        }
        dialog.show()
    }

    private fun showPasteButton(clipboard: ClipboardManager?, viewBinding: DlgOpenUrlBinding) {
        viewBinding.btnPaste.isEnabledAlpha = when {
            clipboard == null -> false
            !clipboard.hasPrimaryClip() -> false
            clipboard.primaryClipDescription?.hasMimeType("text/plain") != true -> false
            else -> true
        }
    }

    private fun pasteTo(clipboard: ClipboardManager?, et: EditText) {
        val text = clipboard?.getUrlFromClipboard()
            ?: return
        val ss = et.selectionStart
        val se = et.selectionEnd
        et.text.replace(ss, se, text)
        et.setSelection(ss, ss + text.length)
    }

    private fun ClipboardManager.getUrlFromClipboard(): String? {
        try {
            val item = primaryClip?.getItemAt(0)
            item?.uri?.toString()?.let { return it }
            item?.text?.toString()?.let { return it }
            log.w("clip has nor uri or text.")
        } catch (ex: Throwable) {
            log.w(ex, "getUrlFromClipboard failed.")
        }
        return null
    }
}
