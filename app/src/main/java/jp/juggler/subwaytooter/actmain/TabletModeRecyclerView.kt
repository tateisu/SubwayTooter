package jp.juggler.subwaytooter.actmain

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class TabletModeRecyclerView : RecyclerView {

    private var mForbidStartDragging: Boolean = false
    private var mScrollPointerId: Int = 0
    private var mInitialTouchX: Int = 0
    private var mInitialTouchY: Int = 0
    private var mTouchSlop: Int = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context)
    }

    private fun init(context: Context) {
        val vc = ViewConfiguration.get(context)
        mTouchSlop = vc.scaledTouchSlop
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // final int actionIndex = e.getActionIndex( );

        when (e.action) {

            MotionEvent.ACTION_DOWN -> {
                mForbidStartDragging = false
                mScrollPointerId = e.getPointerId(0)
                mInitialTouchX = (e.x + 0.5f).toInt()
                mInitialTouchY = (e.y + 0.5f).toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                val index = e.findPointerIndex(mScrollPointerId)
                if (index >= 0) {
                    if (mForbidStartDragging) return false

                    val layoutManager = this.layoutManager ?: return false

                    val x = (e.getX(index) + 0.5f).toInt()
                    val y = (e.getY(index) + 0.5f).toInt()
                    val canScrollHorizontally = layoutManager.canScrollHorizontally()
                    val canScrollVertically = layoutManager.canScrollVertically()

                    val dx = x - mInitialTouchX
                    val dy = y - mInitialTouchY

                    if (!canScrollVertically && abs(dy) > mTouchSlop || !canScrollHorizontally && abs(
                            dx
                        ) > mTouchSlop
                    ) {
                        mForbidStartDragging = true
                        return false
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(e)
    }
}
