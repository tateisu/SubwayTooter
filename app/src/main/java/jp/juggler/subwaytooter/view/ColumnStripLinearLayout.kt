package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.LinearLayout

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler

class ColumnStripLinearLayout : LinearLayout {
	
	private val paint = Paint()
	private val rect = Rect()
	private var h : Int = 0
	
	private var first : Int = 0
	private var last : Int = 0
	private var slide_ratio : Float = 0.toFloat()
	
	private var color : Int = 0
	
	constructor(context : Context) : super(context) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(context, attrs, defStyleAttr) {
		init()
	}
	
	internal fun init() {
		h = (0.5f + 2f * resources.displayMetrics.density).toInt()
	}
	
	fun setColumnRange(first : Int, last : Int, slide_ratio : Float) {
		if(this.first == first && this.last == last && this.slide_ratio == slide_ratio) return
		this.first = first
		this.last = last
		this.slide_ratio = slide_ratio
		invalidate()
	}
	
	fun setColor(color : Int) {
		val color2 = if( color != 0 ) color else Styler.getAttributeColor(context, R.attr.colorAccent)
		if(this.color == color2) return
		this.color = color2
		invalidate()
	}
	
	
	override fun dispatchDraw(canvas : Canvas) {
		super.dispatchDraw(canvas)
		
		if(first < 0 || last >= childCount) return
		
		var child = getChildAt(first)
		rect.left = child.left
		child = getChildAt(last)
		rect.right = child.right
		
		if(slide_ratio != 0f) {
			child = getChildAt(first)
			val w = child.width
			val slide = (0.5f + slide_ratio * w).toInt()
			rect.left += slide
			rect.right += slide
		}
		
		rect.top = 0
		rect.bottom = h
		paint.color = color
		canvas.drawRect(rect, paint)
	}
	
}
