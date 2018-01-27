package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.support.annotation.IntRange
import android.text.style.ReplacementSpan

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.util.ApngFrames
import jp.juggler.subwaytooter.util.LogCategory
import java.lang.ref.WeakReference

class NetworkEmojiSpan internal constructor(private val url : String) : ReplacementSpan() {
	
	companion object {
		
		internal val log = LogCategory("NetworkEmojiSpan")
		
		private const val scale_ratio = 1.14f
		private const val descent_ratio = 0.211f
	}
	
	private val mPaint = Paint()
	private val rect_src = Rect()
	private val rect_dst = RectF()
	
	private var invalidate_callback : InvalidateCallback? = null
	private var refDrawTarget : WeakReference<Any>? = null
	
	// フレーム探索結果を格納する構造体を確保しておく
	private val mFrameFindResult = ApngFrames.FindFrameResult()
	
	init {
		mPaint.isFilterBitmap = true
	}
	
	interface InvalidateCallback {
		val timeFromStart : Long
		fun delayInvalidate(delay : Long)
	}
	
	fun setInvalidateCallback(
		draw_target_tag : Any,
		invalidate_callback : InvalidateCallback
	) {
		this.refDrawTarget = WeakReference(draw_target_tag)
		this.invalidate_callback = invalidate_callback
	}
	
	override fun getSize(
		paint : Paint,
		text : CharSequence,
		@IntRange(from = 0) start : Int,
		@IntRange(from = 0) end : Int,
		fm : Paint.FontMetricsInt?
	) : Int {
		val size = (0.5f + scale_ratio * paint.textSize).toInt()
		
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
	
	override fun draw(
		canvas : Canvas,
		text : CharSequence,
		start : Int,
		end : Int,
		x : Float,
		top : Int,
		baseline : Int,
		bottom : Int,
		textPaint : Paint
	) {
		val invalidate_callback = this.invalidate_callback
		if(invalidate_callback == null) {
			log.e("draw: invalidate_callback is null.")
			return
		}
		
		// APNGデータの取得
		val frames = App1.custom_emoji_cache.getFrames(refDrawTarget, url) {
			invalidate_callback.delayInvalidate(0)
		} ?: return
		
		val t = if(Pref.bpDisableEmojiAnimation(App1.pref))
			0L
		else
			invalidate_callback.timeFromStart
		
		// アニメーション開始時刻からの経過時間に応じたフレームを探索
		frames.findFrame(mFrameFindResult, t)
		
		val b = mFrameFindResult.bitmap
		if(b == null || b.isRecycled) {
			log.e("draw: bitmap is null or recycled.")
			return
		}
		
		val size = (0.5f + scale_ratio * textPaint.textSize).toInt()
		val c_descent = (0.5f + size * descent_ratio).toInt()
		val transY = baseline - size + c_descent
		
		canvas.save()
		canvas.translate(x, transY.toFloat())
		rect_src.set(0, 0, b.width, b.height)
		rect_dst.set(0f, 0f, size.toFloat(), size.toFloat())
		canvas.drawBitmap(b, rect_src, rect_dst, mPaint)
		canvas.restore()
		
		// 少し後に描画しなおす
		val delay = mFrameFindResult.delay
		if(delay != Long.MAX_VALUE && ! Pref.bpDisableEmojiAnimation(App1.pref)) {
			invalidate_callback.delayInvalidate(delay)
		}
	}
	
}
