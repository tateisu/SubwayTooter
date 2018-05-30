package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import jp.juggler.subwaytooter.api.entity.TootTrendTag
import jp.juggler.subwaytooter.util.clipRange

class TrendTagHistoryView : View {
	
	private val paint = Paint()
	private var values : List<Float>? = null
	private var delta : Float = 0f
	private val path = Path()
	private var lineWidth = 1f
	private var y_workarea : Array<Float>? = null
	
	constructor(context : Context) : super(context) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(
		context,
		attrs,
		defStyleAttr
	) {
		init()
	}
	
	private fun init() {
		val density = context.resources.displayMetrics.density
		this.lineWidth = 2f * density
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = lineWidth
	}
	
	fun setColor(c : Int) {
		paint.color = c
		invalidate()
	}
	
	fun setHistory(history : ArrayList<TootTrendTag.History>?) {
		if(history?.isEmpty() != false) {
			delta = 0f
			values = null
		} else {
			var min = Long.MAX_VALUE
			var max = Long.MIN_VALUE
			for(h in history) {
				min = Math.min(min, h.uses)
				max = Math.max(max, h.uses)
			}
			val delta = (max - min).toFloat()
			this.delta = delta
			if(delta == 0f) {
				values = null
			} else {
				values = history.map { (it.uses - min).toFloat() / delta }.reversed()
				y_workarea = Array(history.size) { 0f }
			}
		}
		invalidate()
	}
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val values = this.values ?: return
		
		val view_w = width.toFloat()
		val view_h = height.toFloat()
		if(view_w < 1f || view_h < 1f) return
		
		if(delta == 0f) {
			val y = height / 2f
			canvas.drawLine(0f, y, view_w, y, paint)
			return
		}
		
		val size = values.size
		val x_step = view_w / (size - 1).toFloat()
		var x = 0f
		path.reset()
		var lastSlope = 0f
		var lastY = 0f
		var lastX = 0f
		val controlXStep = x_step / 2f
		val y_workarea = this.y_workarea ?: return
		val y_min = lineWidth * 2f
		val y_max = view_h - lineWidth * 2f
		val y_width = y_max - y_min
		for(i in 0 until size) {
			y_workarea[i] = (1f - values[i]) * y_width + y_min
		}
		for(i in 0 until size) {
			val y = y_workarea[i]
			when(i) {
				0 -> {
					path.moveTo(x, y)
					lastSlope = (y_workarea[i + 1] - y) / x_step
				}
				
				size - 1 -> {
					// 制御点1
					val c1x = lastX + controlXStep
					val c1y = clipRange(y_min, y_max, lastY + controlXStep * lastSlope)
					// 制御点2
					val slope = (y - lastY) / x_step
					val c2x = x - controlXStep
					val c2y = y - controlXStep * slope
					path.cubicTo(c1x, c1y, c2x, c2y, x, y)
				}
				
				else -> {
					// 制御点1
					val c1x = lastX + controlXStep
					val c1y = clipRange(y_min, y_max, lastY + controlXStep * lastSlope)
					
					// 制御点2
					val nextY = y_workarea[i + 1]
					val slope = if((y > lastY && y > nextY) || (y < lastY && y < nextY)) {
						// 極値は傾き0とみなす
						0f
					} else if(y == lastY || y == nextY) {
						// 左右のどちらかが平坦なら平坦とみなす
						0f
					} else {
						// 前後で同じように勾配しているなら傾きは平均とする
						val slope1 = (y - lastY) / x_step
						val slope2 = (nextY - y) / x_step
						(slope1 + slope2) / 2f
					}
					val c2x = x - controlXStep
					val c2y = clipRange(y_min, y_max, y - controlXStep * slope)
					
					path.cubicTo(c1x, c1y, c2x, c2y, x, y)
					lastSlope = slope
				}
			}
			lastX = x
			lastY = y
			x += x_step
		}
		canvas.drawPath(path, paint)
	}
	
}
