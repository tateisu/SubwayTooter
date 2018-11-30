package jp.juggler.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import jp.juggler.subwaytooter.util.Utils

////////////////////////////////////////////////////////////////////
// View

fun View?.scan(callback : (view : View) -> Unit) {
	this ?: return
	callback(this)
	if(this is ViewGroup) {
		for(i in 0 until this.childCount) {
			this.getChildAt(i)?.scan(callback)
		}
	}
}

val View?.activity : Activity?
	get() {
		var context = this?.context
		while(context is ContextWrapper) {
			if(context is Activity) return context
			context = context.baseContext
		}
		return null
	}

fun View.hideKeyboard() {
	try {
		val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
		if(imm is InputMethodManager) {
			imm.hideSoftInputFromWindow(this.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
		} else {
			Utils.log.e("hideKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		Utils.log.trace(ex)
	}
}

fun View.showKeyboard() {
	try {
		val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
		if(imm is InputMethodManager) {
			imm.showSoftInput(this, InputMethodManager.HIDE_NOT_ALWAYS)
		} else {
			Utils.log.e("showKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		Utils.log.trace(ex)
	}
}

fun vg(v : View, visible : Boolean) {
	v.visibility = if(visible) View.VISIBLE else View.GONE
}
