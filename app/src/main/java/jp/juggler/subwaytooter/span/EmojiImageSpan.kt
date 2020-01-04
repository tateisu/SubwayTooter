package jp.juggler.subwaytooter.span

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class EmojiImageSpan(
	context : Context,
	private val res_id : Int,
	private val useColorShader : Boolean = false,
	private val color : Int? = null,
	private val scale: Float = 1f
) : ReplacementSpan() {
	
	companion object {
		
		// private static final LogCategory warning = new LogCategory( "EmojiImageSpan" );
		
		// static DynamicDrawableSpan x = null;
		
		private const val scale_ratio = 1.14f
		private const val descent_ratio = 0.211f
		
	}
	
	private val context : Context
	private var mDrawableRef : WeakReference<Drawable>? = null
	
	private val cachedDrawable : Drawable?
		get() {
			var d = mDrawableRef?.get()
			if(d == null) {
				d = ContextCompat.getDrawable(context, res_id) ?: return null
				mDrawableRef = WeakReference(d)
			}
			return d
		}
	
	init {
		this.context = context.applicationContext
	}
	
	private fun getImageSize(paint : Paint) = (0.5f + scale_ratio * scale * paint.textSize).toInt()

	override fun getSize(
		paint : Paint,
		text : CharSequence,
		@IntRange(from = 0) start : Int,
		@IntRange(from = 0) end : Int,
		fm : Paint.FontMetricsInt?
	) : Int {

		val size = getImageSize(paint)
		
		if(fm != null) {
			val c_descent = (0.5f + size * descent_ratio).toInt()
			val c_ascent = c_descent - size
			if(fm.ascent > c_ascent) fm.ascent = c_ascent
			if(fm.top > c_ascent) fm.top = c_ascent
			if(fm.descent < c_descent) fm.descent = c_descent
			if(fm.bottom < c_descent) fm.bottom = c_descent
		}
		return size
	}
	
	private var lastColor : Int? = null
	private var lastColorFilter : PorterDuffColorFilter? = null
	
	override fun draw(
		canvas : Canvas,
		text : CharSequence,
		start : Int,
		end : Int,
		x : Float,
		top : Int,
		baseline : Int,
		bottom : Int,
		paint : Paint
	) {
		val d = cachedDrawable ?: return
		
		val size = getImageSize(paint)
		val c_descent = (0.5f + size * descent_ratio).toInt()
		val transY = baseline - size + c_descent
		
		canvas.save()
		canvas.translate(x, transY.toFloat())
		d.setBounds(0, 0, size, size)
		
		if(useColorShader) {
			val pc:Int
				val pa:Int
			if( color ==null) {
				pc = paint.color
				pa = paint.alpha
			}else{
				pc = color or Color.BLACK
				pa = color ushr 24
			}
			// Log.d("EmojiImageSpan",String.format("paint c=0x%x a=0x%x",pc,pa))
			if(pc != lastColor || lastColorFilter == null) {
				lastColor = pc
				lastColorFilter = PorterDuffColorFilter( pc or Color.BLACK , PorterDuff.Mode.SRC_ATOP)
			}
			val saveColorFilter = d.colorFilter
			val saveAlpha = d.alpha
			d.colorFilter = lastColorFilter
			d.alpha = pa
			d.draw(canvas)
			d.colorFilter = saveColorFilter
			d.alpha = saveAlpha
		} else {
			d.draw(canvas)
		}
		canvas.restore()
	}
	
}

