package jp.juggler.apng

import android.graphics.Bitmap
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.integration.webp.WebpImage
import com.bumptech.glide.integration.webp.decoder.WebpDecoder
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max

class MyWebPDecoder(val callback: MyGifDecoderCallback) {

    private val bitmapProvider = object : GifDecoder.BitmapProvider {
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)

        override fun release(bitmap: Bitmap) {
            bitmap.recycle()
        }

        override fun release(bytes: ByteArray) {
        }

        override fun release(array: IntArray) {
        }
    }

    fun parse(src: InputStream) {
        val decoder = WebpDecoder(
            bitmapProvider,
            WebpImage.create(src.readBytes()) ?: error("WebpImage.create returns null."),
            ByteBuffer.allocate(0),
            1 // sampleSize。元データのサイズをこの数字で割る。
        )
        try {
            val frameCount = decoder.frameCount
            if (frameCount < 1) error("webp has no frames.")

            if (decoder.width < 1 || decoder.height < 1) error("too small size.")
            // logical screen size

            // list of pair of ApngFrameControl, ApngBitmap
            repeat(frameCount) { frameNumber ->
                decoder.advance()
                val frameDelay = decoder.nextDelay
                val bitmap = decoder.nextFrame!!
                try {
                    val srcWidth = bitmap.width
                    val srcHeight = bitmap.height
                    if (frameNumber == 0) {
                        val header = ApngImageHeader(
                            width = srcWidth,
                            height = srcHeight,
                            bitDepth = 8,
                            colorType = ColorType.INDEX,
                            compressionMethod = CompressionMethod.Standard,
                            filterMethod = FilterMethod.Standard,
                            interlaceMethod = InterlaceMethod.None
                        )
                        val animationControl = ApngAnimationControl(
                            numFrames = frameCount,
                            numPlays = max(0, decoder.netscapeLoopCount) // 0 means infinite
                        )

                        callback.onGifHeader(header)
                        callback.onGifAnimationInfo(header, animationControl)
                    }
                    callback.onGifAnimationFrame(
                        ApngFrameControl(
                            width = srcWidth,
                            height = srcHeight,
                            xOffset = 0,
                            yOffset = 0,
                            disposeOp = DisposeOp.None,
                            blendOp = BlendOp.Source,
                            sequenceNumber = frameNumber,
                            delayMilliseconds = frameDelay.toLong(),
                        ),
                        ApngBitmap(srcWidth, srcHeight).also { dst ->
                            bitmap.getPixels(dst.colors, 0, dst.width, 0, 0, srcWidth, srcHeight)
                        }
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            decoder.clear()
            // WebPImageはdecoderがdisposeする
        }
    }
}
