package jp.juggler.subwaytooter.drawable

import android.graphics.*
import android.graphics.drawable.Drawable

class PollPlotDrawable(
    private val color: Int,
    private val startWidth: Int, // pixel width for minimum gauge
    private val ratio: Float, // gauge ratio in 0..1
    private val isRtl: Boolean = false, // false for LTR, true for RTL layout
) : Drawable() {

    override fun setAlpha(alpha: Int) {
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    private val rect = Rect()
    private val paint = Paint()

    override fun draw(canvas: Canvas) {

        val bounds = bounds
        val w = bounds.width()
        val ratioWidth = ((w - startWidth) * ratio + 0.5f).toInt()

        val remainWidth = w - ratioWidth - startWidth

        if (isRtl) {
            rect.set(bounds.left + remainWidth, bounds.top, bounds.right, bounds.bottom)
        } else {
            rect.set(bounds.left, bounds.top, bounds.right - remainWidth, bounds.bottom)
        }
        paint.color = color
        canvas.drawRect(rect, paint)
    }
}
