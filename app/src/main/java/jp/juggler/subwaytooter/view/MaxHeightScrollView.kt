package jp.juggler.subwaytooter.view

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

import jp.juggler.subwaytooter.R

class MaxHeightScrollView : ScrollView {
	
	private var maxHeight : Int = 0
	
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
			val hSize = View.MeasureSpec.getSize(heightMeasureSpec)
			val hMode = View.MeasureSpec.getMode(heightMeasureSpec)
			
			when(hMode) {
				View.MeasureSpec.AT_MOST -> heightMeasureSpec2 = View.MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), View.MeasureSpec.AT_MOST)
				View.MeasureSpec.UNSPECIFIED -> heightMeasureSpec2 = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
				View.MeasureSpec.EXACTLY -> heightMeasureSpec2 = View.MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), View.MeasureSpec.EXACTLY)
			}
		}
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec2)
	}
	
}
