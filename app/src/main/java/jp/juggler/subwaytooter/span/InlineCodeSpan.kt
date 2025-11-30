package jp.juggler.subwaytooter.span

import android.graphics.Typeface
import android.text.TextPaint
import jp.juggler.util.ui.FontSpan

class InlineCodeSpan(
    tf: Typeface = Typeface.MONOSPACE,
    private val bgColor: Int = 0x40808080,
) : FontSpan(tf) {
    override fun updateDrawState(drawState: TextPaint) {
        super.updateDrawState(drawState)
        drawState.bgColor = bgColor
    }
}
