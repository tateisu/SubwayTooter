package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class EnqueteTimerView : View {
	
	companion object {
		private const val bg_color = 0x40808080
		private const val fg_color = Color.WHITE
	}
	
	private var time_start : Long = 0
	private var duration : Long = 0
	private val paint = Paint()
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs)
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(context, attrs, defStyleAttr)
	
	fun setParams(time_start : Long, duration : Long) {
		this.time_start = time_start
		this.duration = duration
		invalidate()
	}
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val view_w = width
		val view_h = height
		
		paint.color = bg_color
		canvas.drawRect(0f, 0f, view_w.toFloat(), view_h.toFloat(), paint)
		
		val progress = System.currentTimeMillis() - time_start
		val ratio = if(duration <= 0L) 1f else if(progress <= 0L) 0f else if(progress >= duration) 1f else progress / duration.toFloat()
		
		paint.color = fg_color
		canvas.drawRect(0f, 0f, view_w * ratio, view_h.toFloat(), paint)
		
		if(ratio < 1f) postInvalidateOnAnimation()
	}
}
