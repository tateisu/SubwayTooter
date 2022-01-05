package jp.juggler.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefI
import org.xmlpull.v1.XmlPullParser
import kotlin.math.pow

private val log = LogCategory("ViewUtils")

fun View?.scan(callback: (view: View) -> Unit) {
    this ?: return
    callback(this)
    if (this is ViewGroup) {
        for (i in 0 until this.childCount) {
            this.getChildAt(i)?.scan(callback)
        }
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
        val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
        if (imm is InputMethodManager) {
            imm.hideSoftInputFromWindow(this.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        } else {
            log.e("hideKeyboard: can't get InputMethodManager")
        }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
}

fun View.showKeyboard() {
    try {
        val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
        if (imm is InputMethodManager) {
            imm.showSoftInput(this, InputMethodManager.HIDE_NOT_ALWAYS)
        } else {
            log.e("showKeyboard: can't get InputMethodManager")
        }
    } catch (ex: Throwable) {
        log.trace(ex)
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

fun ViewGroup.generateLayoutParamsEx(): ViewGroup.LayoutParams? =
    try {
        val parser = resources.getLayout(R.layout.generate_params)
        // Skip everything until the view tag.
        while (true) {
            val token = parser.nextToken()
            if (token == XmlPullParser.START_TAG) break
        }
        generateLayoutParams(parser)
    } catch (ex: Throwable) {
        log.e(ex, "generateLayoutParamsEx failed")
        null
    }

// isChecked with skipping animation
var CompoundButton.isCheckedNoAnime: Boolean
    get() = isChecked
    set(value) {
        isChecked = value
        jumpDrawablesToCurrentState()
    }

private fun mixColor(col1: Int, col2: Int): Int = Color.rgb(
    (Color.red(col1) + Color.red(col2)) ushr 1,
    (Color.green(col1) + Color.green(col2)) ushr 1,
    (Color.blue(col1) + Color.blue(col2)) ushr 1
)

fun Context.setSwitchColor(root: View?) {
    val colorBg = attrColor(R.attr.colorWindowBackground)
    val colorOn = PrefI.ipSwitchOnColor()
    val colorOff = /* PrefI.ipSwitchOffColor().notZero() ?: */
        attrColor(android.R.attr.colorPrimary)

    val colorDisabled = mixColor(colorBg, colorOff)

    val colorTrackDisabled = mixColor(colorBg, colorDisabled)
    val colorTrackOn = mixColor(colorBg, colorOn)
    val colorTrackOff = mixColor(colorBg, colorOff)

    // https://stackoverflow.com/a/25635526/9134243
    val thumbStates = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        ),
        intArrayOf(
            colorDisabled,
            colorOn,
            colorOff
        )
    )

    val trackStates = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        ),
        intArrayOf(
            colorTrackDisabled,
            colorTrackOn,
            colorTrackOff
        )
    )

    root?.scan {
        (it as? SwitchCompat)?.apply {
            thumbTintList = thumbStates
            trackTintList = trackStates
        }
    }
}

private fun rgbToLab(rgb: Int): Triple<Float, Float, Float> {

    fun Int.revGamma(): Float {
        val v = toFloat() / 255f
        return when {
            v > 0.04045f -> ((v + 0.055f) / 1.055f).pow(2.4f)
            else -> v / 12.92f
        }
    }

    val r = Color.red(rgb).revGamma()
    val g = Color.green(rgb).revGamma()
    val b = Color.blue(rgb).revGamma()

    //https://en.wikipedia.org/wiki/Lab_color_space#CIELAB-CIEXYZ_conversions

    fun f(src: Float, k: Float): Float {
        val v = src * k
        return when {
            v > 0.008856f -> v.pow(1f / 3f)
            else -> (7.787f * v) + (4f / 29f)
        }
    }

    val x = f(r * 0.4124f + g * 0.3576f + b * 0.1805f, 100f / 95.047f)
    val y = f(r * 0.2126f + g * 0.7152f + b * 0.0722f, 100f / 100f)
    val z = f(r * 0.0193f + g * 0.1192f + b * 0.9505f, 100f / 108.883f)

    return Triple(
        (116 * y) - 16, // L
        500 * (x - y), // a
        200 * (y - z) //b
    )
}

fun AppCompatActivity.setStatusBarColor(forceDark: Boolean = false) {
    window?.apply {
        // 古い端末ではナビゲーションバーのアイコン色を設定できないため
        // メディアビューア画面ではステータスバーやナビゲーションバーの色を設定しない…
        if (forceDark && Build.VERSION.SDK_INT < 26) return

        if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
        }

        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        var c = when {
            forceDark -> Color.BLACK
            else -> PrefI.ipStatusBarColor().notZero() ?: attrColor(R.attr.colorPrimaryDark)
        }
        setStatusBarColorCompat(c)

        c = when {
            forceDark -> Color.BLACK
            else -> PrefI.ipNavigationBarColor()
        }
        setNavigationBarColorCompat(c)
    }
}

private fun AppCompatActivity.setStatusBarColorCompat(@ColorInt c: Int) {
    window?.apply {
        statusBarColor = Color.BLACK or c

        if (Build.VERSION.SDK_INT >= 30) {
            decorView.windowInsetsController?.run {
                val bit = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                setSystemBarsAppearance(if (rgbToLab(c).first >= 50f) bit else 0, bit)
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            val bit = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                if (rgbToLab(c).first >= 50f) {
                    //Dark Text to show up on your light status bar
                    decorView.systemUiVisibility or bit
                } else {
                    //Light Text to show up on your dark status bar
                    decorView.systemUiVisibility and bit.inv()
                }
        }
    }
}

private fun AppCompatActivity.setNavigationBarColorCompat(@ColorInt c: Int) {
    if (c == 0) {
        // no way to restore to system default, need restart app.
        return
    }

    window?.apply {
        navigationBarColor = c or Color.BLACK

        if (Build.VERSION.SDK_INT >= 30) {
            decorView.windowInsetsController?.run {
                val bit = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                setSystemBarsAppearance(if (rgbToLab(c).first >= 50f) bit else 0, bit)
            }
        } else if (Build.VERSION.SDK_INT >= 26) {
            @Suppress("DEPRECATION")
            val bit = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = when {
                rgbToLab(c).first >= 50f -> {
                    //Dark Text to show up on your light status bar
                    decorView.systemUiVisibility or bit
                }
                else -> {
                    //Light Text to show up on your dark status bar
                    decorView.systemUiVisibility and bit.inv()
                }
            }
        }
    }
}

var TextView.textOrGone: CharSequence?
    get() = text
    set(value) {
        vg(value?.isNotEmpty() == true)?.text = value
    }
