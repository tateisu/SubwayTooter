package jp.juggler.subwaytooter.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import jp.juggler.subwaytooter.R
import jp.juggler.util.ui.attrColor
import kotlin.math.min

class MediaBackgroundDrawable(
    private val context: Context,
    private val tileStep: Int,
    private val kind: Kind,
) : Drawable() {

    enum class Kind(
        val c1: Context.() -> Int,
        val c2: Context.() -> Int,
        val isMediaBackground: Boolean = true,
    ) {
        Black({ Color.BLACK }, { 0 }),
        BlackTile({ Color.BLACK }, { Color.BLACK or 0x202020 }),
        Grey({ Color.BLACK or 0x787878 }, { 0 }),
        GreyTile({ Color.BLACK or 0x707070 }, { Color.BLACK or 0x808080 }),
        White({ Color.WHITE }, { 0 }),
        WhiteTile({ Color.WHITE }, { Color.BLACK or 0xe0e0e0 }),

        EmojiPickerBg(
            { attrColor(R.attr.colorWindowBackground) },
            { attrColor(R.attr.colorTimeSmall) },
            isMediaBackground = false
        ),

        ;

        fun toIndex() = entries.indexOf(this)

        companion object {
            fun fromIndex(idx: Int) = entries.elementAtOrNull(idx) ?: BlackTile
        }
    }

    private val rect = Rect()

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("deprecated in API level 29.")
    override fun getOpacity() = when (paint.alpha) {
        255 -> PixelFormat.OPAQUE
        0 -> PixelFormat.TRANSPARENT
        else -> PixelFormat.TRANSLUCENT
    }

    override fun draw(canvas: Canvas) {
        val bounds = this.bounds
        val c1 = kind.c1.invoke(context)
        val c2 = kind.c2.invoke(context)
        if (c2 == 0) {
            paint.color = c1
            canvas.drawRect(bounds, paint)
            return
        }

        val xStart = bounds.left
        val xRepeat = (bounds.right - bounds.left + tileStep - 1) / tileStep
        val yStart = bounds.top
        val yRepeat = (bounds.bottom - bounds.top + tileStep - 1) / tileStep
        for (y in 0 until yRepeat) {
            val ys = yStart + y * tileStep
            rect.top = ys
            rect.bottom = min(bounds.bottom, ys + tileStep)
            for (x in 0 until xRepeat) {
                paint.color = when ((x + y).and(1)) {
                    0 -> c1
                    else -> c2
                }
                val xs = xStart + x * tileStep
                rect.left = xs
                rect.right = min(bounds.right, xs + tileStep)
                canvas.drawRect(rect, paint)
            }
        }
    }
}
