package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.APNGFrames
import java.lang.ref.WeakReference

class NetworkEmojiView : View {
	
	constructor(context : Context) : super(context)
	
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs)
	
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(context, attrs, defStyleAttr)
	
	private var url : String? = null
	
	private val tagDrawTarget : WeakReference<Any>
	
	// フレーム探索結果を格納する構造体を確保しておく
	private val mFrameFindResult = APNGFrames.FindFrameResult()
	
	// 最後に描画した時刻
	private var t_last_draw : Long = 0
	
	// アニメーション開始時刻
	private var t_start : Long = 0
	
	private val mPaint = Paint()
	private val rect_src = Rect()
	private val rect_dst = RectF()
	
	init {
		tagDrawTarget = WeakReference(this)
	}
	
	fun setEmoji(url : String?) {
		this.url = url
		mPaint.isFilterBitmap = true
	}
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val url = this.url
		if(url == null || url.isBlank()) return
		
		// APNGデータの取得
		val frames = App1.custom_emoji_cache.getFrames(tagDrawTarget,url){
			postInvalidateOnAnimation()
		}?: return
		
		val now = SystemClock.elapsedRealtime()
		
		// アニメーション開始時刻を計算する
		if(t_start == 0L || now - t_last_draw >= 60000L) {
			t_start = now
		}
		t_last_draw = now
		
		// アニメーション開始時刻からの経過時間に応じたフレームを探索
		frames.findFrame(mFrameFindResult, now - t_start)
		
		val b = mFrameFindResult.bitmap
		if(b == null || b.isRecycled) return
		
		rect_src.set(0, 0, b.width, b.height)
		rect_dst.set(0f, 0f, this.width.toFloat(), this.height.toFloat())
		canvas.drawBitmap(b, rect_src, rect_dst, mPaint)
		
		// 少し後に描画しなおす
		val delay = mFrameFindResult.delay
		if(delay != Long.MAX_VALUE) {
			postInvalidateDelayed(delay)
		}
	}
	
}
