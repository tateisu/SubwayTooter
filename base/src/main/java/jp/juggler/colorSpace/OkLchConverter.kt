package jp.juggler.colorSpace

import jp.juggler.util.data.PI_FLOAT
import jp.juggler.util.data.clip
import jp.juggler.util.data.pow2
import jp.juggler.util.data.sqrt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class OkLchConverter(
    private val rgbGammaToLinear: (Float) -> Float = ::sRGBGammaToLinearFloat,
    private val rgbLinearToGamma: (Float) -> Float = ::sRGBLinearToGammaFloat,
) {
    companion object {
        val rgbValidRange = -0.00001f..1.00001f
    }

    fun rgbToLch(
        dst: OkLch,
        src: RgbFloat,
    ) = rgbToLch(
        dst = dst,
        r = src.r,
        g = src.g,
        b = src.b,
    )

    @Suppress("FloatingPointLiteralPrecision")
    fun rgbToLch(
        dst: OkLch,
        r: Float,
        g: Float,
        b: Float,
    ) {
        val rLinear: Float = rgbGammaToLinear(r)
        val gLinear: Float = rgbGammaToLinear(g)
        val bLinear: Float = rgbGammaToLinear(b)

        val lLinear: Float = 0.4122214708f * rLinear + 0.5363325363f * gLinear + 0.0514459929f * bLinear
        val mLinear: Float = 0.2119034982f * rLinear + 0.6806995451f * gLinear + 0.1073969566f * bLinear
        val sLinear: Float = 0.0883024619f * rLinear + 0.2817188376f * gLinear + 0.6299787005f * bLinear

        val lNonLinear: Float = lLinear.pow(1f / 3f)
        val mNonLinear: Float = mLinear.pow(1f / 3f)
        val sNonLinear: Float = sLinear.pow(1f / 3f)

        val labL: Float = 0.2104542553f * lNonLinear + 0.7936177850f * mNonLinear - 0.0040720468f * sNonLinear
        val labA: Float = 1.9779984951f * lNonLinear - 2.4285922050f * mNonLinear + 0.4505937099f * sNonLinear
        val labB: Float = 0.0259040371f * lNonLinear + 0.7827717662f * mNonLinear - 0.8086757660f * sNonLinear

        val c = (labA.pow2() + labB.pow2()).sqrt()
        val hMayNegative = if (c < 0.000001f) 0f else atan2(labB, labA).times(180f).div(PI_FLOAT)
        dst.set(
            l = labL,
            c = c,
            h = if (hMayNegative < 0f) hMayNegative + 360f else hMayNegative,
        )
    }

    fun lchToRgb(
        dst: RgbFloat,
        src: OkLch,
    ) = lchToRgb(
        dst = dst,
        l = src.l,
        c = src.c,
        h = src.h,
    )

    @Suppress("FloatingPointLiteralPrecision")
    fun lchToRgb(
        dst: RgbFloat,
        l: Float,
        c: Float,
        h: Float,
    ) {
        val radian = h.times(PI_FLOAT).div(180f)

        var rLinear = 0f
        var gLinear = 0f
        var bLinear = 0f
        fun calculate(lightness: Float, chroma: Float) {
            val a = chroma * cos(radian)
            val b = chroma * sin(radian)

            // lab to lms(non Linear)
            val lNonLinear: Float = lightness + 0.3963377774f * a + 0.2158037573f * b
            val mNonLinear: Float = lightness - 0.1055613458f * a - 0.0638541728f * b
            val sNonLinear: Float = lightness - 0.0894841775f * a - 1.2914855480f * b

            // lms non linear to linear
            val lLinear = lNonLinear * lNonLinear * lNonLinear
            val mLinear = mNonLinear * mNonLinear * mNonLinear
            val sLinear = sNonLinear * sNonLinear * sNonLinear

            // lms linear to rgb linear
            rLinear = +4.0767416621f * lLinear - 3.3077115913f * mLinear + 0.2309699292f * sLinear
            gLinear = -1.2684380046f * lLinear + 2.6097574011f * mLinear - 0.3413193965f * sLinear
            bLinear = -0.0041960863f * lLinear - 0.7034186147f * mLinear + 1.7076147010f * sLinear
        }

        fun isClipped() = rLinear !in rgbValidRange || gLinear !in rgbValidRange || bLinear !in rgbValidRange

        calculate(l, c)

        // RGBがクリップしたら、LやCを変えてリトライする
        if (isClipped()) {
            var rPrev = Float.NaN
            var gPrev = Float.NaN
            var bPrev = Float.NaN
            fun isValueStabled() = when {
                rPrev.isNaN() -> false
                else -> {
                    val delta = max(
                        abs(rPrev - rLinear),
                        max(
                            abs(gPrev - gLinear),
                            abs(bPrev - bLinear),
                        )
                    )
                    delta < 0.00001f
                }
            }

            val lClipped = l.clip(0f, 1f)
            var cStart = 0f
            var cEnd = max(0f, c)
            var count = 0
            while (cEnd >= cStart) {
                ++count
                val cMid = (cStart + cEnd) * 0.5f
                calculate(lClipped, cMid)
                when {
                    isClipped() -> cEnd = cMid - 0.000001f
                    !isValueStabled() -> cStart = cMid
                    else -> {
                        // println("clipping search count=$count")
                        break
                    }
                }
                // 安全のため試行限界を設ける
                if (count >= 50) {
                    println(
                        "clipping search: abort.count=%d, lch=(%f,%f=>(%f,%f),%f), rgbLinear=(%f,%f,%f)".format(
                            count,
                            lClipped,
                            c,
                            cStart,
                            cEnd,
                            h,
                            rLinear,
                            gLinear,
                            bLinear
                        )
                    )
                    break
                }
                rPrev = rLinear
                gPrev = gLinear
                bPrev = bLinear
            }
        }
        dst.setRgb(
            r = rgbLinearToGamma(rLinear.clip(0f, 1f)),
            g = rgbLinearToGamma(gLinear.clip(0f, 1f)),
            b = rgbLinearToGamma(bLinear.clip(0f, 1f)),
        )
    }
}
