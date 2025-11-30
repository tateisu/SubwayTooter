package jp.juggler.subwaytooter.actmain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.LinearLayout

// 画面下のカラムストリップのLinearLayout
// 可視範囲を示すインジケーターを表示する
class ColumnStripLinearLayout : LinearLayout {

    // 可視範囲
    private var visibleFirst: Int = 0
    private var visibleLast: Int = 0
    private var visibleSlideRatio: Float = 0.toFloat()

    // インジケーターの高さ
    private val indicatorHeight: Int

    // インジケーターの色
    var indicatorColor: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        indicatorHeight = (0.5f + 2f * resources.displayMetrics.density).toInt()
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setVisibleRange(first: Int, last: Int, slideRatio: Float) {
        if (this.visibleFirst == first &&
            this.visibleLast == last &&
            this.visibleSlideRatio == slideRatio
        ) return
        this.visibleFirst = first
        this.visibleLast = last
        this.visibleSlideRatio = slideRatio
        invalidate()
    }

    private val paint = Paint()
    private val rect = Rect()

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        if (visibleFirst < 0 || visibleLast >= childCount) return

        val childFirst = getChildAt(visibleFirst)
        val childLast = getChildAt(visibleLast)

        val x = if (visibleSlideRatio != 0f) {
            (0.5f + visibleSlideRatio * childFirst.width).toInt()
        } else {
            0
        }

        rect.left = childFirst.left + x
        rect.right = childLast.right + x
        rect.top = 0
        rect.bottom = indicatorHeight

        paint.color = indicatorColor

        canvas.drawRect(rect, paint)
    }
}
