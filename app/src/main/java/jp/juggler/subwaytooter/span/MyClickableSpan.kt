package jp.juggler.subwaytooter.span

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

import java.lang.ref.WeakReference

import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.LinkClickContext


class MyClickableSpan internal constructor(
	val lcc : LinkClickContext, val text : String, val url : String, ac : AcctColor?, val tag : Any?
) : ClickableSpan() {
	
	companion object {
		var link_callback : WeakReference<MyClickableSpanClickCallback>? = null
	}

	val color_fg : Int
	val color_bg : Int
	
	init {
		
		if(ac != null) {
			this.color_fg = ac.color_fg
			this.color_bg = ac.color_bg
		} else {
			this.color_fg = 0
			this.color_bg = 0
		}
	}
	
	override fun onClick( view : View) {
		val cb = link_callback?.get()
		if( cb != null ) cb(view,this )
	}
	
	override fun updateDrawState(ds : TextPaint) {
		super.updateDrawState(ds)
		
		if(color_fg != 0) {
			ds.color = color_fg
		}
		if(color_bg != 0) {
			ds.bgColor = color_bg
		}
		
	}
	
}
