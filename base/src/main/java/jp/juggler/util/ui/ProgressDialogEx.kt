@file:Suppress("DEPRECATION")

package jp.juggler.util.ui

import android.app.ProgressDialog
import android.content.Context
import jp.juggler.util.coroutine.runOnMainLooper

class ProgressDialogEx(context: Context) : ProgressDialog(context) {

    companion object {
        const val STYLE_SPINNER = ProgressDialog.STYLE_SPINNER
        const val STYLE_HORIZONTAL = ProgressDialog.STYLE_HORIZONTAL
    }

    var isIndeterminateEx: Boolean
        get() = isIndeterminate
        set(value) {
            isIndeterminate = value
        }

    fun setMessageEx(msg: CharSequence?) = runOnMainLooper { super.setMessage(msg) }
    // synchronizedの中から呼ばれることがあるので、コルーチンではなくHandlerで制御する
}
