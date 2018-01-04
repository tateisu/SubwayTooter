package jp.juggler.subwaytooter.view

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration

class MyRecyclerView : RecyclerView {
	
	
	private var mForbidStartDragging : Boolean = false
	private var mScrollPointerId : Int = 0
	private var mInitialTouchX : Int = 0
	private var mInitialTouchY : Int = 0
	private var mTouchSlop : Int = 0
	
	constructor(context : Context) : super(context) {
		init(context)
	}
	
	
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
		init(context)
	}
	
	constructor(context : Context, attrs : AttributeSet?, defStyle : Int) : super(context, attrs, defStyle) {
		init(context)
	}
	
	private fun init(context : Context) {
		val vc = ViewConfiguration.get(context)
		mTouchSlop = vc.scaledTouchSlop
	}
	
	override fun onInterceptTouchEvent(e : MotionEvent) : Boolean {
		val action = e.action
		// final int actionIndex = e.getActionIndex( );
		
		when(action) {
			
			MotionEvent.ACTION_DOWN -> {
				mForbidStartDragging = false
				mScrollPointerId = e.getPointerId(0)
				mInitialTouchX = (e.x + 0.5f).toInt()
				mInitialTouchY = (e.y + 0.5f).toInt()
			}
			
			MotionEvent.ACTION_MOVE -> {
				val index = e.findPointerIndex(mScrollPointerId)
				if(index >= 0) {
					if(mForbidStartDragging) return false
					
					val x = (e.getX(index) + 0.5f).toInt()
					val y = (e.getY(index) + 0.5f).toInt()
					val canScrollHorizontally = layoutManager.canScrollHorizontally()
					val canScrollVertically = layoutManager.canScrollVertically()
					
					val dx = x - mInitialTouchX
					val dy = y - mInitialTouchY
					
					if(! canScrollVertically && Math.abs(dy) > mTouchSlop || ! canScrollHorizontally && Math.abs(dx) > mTouchSlop) {
						mForbidStartDragging = true
						return false
					}
				}
			}
		}
		return super.onInterceptTouchEvent(e)
	}
}
