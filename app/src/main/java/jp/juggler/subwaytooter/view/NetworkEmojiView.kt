package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import jp.juggler.apng.ApngFrames
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.util.EmojiImageRect
import jp.juggler.subwaytooter.util.EmojiSizeMode
import java.lang.ref.WeakReference

@SuppressLint("ViewConstructor")
class NetworkEmojiView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    sizeMode: EmojiSizeMode,
    maxEmojiWidth: Float,
    scale: Float = 1f,
    private val errorDrawableId: Int = R.drawable.outline_broken_image_24,
) : View(context, attrs, defStyleAttr) {

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

    private var errorDrawableCache: Drawable? = null

    private val emojiImageRect = EmojiImageRect(
        sizeMode = sizeMode,
        scale = scale,
        scaleRatio = 1f,
        descentRatio = 0f,
        maxEmojiWidth = maxEmojiWidth,
//        layout = { w, h ->
//            val lp = layoutParams
//            lp.width = w
//            lp.height = h
//            layoutParams = lp
//            requestLayout()
//        },
    )

    fun setEmoji(
        url: String?,
        initialAspect: Float?,
        defaultHeight: Int,
    ) {
        this.url = url
        mPaint.isFilterBitmap = true
        invalidate()
        emojiImageRect.updateRect(
            url = url ?: "",
            aspectArg = initialAspect,
            h = defaultHeight.toFloat(),
        )
        val lp = layoutParams
        lp.width = (emojiImageRect.emojiWidth + 0.5f).toInt()
        lp.height = (emojiImageRect.emojiHeight + 0.5f).toInt()
        layoutParams = lp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawImage(canvas)) return
        drawError(canvas)
    }

    private fun drawImage(canvas: Canvas): Boolean {
        val url = this.url
        if (url == null || url.isBlank()) return false

        // APNGデータの取得
        val frames = App1.custom_emoji_cache.getFrames(tagDrawTarget, url) {
            postInvalidateOnAnimation()
        } ?: return false

        val now = SystemClock.elapsedRealtime()

        // アニメーション開始時刻を計算する
        if (tStart == 0L || now - tLastDraw >= 60000L) {
            tStart = now
        }
        tLastDraw = now

        // アニメーション開始時刻からの経過時間に応じたフレームを探索
        frames.findFrame(mFrameFindResult, now - tStart)

        val b = mFrameFindResult.bitmap
        if (b == null || b.isRecycled) return false

        val srcWidth = b.width.toFloat()
        val srcHeight = b.height.toFloat()
        if (srcWidth < 1f || srcHeight < 1f) return false
        rectSrc.set(0, 0, b.width, b.height)

        val srcAspect = srcWidth / srcHeight

        val dstWidth = this.width.toFloat()
        val dstHeight = this.height.toFloat()
        if (dstWidth < 1f || dstHeight < 1f) return false

        emojiImageRect.updateRect(
            url = url,
            aspectArg = srcAspect,
            h = dstHeight,
        )
        val width = (emojiImageRect.emojiWidth + 0.5f).toInt()
        if (width != layoutParams.width) {
            val lp = layoutParams
            lp.width = width
            lp.height = (emojiImageRect.emojiHeight + 0.5f).toInt()
            layoutParams = lp
        }

        canvas.drawBitmap(b, rectSrc, emojiImageRect.rectDst, mPaint)

        // 少し後に描画しなおす
        val delay = mFrameFindResult.delay
        if (delay != Long.MAX_VALUE) {
            postInvalidateDelayed(delay)
        }
        return true
    }

    private fun drawError(canvas: Canvas) {
        val drawable = errorDrawableCache
            ?: ContextCompat.getDrawable(lazyContext, errorDrawableId)
                ?.also { errorDrawableCache = it }

        drawable ?: return
        val srcWidth = drawable.intrinsicWidth.toFloat()
        val srcHeight = drawable.intrinsicHeight.toFloat()
        if (srcWidth < 1f || srcHeight < 1f) return

        val dstWidth = this.width.toFloat()
        val dstHeight = this.height.toFloat()
        if (dstWidth < 1f || dstHeight < 1f) return

        emojiImageRect.updateRect(
            url = "",
            aspectArg = srcWidth / srcHeight,
            h = dstHeight,
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
            NetworkEmojiSpan.log.w(ex, "drawBitmap failed.")

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
