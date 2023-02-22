package jp.juggler.subwaytooter.util

import android.graphics.RectF
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.log.LogCategory
import kotlin.math.min

enum class EmojiSizeMode {
    Square,
    Wide,
}

fun SavedAccount?.emojiSizeMode(): EmojiSizeMode {
    val ti = this?.let { TootInstance.getCached(it) }
    return when {
        ti == null -> EmojiSizeMode.Square
        ti.isMisskey || !ti.fedibirdCapabilities.isNullOrEmpty() -> EmojiSizeMode.Wide
        else -> EmojiSizeMode.Square
    }
}

/**
 * カスタム絵文字のSpanやビューの描画領域やサイズを計算する
 */
class EmojiImageRect(
    private val sizeMode: EmojiSizeMode,
    val scale: Float = 1f,
    val scaleRatio: Float = 1f,
    val descentRatio: Float = 0f,
    val maxEmojiWidth: Float,
    val layout: (Int, Int) -> Unit,
) {
    companion object {
        private val log = LogCategory("EmojiImageRect")

        val imageAspectCache = HashMap<String, Float>()
    }

    val rectDst = RectF()
    var emojiWidth = 0f
    var emojiHeight = 0f
    var transY = 0f

    var lastWidth: Float? = null

    /**
     * lastAspect に基づいて rectDst と transY を更新する
     */
    fun updateRect(
        url: String,
        aspectArg: Float? = null,
        textSize: Float,
        baseline: Float,
    ) {
        // テキストサイズをスケーリングした基本高さ
        val h = textSize * scaleRatio * scale

        // ベースラインから上下方向にずらすオフセット
        val cDescent = emojiHeight * descentRatio
        this.transY = baseline - emojiHeight + cDescent
        updateRect(url, aspectArg, h)
    }

    fun updateRect(
        url: String,
        aspectArg: Float? = null,
        h: Float,
    ) {
        this.emojiHeight = h
        val aspect = when (aspectArg) {
            null -> imageAspectCache[url] ?: 1f
            else -> {
                imageAspectCache.put(url, aspectArg)
                aspectArg
            }
        }.takeIf { it > 0f } ?: 1f

        when {
            // 横長画像で、それを許可するモード
            aspect > 1.36f && sizeMode == EmojiSizeMode.Wide -> {
                // 絵文字のアスペクト比から描画範囲の幅と高さを決める
                val dstWidth = min(maxEmojiWidth, aspect * emojiHeight)
                val dstHeight = dstWidth / aspect
                val dstX = 0f
                val dstY = (emojiHeight - dstHeight) / 2f
                rectDst.set(dstX, dstY, dstX + dstWidth, dstY + dstHeight)
                emojiWidth = dstWidth
            }

            else -> {
                emojiWidth = emojiHeight
                // 絵文字のアスペクト比から描画範囲の幅と高さを決める
                val dstWidth: Float
                val dstHeight: Float
                if (aspect >= 1f) {
                    dstWidth = emojiHeight
                    dstHeight = emojiHeight / aspect
                } else {
                    dstHeight = emojiHeight
                    dstWidth = emojiHeight * aspect
                }
                val dstX = (emojiHeight - dstWidth) / 2f
                val dstY = (emojiHeight - dstHeight) / 2f
                rectDst.set(dstX, dstY, dstX + dstWidth, dstY + dstHeight)
            }
        }
        // 出力サイズが変化したならrequestLayout
        val newWidth = emojiWidth
        if (lastWidth != null && lastWidth != newWidth) {
            log.i("updateRect: width changed. $lastWidth → $newWidth")
            layout((emojiWidth + 0.5f).toInt(), (emojiHeight + 0.5f).toInt())
        }
        lastWidth = newWidth
    }
}
