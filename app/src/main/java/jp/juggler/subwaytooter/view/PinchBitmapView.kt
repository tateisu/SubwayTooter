package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import jp.juggler.util.LogCategory
import jp.juggler.util.runOnMainLooper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class PinchBitmapView(context : Context, attrs : AttributeSet?, defStyle : Int) :
	View(context, attrs, defStyle) {
	
	companion object {
		
		internal val log = LogCategory("PinchImageView")
		
		// 数値を範囲内にクリップする
		private fun clip(min : Float, max : Float, v : Float) : Float {
			return if(v < min) min else if(v > max) max else v
		}
		
		// ビューの幅と画像の描画サイズを元に描画位置をクリップする
		private fun clipTranslate(
			view_w : Float // ビューの幅
			, bitmap_w : Float // 画像の幅
			, current_scale : Float // 画像の拡大率
			, trans_x : Float // タッチ操作による表示位置
		) : Float {
			
			// 余白(拡大率が小さい場合はプラス、拡大率が大きい場合はマイナス)
			val padding = view_w - bitmap_w * current_scale
			
			// 余白が>=0なら画像を中心に表示する。 <0なら操作された位置をクリップする。
			return if(padding >= 0f) padding / 2f else clip(padding, 0f, trans_x)
		}
	}
	
	private var callback : Callback? = null
	
	private var bitmap : Bitmap? = null
	private var bitmap_w : Float = 0.toFloat()
	private var bitmap_h : Float = 0.toFloat()
	private var bitmap_aspect : Float = 0.toFloat()
	
	// 画像を表示する位置と拡大率
	private var current_trans_x : Float = 0.toFloat()
	private var current_trans_y : Float = 0.toFloat()
	private var current_scale : Float = 0.toFloat()
	
	// 画像表示に使う構造体
	private val drawMatrix = Matrix()
	internal val paint = Paint()
	
	// タッチ操作中に指を動かした
	private var bDrag : Boolean = false
	
	// タッチ操作中に指の数を変えた
	private var bPointerCountChanged : Boolean = false
	
	// ページめくりに必要なスワイプ強度
	private var swipe_velocity = 0f
	private var swipe_velocity2 = 0f
	
	// 指を動かしたと判断する距離
	private var drag_length = 0f
	
	private var time_touch_start = 0L
	
	// フリック操作の検出に使う
	private var velocityTracker : VelocityTracker? = null
	
	private var click_time = 0L
	private var click_count = 0
	
	// 移動後の指の位置
	internal val pos = PointerAvg()
	
	// 移動開始時の指の位置
	private val start_pos = PointerAvg()
	
	// 移動開始時の画像の位置
	private var start_image_trans_x : Float = 0.toFloat()
	private var start_image_trans_y : Float = 0.toFloat()
	private var start_image_scale : Float = 0.toFloat()
	
	private var scale_min : Float = 0.toFloat()
	private var scale_max : Float = 0.toFloat()
	
	private var view_w : Float = 0.toFloat()
	private var view_h : Float = 0.toFloat()
	private var view_aspect : Float = 0.toFloat()
	
	private val tracking_matrix = Matrix()
	private val tracking_matrix_inv = Matrix()
	private val avg_on_image1 = FloatArray(2)
	private val avg_on_image2 = FloatArray(2)
	
	constructor(context : Context) : this(context, null) {
		init(context)
	}
	
	constructor(context : Context, attrs : AttributeSet?) : this(context, attrs, 0) {
		init(context)
	}
	
	init {
		init(context)
	}
	
	internal fun init(context : Context) {
		
		// 定数をdpからpxに変換
		val density = context.resources.displayMetrics.density
		swipe_velocity = 1000f * density
		swipe_velocity2 = 250f * density
		drag_length = 4f * density // 誤反応しがちなのでやや厳しめ
	}
	
	// ページめくり操作のコールバック
	interface Callback {
		
		fun onSwipe(deltaX : Int, deltaY : Int)
		
		fun onMove(bitmap_w : Float, bitmap_h : Float, tx : Float, ty : Float, scale : Float)
	}
	
	fun setCallback(callback : Callback?) {
		this.callback = callback
	}
	
	fun setBitmap(b : Bitmap?) {
		
		bitmap?.recycle()
		
		this.bitmap = b
		
		initializeScale()
	}
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val bitmap = this.bitmap
		if(bitmap != null && ! bitmap.isRecycled) {
			
			drawMatrix.reset()
			drawMatrix.postScale(current_scale, current_scale)
			drawMatrix.postTranslate(current_trans_x, current_trans_y)
			
			paint.isFilterBitmap = current_scale < 4f
			canvas.drawBitmap(bitmap, drawMatrix, paint)
		}
	}
	
	override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		
		view_w = Math.max(1f, w.toFloat())
		view_h = Math.max(1f, h.toFloat())
		view_aspect = view_w / view_h
		
		initializeScale()
	}
	
	override fun performClick() : Boolean {
		super.performClick()
		
		initializeScale()
		
		return true
	}
	
	private var defaultScale : Float = 1f
	
	// 表示位置の初期化
	// 呼ばれるのは、ビットマップを変更した時、ビューのサイズが変わった時、画像をクリックした時
	private fun initializeScale() {
		val bitmap = this.bitmap
		if(bitmap != null && ! bitmap.isRecycled && view_w >= 1f) {
			
			bitmap_w = Math.max(1f, bitmap.width.toFloat())
			bitmap_h = Math.max(1f, bitmap.height.toFloat())
			bitmap_aspect = bitmap_w / bitmap_h
			
			if(view_aspect > bitmap_aspect) {
				scale_min = view_h / bitmap_h / 2f
				scale_max = view_w / bitmap_w * 8f
			} else {
				scale_min = view_w / bitmap_w / 2f
				scale_max = view_h / bitmap_h * 8f
			}
			if(scale_max < scale_min) scale_max = scale_min * 16f
			
			defaultScale = if(view_aspect > bitmap_aspect) {
				view_h / bitmap_h
			} else {
				view_w / bitmap_w
			}
			
			val draw_w = bitmap_w * defaultScale
			val draw_h = bitmap_h * defaultScale
			
			current_scale = defaultScale
			current_trans_x = (view_w - draw_w) / 2f
			current_trans_y = (view_h - draw_h) / 2f
			
			callback?.onMove(bitmap_w, bitmap_h, current_trans_x, current_trans_y, current_scale)
		} else {
			defaultScale = 1f
			scale_min = 1f
			scale_max = 1f
			
			current_scale = defaultScale
			current_trans_y = 0f
			current_trans_x = 0f
			
			callback?.onMove(0f, 0f, current_trans_x, current_trans_y, current_scale)
		}
		
		// 画像がnullに変化した時も再描画が必要
		invalidate()
	}
	
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev : MotionEvent) : Boolean {
		
		val bitmap = this.bitmap
		if(bitmap == null
			|| bitmap.isRecycled
			|| view_w < 1f)
			return false
		
		val action = ev.action
		
		if(action == MotionEvent.ACTION_DOWN) {
			time_touch_start = SystemClock.elapsedRealtime()
			
			velocityTracker?.clear()
			velocityTracker = VelocityTracker.obtain()
			velocityTracker?.addMovement(ev)
			
			bPointerCountChanged = false
			bDrag = bPointerCountChanged
			trackStart(ev)
			return true
		}
		
		velocityTracker?.addMovement(ev)
		
		when(action) {
			MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
				// タッチ操作中に指の数を変えた
				bPointerCountChanged = true
				bDrag = bPointerCountChanged
				trackStart(ev)
			}
			
			MotionEvent.ACTION_MOVE -> trackNext(ev)
			
			MotionEvent.ACTION_UP -> {
				trackNext(ev)
				
				checkClickOrPaging()
				
				velocityTracker?.recycle()
				velocityTracker = null
			}
		}
		return true
	}
	
	private fun checkClickOrPaging() {
		
		if(! bDrag) {
			// 指を動かしていないなら
			
			val now = SystemClock.elapsedRealtime()
			
			if(now - time_touch_start >= 1000L) {
				// ロングタップはタップカウントをリセットする
				log.d("click count reset by long tap")
				click_count = 0
				return
			}
			
			val delta = now - click_time
			click_time = now
			
			if(delta > 334L) {
				// 前回のタップからの時刻が長いとタップカウントをリセットする
				log.d("click count reset by long interval")
				click_count = 0
			}
			
			++ click_count
			
			log.d("click %d %d", click_count, delta)
			
			if(click_count >= 2) {
				// ダブルタップでクリック操作
				click_count = 0
				performClick()
			}
			
			return
		}
		
		click_count = 0
		
		val velocityTracker = this.velocityTracker
		if(! bPointerCountChanged && velocityTracker != null) {
			
			// 指の数を変えていないならページめくり操作かもしれない
			
			// 「画像を動かした」かどうかのチェック
			val image_moved = max(
				abs(current_trans_x - start_image_trans_x),
				abs(current_trans_y - start_image_trans_y)
			)
			if(image_moved >= drag_length) {
				log.d("image moved. not flick action. $image_moved")
				return
			}
			
			velocityTracker.computeCurrentVelocity(1000)
			val vx = velocityTracker.xVelocity
			val vy = velocityTracker.yVelocity
			val avx = abs(vx)
			val avy = abs(vy)
			val velocity = sqrt(vx * vx + vy * vy)
			val aspect = try {
				avx / avy
			} catch(ex : Throwable) {
				Float.MAX_VALUE
			}
			
			when {
				aspect >= 0.9f -> {
					// 指を動かした方向が左右だった
					
					val vMin = when {
						current_scale * bitmap_w <= view_w -> swipe_velocity2
						else -> swipe_velocity
					}
					
					if(velocity < vMin) {
						log.d("velocity $velocity not enough to pagingX")
						return
					}
					
					log.d("pagingX! m=$image_moved a=$aspect v=$velocity")
					runOnMainLooper { callback?.onSwipe(if(vx >= 0f) - 1 else 1, 0) }
				}
				
				aspect <= 0.333f -> {
					// 指を動かした方向が上下だった
					
					val vMin = when {
						current_scale * bitmap_h <= view_h -> swipe_velocity2
						else -> swipe_velocity
					}
					
					if(velocity < vMin) {
						log.d("velocity $velocity not enough to pagingY")
						return
					}
					
					log.d("pagingY! m=$image_moved a=$aspect v=$velocity")
					runOnMainLooper { callback?.onSwipe(0, if(vy >= 0f) - 1 else 1) }
				}
				
				else -> log.d("flick is not horizontal/vertical. aspect=$aspect")
			}
		}
	}
	
	// マルチタッチの中心位置の計算
	internal class PointerAvg {
		
		// タッチ位置の数
		var count : Int = 0
		
		// タッチ位置の平均
		val avg = FloatArray(2)
		
		// 中心と、中心から最も離れたタッチ位置の間の距離
		var max_radius : Float = 0.toFloat()
		
		fun update(ev : MotionEvent) {
			
			count = ev.pointerCount
			if(count <= 1) {
				avg[0] = ev.x
				avg[1] = ev.y
				max_radius = 0f
				
			} else {
				avg[0] = 0f
				avg[1] = 0f
				for(i in 0 until count) {
					avg[0] += ev.getX(i)
					avg[1] += ev.getY(i)
				}
				avg[0] /= count.toFloat()
				avg[1] /= count.toFloat()
				max_radius = 0f
				for(i in 0 until count) {
					val dx = ev.getX(i) - avg[0]
					val dy = ev.getY(i) - avg[1]
					val radius = dx * dx + dy * dy
					if(radius > max_radius) max_radius = radius
				}
				max_radius = Math.sqrt(max_radius.toDouble()).toFloat()
				if(max_radius < 1f) max_radius = 1f
			}
		}
	}
	
	private fun trackStart(ev : MotionEvent) {
		
		// 追跡開始時の指の位置
		start_pos.update(ev)
		
		// 追跡開始時の画像の位置
		start_image_trans_x = current_trans_x
		start_image_trans_y = current_trans_y
		start_image_scale = current_scale
		
	}
	
	// 画面上の指の位置から画像中の指の位置を調べる
	private fun getCoordinateOnImage(dst : FloatArray, src : FloatArray) {
		tracking_matrix.reset()
		tracking_matrix.postScale(current_scale, current_scale)
		tracking_matrix.postTranslate(current_trans_x, current_trans_y)
		tracking_matrix.invert(tracking_matrix_inv)
		tracking_matrix_inv.mapPoints(dst, src)
	}
	
	private fun trackNext(ev : MotionEvent) {
		pos.update(ev)
		
		if(pos.count != start_pos.count) {
			// タッチ操作中に指の数が変わった
			log.d("nextTracking: pointer count changed")
			bPointerCountChanged = true
			bDrag = bPointerCountChanged
			trackStart(ev)
			return
		}
		
		// ズーム操作
		if(pos.count > 1) {
			
			// タッチ位置にある絵柄の座標を調べる
			getCoordinateOnImage(avg_on_image1, pos.avg)
			
			// ズーム率を変更する
			current_scale = clip(
				scale_min,
				scale_max,
				start_image_scale * pos.max_radius / start_pos.max_radius
			)
			
			// 再び調べる
			getCoordinateOnImage(avg_on_image2, pos.avg)
			
			// ズーム変更の前後で位置がズレた分だけ移動させると、タッチ位置にある絵柄がズレない
			start_image_trans_x += current_scale * (avg_on_image2[0] - avg_on_image1[0])
			start_image_trans_y += current_scale * (avg_on_image2[1] - avg_on_image1[1])
			
		}
		
		// 平行移動
		run {
			// start時から指を動かした量
			val move_x = pos.avg[0] - start_pos.avg[0]
			val move_y = pos.avg[1] - start_pos.avg[1]
			
			// 「指を動かした」と判断したらフラグを立てる
			if(Math.abs(move_x) >= drag_length || Math.abs(move_y) >= drag_length) {
				bDrag = true
			}
			
			// 画像の表示位置を更新
			current_trans_x =
				clipTranslate(view_w, bitmap_w, current_scale, start_image_trans_x + move_x)
			current_trans_y =
				clipTranslate(view_h, bitmap_h, current_scale, start_image_trans_y + move_y)
		}
		
		callback?.onMove(bitmap_w, bitmap_h, current_trans_x, current_trans_y, current_scale)
		invalidate()
	}
	
}
