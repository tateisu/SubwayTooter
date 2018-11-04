package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.support.annotation.IntRange
import android.text.style.ReplacementSpan
import jp.juggler.apng.ApngFrames

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.util.LogCategory
import java.lang.ref.WeakReference

class NetworkEmojiSpan internal constructor(
	private val url : String,
	private val scale : Float = 1f
) : ReplacementSpan(),AnimatableSpan {
	
	companion object {
		
		internal val log = LogCategory("NetworkEmojiSpan")
		
		private const val scale_ratio = 1.14f
		private const val descent_ratio = 0.211f
	}
	
	private val mPaint = Paint()
	private val rect_src = Rect()
	private val rect_dst = RectF()
	

	// フレーム探索結果を格納する構造体を確保しておく
	private val mFrameFindResult = ApngFrames.FindFrameResult()
	
	init {
		mPaint.isFilterBitmap = true
	}
	
	private var invalidate_callback : AnimatableSpanInvalidator? = null
	private var refDrawTarget : WeakReference<Any>? = null
	
	override fun setInvalidateCallback(
		draw_target_tag : Any,
		invalidate_callback : AnimatableSpanInvalidator
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
		val size = (paint.textSize * scale_ratio * scale + 0.5f).toInt()
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
		val srcWidth = b.width
		val srcHeight = b.height
		if(srcWidth < 1 || srcHeight <1){
			log.e("draw: bitmap size is too small.")
			return
		}
		rect_src.set(0, 0, srcWidth, srcHeight )

		// 絵文字の正方形のサイズ
		val dstSize = textPaint.textSize * scale_ratio * scale

		// ベースラインから上下方向にずらすオフセット
		val c_descent = dstSize * descent_ratio
		val transY = baseline - dstSize + c_descent
		
		// 絵文字のアスペクト比から描画範囲の幅と高さを決める
		val dstWidth:Float
		val dstHeight:Float
		val aspectSrc = srcWidth.toFloat() / srcHeight.toFloat()
		if( aspectSrc >= 1f){
			dstWidth = dstSize
			dstHeight = dstSize /aspectSrc
		}else{
			dstHeight = dstSize
			dstWidth = dstSize * aspectSrc
		}
		val dstX = (dstSize-dstWidth)/2f
		val dstY = (dstSize-dstHeight)/2f
		rect_dst.set(dstX,dstY,dstX+dstWidth,dstY+dstHeight)
		
		canvas.save()
		canvas.translate(x, transY)
		canvas.drawBitmap(b, rect_src, rect_dst, mPaint)
		canvas.restore()
		
		// 少し後に描画しなおす
		val delay = mFrameFindResult.delay
		if(delay != Long.MAX_VALUE && ! Pref.bpDisableEmojiAnimation(App1.pref)) {
			invalidate_callback.delayInvalidate(delay)
		}
	}
	
}
