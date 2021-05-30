package jp.juggler.subwaytooter.span

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.util.activity
import jp.juggler.util.cast
import jp.juggler.util.notZero

interface MyClickableSpanHandler{
	fun onMyClickableSpanClicked(viewClicked:View,span:MyClickableSpan)
}

class LinkInfo(
	var url : String,
	var ac : AcctColor? =null,
	var tag : Any?=null,
	var caption : CharSequence ="",
	var mention : TootMention? = null
){
	val text : String
		get() = caption.toString()
}

class MyClickableSpan(val linkInfo : LinkInfo) : ClickableSpan() {
	
	companion object {
		var defaultLinkColor : Int = 0
		var showLinkUnderline = true
	}

	val color_fg : Int
	val color_bg : Int
	
	init {
		val ac = linkInfo.ac
		if(ac != null) {
			this.color_fg = ac.color_fg
			this.color_bg = ac.color_bg
		} else {
			this.color_fg = 0
			this.color_bg = 0
		}
	}
	
	override fun onClick(view : View) {
		view.activity
			?.cast<MyClickableSpanHandler>()
			?.onMyClickableSpanClicked(view,this)
	}
	
	override fun updateDrawState(ds : TextPaint) {
		super.updateDrawState(ds)
		if(color_bg != 0) ds.bgColor = color_bg
		
		ds.color = color_fg.notZero() ?: defaultLinkColor
		ds.isUnderlineText = showLinkUnderline
	}
}
