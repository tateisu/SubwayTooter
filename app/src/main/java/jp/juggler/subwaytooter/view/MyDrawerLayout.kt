package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import jp.juggler.util.LogCategory

class MyDrawerLayout : DrawerLayout {
	
	companion object {
		
		private val log = LogCategory("MyDrawerLayout")
	}
	
	constructor(context : Context) :
		super(context)
	
	constructor(context : Context, attrs : AttributeSet?) :
		super(context, attrs)
	
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) :
		super(context, attrs, defStyleAttr)
	
	private var bottomExclusionWidth : Int = 0
	private var bottomExclusionHeight : Int = 0
	private val exclusionRects = listOf(Rect(), Rect(), Rect(), Rect())
	
	override fun onLayout(changed : Boolean, l : Int, t : Int, r : Int, b : Int) {
		super.onLayout(changed, l, t, r, b)
		
		// 画面下部の左右にはボタンがあるので、システムジェスチャーナビゲーションの対象外にする
		val w = r - l
		val h = b - t
		if(w > 0 && h > 0) {
			
			log.d("onLayout $l,$t,$r,$b bottomExclusionSize=$bottomExclusionWidth,$bottomExclusionHeight")
			
			exclusionRects[0].set(
				0,
				h - bottomExclusionHeight * 2,
				0 + bottomExclusionWidth,
				h
			)
			
			exclusionRects[1].set(
				w - bottomExclusionWidth,
				h - bottomExclusionHeight * 2,
				w,
				h
			)
			
			exclusionRects[2].set(
				0,
				0,
				bottomExclusionWidth,
				(bottomExclusionHeight * 1.5f).toInt()
			)
			
			exclusionRects[3].set(
				w - bottomExclusionWidth,
				0,
				w,
				(bottomExclusionHeight * 1.5).toInt()
			)
			
			ViewCompat.setSystemGestureExclusionRects(this, exclusionRects)
			
			
			setWillNotDraw(false)
		}
	}
	
	// デバッグ用
	//	val paint = Paint()
	//	override fun dispatchDraw(canvas : Canvas?) {
	//		super.dispatchDraw(canvas)
	//
	//		canvas ?: return
	//
	//		log.d("dispatchDraw")
	//		for(rect in exclusionRects) {
	//			paint.color = 0x40ff0000
	//			canvas.drawRect(rect, paint)
	//		}
	//	}
	
	fun setExclusionSize(sizeDp : Int) {
		val w = (sizeDp * 1.25f + 0.5f).toInt()
		val h = (sizeDp * 1.5f + 0.5f).toInt()
		
		bottomExclusionWidth = w
		bottomExclusionHeight = h
		
	}
}