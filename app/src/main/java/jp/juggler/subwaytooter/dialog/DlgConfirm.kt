package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.DlgConfirmBinding
import jp.juggler.util.dismissSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object DlgConfirm {

//    interface Callback {
//        var isConfirmEnabled: Boolean
//
//        fun onOK()
//    }

//    @SuppressLint("InflateParams")
//    fun open(activity: Activity, message: String, callback: Callback): Dialog {
//
//        if (!callback.isConfirmEnabled) {
//            callback.onOK()
//            return
//        }
//
//        val view = activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
//        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
//        val cbSkipNext = view.findViewById<CheckBox>(R.id.cbSkipNext)
//        tvMessage.text = message
//
//        AlertDialog.Builder(activity)
//            .setView(view)
//            .setCancelable(true)
//            .setNegativeButton(R.string.cancel, null)
//            .setPositiveButton(R.string.ok) { _, _ ->
//                if (cbSkipNext.isChecked) {
//                    callback.isConfirmEnabled = false
//                }
//                callback.onOK()
//            }
//            .show()
//    }

//    @SuppressLint("InflateParams")
//    fun openSimple(activity: Activity, message: String, callback: () -> Unit) {
//        val view = activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
//        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
//        val cbSkipNext = view.findViewById<CheckBox>(R.id.cbSkipNext)
//        tvMessage.text = message
//        cbSkipNext.visibility = View.GONE
//
//        AlertDialog.Builder(activity)
//            .setView(view)
//            .setCancelable(true)
//            .setNegativeButton(R.string.cancel, null)
//            .setPositiveButton(R.string.ok) { _, _ -> callback() }
//            .show()
//    }

    @SuppressLint("InflateParams")
    suspend fun AppCompatActivity.confirm(
        message: String,
        getConfirmEnabled: Boolean,
        setConfirmEnabled: (newConfirmEnabled: Boolean) -> Unit,
    ) {
        if (!getConfirmEnabled) return
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                val views = DlgConfirmBinding.inflate(layoutInflater)
                views.tvMessage.text = message
                val dialog = AlertDialog.Builder(this)
                    .setView(views.root)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (views.cbSkipNext.isChecked) {
                            setConfirmEnabled(false)
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                dialog.setOnDismissListener {
                    if (cont.isActive) cont.resumeWithException(CancellationException("dialog cancelled."))
                }
                dialog.show()
            } catch (ex: Throwable) {
                cont.resumeWithException(ex)
            }
        }
    }

    suspend fun AppCompatActivity.confirm(@StringRes messageId: Int, vararg args: Any?) =
        confirm(getString(messageId, *args))

    suspend fun AppCompatActivity.confirm(message: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                val views = DlgConfirmBinding.inflate(layoutInflater)
                views.tvMessage.text = message
                views.cbSkipNext.visibility = View.GONE

                val dialog = AlertDialog.Builder(this)
                    .setView(views.root)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (cont.isActive) cont.resume(Unit)
                    }
                    .create()
                dialog.setOnDismissListener {
                    if (cont.isActive) cont.resumeWithException(CancellationException("dialog closed."))
                }
                dialog.show()
                cont.invokeOnCancellation { dialog.dismissSafe() }
            } catch (ex: Throwable) {
                cont.resumeWithException(ex)
            }
        }
    }
}
