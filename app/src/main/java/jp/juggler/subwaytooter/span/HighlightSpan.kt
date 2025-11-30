package jp.juggler.subwaytooter.span

import android.text.TextPaint
import android.text.style.CharacterStyle

class HighlightSpan(private val colorFg: Int, val colorBg: Int) : CharacterStyle() {

    override fun updateDrawState(ds: TextPaint) {
        if (colorFg != 0) ds.color = colorFg
        if (colorBg != 0) ds.bgColor = colorBg
    }
}
