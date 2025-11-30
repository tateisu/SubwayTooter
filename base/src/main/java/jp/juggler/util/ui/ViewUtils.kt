package jp.juggler.util.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.children
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ViewUtils")

fun View?.scan(
    callback: (view: View) -> Unit,
) {
    this ?: return
    callback(this)
    if (this is ViewGroup) {
        children.forEach { it.scan(callback) }
    }
}

val View?.activity: Activity?
    get() {
        var context = this?.context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

fun View.hideKeyboard() {
    try {
        when (val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)) {
            is InputMethodManager ->
                imm.hideSoftInputFromWindow(this.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            else -> log.e("hideKeyboard: can't get InputMethodManager")
        }
    } catch (ex: Throwable) {
        log.e(ex, "hideKeyboard failed.")
    }
}

fun View.showKeyboard(
    // InputMethodManager.SHOW_IMPLICIT or InputMethodManager.SHOW_FORCED,
    flag: Int = InputMethodManager.SHOW_IMPLICIT,
) {
    try {
        context.getSystemService<InputMethodManager>()!!
            .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    } catch (ex: Throwable) {
        log.e(ex, "showKeyboard failed. flag=$flag")
    }
}

// set visibility VISIBLE or GONE
// return this or null
// レシーバがnullableなのはplatform typeによるnull例外を避ける目的
fun <T : View> T?.vg(visible: Boolean): T? {
    this?.visibility = if (visible) View.VISIBLE else View.GONE
    return if (visible) this else null
}

// set visibility VISIBLE or INVISIBLE
// return this or null
// レシーバがnullableなのはplatform typeによるnull例外を避ける目的
fun <T : View> T?.visibleOrInvisible(visible: Boolean): T? {
    this?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    return if (visible) this else null
}

fun <T : View> T.visible(): T = apply { visibility = View.VISIBLE }
fun <T : View> T.invisible(): T = apply { visibility = View.INVISIBLE }
fun <T : View> T.gone(): T = apply { visibility = View.GONE }

// isChecked with skipping animation
var CompoundButton.isCheckedNoAnime: Boolean
    get() = isChecked
    set(value) {
        isChecked = value
        jumpDrawablesToCurrentState()
    }

var TextView.textOrGone: CharSequence?
    get() = text
    set(value) {
        vg(value?.isNotEmpty() == true)?.text = value
    }
