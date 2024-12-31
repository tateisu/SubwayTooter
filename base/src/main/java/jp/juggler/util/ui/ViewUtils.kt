package jp.juggler.util.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.getSystemService
import jp.juggler.util.colorSpace.OkLch
import jp.juggler.util.colorSpace.OkLchConverter
import jp.juggler.util.colorSpace.RgbFloat
import jp.juggler.util.colorSpace.RgbFloat.Companion.FF_FLOAT
import jp.juggler.util.colorSpace.RgbFloat.Companion.argbToBits
import jp.juggler.util.data.clip
import jp.juggler.util.log.LogCategory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

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

fun mixColor(col1: Int, col2: Int): Int = Color.rgb(
    (Color.red(col1) + Color.red(col2)) ushr 1,
    (Color.green(col1) + Color.green(col2)) ushr 1,
    (Color.blue(col1) + Color.blue(col2)) ushr 1
)

data class Lab(
    val l: Float,
    val a: Float,
    val b: Float,
)

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

private val okLchConverter by lazy { OkLchConverter() }

private fun Int.argbToOkLch(dst: OkLch): OkLch {
    okLchConverter.rgbToLch(
        dst = dst,
        r = argbToBits(16).toFloat().div(FF_FLOAT),
        g = argbToBits(8).toFloat().div(FF_FLOAT),
        b = argbToBits(0).toFloat().div(FF_FLOAT),
    )
    return dst
}

private fun lchToArgb(
    alpha: Int,
    l: Float,
    c: Float,
    h: Float,
): Int {
    val dst = RgbFloat()
    okLchConverter.lchToRgb(
        dst = dst,
        l = l,
        c = c,
        h = h,
    )
    return alpha.clip(0, 255).shl(24)
        .or((dst.r * 255f).clip(0f, 255f).roundToInt().shl(16))
        .or((dst.g * 255f).clip(0f, 255f).roundToInt().shl(8))
        .or((dst.b * 255f).clip(0f, 255f).roundToInt())
}

/**
 * sampleとsrcの明るさの差が sampleとthresholdより大きいなら、
 * sampleとsrcの中間の色を返す
 * alpha はsrcのまま
 */
@ColorInt
fun fixColor(
    @ColorInt src: Int,
    thresholdLightness: Float, // 0..1f
    expectLightness: Float, // 0..1f
): Int {
    val lchSrc = OkLch()
    val lSrc = src.argbToOkLch(lchSrc).l
    val expectToSrc = abs(lSrc - expectLightness)
    val expectToThreshold = abs(thresholdLightness - expectLightness)
    return when {
        expectToSrc <= expectToThreshold -> src
        else -> lchToArgb(
            alpha = src.argbToBits(24),
            l = (lchSrc.l + expectLightness) / 2f,
            c = lchSrc.c,
            h = lchSrc.h,
        )
    }
}

fun Activity.setStatusBarColorCompat(@ColorInt c: Int) {
    window?.apply {
        statusBarColor = Color.BLACK or c

        if (Build.VERSION.SDK_INT >= 30) {
            decorView.windowInsetsController?.run {
                val bit = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                setSystemBarsAppearance(if (rgbToLab(c).first >= 50f) bit else 0, bit)
            }
        } else {
            @Suppress("DEPRECATION")
            val bit = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                when {
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

fun Activity.setNavigationBarColorCompat(@ColorInt c: Int) {
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
        } else {
            @Suppress("DEPRECATION")
            val bit = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = when {
                //Dark Text to show up on your light status bar
                rgbToLab(c).first >= 50f ->
                    decorView.systemUiVisibility or bit
                //Light Text to show up on your dark status bar
                else ->
                    decorView.systemUiVisibility and bit.inv()
            }
        }
    }
}

var TextView.textOrGone: CharSequence?
    get() = text
    set(value) {
        vg(value?.isNotEmpty() == true)?.text = value
    }
