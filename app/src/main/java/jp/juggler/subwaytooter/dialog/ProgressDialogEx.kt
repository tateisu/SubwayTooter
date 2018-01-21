@file:Suppress("DEPRECATION")

package jp.juggler.subwaytooter.dialog

import android.app.ProgressDialog
import android.content.Context

class ProgressDialogEx(context : Context) : ProgressDialog(context) {
	companion object {
		const val STYLE_SPINNER = ProgressDialog.STYLE_SPINNER
		const val STYLE_HORIZONTAL = ProgressDialog.STYLE_HORIZONTAL
	}
	
	override fun dismiss() {
		try {
			super.dismiss()
		} catch(ignored : Throwable) {
			// java.lang.IllegalArgumentException:
			// at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:396)
			// at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:322)
			// at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:116)
			// at android.app.Dialog.dismissDialog(Dialog.java:341)
			// at android.app.Dialog.dismiss(Dialog.java:324)
			
		}
	}
}
