package jp.juggler.subwaytooter.span

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import java.lang.ref.WeakReference
import kotlin.math.sin

class MisskeyBigSpan(
    private val typeface: Typeface
) : MetricAffectingSpan(), AnimatableSpan {

    private var invalidateCallback: AnimatableSpanInvalidator? = null
    private var refDrawTarget: WeakReference<Any>? = null

    override fun setInvalidateCallback(
        drawTargetTag: Any,
        invalidateCallback: AnimatableSpanInvalidator
    ) {
        this.refDrawTarget = WeakReference(drawTargetTag)
        this.invalidateCallback = invalidateCallback
    }

    private val textScalingMax = 1.5f

    override fun updateMeasureState(paint: TextPaint) {
        apply(paint, bDrawing = false)
    }

    override fun updateDrawState(drawState: TextPaint) {
        apply(drawState, bDrawing = true)
    }

    private fun apply(paint: Paint, bDrawing: Boolean) {

        val textScaling = when {
            !bDrawing -> textScalingMax
            else -> {
                invalidateCallback?.delayInvalidate(100L)
                val t = (invalidateCallback?.timeFromStart ?: 0L) / 500f

                textScalingMax * (sin(t.toDouble()).toFloat() * 0.1f + 0.9f)
            }
        }

        paint.textSize = paint.textSize * textScaling

        val oldTypeface = paint.typeface
        val oldStyle = oldTypeface?.style ?: 0
        val fakeStyle = oldStyle and typeface.style.inv()

        if (fakeStyle and Typeface.BOLD != 0) {
            paint.isFakeBoldText = true
        }

        if (fakeStyle and Typeface.ITALIC != 0) {
            paint.textSkewX = -0.25f
        }

        paint.typeface = typeface
    }
}
