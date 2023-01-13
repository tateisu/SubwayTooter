package jp.juggler.subwaytooter.span

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.os.SystemClock
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import com.caverock.androidsvg.SVG
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.util.log.LogCategory

// 絵文字リソースの種類によって異なるスパンを作る
fun UnicodeEmoji.createSpan(context: Context, scale: Float = 1f) =
    if (isSvg) {
        SvgEmojiSpan(context, assetsName!!, scale = scale)
    } else {
        EmojiImageSpan(context, drawableId, scale = scale)
    }

// SVG絵文字スパン
class SvgEmojiSpan internal constructor(
    context: Context,
    private val assetsName: String,
    private val scale: Float = 1f,
) : ReplacementSpan() {

    companion object {

        internal val log = LogCategory("SvgEmojiSpan")

        private const val scale_ratio = 1.14f
        private const val descent_ratio = 0.211f

        private var assetsManager: AssetManager? = null

        private fun loadSvg(assetsName: String): SVG? =
            when (val assetsManager = assetsManager) {
                null -> null
                else -> try {
                    SVG.getFromAsset(assetsManager, assetsName)
                } catch (ex: Throwable) {
                    log.e(ex, "SVG.getFromAsset failed.")
                    null
                }
            }

        class BitmapCacheKey(var code: String = "", var size: Int = 1) :
            Comparable<BitmapCacheKey> {

            override fun hashCode(): Int {
                return code.hashCode() xor size
            }

            override fun compareTo(other: BitmapCacheKey): Int {
                val i = code.compareTo(other.code)
                if (i != 0) return i
                return size.compareTo(other.size)
            }

            override fun equals(other: Any?): Boolean =
                if (other is BitmapCacheKey) {
                    compareTo(other) == 0
                } else {
                    false
                }
            // don't use "when(other){ this -> …", it recursive call "equals()"
        }

        class BitmapCacheValue(val bitmap: Bitmap?, var lastUsed: Long)

        private val bitmapCache = HashMap<BitmapCacheKey, BitmapCacheValue>()

        // 時々キャッシュを掃除する
        private const val sweepInterval = 30000L
        private const val sweepExpire = 10000L
        private const val sweepLimit1 = 64 // この個数を超えたら
        private const val sweepLimit2 = 32 // この個数まで減らす
        private var lastSweepTime = 0L

        private fun sweepCache(now: Long) {
            val cacheSize = bitmapCache.size
            if (now - lastSweepTime >= sweepInterval && cacheSize >= sweepLimit1) {
                lastSweepTime = now
                val list = bitmapCache.entries.sortedBy { it.value.lastUsed }
                // 最低保持数より多い分が検査対象
                var mayRemove = cacheSize - sweepLimit2
                val it = list.iterator()
                while (it.hasNext() && mayRemove > 0) {
                    val item = it.next()
                    // 最近作られたキャッシュは破棄しない
                    if (now - item.value.lastUsed <= sweepExpire) break
                    item.value.bitmap?.recycle()
                    bitmapCache.remove(item.key)
                    --mayRemove
                }
                log.d("sweep. cache size $cacheSize=>${bitmapCache.size}")
            }
        }

        private val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        private val rect_dst = RectF()

        private fun renderBitmap(bitmap: Bitmap, svg: SVG, dstSize: Float) {
            try {
                // the width in pixels, or -1 if there is no width available.
                // the height in pixels, or -1 if there is no height available.
                val src_w = svg.documentWidth
                val src_h = svg.documentHeight
                val srcAspect = if (src_w <= 0f || src_h <= 0f) {
                    // widthやheightの情報がない
                    1f
                } else {
                    src_w / src_h
                }

                // 絵文字のアスペクト比から描画範囲の幅と高さを決める
                val dstWidth: Float
                val dstHeight: Float
                if (srcAspect >= 1f) {
                    dstWidth = dstSize
                    dstHeight = dstSize / srcAspect
                } else {
                    dstHeight = dstSize
                    dstWidth = dstSize * srcAspect
                }
                val dstX = (dstSize - dstWidth) / 2f
                val dstY = (dstSize - dstHeight) / 2f
                rect_dst.set(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

                bitmap.eraseColor(Color.TRANSPARENT)
                svg.renderToCanvas(Canvas(bitmap), rect_dst)
            } catch (ex: Throwable) {
                log.e(ex, "rendering failed.!")
            }
        }

        private val bitmapCacheKeyForSearch = BitmapCacheKey()

        private fun prepareBitmap(assetsName: String, dstSize: Float): Bitmap? {

            val dstSizeInt = (dstSize + 0.995f).toInt()
            synchronized(bitmapCache) {
                val now = SystemClock.elapsedRealtime()

                // check bitmap cache
                bitmapCacheKeyForSearch.code = assetsName
                bitmapCacheKeyForSearch.size = dstSizeInt
                val cached = bitmapCache[bitmapCacheKeyForSearch]
                if (cached != null) {
                    val bitmap = cached.bitmap
                    if (bitmap != null) {
                        cached.lastUsed = now
                        return bitmap
                    } else if (now - cached.lastUsed < sweepExpire) {
                        return null
                        // if recently created, just return error cache
                        // don't update lastUsed.
                    }
                    // fall: retry error cache
                }

                sweepCache(now)

                val bitmap: Bitmap? = when (val svg = loadSvg(assetsName)) {
                    null -> null

                    else -> try {
                        Bitmap.createBitmap(dstSizeInt, dstSizeInt, Bitmap.Config.ARGB_8888)
                            ?.also { renderBitmap(it, svg, dstSize) }
                    } catch (ex: Throwable) {
                        log.e(ex, "bitmap allocation failed.")
                        null
                    }
                }

                // create cache even if bitmap is null.
                bitmapCache[BitmapCacheKey(assetsName, dstSizeInt)] = BitmapCacheValue(bitmap, now)

                return bitmap
            }
        }
    }

    init {
        if (assetsManager == null) {
            assetsManager = context.applicationContext.assets
        }
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val size = (paint.textSize * scale_ratio * scale + 0.5f).toInt()
        if (fm != null) {
            val cDescent = (0.5f + size * descent_ratio).toInt()
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
        // 絵文字の正方形のサイズ
        val dstSize = textPaint.textSize * scale_ratio * scale
        val bitmap = prepareBitmap(assetsName, dstSize)

        if (bitmap != null) {
            val y = baseline - dstSize + dstSize * descent_ratio
            rect_dst.set(x, y, x + bitmap.width, y + bitmap.height)
            canvas.drawBitmap(bitmap, null, rect_dst, paint)
        }
    }
}
