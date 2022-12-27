package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ListView
import jp.juggler.subwaytooter.itemviewholder.StatusButtonsPopup

import jp.juggler.util.LogCategory

class MyListView : ListView {

    companion object {
        private val log = LogCategory("MyListView")
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context,
        attrs,
        defStyleAttr)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {

        // ポップアップを閉じた時にクリックでリストを触ったことになってしまう不具合の回避
        val now = SystemClock.elapsedRealtime()
        if (now - StatusButtonsPopup.lastPopupClose < 30L) {
            val action = ev.action
            if (action == MotionEvent.ACTION_DOWN) {
                // ポップアップを閉じた直後はタッチダウンを無視する
                return false
            }

            val rv = super.onTouchEvent(ev)
            log.d("onTouchEvent action=$action, rv=$rv")
            return rv
        }

        return super.onTouchEvent(ev)
    }

    override fun layoutChildren() {
        try {
            super.layoutChildren()
        } catch (ex: Throwable) {
            log.e(ex, "layoutChildren failed.")
        }
    }
}
