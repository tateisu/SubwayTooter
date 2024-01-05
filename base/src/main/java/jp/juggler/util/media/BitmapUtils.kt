package jp.juggler.util.media

//import it.sephiroth.android.library.exif2.ExifInterface
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.net.Uri
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val log = LogCategory("BitmapUtils")

fun InputStream.imageOrientation(): Int? =
    try {
        ExifInterface(this)
            //			.readExif(
            //				this@imageOrientation,
            //				ExifInterface.Options.OPTION_IFD_0
            //					or ExifInterface.Options.OPTION_IFD_1
            //					or ExifInterface.Options.OPTION_IFD_EXIF
            //			)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
            .takeIf { it >= 0 }
    } catch (ex: Throwable) {
        log.w(ex, "imageOrientation: exif parse failed.")
        null
    }

// 回転情報の値に合わせて、wとhを入れ替える
fun rotateSize(orientation: Int?, w: Float, h: Float): PointF =
    when (orientation) {
        5, 6, 7, 8 -> PointF(h, w)
        else -> PointF(w, h)
    }

// 回転情報を解決するようにmatrixに回転を加える
fun Matrix.resolveOrientation(orientation: Int?): Matrix {
    when (orientation) {
        2 -> postScale(1f, -1f)
        3 -> postRotate(180f)
        4 -> postScale(-1f, 1f)

        5 -> {
            postScale(1f, -1f)
            postRotate(-90f)
        }

        6 -> postRotate(90f)

        7 -> {
            postScale(1f, -1f)
            postRotate(90f)
        }

        8 -> postRotate(-90f)
    }
    return this
}

enum class ResizeType {
    // リサイズなし
    None,

    // 長辺がsize以下になるようリサイズ
    LongSide,

    // 平方ピクセルが size*size 以下になるようリサイズ
    SquarePixel,
}

class ResizeConfig(
    val type: ResizeType,
    val size: Int,
    @StringRes val extraStringId: Int = 0,
) {
    val spec: String
        get() = when (type) {
            ResizeType.None -> type.toString()
            else -> "$type,$size"
        }

    override fun toString() = "ResizeConfig($spec)"
}

private fun PointF.limitBySqPixel(aspect: Float, maxSqPixels: Float): PointF {
    val currentSqPixels = x * y
    return when {
        maxSqPixels <= 0 -> this
        currentSqPixels <= maxSqPixels -> this
        else -> {
            val y = sqrt(maxSqPixels / aspect)
            val x = aspect * y
            PointF(x, y)
        }
    }
}

fun createResizedBitmap(
    context: Context,
    uri: Uri,
    sizeLongSide: Int,
    serverMaxSqPixel: Int? = null,
    skipIfNoNeedToResizeAndRotate: Boolean = false,
) = createResizedBitmap(
    context,
    uri,
    when {
        sizeLongSide <= 0 -> ResizeConfig(ResizeType.None, 0)
        else -> ResizeConfig(ResizeType.LongSide, sizeLongSide)
    },
    serverMaxSqPixel = serverMaxSqPixel,
    canSkip = skipIfNoNeedToResizeAndRotate
)

fun Uri.bitmapMimeType(contentResolver: ContentResolver): String? =
    try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        options.inScaled = false
        contentResolver.openInputStream(this)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.outMimeType?.notEmpty()
    } catch (ex: Throwable) {
        log.w(ex, "bitmapMimeType: can't check bitmap mime type.")
        null
    }

