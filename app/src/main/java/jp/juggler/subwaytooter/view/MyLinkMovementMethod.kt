package jp.juggler.subwaytooter.view

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.Touch
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView

object MyLinkMovementMethod : LinkMovementMethod() {
	
	override fun onTouchEvent(widget : TextView, buffer : Spannable, event : MotionEvent) : Boolean {
		
		val action = event.action
		
		if(action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) {
			return Touch.onTouchEvent(widget, buffer, event)
		}
		
		var x = event.x.toInt()
		var y = event.y.toInt()
		
		x -= widget.totalPaddingLeft
		y -= widget.totalPaddingTop
		
		x += widget.scrollX
		y += widget.scrollY
		
		val layout = widget.layout
		
		val line = layout.getLineForVertical(y)
		if(0 <= line && line < layout.lineCount) {
			
			val line_left = layout.getLineLeft(line)
			val line_right = layout.getLineRight(line)
			if(line_left <= x && x <= line_right) {
				
				val offset = layout.getOffsetForHorizontal(line, x.toFloat())
				
				val link = buffer.getSpans(offset, offset, ClickableSpan::class.java)
				if(link != null && link.isNotEmpty()) {
					
					if(action == MotionEvent.ACTION_UP) {
						link[0].onClick(widget)
					}
					if(widget is MyTextView) {
						widget.linkHit = true
					}
					return true
				}
			}
		}
		
		Touch.onTouchEvent(widget, buffer, event)
		return false
	}
	
}
