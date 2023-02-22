package jp.juggler.subwaytooter.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import jp.juggler.apng.ApngFrames
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.util.EmojiImageRect
import jp.juggler.subwaytooter.util.EmojiSizeMode
import jp.juggler.util.log.LogCategory
import java.lang.ref.WeakReference

class NetworkEmojiSpan constructor(
    private val url: String,
    sizeMode: EmojiSizeMode,
    scale: Float = 1f,
    private val errorDrawableId: Int = R.drawable.outline_broken_image_24,
) : ReplacementSpan(), AnimatableSpan {

    companion object {
        internal val log = LogCategory("NetworkEmojiSpan")
        private const val scaleRatio = 1.14f
        private const val descentRatio = 0.211f

        // 最大幅
        var maxEmojiWidth = Float.MAX_VALUE
    }

    private val mPaint = Paint().apply { isFilterBitmap = true }

    // フレーム探索結果を格納する構造体を確保しておく
    private val mFrameFindResult = ApngFrames.FindFrameResult()

    private var invalidateCallback: AnimatableSpanInvalidator? = null

    private var refDrawTarget: WeakReference<Any>? = null

    private var errorDrawableCache: Drawable? = null

    private val rectSrc = Rect()

    private val emojiImageRect = EmojiImageRect(
        sizeMode = sizeMode,
        scale = scale,
        scaleRatio = scaleRatio,
        descentRatio = descentRatio,
        maxEmojiWidth = maxEmojiWidth,
        layout = { _,_-> invalidateCallback?.requestLayout() },
    )

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
        emojiImageRect.updateRect(
            url = url, aspectArg = null, paint.textSize, baseline = 0f
        )
        val height = (emojiImageRect.emojiHeight + 0.5f).toInt()
        if (fm != null) {
            val cDescent = (0.5f + height * descentRatio).toInt()
            val cAscent = cDescent - height
            if (fm.ascent > cAscent) fm.ascent = cAscent
            if (fm.top > cAscent) fm.top = cAscent
            if (fm.descent < cDescent) fm.descent = cDescent
            if (fm.bottom < cDescent) fm.bottom = cDescent
        }
        return (emojiImageRect.emojiWidth + 0.5f).toInt()
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
        if (drawFrame(canvas, x, baseline, textPaint)) return
        drawError(canvas, x, baseline, textPaint)
    }

    private fun drawFrame(
        canvas: Canvas,
        x: Float,
        baseline: Int,
        textPaint: Paint,
    ): Boolean {
        val invalidateCallback = this.invalidateCallback
        if (invalidateCallback == null) {
            log.e("draw: invalidate_callback is null.")
            return false
        }

        // APNGデータの取得
        val frames = App1.custom_emoji_cache.getFrames(refDrawTarget, url) {
            invalidateCallback.delayInvalidate(0L)
        } ?: return false

        val t = when {
            PrefB.bpDisableEmojiAnimation.value -> 0L
            else -> invalidateCallback.timeFromStart
        }

        // アニメーション開始時刻からの経過時間に応じたフレームを探索
        frames.findFrame(mFrameFindResult, t)

        val b = mFrameFindResult.bitmap
        if (b == null || b.isRecycled) {
            log.e("draw: bitmap is null or recycled.")
            return false
        }
        val srcWidth = b.width
        val srcHeight = b.height
        if (srcWidth < 1 || srcHeight < 1) {
            log.e("draw: bitmap size is too small.")
            return false
        }
        rectSrc.set(0, 0, srcWidth, srcHeight)
        emojiImageRect.updateRect(
            url = url,
            aspectArg = srcWidth.toFloat() / srcHeight.toFloat(),
            textPaint.textSize,
            baseline.toFloat()
        )

        canvas.save()
        try {
            canvas.translate(x, emojiImageRect.transY)
            canvas.drawBitmap(b, rectSrc, emojiImageRect.rectDst, mPaint)
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
        return true
    }

    private fun drawError(
        canvas: Canvas,
        x: Float,
        baseline: Int,
        textPaint: Paint,
    ) {
        val drawable = errorDrawableCache
            ?: ContextCompat.getDrawable(lazyContext, errorDrawableId)
                ?.also { errorDrawableCache = it }

        drawable ?: return
        val srcWidth = drawable.intrinsicWidth.toFloat()
        val srcHeight = drawable.intrinsicHeight.toFloat()

        emojiImageRect.updateRect(
            url = "",
            aspectArg = srcWidth / srcHeight,
            textSize = textPaint.textSize,
            baseline = baseline.toFloat()
        )

        canvas.save()
        try {
            canvas.translate(x, emojiImageRect.transY)
            drawable.setBounds(
                emojiImageRect.rectDst.left.toInt(),
                emojiImageRect.rectDst.top.toInt(),
                emojiImageRect.rectDst.right.plus(0.5f).toInt(),
                emojiImageRect.rectDst.bottom.plus(0.5f).toInt(),
            )
            drawable.draw(canvas)
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
    }
}
