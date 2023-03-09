package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan

class OrderedListItemSpan(
    private val order: String,
    orders: List<String>,
) : LeadingMarginSpan {
    companion object {
        fun List<String>.longest(paint: TextPaint): String? {
            var longestText: String? = null
            var longestTextWidth: Int? = null
            forEach { text ->
                val textWidth = (paint.measureText(text) + .5f).toInt()
                if (longestTextWidth?.takeIf { it > textWidth } != null) return@forEach
                longestText = text
                longestTextWidth = textWidth
            }
            return longestText
        }
    }

    private val paint = TextPaint()
    private val longestOrder = orders.longest(paint) ?: ""
    private var longestOrderWidth =
        (paint.measureText(longestOrder) + .5f).toInt() +
                (paint.measureText(". ") * 2f + .5f).toInt()

    override fun getLeadingMargin(first: Boolean): Int = longestOrderWidth

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
        // if there was a line break, we don't need to draw anything
        if (!first || !selfStart(start, text, this)) {
            return
        }

        paint.set(p)

        val longestOrderWidth = (paint.measureText(longestOrder) + .5f).toInt() +
                (paint.measureText(".") * 2f + .5f).toInt()
        this.longestOrderWidth = longestOrderWidth

        val line = layout.getLineForOffset(start)
        if (dir > 0) {
            paint.textAlign = Paint.Align.LEFT
            val left = layout.getParagraphLeft(line) - longestOrderWidth
            c.drawText("${order}.", left.toFloat(), baseline.toFloat(), paint)
        } else {
            paint.textAlign = Paint.Align.RIGHT
            val right = layout.getParagraphRight(line) + longestOrderWidth
            c.drawText(".${order}", right.toFloat(), baseline.toFloat(), paint)
        }
    }
}
