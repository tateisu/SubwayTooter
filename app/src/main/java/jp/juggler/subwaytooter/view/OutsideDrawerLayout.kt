package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import jp.juggler.util.cast
import java.util.*

/*
	「Viewからはみ出した部分の描画」を実現する。
	子孫Viewとコールバックを登録すると、onDraw時に子孫Viewの位置とcanvasをコールバックに渡す。
 */
class OutsideDrawerLayout : LinearLayout {

    constructor(context: Context) :
        super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) :
        super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        init()
    }

    fun init() {
        setWillNotDraw(false)
    }

    private class Callback(
        val view: View,
        val draw: (
            canvas: Canvas,
            parent: ViewGroup,
            descendant: View,
            left: Int,
            top: Int
        ) -> Unit
    )

    private val callbackList = LinkedList<Callback>()

    fun addOutsideDrawer(
        view: View,
        draw: (
            canvas: Canvas,
            parent: ViewGroup,
            descendant: View,
            left: Int,
            top: Int
        ) -> Unit
    ) {
        if (null == callbackList.find { it.view == view && it.draw == draw }) callbackList.add(
            Callback(view, draw)
        )
    }

    @Suppress("unused")
    fun removeOutsideDrawer(view: View) {
        val it = callbackList.iterator()
        while (it.hasNext()) {
            val cb = it.next()
            if (cb.view == view) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas ?: return

        val it = callbackList.iterator()
        while (it.hasNext()) {
            val drawer = it.next()

            var left = 0
            var top = 0
            var v = drawer.view
            while (true) {
                if (v == this) break
                val parent = v.parent.cast<ViewGroup>() ?: break
                left += v.left
                top += v.top
                v = parent
            }
            canvas.save()
            try {
                drawer.draw(canvas, this, drawer.view, left, top)
            } finally {
                canvas.restore()
            }
        }
    }
}
