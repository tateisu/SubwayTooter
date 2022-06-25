package jp.juggler.subwaytooter.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

class GestureInterceptor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    var intercept: (MotionEvent) -> Boolean = { false }
    var touch: (MotionEvent) -> Boolean = { false }

    override fun onInterceptTouchEvent(ev: MotionEvent) =
        intercept(ev)

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?) =
        event?.let { touch(it) } ?: false
}
