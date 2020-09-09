package jp.juggler.subwaytooter.view

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.ScrollView
import jp.juggler.subwaytooter.R
import kotlin.math.min

class MaxHeightScrollView : ScrollView {
	
	var maxHeight : Int = 0
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
		val a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView)
		parseAttr(a)
		a.recycle()
	}
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) : super(context, attrs, defStyleAttr) {
		val a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView, defStyleAttr, 0)
		parseAttr(a)
		a.recycle()
	}
	
	private fun parseAttr(a : TypedArray) {
		maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, 0)
	}
	
	override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
		var heightMeasureSpec2 = heightMeasureSpec
		if(maxHeight > 0) {
			val hSize = MeasureSpec.getSize(heightMeasureSpec)
			
			when(MeasureSpec.getMode(heightMeasureSpec)) {
				MeasureSpec.AT_MOST -> heightMeasureSpec2 = MeasureSpec.makeMeasureSpec(min(hSize, maxHeight), MeasureSpec.AT_MOST)
				MeasureSpec.UNSPECIFIED -> heightMeasureSpec2 = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
				MeasureSpec.EXACTLY -> heightMeasureSpec2 = MeasureSpec.makeMeasureSpec(min(hSize, maxHeight), MeasureSpec.EXACTLY)
			}
		}
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec2)
	}
	
}
