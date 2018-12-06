package jp.juggler.subwaytooter.span

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import java.lang.ref.WeakReference

class MisskeyBigSpan (
	private val typeface : Typeface
): MetricAffectingSpan() ,AnimatableSpan{
	
	private var invalidate_callback : AnimatableSpanInvalidator? = null
	private var refDrawTarget : WeakReference<Any>? = null
	
	override fun setInvalidateCallback(
		draw_target_tag : Any,
		invalidate_callback : AnimatableSpanInvalidator
	) {
		this.refDrawTarget = WeakReference(draw_target_tag)
		this.invalidate_callback = invalidate_callback
	}
	
	private val textScalingMax = 1.5f
	
	override fun updateMeasureState(paint : TextPaint) {
		apply(paint,bDrawing=false)
	}
	
	override fun updateDrawState(drawState : TextPaint) {
		apply(drawState,bDrawing=true)
	}
	
	
	private fun apply(paint : Paint, bDrawing:Boolean ) {
		
		
		val textScaling = when{
			!bDrawing -> textScalingMax
			else -> {
				invalidate_callback?.delayInvalidate(100L)
				val t = (invalidate_callback?.timeFromStart ?: 0L) /500f
				
				textScalingMax * ( Math.sin(t.toDouble()).toFloat() * 0.1f + 0.9f)
			}
		}
		
		paint.textSize = paint.textSize * textScaling
		
		val oldTypeface = paint.typeface
		val oldStyle = oldTypeface?.style ?: 0
		val fakeStyle = oldStyle and typeface.style.inv()
		
		if(fakeStyle and Typeface.BOLD != 0) {
			paint.isFakeBoldText = true
		}
		
		if(fakeStyle and Typeface.ITALIC != 0) {
			paint.textSkewX = - 0.25f
		}
		
		paint.typeface = typeface
		
	}
	
	
}
