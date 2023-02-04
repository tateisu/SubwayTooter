package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import jp.juggler.apng.ApngFrames
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.log.LogCategory
import java.lang.ref.WeakReference

class NetworkEmojiSpan internal constructor(
    private val url: String,
    private val scale: Float = 1f,
) : ReplacementSpan(), AnimatableSpan {

    companion object {

        internal val log = LogCategory("NetworkEmojiSpan")

        private const val scaleRatio = 1.14f
        private const val descentRatio = 0.211f
    }

    private val mPaint = Paint()
    private val rectSrc = Rect()
    private val rectDst = RectF()

    // フレーム探索結果を格納する構造体を確保しておく
    private val mFrameFindResult = ApngFrames.FindFrameResult()

    init {
        mPaint.isFilterBitmap = true
    }

    private var invalidateCallback: AnimatableSpanInvalidator? = null
    private var refDrawTarget: WeakReference<Any>? = null

    override fun setInvalidateCallback(
        drawTargetTag: Any,
        invalidateCallback: AnimatableSpanInvalidator,
    ) {
        this.refDrawTarget = WeakReference(drawTargetTag)
        this.invalidateCallback = invalidateCallback
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val size = (paint.textSize * scaleRatio * scale + 0.5f).toInt()
        if (fm != null) {
            val cDescent = (0.5f + size * descentRatio).toInt()
            val cAscent = cDescent - size
            if (fm.ascent > cAscent) fm.ascent = cAscent
            if (fm.top > cAscent) fm.top = cAscent
            if (fm.descent < cDescent) fm.descent = cDescent
            if (fm.bottom < cDescent) fm.bottom = cDescent
        }
        return size
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        baseline: Int,
        bottom: Int,
        textPaint: Paint,
    ) {
        val invalidateCallback = this.invalidateCallback
        if (invalidateCallback == null) {
            log.e("draw: invalidate_callback is null.")
            return
        }

        // APNGデータの取得
        val frames = App1.custom_emoji_cache.getFrames(refDrawTarget, url) {
            invalidateCallback.delayInvalidate(0L)
        } ?: return

        val t = when {
            PrefB.bpDisableEmojiAnimation.value -> 0L
            else -> invalidateCallback.timeFromStart
        }

        // アニメーション開始時刻からの経過時間に応じたフレームを探索
        frames.findFrame(mFrameFindResult, t)

        val b = mFrameFindResult.bitmap
        if (b == null || b.isRecycled) {
            log.e("draw: bitmap is null or recycled.")
            return
        }
        val srcWidth = b.width
        val srcHeight = b.height
        if (srcWidth < 1 || srcHeight < 1) {
            log.e("draw: bitmap size is too small.")
            return
        }
        rectSrc.set(0, 0, srcWidth, srcHeight)

        // 絵文字の正方形のサイズ
        val dstSize = textPaint.textSize * scaleRatio * scale

        // ベースラインから上下方向にずらすオフセット
        val cDescent = dstSize * descentRatio
        val transY = baseline - dstSize + cDescent

        // 絵文字のアスペクト比から描画範囲の幅と高さを決める
        val dstWidth: Float
        val dstHeight: Float
        val aspectSrc = srcWidth.toFloat() / srcHeight.toFloat()
        if (aspectSrc >= 1f) {
            dstWidth = dstSize
            dstHeight = dstSize / aspectSrc
        } else {
            dstHeight = dstSize
            dstWidth = dstSize * aspectSrc
        }
        val dstX = (dstSize - dstWidth) / 2f
        val dstY = (dstSize - dstHeight) / 2f
        rectDst.set(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

        canvas.save()
        try {
            canvas.translate(x, transY)
            canvas.drawBitmap(b, rectSrc, rectDst, mPaint)
        } catch (ex: Throwable) {
            log.w(ex, "drawBitmap failed.")

            // 10月6日 18:18（アプリのバージョン: 378） Sony Xperia X Compact（F5321）, Android 8.0
            // 10月6日 11:35（アプリのバージョン: 380） Samsung Galaxy S7 Edge（hero2qltetmo）, Android 8.0
            // 10月2日 21:56（アプリのバージョン: 376） Google Pixel 3（blueline）, Android 9
            //	java.lang.RuntimeException:
            //			at android.graphics.BaseCanvas.throwIfCannotDraw (BaseCanvas.java:55)
            //			at android.view.DisplayListCanvas.throwIfCannotDraw (DisplayListCanvas.java:226)
            //			at android.view.RecordingCanvas.drawBitmap (RecordingCanvas.java:123)
            //			at jp.juggler.subwaytooter.span.NetworkEmojiSpan.draw (NetworkEmojiSpan.kt:137)
        } finally {
            canvas.restore()
        }

        // 少し後に描画しなおす
        val delay = mFrameFindResult.delay
        if (delay != Long.MAX_VALUE && !PrefB.bpDisableEmojiAnimation.value) {
            invalidateCallback.delayInvalidate(delay)
        }
    }
}
