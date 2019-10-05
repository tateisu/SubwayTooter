package jp.juggler.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.CompoundButton
import jp.juggler.subwaytooter.ColumnViewHolder
import jp.juggler.subwaytooter.R
import org.xmlpull.v1.XmlPullParser

private val log = LogCategory("ViewUtils")

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
			log.e("hideKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		log.trace(ex)
	}
}

fun View.showKeyboard() {
	try {
		val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
		if(imm is InputMethodManager) {
			imm.showSoftInput(this, InputMethodManager.HIDE_NOT_ALWAYS)
		} else {
			log.e("showKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		log.trace(ex)
	}
}

// set visibility VISIBLE or GONE
// return true if visible
fun vg(v : View, visible : Boolean) : Boolean {
	v.visibility = if(visible) View.VISIBLE else View.GONE
	return visible
}

fun ViewGroup.generateLayoutParamsEx() : ViewGroup.LayoutParams? =
	try {
		val parser = resources.getLayout(R.layout.generate_params)
		// Skip everything until the view tag.
		while(true) {
			val token = parser.nextToken()
			if(token == XmlPullParser.START_TAG) break
		}
		generateLayoutParams(parser)
	} catch(ex : Throwable) {
		log.e(ex, "generateLayoutParamsEx failed")
		null
	}

// isChecked with skipping animation
var CompoundButton.isCheckedNoAnime : Boolean
	get() = isChecked
	set(value) {
		isChecked = value
		jumpDrawablesToCurrentState()
	}
