package jp.juggler.subwaytooter.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

/**
 * dd 要素の字下げスパン
 */
class DdSpan(
    context: Context,
    marginDp: Float = 24f,
) : LeadingMarginSpan {
    private val marginPx = (context.resources.displayMetrics.density * marginDp + 0.5f).toInt()

    override fun getLeadingMargin(first: Boolean): Int = marginPx

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
    }
}
