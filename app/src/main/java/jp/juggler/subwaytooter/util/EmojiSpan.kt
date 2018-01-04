// emojiOne フォントを使っていた時代の遺物
//package jp.juggler.subwaytooter.util
//
//import android.graphics.Paint
//import android.graphics.Typeface
//import android.text.TextPaint
//import android.text.style.MetricAffectingSpan
//
//class EmojiSpan(private val typeface : Typeface) : MetricAffectingSpan() {
//
//	override fun updateDrawState(drawState : TextPaint) {
//		apply(drawState)
//	}
//
//	override fun updateMeasureState(paint : TextPaint) {
//		apply(paint)
//	}
//
//	private fun apply(paint : Paint) {
//		val oldTypeface = paint.typeface
//		val oldStyle = oldTypeface?.style ?: 0
//		val fakeStyle = oldStyle and typeface.getStyle().inv()
//
//		if(fakeStyle and Typeface.BOLD != 0) {
//			paint.isFakeBoldText = true
//		}
//
//		if(fakeStyle and Typeface.ITALIC != 0) {
//			paint.textSkewX = - 0.25f
//		}
//
//		paint.typeface = typeface
//	}
//}
