package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.util.clipRange

class FocusPointView : View {
	
	constructor(context : Context) : super(context) {
		init(context)
	}
	
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
		init(context)
	}
	
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(
		context,
		attrs,
		defStyleAttr
	) {
		init(context)
	}
	
	private val paint = Paint()
	private val rect = Rect()
	private val rectF = RectF()
	private var strokeWidth : Float = 0f
	private var circleRadius : Float = 0f
	private var crossRadius : Float = 0f
	private var attachment : TootAttachment? = null
	private var bitmap : Bitmap? = null
	var callback : (x : Float, y : Float)->Unit = {_,_->}
	
	private var focusX : Float = 0f
	private var focusY : Float = 0f
	
	private fun init(context : Context) {
		
		paint.isFilterBitmap = true
		paint.isAntiAlias = true
		
		val density = context.resources.displayMetrics.density
		this.strokeWidth = density * 2f
		this.circleRadius = density * 10f
		this.crossRadius = density * 13f
	}
	
	fun setAttachment(
		attachment : TootAttachment,
		bitmap : Bitmap
	) {
		this.attachment = attachment
		this.bitmap = bitmap
		this.focusX = attachment.focusX
		this.focusY = attachment.focusY
		invalidate()
	}
	
	private var scale : Float = 0f
	private var draw_w : Float = 0f
	private var draw_h : Float = 0f
	private var draw_x : Float = 0f
	private var draw_y : Float = 0f
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val bitmap = this.bitmap
		val attachment = this.attachment
		if(bitmap == null || attachment == null || bitmap.isRecycled) return
		
		// draw bitmap
		
		val view_w = this.width.toFloat()
		val view_h = this.height.toFloat()
		if(view_w <= 0f || view_h <= 0f) return
		
		val bitmap_w = bitmap.width.toFloat()
		val bitmap_h = bitmap.height.toFloat()
		if(bitmap_w <= 0f || bitmap_h <= 0f) return
		
		val view_aspect = view_w / view_h
		val bitmap_aspect = bitmap_w / bitmap_h
		
		if(bitmap_aspect >= view_aspect) {
			scale = view_w / bitmap_w
			draw_w = view_w
			draw_h = scale * bitmap_h
		} else {
			scale = view_h / bitmap_h
			draw_w = scale * bitmap_w
			draw_h = view_h
		}
		draw_x = (view_w - draw_w) * 0.5f
		draw_y = (view_h - draw_h) * 0.5f
		
		rect.left = 0
		rect.top = 0
		rect.right = bitmap_w.toInt()
		rect.bottom = bitmap_h.toInt()
		
		rectF.left = draw_x
		rectF.top = draw_y
		rectF.right = draw_x + draw_w
		rectF.bottom = draw_y + draw_h
		
		paint.style = Paint.Style.FILL
		canvas.drawBitmap(bitmap, rect, rectF, paint)
		
		// draw focus point
		
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = strokeWidth
		paint.color = when((SystemClock.elapsedRealtime() / 500L) % 3) {
			2L -> Color.RED
			1L -> Color.BLUE
			else -> Color.GREEN
		}
		
		val point_x = (focusX + 1f) * 0.5f * draw_w + draw_x
		val point_y = (- focusY + 1f) * 0.5f * draw_h + draw_y
		canvas.drawCircle(point_x, point_y, circleRadius, paint)
		canvas.drawLine(point_x, point_y - crossRadius, point_x, point_y + crossRadius, paint)
		canvas.drawLine(point_x - crossRadius, point_y, point_x + crossRadius, point_y, paint)
		
		postInvalidateDelayed(500L)
	}
	
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event : MotionEvent) : Boolean {
		when(event.action) {
			MotionEvent.ACTION_DOWN -> {
				updateFocusPoint(event)
			}
			
			MotionEvent.ACTION_MOVE -> {
				updateFocusPoint(event)
			}
			
			MotionEvent.ACTION_UP -> {
				updateFocusPoint(event)
				callback(focusX, focusY)
			}
		}
		return true
	}
	
	private fun updateFocusPoint(event : MotionEvent) {
		focusX = clipRange(- 1f, 1f, ((event.x - draw_x) / draw_w) * 2f - 1f)
		focusY = - clipRange(- 1f, 1f, ((event.y - draw_y) / draw_h) * 2f - 1f)
		invalidate()
	}
	
}
