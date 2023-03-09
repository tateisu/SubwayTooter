package jp.juggler.subwaytooter.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.style.LeadingMarginSpan
import jp.juggler.subwaytooter.R
import jp.juggler.util.ui.attrColor
import kotlin.math.max
import kotlin.math.min

/**
 * ブロック引用の装飾スパン
 */
class BlockQuoteSpan(
    context: Context,
    private val quoteWidth: Int = (context.resources.displayMetrics.density * 4f + 0.5f).toInt(),
    private val blockQuoteColor: Int = context.attrColor(R.attr.colorTextHint),
) : LeadingMarginSpan {
    private val rect = Rect()
    private val paint = Paint()
    override fun getLeadingMargin(first: Boolean): Int = quoteWidth*2

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
        paint.set(p)
        paint.style = Paint.Style.FILL
        paint.color = blockQuoteColor

        val line = layout.getLineForOffset(start)
        val edge = if (dir > 0) {
            layout.getParagraphLeft(line)
        } else {
            layout.getParagraphRight(line)
        }
        val width = quoteWidth
        val l = edge - dir * width
        val r = edge - dir * width * 2
        rect.set(
            min(l, r),
            top,
            max(l, r),
            bottom
        )
        c.drawRect(rect, paint)
    }
}
