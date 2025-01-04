package jp.juggler.colorSpace

import kotlin.math.pow

// sRGBの各値をガンマ付きからリニア光量に変換する
fun sRGBGammaToLinearFloat(v: Float): Float = when {
    v <= 0.040450f -> v / 12.92f
    else -> v.plus(0.055f).div(1.055f).pow(2.4f)
}

// sRGBの各値をリニア光量からガンマ付きに変換する
fun sRGBLinearToGammaFloat(v: Float): Float = when {
    v <= 0.0031308f -> v * 12.92f
    else -> v.pow(1f / 2.4f).times(1.055f).minus(0.055f)
}

// adobe RGB の各値をガンマ付きからリニア光量に変換する
fun adobeRGBGammaToLinearFloat(v: Float): Float = when {
    v <= 0.0556f -> v / 32f
    else -> v.pow(2.2f)
}

// adobe RGB の各値をリニア光量からガンマ付きに変換する
fun adobeRGBLinearToGamma(v: Float): Float = when {
    v <= 0.00174f -> v * 32.0f
    else -> v.pow(1f / 2.2f)
}
