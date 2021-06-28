package jp.juggler.subwaytooter.itemviewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class EnqueteTimerView : View {

    companion object {
        private const val bg_color = 0x40808080
        private const val fg_color = Color.WHITE
    }

    private var timeStart: Long = 0
    private var duration: Long = 0
    private val paint = Paint()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setParams(timeStart: Long, duration: Long) {
        this.timeStart = timeStart
        this.duration = duration
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewW = width
        val viewH = height

        paint.color = bg_color
        canvas.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), paint)

        val progress = System.currentTimeMillis() - timeStart
        val ratio =
            if (duration <= 0L) 1f else if (progress <= 0L) 0f else if (progress >= duration) 1f else progress / duration.toFloat()

        paint.color = fg_color
        canvas.drawRect(0f, 0f, viewW * ratio, viewH.toFloat(), paint)

        if (ratio < 1f) postInvalidateOnAnimation()
    }
}
