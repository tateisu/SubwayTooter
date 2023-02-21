package jp.juggler.subwaytooter.util

import android.os.Handler
import android.os.SystemClock
import android.text.Spannable
import android.widget.TextView
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.span.AnimatableSpan
import jp.juggler.subwaytooter.span.AnimatableSpanInvalidator
import jp.juggler.util.data.clip
import java.lang.ref.WeakReference

class NetworkEmojiInvalidator(
    internal val handler: Handler,
    internal val view: TextView,
    // val parent:View? = null,
) : AnimatableSpanInvalidator {

    var text: CharSequence
        get() = view.text
        set(value) {
            register(value as? Spannable)
            view.text = value
        }

    private val drawTargetList = ArrayList<WeakReference<Any>>()

    // 最後に描画した時刻
    private var tLastDraw = 0L

    // アニメーション開始時刻
    private var tStart = 0L

    // アニメーション開始時刻を計算する
    override val timeFromStart: Long
        get() {
            val now = SystemClock.elapsedRealtime()
            if (tStart == 0L || now - tLastDraw >= 60000L) {
                tStart = now
            }
            tLastDraw = now

            return now - tStart
        }

    // Handler経由で遅延実行される
    private val runnableInvalidate = object : Runnable {
        override fun run() {
            // 後から来たのをこのタイミングで重複排除する
            handler.removeCallbacks(this)
            if (view.isAttachedToWindow) {
                view.postInvalidateOnAnimation()
            }
        }
    }

    // Handler経由で遅延実行される
    private val runnableRequestLayout = object : Runnable {
        override fun run() {
            // 後から来たのをこのタイミングで重複排除する
            handler.removeCallbacks(this)
            if (view.isAttachedToWindow) {
                view.requestLayout()
            }
        }
    }

    // 絵文字スパンを描画した直後に呼ばれる
    // (絵文字が多いと描画の度に大量に呼び出される)
    override fun delayInvalidate(delay: Long) {
        // あとから来たのをconflateしたいので、このタイミングでは removeCallbacks しない
        handler.postDelayed(runnableInvalidate, delay.clip(10L, 711L))
    }

    override fun requestLayout() {
        // あとから来たのをconflateしたいので、このタイミングでは removeCallbacks しない
        handler.postDelayed(runnableRequestLayout, 10L)
    }

    fun clear() {
        for (o in drawTargetList) {
            App1.custom_emoji_cache.cancelRequest(o)
        }
        drawTargetList.clear()
    }

    // 装飾テキスト中のカスタム絵文字スパンにコールバックを登録する
    fun register(dst: Spannable?) {
        clear()

        if (dst != null) {
            for (span in dst.getSpans(0, dst.length, AnimatableSpan::class.java)) {
                val tag = WeakReference(Any())
                drawTargetList.add(tag)
                span.setInvalidateCallback(tag, this)
            }
        }
    }
}
