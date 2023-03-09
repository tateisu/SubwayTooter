package jp.juggler.subwaytooter.span


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.util.ui.attrColor
import kotlin.math.max
import kotlin.math.min

/**
 * コードブロック用の装飾スパン
 */
class BlockCodeSpan (
    context: Context = lazyContext,
    private var typeface: Typeface = Typeface.MONOSPACE,
    private var relativeTextSize: Float = 0.7f,
    private var margin: Int = 0,
    private var textColor: Int = context.attrColor(R.attr.colorTextContent),
    private var backgroundColor: Int = 0x40808080,
) : MetricAffectingSpan(), LeadingMarginSpan {

    private val rect = Rect()
    private val paint = Paint()

    private fun apply(paint: TextPaint) {
        paint.color = textColor
        paint.typeface = typeface
        paint.textSize = paint.textSize * relativeTextSize
    }

    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        apply(textPaint)
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return margin
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence?,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout
    ) {
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor

        val line = layout.getLineForOffset(start)
        val left =  layout.getParagraphLeft(line)
        val right = layout.getParagraphRight(line)
        rect.set(
            min(left,right),
            top,
            max(left,right),
            bottom,
        )
        c.drawRect(rect, paint)
    }
}
