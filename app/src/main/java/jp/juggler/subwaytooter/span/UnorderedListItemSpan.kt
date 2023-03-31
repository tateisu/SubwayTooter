package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import androidx.annotation.IntRange

class UnorderedListItemSpan(
    @IntRange(from = 0) private val level: Int,
    private var bulletWidth: Int = 0,
) : LeadingMarginSpan {

    private val circle = RectF()
    private val rectangle = Rect()
    private val paint = TextPaint()

    private var marginWidth = (paint.descent() - paint.ascent() + .5f).toInt()

    override fun getLeadingMargin(first: Boolean) = marginWidth

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
        if (!first || !selfStart(start, text, this)) return

        paint.set(p)
        val save = c.save()
        try {
            // テキストの高さ
            val textLineHeight = (paint.descent() - paint.ascent() + .5f).toInt()
            // ドットを含む横マージンの幅
            @Suppress("UnnecessaryVariable")
            val marginWidth = textLineHeight
            this.marginWidth = marginWidth
            val marginHalf = marginWidth / 2

            // ドットの直径
            val bulletWidth = textLineHeight / 2

            // 行頭からドットの開始端までの距離
            val marginLeft = (marginWidth - bulletWidth) / 2

            val line = layout.getLineForOffset(start)
            val edge = if (dir > 0) {
                layout.getParagraphLeft(line)
            } else {
                layout.getParagraphRight(line)
            }
            val l = edge - dir * marginHalf - bulletWidth / 2
            val t =
                baseline + ((paint.descent() + paint.ascent()) / 2f + .5f).toInt() - bulletWidth / 2
            val r = l + bulletWidth
            val b = t + bulletWidth
            if (level == 0 || level == 1) {
                circle.set(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
                paint.style = when (level) {
                    0 -> Paint.Style.FILL
                    else -> Paint.Style.STROKE
                }
                c.drawOval(circle, paint)
            } else {
                rectangle.set(l, t, r, b)
                paint.style = Paint.Style.FILL
                c.drawRect(rectangle, paint)
            }
        } finally {
            c.restoreToCount(save)
        }
    }
}
