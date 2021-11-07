package jp.juggler.subwaytooter.drawable

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.min

class MediaBackgroundDrawable(
    private val tileStep: Int,
    private val kind: Kind
) : Drawable() {

    enum class Kind(@ColorInt val c1: Int, @ColorInt val c2: Int = 0) {
        Black(Color.BLACK),
        BlackTile(Color.BLACK, Color.BLACK or 0x202020),
        Grey(Color.BLACK or 0x787878),
        GreyTile(Color.BLACK or 0x707070, Color.BLACK or 0x808080),
        White(Color.WHITE),
        WhiteTile(Color.WHITE, Color.BLACK or 0xe0e0e0),

        ;

        fun toIndex() = values().indexOf(this)

        companion object {
            fun fromIndex(idx: Int) = values().elementAtOrNull(idx) ?: BlackTile
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

    override fun getOpacity() = when (paint.alpha) {
        255 -> PixelFormat.OPAQUE
        0 -> PixelFormat.TRANSPARENT
        else -> PixelFormat.TRANSLUCENT
    }

    override fun draw(canvas: Canvas) {
        val bounds = this.bounds
        val c1 = kind.c1
        val c2 = kind.c2
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
