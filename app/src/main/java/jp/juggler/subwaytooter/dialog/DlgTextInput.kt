package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.graphics.Bitmap
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.DlgTextInputBinding
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.visible
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

suspend fun AppCompatActivity.showTextInputDialog(
    title: CharSequence,
    initialText: CharSequence?,
    allowEmpty: Boolean = false,
    inputType: Int? = null,
    bitmap: Bitmap? = null,
    onEmptyText: suspend () -> Unit,
    // returns true if we can close dialog
    onOk: suspend (String) -> Boolean,
) {
    val views = DlgTextInputBinding.inflate(layoutInflater)
    views.tvCaption.text = title
    initialText?.notEmpty()?.let {
        views.etInput.setText(it)
        views.etInput.setSelection(it.length)
    }
    // views.llInput.maxHeight = (100f * resources.displayMetrics.density + 0.5f).toInt()
    inputType?.let { views.etInput.inputType = it }
    bitmap?.let { views.ivBitmap.visible().setImageBitmap(it) }
    views.etInput.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            views.btnOk.performClick()
            true
        } else {
            false
        }
    }
    val dialog = Dialog(this)
    dialog.setContentView(views.root)
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT
    )
    suspendCancellableCoroutine { cont ->
        views.btnOk.setOnClickListener {
            launchAndShowError {
                val text = views.etInput.text.toString().trim { it <= ' ' }
                if (text.isEmpty() && !allowEmpty) {
                    onEmptyText()
                } else if (onOk(text)) {
                    if (cont.isActive) cont.resume(Unit) {}
                    dialog.dismissSafe()
                }
            }
        }
        views.btnCancel.setOnClickListener { dialog.cancel() }
        dialog.setOnDismissListener {
            if (cont.isActive) cont.resumeWithException(CancellationException())
        }
        cont.invokeOnCancellation { dialog.dismissSafe() }
        dialog.show()
    }
}
