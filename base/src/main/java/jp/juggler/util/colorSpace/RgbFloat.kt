package jp.juggler.util.colorSpace

import jp.juggler.util.data.clip
import kotlin.math.abs
import kotlin.math.roundToInt

data class RgbFloat(
    var r: Float = 0f,
    var g: Float = 0f,
    var b: Float = 0f,
) {
    companion object {
        const val FF_INT = 0xff
        const val FF_FLOAT = FF_INT.toFloat()
        private const val ALPHA_MASK = FF_INT.shl(24)

        // ARGB Int値からビット範囲を読む
        fun Int.argbToBits(shift: Int, mask: Int = FF_INT) = this.shr(shift).and(mask)
    }

    override fun toString() =
        "RgbFloat(%f,%f,%f)".format(r, g, b)

    fun nealyEquals(
        other: RgbFloat,
        epsilon: Float = 0.000001f,
    ) = abs(r - other.r) < epsilon &&
            abs(g - other.g) < epsilon &&
            abs(b - other.b) < epsilon

    fun setRgb(r: Float, g: Float, b: Float) {
        this.r = r
        this.g = g
        this.b = b
    }

    fun setRgb(r: Double, g: Double, b: Double) {
        this.r = r.toFloat()
        this.g = g.toFloat()
        this.b = b.toFloat()
    }

    fun setRgb(src: RgbFloat) {
        this.r = src.r
        this.g = src.g
        this.b = src.b
    }

    fun setArgb(argb: Int) {
        this.r = argb.argbToBits(16).toFloat().div(FF_FLOAT)
        this.g = argb.argbToBits(8).toFloat().div(FF_FLOAT)
        this.b = argb.argbToBits(0).toFloat().div(FF_FLOAT)
    }

    fun toArgb(): Int {
        val rInt = r.times(FF_FLOAT).roundToInt().clip(0, FF_INT)
        val gInt = g.times(FF_FLOAT).roundToInt().clip(0, FF_INT)
        val bInt = b.times(FF_FLOAT).roundToInt().clip(0, FF_INT)
        return bInt
            .or(gInt.shl(8))
            .or(rInt.shl(16))
            .or(ALPHA_MASK)
    }
}