fun createResizedBitmap(
    context: Context,

    // contentResolver.openInputStream に渡すUri
    uri: Uri,

    // リサイズ指定
    resizeConfig: ResizeConfig,

    // サーバ側の最大平方ピクセル
    serverMaxSqPixel: Int? = null,

    // 真の場合、リサイズも回転も必要ないならnullを返す
    canSkip: Boolean = false,
): Bitmap? {
    try {
        val orientation: Int? = context.contentResolver.openInputStream(uri)?.use {
            it.imageOrientation()
        }

        // 画像のサイズを調べる
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        options.inScaled = false
        options.outWidth = 0
        options.outHeight = 0
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var srcWidth = options.outWidth
        var srcHeight = options.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            context.showToast(false, "could not get image bounds.")
            return null
        }

        // 回転後のサイズ
        val srcSize = rotateSize(orientation, srcWidth.toFloat(), srcHeight.toFloat())
        val aspect = srcSize.x / srcSize.y

        /// 出力サイズの計算
        val sizeSpec = resizeConfig.size.toFloat()
        var dstSize: PointF = when (resizeConfig.type) {
            ResizeType.None -> srcSize

            ResizeType.SquarePixel ->
                srcSize.limitBySqPixel(aspect, sizeSpec * sizeSpec)

            ResizeType.LongSide -> when {
                max(srcSize.x, srcSize.y) <= resizeConfig.size -> srcSize
                aspect >= 1f -> PointF(sizeSpec, sizeSpec / aspect)
                else -> PointF(sizeSpec * aspect, sizeSpec)
            }
        }

        if (serverMaxSqPixel != null && serverMaxSqPixel > 0) {
            dstSize = dstSize.limitBySqPixel(aspect, serverMaxSqPixel.toFloat())
        }

        var dstSizeInt = Point(
            max(1, (dstSize.x + 0.5f).toInt()),
            max(1, (dstSize.y + 0.5f).toInt())
        )

        val resizeRequired = dstSizeInt.x != srcSize.x.toInt() || dstSizeInt.y != srcSize.y.toInt()

        log.i(
            "createResizedBitmap: rc=${
                resizeConfig
            }, src=${
                srcSize
            }, dst=${
                dstSizeInt
            }, ori=${
                orientation
            }, resizeRequired=${
                resizeRequired
            }"
        )

        // リサイズも回転も必要がない場合
        if (canSkip &&
            !resizeRequired &&
            (orientation == null || orientation == 1)
        ) {
            log.w("createResizedBitmap: no need to resize or rotate.")
            return null
        }

        // リサイズする場合、ビットマップサイズ上限の成約がある
        if (max(dstSizeInt.x, dstSizeInt.y) > 4096) {
            val scale = 4096f / max(dstSizeInt.x, dstSizeInt.y).toFloat()
            dstSize = PointF(
                min(4096f, dstSize.x * scale),
                min(4096f, dstSize.y * scale),
            )
            dstSizeInt = Point(
                max(1, (dstSize.x + 0.5f).toInt()),
                max(1, (dstSize.y + 0.5f).toInt())
            )
        }

        // 長辺
        val dstMax = min(4096, max(dstSize.x, dstSize.y).toInt())

        // inSampleSizeを計算
        var bits = 0
        var n = max(srcSize.x, srcSize.y).toInt()
        while (n > 4096 || (n > 512 && n > dstMax * 2)) {
            ++bits
            n = n shr 1
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1 shl bits

        val sourceBitmap: Bitmap? =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

        if (sourceBitmap == null) {
            context.showToast(false, "could not decode image.")
            return null
        }
        try {
            // サンプル数が変化している
            srcWidth = options.outWidth
            srcHeight = options.outHeight

            val matrix = Matrix().apply {
                reset()

                // 画像の中心が原点に来るようにして
                postTranslate(srcWidth * -0.5f, srcHeight * -0.5f)

                // スケーリング
                val scale = dstMax.toFloat() / max(srcWidth, srcHeight)
                postScale(scale, scale)

                // 回転情報があれば回転
                resolveOrientation(orientation)

                // 表示領域に埋まるように平行移動
                postTranslate(dstSizeInt.x.toFloat() * 0.5f, dstSizeInt.y.toFloat() * 0.5f)
            }

            // 出力用Bitmap作成
            val dst = Bitmap.createBitmap(dstSizeInt.x, dstSizeInt.y, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(dst)
                val paint = Paint()
                paint.isFilterBitmap = true
                canvas.drawBitmap(sourceBitmap, matrix, paint)
                log.d("createResizedBitmap: resized to ${dstSizeInt.x}x${dstSizeInt.y}")
                return dst
            } catch (ex: Throwable) {
                dst.recycle()
                throw ex
            }
        } finally {
            sourceBitmap.recycle()
        }
    } catch (ex: FileNotFoundException) {
        log.w(ex, "not found. $uri")
    } catch (ex: SecurityException) {
        log.w(ex, "maybe we need pick up image again.")
    } catch (ex: Throwable) {
        log.e(ex, "createResizedBitmap failed.")
    }
    return null
}
