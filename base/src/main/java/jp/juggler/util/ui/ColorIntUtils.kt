package jp.juggler.util.ui

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import jp.juggler.colorSpace.OkLch
import jp.juggler.colorSpace.OkLch.Companion.mixHue
import jp.juggler.colorSpace.OkLchConverter
import jp.juggler.colorSpace.RgbFloat
import jp.juggler.colorSpace.RgbFloat.Companion.FF_FLOAT
import jp.juggler.colorSpace.RgbFloat.Companion.argbToBits
import jp.juggler.util.data.clip
import jp.juggler.util.log.LogCategory
import kotlin.math.roundToInt

private val log = LogCategory("ColorIntUtils")

private val okLchConverter by lazy { OkLchConverter() }

// ColorInt を 0xAARRGBB のような文字列にする
fun Int.colorIntString() = "0x%x".format(this)

fun Int.argbToOkLch(dst: OkLch): OkLch {
    okLchConverter.rgbToLch(
        dst = dst,
        r = argbToBits(16).toFloat().div(FF_FLOAT),
        g = argbToBits(8).toFloat().div(FF_FLOAT),
        b = argbToBits(0).toFloat().div(FF_FLOAT),
    )
    return dst
}

fun lchToArgb(
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
    // 明るい背景を期待しているなら1f。暗い背景を期待しているなら0f。
    lExpect: Float,
): Int {
    // 色空間の変換
    val lchSrc = OkLch().also { src.argbToOkLch(it) }
    // src色の明るさ
    val lSrc = lchSrc.l
    val lLimit: Float
    when {
        // 明るい背景を期待しているなら下限は黒より明るくなる
        lExpect >= 0.7f -> {
            lLimit = 0.4f
            if (lSrc >= lLimit) return src
        }
        // 暗い背景を期待しているなら上限は白より暗くなる
        else -> {
            lLimit = 0.9f
            if (lSrc <= lLimit) return src
        }
    }
    // src色の明るさを変更する
    val fixed = lchToArgb(
        alpha = src.argbToBits(24),
        l = lLimit,
        c = lchSrc.c,
        h = lchSrc.h,
    )
    log.i("fixColor: src=0x${src.colorIntString()}, fixed=0x${fixed.colorIntString()}")
    return fixed
}

// ColorInt2つの中間の色を作る
// アルファ値は255になる
// - 2025/1 : oklch色空間での中間色を作る
fun mixColor(col1: Int, col2: Int): Int {
    val lch1 = OkLch().also { col1.argbToOkLch(it) }
    val lch2 = OkLch().also { col2.argbToOkLch(it) }
    return lchToArgb(
        alpha = 255,
        l = (lch1.l + lch2.l) / 2f,
        c = (lch1.c + lch2.c) / 2f,
        h = mixHue(lch1, lch2),
    )
}

fun Drawable.wrapAndTint(color: Int): Drawable =
    DrawableCompat.wrap(this).also {
        DrawableCompat.setTint(it, color)
        // DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN);
        it.setBounds(0, 0, it.getIntrinsicWidth(), it.getIntrinsicHeight())
    }
