package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import androidx.viewpager.widget.ViewPager
import android.util.AttributeSet
import android.view.MotionEvent

class MyViewPager : androidx.viewpager.widget.ViewPager {
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
