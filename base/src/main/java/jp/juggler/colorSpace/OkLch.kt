package jp.juggler.colorSpace

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OkLch(
    var l: Float = 0f,
    var c: Float = 0f,
    var h: Float = 0f,
) {
    companion object {
        private fun Float.round360(): Float {
            var a = this
            while (a < 0f) a += 360f
            while (a >= 360f) a -= 360f
            return a
        }

        fun mixHue(
            lch1: OkLch,
            lch2: OkLch,
            epsilon: Float = 0.000001f,
        ): Float {
            // 片方が無彩色なら、もう片方の色相をそのまま返す
            if (lch1.c < epsilon) return lch2.h
            if (lch2.c < epsilon) return lch1.h

            val roundH1 = lch1.h.round360()
            val roundH2 = lch2.h.round360()
            val min = min(roundH1, roundH2)
            val max = max(roundH1, roundH2)
            return when {
                max - min > 180f -> (min + 360f + max) / 2f
                else -> (min + max) / 2f
            }.round360()
        }
    }

    override fun toString() =
        "OkLch(%f,%f,%f)".format(l, c, h)

    fun nealyEquals(
        other: OkLch,
        epsilon: Float = 0.000001f,
    ) = abs(l - other.l) < epsilon &&
            abs(c - other.c) < epsilon &&
            abs(h - other.h) < epsilon

    fun set(l: Float, c: Float, h: Float) {
        this.l = l
        this.c = c
        this.h = h
    }

    fun set(other: OkLch) {
        this.l = other.l
        this.c = other.c
        this.h = other.h
    }
}
