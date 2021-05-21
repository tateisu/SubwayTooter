package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.appcompat.widget.AppCompatEditText
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

import jp.juggler.util.LogCategory

class MyEditText : AppCompatEditText {
	
	companion object {
		private val log = LogCategory("MyEditText")
	}
	
	private var mOnSelectionChangeListener : OnSelectionChangeListener? = null
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet) : super(context, attrs)
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) : super(
		context,
		attrs,
		defStyleAttr
	)
	
	////////////////////////////////////////////////////
	// 選択範囲変更イベントをコールバックに渡す
	
	interface OnSelectionChangeListener {
		fun onSelectionChanged(selStart : Int, selEnd : Int)
	}
	
	fun setOnSelectionChangeListener(listener : OnSelectionChangeListener) {
		mOnSelectionChangeListener = listener
	}
	
	override fun onSelectionChanged(selStart : Int, selEnd : Int) {
		super.onSelectionChanged(selStart, selEnd)
		mOnSelectionChangeListener?.onSelectionChanged(selStart, selEnd)
	}
	
	////////////////////////////////////////////////////
	// Android 6.0 でのクラッシュ対応
	
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event : MotionEvent) : Boolean {
		return try {
			super.onTouchEvent(event)
		} catch(ex : Throwable) {
			log.trace(ex)
			false
			//		java.lang.NullPointerException:
			//		at android.widget.Editor$SelectionModifierCursorController.onTouchEvent (Editor.java:4889)
			//		at android.widget.Editor.onTouchEvent (Editor.java:1223)
			//		at android.widget.TextView.onTouchEvent (TextView.java:8304)
			//		at android.view.View.dispatchTouchEvent (View.java:9303)
		}
	}
	
	///////////////////////////////////////////////////////
	// IMEから画像を送られてくることがあるらしい
	
	var commitContentListener :InputConnectionCompat.OnCommitContentListener? = null
	var contentMineTypeArray :Array<String>? = null
	
	override fun onCreateInputConnection(outAttrs : EditorInfo?) : InputConnection? {
		
		log.d("onCreateInputConnection: listener=${commitContentListener}")
		
		val super_ic = super.onCreateInputConnection(outAttrs)
		
		val listener = commitContentListener
		val mimeArray = contentMineTypeArray
		return if( listener == null || mimeArray ==null || outAttrs == null ){
			super_ic
		}else{
			EditorInfoCompat.setContentMimeTypes(outAttrs, mimeArray)
			super_ic?.let{ InputConnectionCompat.createWrapper(it ,outAttrs,listener)}
		}
	}
	
	
}
