package jp.juggler.apng

import android.graphics.*
import android.util.Log
import jp.juggler.util.data.encodeUTF8
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

class ApngFrames private constructor(
    private val pixelSizeMax: Float = 0f,
    private val debug: Boolean = false,
) : ApngDecoderCallback, MyGifDecoderCallback {

    companion object {

        private const val TAG = "ApngFrames"

        // ループしない画像の場合は3秒でまたループさせる
        private const val DELAY_AFTER_END = 3000L

        // アニメーションフレームの描画に使う
        private val sPaintDontBlend = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            isFilterBitmap = true
        }
        private val sPaintClear = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = 0
        }

        // return w,h
        fun scaleEmojiSize(
            srcW: Float,
            srcH: Float,
            maxSize: Float,
        ): Pair<Float, Float> = when {
            // 入力サイズの情報がない
            srcW <= 0f || srcH <= 0f -> Pair(maxSize, maxSize)
            else -> {
                val sqMax = maxSize * maxSize
                val sqOriginal = srcW * srcH
                val aspect = srcW / srcH
                when {
                    // 既に十分小さい
                    sqOriginal <= sqMax -> Pair(srcW, srcH)
                    // アスペクト比に応じたスケーリング
                    else -> Pair(
                        sqrt(sqMax * aspect),
                        sqrt(sqMax / aspect),
                    )
                }
            }
        }

        private fun createBlankBitmap(w: Int, h: Int) =
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        private fun scaleBitmap(
            sizeMax: Float,
            src: Bitmap,
            recycleSrc: Boolean = true, // true: ownership of "src" will be moved or recycled.
        ): Bitmap {
            if (sizeMax <= 0) {
                return when {
                    recycleSrc -> src
                    else -> src.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            val wSrc = src.width
            val hSrc = src.height
            val (wDst, hDst) = scaleEmojiSize(
                wSrc.toFloat(),
                hSrc.toFloat(),
                sizeMax,
            )
            val wDstInt = max(1, round(wDst).toInt())
            val hDstInt = max(1, round(hDst).toInt())
            if (wSrc <= wDstInt && hSrc <= hDstInt) {
                return when {
                    recycleSrc -> src
                    else -> src.copy(Bitmap.Config.ARGB_8888, false)
                }
            }

            val b2 = createBlankBitmap(wDstInt, hDstInt)
            val canvas = Canvas(b2)
            val rectSrc = Rect(0, 0, wSrc, hSrc)
            val rectDst = Rect(0, 0, wDstInt, hDstInt)
            canvas.drawBitmap(src, rectSrc, rectDst, sPaintDontBlend)

            if (recycleSrc) src.recycle()

            return b2
        }

        private fun toAndroidBitmap(src: ApngBitmap) =
            Bitmap.createBitmap(
                src.colors, // int[] 配列
                0, // offset
                src.width, //stride
                src.width, // width
                src.height, //height
                Bitmap.Config.ARGB_8888
            )

        private fun toAndroidBitmap(src: ApngBitmap, sizeMax: Float) =
            scaleBitmap(sizeMax, toAndroidBitmap(src))

        private fun parseApng(
            inStream: InputStream,
            pixelSizeMax: Float,
            debug: Boolean = false,
        ): ApngFrames {
            val result = ApngFrames(pixelSizeMax, debug)
            try {
                ApngDecoder.parseStream(inStream, result)
                result.onParseComplete()
                return result.takeIf { result.defaultImage != null || result.frames?.isNotEmpty() == true }
                    ?: error("APNG has no image")
            } catch (ex: Throwable) {
                result.dispose()
                throw ex
            }
        }

        private fun parseWebP(
            inStream: InputStream,
            pixelSizeMax: Float,
            debug: Boolean = false,
        ): ApngFrames {
            val result = ApngFrames(pixelSizeMax, debug)
            try {
                MyWebPDecoder(result).parse(inStream)
                result.onParseComplete()
                return result.takeIf { result.defaultImage != null || result.frames?.isNotEmpty() == true }
                    ?: error("WebP has no image")
            } catch (ex: Throwable) {
                result.dispose()
                throw ex
            }
        }

        private fun parseGif(
            inStream: InputStream,
            pixelSizeMax: Float,
            debug: Boolean = false,
        ): ApngFrames {
            val result = ApngFrames(pixelSizeMax, debug)
            try {
                MyGifDecoder(result).parse(inStream)
                result.onParseComplete()
                return result.takeIf { result.defaultImage != null || result.frames?.isNotEmpty() == true }
                    ?: error("GIF has no image")
            } catch (ex: Throwable) {
                result.dispose()
                throw ex
            }
        }

        private val apngHeadKey = byteArrayOf(0x89.toByte(), 0x50)
        private val webpHeadKey1 = "RIFF".encodeUTF8()
        private val webpHeadKey2 = "WEBP".encodeUTF8()
        private val gifHeadKey = "GIF".toByteArray(Charsets.UTF_8)

        private fun matchBytes(
            ba1: ByteArray,
            ba2: ByteArray,
            length: Int = min(ba1.size, ba2.size),
        ): Boolean {
            for (i in 0 until length) {
                if (ba1[i] != ba2[i]) return false
            }
            return true
        }

        private fun matchBytesOffset(
            ba1: ByteArray,
            ba1Offset: Int,
            ba2: ByteArray,
            length: Int = ba2.size,
        ): Boolean {
            for (i in 0 until length) {
                if (ba1[i + ba1Offset] != ba2[i]) return false
            }
            return true
        }

        fun parse(
            pixelSizeMax: Float,
            debug: Boolean = false,
            opener: () -> InputStream?,
        ): ApngFrames? {

            val buf = ByteArray(12) { 0.toByte() }
            opener()?.use { it.read(buf, 0, buf.size) }

            if (buf.size >= 8 && matchBytes(buf, apngHeadKey)) {
                return opener()?.use { parseApng(it, pixelSizeMax, debug) }
            }
            if (buf.size >= 12 &&
                matchBytesOffset(buf, 0, webpHeadKey1) &&
                matchBytesOffset(buf, 8, webpHeadKey2)
            ) {
                return opener()?.use { parseWebP(it, pixelSizeMax, debug) }
            }
            if (buf.size >= 6 && matchBytes(buf, gifHeadKey)) {
                return opener()?.use { parseGif(it, pixelSizeMax, debug) }
            }

            return null
        }
    }

    private var header: ApngImageHeader? = null
    private var animationControl: ApngAnimationControl? = null

    // width,height (after resized)
    var width: Int = 1
        private set

    var height: Int = 1
        private set

    class Frame(
        val bitmap: Bitmap,
        val timeStart: Long,
        val timeWidth: Long,
    )

    var frames: ArrayList<Frame>? = null

    @Suppress("MemberVisibilityCanBePrivate")
    val numFrames: Int
        get() = frames?.size ?: 1

    @Suppress("unused")
    val hasMultipleFrame: Boolean
        get() = numFrames > 1

    private var timeTotal = 0L

    private lateinit var canvas: Canvas

    private var canvasBitmap: Bitmap? = null

    // 再生速度の調整
    private var durationScale = 1f

    // APNGじゃなかった場合に使われる
    private var defaultImage: Bitmap? = null
        set(value) {
            field = value
            if (value != null) {
                width = value.width
                height = value.height
            }
        }

    val aspect: Float?
        get() = if (width <= 0 || height <= 0) null else width.toFloat().div(height)

    constructor(bitmap: Bitmap) : this() {
        defaultImage = bitmap
    }

    private fun onParseComplete() {
        canvasBitmap?.recycle()
        canvasBitmap = null

        val frames = this.frames
        if (frames != null) {
            if (frames.size > 1) {
                defaultImage?.recycle()
                defaultImage = null
            } else if (frames.size == 1) {
                defaultImage?.recycle()
                defaultImage = frames.first().bitmap
                frames.clear()
            }
        }
    }

    fun dispose() {
        canvasBitmap?.recycle()
        defaultImage?.recycle()
        frames?.forEach { it.bitmap.recycle() }
    }

    class FindFrameResult {
        var bitmap: Bitmap? = null // may null
        var delay: Long = 0 // 再描画が必要ない場合は Long.MAX_VALUE
    }

    // シーク位置に応じたコマ画像と次のコマまでの残り時間をresultに格納する
    @Suppress("unused")
    fun findFrame(result: FindFrameResult, t: Long) {

        if (defaultImage != null) {
            result.bitmap = defaultImage
            result.delay = Long.MAX_VALUE
            return
        }

        val animationControl = this.animationControl
        val frames = this.frames

        if (animationControl == null || frames.isNullOrEmpty()) {
            // ここは通らないはず…
            result.bitmap = null
            result.delay = Long.MAX_VALUE
            return
        }

        val frameCount = frames.size

        val isFinite = animationControl.isFinite
        val repeatSequenceCount = if (isFinite) animationControl.numPlays else 1
        val endWait = if (isFinite) DELAY_AFTER_END else 0L
        val timeTotalLoop = max(1, timeTotal * repeatSequenceCount + endWait)

        val tf = (max(0, t) / durationScale).toLong()

        // 全体の繰り返し時刻で余りを計算
        val tl = tf % timeTotalLoop

        if (tl >= timeTotalLoop - endWait) {
            // 終端で待機状態
            result.bitmap = frames[frameCount - 1].bitmap
            result.delay = (0.5f + (timeTotalLoop - tl) * durationScale).toLong()
            return
        }

        // １ループの繰り返し時刻で余りを計算
        val tt = tl % timeTotal

        // フレームリストを時刻で二分探索
        var s = 0
        var e = frameCount
        while (e - s > 1) {
            val mid = s + e shr 1
            val frame = frames[mid]
            // log.d("s=%d,m=%d,e=%d tt=%d,fs=%s,fe=%d",s,mid,e,tt,frame.timeStart,frame.timeStart+frame.timeWidth );
            if (tt < frame.timeStart) {
                e = mid
            } else if (tt >= frame.timeStart + frame.timeWidth) {
                s = mid + 1
            } else {
                s = mid
                break
            }
        }
        s = if (s < 0) 0 else if (s >= frameCount - 1) frameCount - 1 else s
        val frame = frames[s]
        val delay = frame.timeStart + frame.timeWidth - tt
        result.bitmap = frames[s].bitmap
        result.delay = (0.5f + durationScale * max(0f, delay.toFloat())).toLong()

        // log.d("findFrame tf=%d,tl=%d/%d,tt=%d/%d,s=%d,w=%d,delay=%d",tf,tl,loop_total,tt,timeTotal,s,frame.timeWidth,result.delay);
    }

    /////////////////////////////////////////////////////
    // implements ApngDecoderCallback

    override fun onApngWarning(message: String) {
        Log.w(TAG, message)
    }

    override fun onApngDebug(message: String) {
        Log.d(TAG, message)
    }

    override fun canApngDebug(): Boolean = debug

    override fun onHeader(apng: Apng, header: ApngImageHeader) {
        this.header = header
    }

    override fun onAnimationInfo(
        apng: Apng,
        header: ApngImageHeader,
        animationControl: ApngAnimationControl,
    ) {
        if (debug) {
            Log.d(TAG, "onAnimationInfo")
        }
        this.animationControl = animationControl
        this.frames = ArrayList(animationControl.numFrames)

        val canvasBitmap = createBlankBitmap(header.width, header.height)
        this.canvasBitmap = canvasBitmap
        this.canvas = Canvas(canvasBitmap)
    }

    override fun onDefaultImage(apng: Apng, bitmap: ApngBitmap) {
        if (debug) {
            Log.d(TAG, "onDefaultImage")
        }
        defaultImage?.recycle()
        defaultImage = toAndroidBitmap(bitmap, pixelSizeMax)
    }

    override fun onAnimationFrame(
        apng: Apng,
        frameControl: ApngFrameControl,
        frameBitmap: ApngBitmap,
    ) {
        if (debug) {
            Log.d(
                TAG,
                "onAnimationFrame seq=${frameControl.sequenceNumber}, xywh=${frameControl.xOffset},${frameControl.yOffset},${frameControl.width},${frameControl.height} blendOp=${frameControl.blendOp}, disposeOp=${frameControl.disposeOp},delay=${frameControl.delayMilliseconds}"
            )
        }
        val frames = this.frames ?: return
        val canvasBitmap = this.canvasBitmap ?: return

        val disposeOp = when {

            // If the first `fcTL` chunk uses a `dispose_op` of APNG_DISPOSE_OP_PREVIOUS it should be treated as APNG_DISPOSE_OP_BACKGROUND.
            frameControl.disposeOp == DisposeOp.Previous && frames.isEmpty() -> DisposeOp.Background

            else -> frameControl.disposeOp
        }

        val previous: Bitmap? = when (disposeOp) {
            DisposeOp.Previous -> Bitmap.createBitmap(
                canvasBitmap,
                frameControl.xOffset,
                frameControl.yOffset,
                frameControl.width,
                frameControl.height
            )

            else -> null
        }

        try {

            val frameBitmapAndroid = toAndroidBitmap(frameBitmap)

            try {

                canvas.drawBitmap(
                    frameBitmapAndroid,
                    frameControl.xOffset.toFloat(),
                    frameControl.yOffset.toFloat(),
                    when (frameControl.blendOp) {
                        // all color components of the frame, including alpha,
                        // overwrite the current contents of the frame's output buffer region.
                        BlendOp.Source -> sPaintDontBlend
                        // the frame should be composited onto the output buffer based on its alpha,
                        // using a simple OVER operation as described in the "Alpha Channel Processing" section of the PNG specification [PNG-1.2].
                        BlendOp.Over -> null
                    }
                )

                val frame = Frame(
                    bitmap = scaleBitmap(pixelSizeMax, canvasBitmap, recycleSrc = false),
                    timeStart = timeTotal,
                    timeWidth = max(1L, frameControl.delayMilliseconds)
                )
                frames.add(frame)
                timeTotal += frame.timeWidth

                when (disposeOp) {

                    // no disposal is done on this frame before rendering the next;
                    // the contents of the output buffer are left as is.
                    DisposeOp.None -> {
                    }

                    // the frame's region of the output buffer is
                    // to be cleared to fully transparent black
                    // before rendering the next frame.
                    DisposeOp.Background -> {
                        val rect = Rect()
                        rect.left = frameControl.xOffset
                        rect.top = frameControl.yOffset
                        rect.right = frameControl.xOffset + frameControl.width
                        rect.bottom = frameControl.yOffset + frameControl.height
                        canvas.drawRect(rect, sPaintClear)
                        //	canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                    }

                    // the frame's region of the output buffer is
                    // to be reverted to the previous contents
                    // before rendering the next frame.
                    DisposeOp.Previous -> if (previous != null) {
                        canvas.drawBitmap(
                            previous,
                            frameControl.xOffset.toFloat(),
                            frameControl.yOffset.toFloat(),
                            sPaintDontBlend
                        )
                    }
                }
            } finally {
                frameBitmapAndroid.recycle()
            }
        } finally {
            previous?.recycle()
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Gif support

    override fun onGifWarning(message: String) {
        Log.w(TAG, message)
    }

    override fun onGifDebug(message: String) {
        Log.d(TAG, message)
    }

    override fun canGifDebug(): Boolean = debug

    override fun onGifHeader(header: ApngImageHeader) {
        this.header = header
    }

    override fun onGifAnimationInfo(
        header: ApngImageHeader,
        animationControl: ApngAnimationControl,
    ) {
        if (debug) {
            Log.d(TAG, "onAnimationInfo")
        }
        this.animationControl = animationControl
        this.frames = ArrayList(animationControl.numFrames)
        val canvasBitmap = createBlankBitmap(header.width, header.height)
        this.canvasBitmap = canvasBitmap
        this.canvas = Canvas(canvasBitmap)
    }

    override fun onGifAnimationFrame(
        frameControl: ApngFrameControl,
        frameBitmap: ApngBitmap,
    ) {
        if (debug) {
            Log.d(
                TAG,
                "onAnimationFrame seq=${frameControl.sequenceNumber}, xywh=${frameControl.xOffset},${frameControl.yOffset},${frameControl.width},${frameControl.height} blendOp=${frameControl.blendOp}, disposeOp=${frameControl.disposeOp},delay=${frameControl.delayMilliseconds}"
            )
        }
        val frames = this.frames ?: return

        if (frames.isEmpty()) {
            defaultImage?.recycle()
            defaultImage = toAndroidBitmap(frameBitmap, pixelSizeMax)
            // ここでwidth,heightがセットされる
        }

        val frameBitmapAndroid = toAndroidBitmap(frameBitmap)
        try {
            val frame = Frame(
                bitmap = scaleBitmap(pixelSizeMax, frameBitmapAndroid, recycleSrc = false),
                timeStart = timeTotal,
                timeWidth = max(1L, frameControl.delayMilliseconds)
            )
            frames.add(frame)
            timeTotal += frame.timeWidth
        } finally {
            frameBitmapAndroid.recycle()
        }
    }
}
