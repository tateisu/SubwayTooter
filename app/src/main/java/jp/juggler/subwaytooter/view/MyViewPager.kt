package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class MyViewPager : ViewPager {
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs)
	
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev : MotionEvent) : Boolean = try {
		super.onTouchEvent(ev)
	} catch(ex : IllegalArgumentException) {
		ex.printStackTrace()
		false
	}
	
	override fun onInterceptTouchEvent(ev : MotionEvent) : Boolean = try {
		super.onInterceptTouchEvent(ev)
	} catch(ex : IllegalArgumentException) {
		ex.printStackTrace()
		false
	}
}
