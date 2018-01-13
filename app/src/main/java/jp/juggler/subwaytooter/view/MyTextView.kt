package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import android.view.MotionEvent

class MyTextView : AppCompatTextView {
	
	internal var linkHit : Boolean = false
	
	private var size_callback : SizeChangedCallback? = null
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs)
	constructor(context : Context, attrs : AttributeSet?, defStyleAttr : Int) : super(context, attrs, defStyleAttr)
	
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event : MotionEvent) : Boolean {
		// リンクをタップした時以外はタッチイベントを処理しない
		linkHit = false
		super.onTouchEvent(event)
		return linkHit
	}
	
	interface SizeChangedCallback {
		fun onSizeChanged(w : Int, h : Int)
	}
	
	@Suppress("unused")
	fun setSizeChangedCallback(cb : SizeChangedCallback?) {
		size_callback = cb
	}
	
	override fun onSizeChanged(w : Int, h : Int, old_w : Int, old_h : Int) {
		super.onSizeChanged(w, h, old_w, old_h)
		if(w > 0 && h > 0 ) size_callback?.onSizeChanged(w, h)
	}
}
