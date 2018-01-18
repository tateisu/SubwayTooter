package jp.juggler.subwaytooter.span

import android.text.TextPaint
import android.text.style.CharacterStyle

class HighlightSpan(val color_fg : Int, val color_bg : Int) : CharacterStyle() {
	
	override fun updateDrawState(ds : TextPaint) {
		if(color_fg != 0) ds.color = color_fg
		if(color_bg != 0) ds.bgColor = color_bg
	}
	
}
