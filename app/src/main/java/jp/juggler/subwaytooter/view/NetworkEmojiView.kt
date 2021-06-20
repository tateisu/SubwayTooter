package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

import java.lang.ref.WeakReference

import jp.juggler.apng.ApngFrames
import jp.juggler.subwaytooter.App1

class NetworkEmojiView : View {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private var url: String? = null

    private val tagDrawTarget: WeakReference<Any> by lazy { WeakReference(this) }

    // フレーム探索結果を格納する構造体を確保しておく
    private val mFrameFindResult = ApngFrames.FindFrameResult()

    // 最後に描画した時刻
    private var tLastDraw = 0L

    // アニメーション開始時刻
    private var tStart = 0L

    private val mPaint = Paint()
    private val rectSrc = Rect()
    private val rectDst = RectF()

    fun setEmoji(url: String?) {
        this.url = url
        mPaint.isFilterBitmap = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val url = this.url
        if (url == null || url.isBlank()) return

        // APNGデータの取得
        val frames = App1.custom_emoji_cache.getFrames(tagDrawTarget, url) {
            postInvalidateOnAnimation()
        } ?: return

        val now = SystemClock.elapsedRealtime()

        // アニメーション開始時刻を計算する
        if (tStart == 0L || now - tLastDraw >= 60000L) {
            tStart = now
        }
        tLastDraw = now

        // アニメーション開始時刻からの経過時間に応じたフレームを探索
        frames.findFrame(mFrameFindResult, now - tStart)

        val b = mFrameFindResult.bitmap
        if (b == null || b.isRecycled) return

        val srcWidth = b.width.toFloat()
        val srcHeight = b.height.toFloat()
        if (srcWidth < 1f || srcHeight < 1f) return
        val srcAspect = srcWidth / srcHeight

        val dstWidth = this.width.toFloat()
        val dstHeight = this.height.toFloat()
        if (dstWidth < 1f || dstHeight < 1f) return
        val dstAspect = dstWidth / dstHeight

        val drawWidth: Float
        val drawHeight: Float
        if (srcAspect >= dstAspect) {
            // ソースの方が横長
            drawWidth = dstWidth
            drawHeight = srcHeight * (dstWidth / srcWidth)
        } else {
            // ソースの方が縦長
            drawHeight = dstHeight
            drawWidth = srcWidth * (dstHeight / srcHeight)
        }

        val drawLeft = (dstWidth - drawWidth) / 2f
        val drawTop = (dstHeight - drawHeight) / 2f

        rectSrc.set(0, 0, b.width, b.height)
        rectDst.set(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
        canvas.drawBitmap(b, rectSrc, rectDst, mPaint)

        // 少し後に描画しなおす
        val delay = mFrameFindResult.delay
        if (delay != Long.MAX_VALUE) {
            postInvalidateDelayed(delay)
        }
    }
}
