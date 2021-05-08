@file:Suppress("JoinDeclarationAndAssignment")

package jp.juggler.apng

import jp.juggler.apng.util.*
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.min

internal class IdatDecoder(
    apng: Apng,
    private val bitmap: ApngBitmap,
    private val inflateBufferPool: BufferPool,
    private val callback: ApngDecoderCallback,
    private val onCompleted: () -> Unit
) {

    private class PassInfo(val xStep: Int, val xStart: Int, val yStep: Int, val yStart: Int)

    companion object {

        private val passInfoList = listOf(
            PassInfo(8, 0, 8, 0), // [0] Adam7 pass 1
            PassInfo(8, 4, 8, 0), // [1] Adam7 pass 2
            PassInfo(4, 0, 8, 4), // [2] Adam7 pass 3
            PassInfo(4, 2, 4, 0), // [3] Adam7 pass 4
            PassInfo(2, 0, 4, 2), // [4] Adam7 pass 5
            PassInfo(2, 1, 2, 0), // [5] Adam7 pass 6
            PassInfo(1, 0, 2, 1), // [6] Adam7 pass 7
            PassInfo(1, 0, 1, 0) // [7] no interlacing
        )

        private val dummyPaletteData = IntArray(0)

        // a = left, b = above, c = upper left
        private fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = abs(p - a)
            val pb = abs(p - b)
            val pc = abs(p - c)
            return when {
                (pa <= pb && pa <= pc) -> a
                (pb <= pc) -> b
                else -> c
            }
        }

        private inline fun scanLine1(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var remain = pass_w
            while (remain >= 8) {
                remain -= 8
                val v = baLine[pos++].toInt()
                block((v shr 7) and 1)
                block((v shr 6) and 1)
                block((v shr 5) and 1)
                block((v shr 4) and 1)
                block((v shr 3) and 1)
                block((v shr 2) and 1)
                block((v shr 1) and 1)
                block(v and 1)
            }
            if (remain > 0) {
                val v = baLine[pos].toInt()
                block((v shr 7) and 1)
                if (remain > 1) block((v shr 6) and 1)
                if (remain > 2) block((v shr 5) and 1)
                if (remain > 3) block((v shr 4) and 1)
                if (remain > 4) block((v shr 3) and 1)
                if (remain > 5) block((v shr 2) and 1)
                if (remain > 6) block((v shr 1) and 1)
            }
        }

        private inline fun scanLine2(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var remain = pass_w
            while (remain >= 4) {
                remain -= 4
                val v = baLine[pos++].toInt()
                block((v shr 6) and 3)
                block((v shr 4) and 3)
                block((v shr 2) and 3)
                block(v and 3)
            }
            if (remain > 0) {
                val v = baLine[pos].toInt()
                block((v shr 6) and 3)
                if (remain > 1) block((v shr 4) and 3)
                if (remain > 2) block((v shr 2) and 3)
            }
        }

        private inline fun scanLine4(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var remain = pass_w
            while (remain >= 2) {
                remain -= 2
                val v = baLine[pos++].toInt()
                block((v shr 4) and 15)
                block(v and 15)
            }
            if (remain > 0) {
                val v = baLine[pos].toInt()
                block((v shr 4) and 15)
            }
        }

        private inline fun scanLine8(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var remain = pass_w
            while (remain-- > 0) {
                block(baLine.getUInt8(pos++))
            }
        }

    }

    private val inflater = Inflater()
    private val inflateBufferQueue = ByteSequenceQueue { inflateBufferPool.recycle(it.array) }
    private val colorType: ColorType
    private val bitDepth: Int
    private val paletteData: IntArray
    private val sampleBits: Int
    private val sampleBytes: Int
    private val scanLineBytesMax: Int
    private val scanLinePool: BufferPool
    private val transparentCheckerGrey: (v: Int) -> Int
    private val transparentCheckerRGB: (r: Int, g: Int, b: Int) -> Int
    private val renderScanLineFunc: (baLine: ByteArray) -> Unit
    private val bitmapPointer = bitmap.pointer()

    private var pass: Int
    private lateinit var passInfo: PassInfo
    private var passWidth: Int = 0
    private var passHeight: Int = 0
    private var passY: Int = 0
    private var scanLineBytes: Int = 0
    private var baPreviousLine: ByteArray? = null
    private var isCompleted = false

    init {
        val header = requireNotNull(apng.header)
        colorType = header.colorType
        bitDepth = header.bitDepth

        sampleBits = when (colorType) {
            ColorType.GREY, ColorType.INDEX -> bitDepth
            ColorType.GREY_ALPHA -> bitDepth * 2
            ColorType.RGB -> bitDepth * 3
            ColorType.RGBA -> bitDepth * 4
        }

        sampleBytes = (sampleBits + 7) / 8
        scanLineBytesMax = 1 + (bitmap.width * sampleBits + 7) / 8
        scanLinePool = BufferPool(scanLineBytesMax)

        paletteData = if (colorType == ColorType.INDEX) {
            apng.palette?.list
                ?: throw ApngParseError("missing ApngPalette for index color")
        } else {
            dummyPaletteData
        }

        val transparentColor = apng.transparentColor

        transparentCheckerGrey = if (transparentColor != null) {
            { v: Int -> if (transparentColor.match(v)) 0 else 255 }
        } else {
            { 255 }
        }

        transparentCheckerRGB = if (transparentColor != null) {
            { r, g, b -> if (transparentColor.match(r, g, b)) 0 else 255 }
        } else {
            { _, _, _ -> 255 }
        }

        renderScanLineFunc = selectRenderFunc()

        pass = when (header.interlaceMethod) {
            InterlaceMethod.Standard -> 0
            InterlaceMethod.None -> 7
        }

        initializePass()
    }

    private fun renderIndex1(baLine: ByteArray) {
        scanLine1(baLine, passWidth) { v ->
            bitmapPointer.setPixel(paletteData[v]).next()
        }
    }

    private fun renderIndex2(baLine: ByteArray) {
        scanLine2(baLine, passWidth) { v ->
            bitmapPointer.setPixel(paletteData[v]).next()
        }
    }

    private fun renderIndex4(baLine: ByteArray) {
        scanLine4(baLine, passWidth) { v ->
            bitmapPointer.setPixel(paletteData[v]).next()
        }
    }

    private fun renderIndex8(baLine: ByteArray) {
        scanLine8(baLine, passWidth) { v ->
            bitmapPointer.setPixel(paletteData[v]).next()
        }
    }

    private fun renderGrey1(baLine: ByteArray) {
        scanLine1(baLine, passWidth) { v ->
            val g8 = if (v == 0) 0 else 255
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8).next()
        }
    }

    private fun renderGrey2(baLine: ByteArray) {
        scanLine2(baLine, passWidth) { v ->
            val g8 = v or (v shl 2) or (v shl 4) or (v shl 6)
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8).next()
        }
    }

    private fun renderGrey4(baLine: ByteArray) {
        scanLine4(baLine, passWidth) { v ->
            val g8 = v or (v shl 4)
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8).next()
        }
    }

    private fun renderGrey8(baLine: ByteArray) {
        scanLine8(baLine, passWidth) { v ->
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, v, v, v).next()
        }
    }

    private fun renderGrey16(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val v = baLine.getUInt16(pos)
            pos += 2
            val g8 = v shr 8
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8).next()
        }
    }

    private fun renderGA8(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val g = baLine.getUInt8(pos)
            val a = baLine.getUInt8(pos + 1)
            pos += 2
            bitmapPointer.setPixel(a, g, g, g).next()
        }
    }

    private fun renderGA16(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val g8 = baLine.getUInt16(pos) shr 8
            val a8 = baLine.getUInt16(pos + 2) shr 8
            pos += 4
            bitmapPointer.setPixel(a8, g8, g8, g8).next()
        }
    }

    private fun renderRGB8(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val r = baLine.getUInt8(pos)
            val g = baLine.getUInt8(pos + 1)
            val b = baLine.getUInt8(pos + 2)
            pos += 3
            val a8 = transparentCheckerRGB(r, g, b)
            bitmapPointer.setPixel(a8, r, g, b).next()
        }
    }

    private fun renderRGB16(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val r = baLine.getUInt16(pos)
            val g = baLine.getUInt16(pos + 2)
            val b = baLine.getUInt16(pos + 4)
            pos += 6
            val a8 = transparentCheckerRGB(r, g, b)
            bitmapPointer.setPixel(a8, r shr 8, g shr 8, b shr 8).next()
        }
    }

    private fun renderRGBA8(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val r = baLine.getUInt8(pos)
            val g = baLine.getUInt8(pos + 1)
            val b = baLine.getUInt8(pos + 2)
            val a = baLine.getUInt8(pos + 3)
            pos += 4
            bitmapPointer.setPixel(a, r, g, b).next()
        }
    }

    private fun renderRGBA16(baLine: ByteArray) {
        var pos = 1
        var remain = passWidth
        while (remain-- > 0) {
            val r = baLine.getUInt16(pos)
            val g = baLine.getUInt16(pos + 2)
            val b = baLine.getUInt16(pos + 4)
            val a = baLine.getUInt16(pos + 6)
            pos += 8
            bitmapPointer.setPixel(a shr 8, r shr 8, g shr 8, b shr 8).next()
        }
    }

    private fun colorBitsNotSupported(): Nothing {
        throw ApngParseError("bitDepth $bitDepth is not supported for $colorType")
    }

    private fun selectRenderFunc() = when (colorType) {
        ColorType.GREY -> when (bitDepth) {
            1 -> ::renderGrey1
            2 -> ::renderGrey2
            4 -> ::renderGrey4
            8 -> ::renderGrey8
            16 -> ::renderGrey16
            else -> colorBitsNotSupported()
        }
        ColorType.RGB -> when (bitDepth) {
            8 -> ::renderRGB8
            16 -> ::renderRGB16
            else -> colorBitsNotSupported()
        }
        ColorType.INDEX -> when (bitDepth) {
            1 -> ::renderIndex1
            2 -> ::renderIndex2
            4 -> ::renderIndex4
            8 -> ::renderIndex8
            else -> colorBitsNotSupported()
        }
        ColorType.GREY_ALPHA -> when (bitDepth) {
            8 -> ::renderGA8
            16 -> ::renderGA16
            else -> colorBitsNotSupported()
        }
        ColorType.RGBA -> when (bitDepth) {
            8 -> ::renderRGBA8
            16 -> ::renderRGBA16
            else -> colorBitsNotSupported()
        }
    }

    private fun initializePass() {
        passInfo = passInfoList[pass]
        passWidth = (bitmap.width + passInfo.xStep - passInfo.xStart - 1) / passInfo.xStep
        passHeight = (bitmap.height + passInfo.yStep - passInfo.yStart - 1) / passInfo.yStep
        passY = 0
        scanLineBytes = 1 + (passWidth * sampleBits + 7) / 8

        scanLinePool.recycle(baPreviousLine)
        baPreviousLine = null

        if (passWidth <= 0 || passHeight <= 0) {
            if (callback.canApngDebug()) callback.onApngDebug("pass $pass is empty. size=${passWidth}x$passHeight ")
            incrementPassOrComplete()
        }
    }

    private fun incrementPassOrComplete() {
        if (pass < 6) {
            ++pass
            initializePass()
        } else if (!isCompleted) {
            isCompleted = true
            onCompleted()
        }
    }

    // スキャンラインを読む。行を処理したらtrueを返す
    private fun readScanLine(): Boolean {

        if (inflateBufferQueue.remain < scanLineBytes) {
            // not yet enough data to process scanline
            return false
        }

        val baLine = scanLinePool.obtain()
        inflateBufferQueue.readBytes(baLine, 0, scanLineBytes)

        val filterNum = baLine.getUInt8(0)

		// if( callback.canApngDebug() ) callback.onApngDebug("y=$passY/${passHeight},filterType=$filterType")

        when (FilterType.values().first { it.num == filterNum }) {
            FilterType.None -> {
            }

            FilterType.Sub -> {
                for (pos in 1 until scanLineBytes) {
                    val vCur = baLine.getUInt8(pos)
                    val leftPos = pos - sampleBytes
                    val vLeft = if (leftPos <= 0) 0 else baLine.getUInt8(leftPos)

                    baLine[pos] = (vCur + vLeft).toByte()

                    // if( callback.canApngDebug() ){
                    //  val x = passInfo.xStart + passInfo.xStep * ((pos-1)/sampleBytes)
                    //  val y = passInfo.yStart + passInfo.yStep * passY
                    //  callback.onApngDebug("sub pos=$pos,x=$x,y=$y,left=$vLeft,cur=$vCur,after=${baLine[pos].toInt() and 255}")
                    // }

                }
            }

            FilterType.Up -> {
                val baPreviousLine = this.baPreviousLine
                for (pos in 1 until scanLineBytes) {
                    val vCur = baLine.getUInt8(pos)
                    val vUp = baPreviousLine?.getUInt8(pos) ?: 0
                    baLine[pos] = (vCur + vUp).toByte()
                }
            }

            FilterType.Average -> {
                val baPreviousLine = this.baPreviousLine
                for (pos in 1 until scanLineBytes) {
                    val vCur = baLine.getUInt8(pos)
                    val leftPos = pos - sampleBytes
                    val vLeft = if (leftPos <= 0) 0 else baLine.getUInt8(leftPos)
                    val vUp = baPreviousLine?.getUInt8(pos) ?: 0
                    baLine[pos] = (vCur + ((vLeft + vUp) shr 1)).toByte()
                }
            }

            FilterType.Paeth -> {
                val baPreviousLine = this.baPreviousLine
                for (pos in 1 until scanLineBytes) {
                    val vCur = baLine.getUInt8(pos)
                    val leftPos = pos - sampleBytes
                    val vLeft = if (leftPos <= 0) 0 else baLine.getUInt8(leftPos)
                    val vUp = baPreviousLine?.getUInt8(pos) ?: 0
                    val vUpperLeft = if (leftPos <= 0) 0 else baPreviousLine?.getUInt8(leftPos) ?: 0

                    baLine[pos] = (vCur + paeth(vLeft, vUp, vUpperLeft)).toByte()

                    //					if( callback.canApngDebug() ){
                    //						val x = passInfo.xStart + passInfo.xStep * ((pos-1)/sampleBytes)
                    //						val y = passInfo.yStart + passInfo.yStep * passY
                    //						callback.onApngDebug("paeth pos=$pos,x=$x,y=$y,left=$vLeft,up=$vUp,ul=$vUpperLeft,cur=$vCur,paeth=${paeth(vLeft, vUp, vUpperLeft)}")
                    //					}

                }
            }
        }

        // render scanline
        bitmapPointer.setXY(
            x = passInfo.xStart,
            y = passInfo.yStart + passInfo.yStep * passY,
            step = passInfo.xStep
        )
        renderScanLineFunc(baLine)

        // save previous line
        scanLinePool.recycle(baPreviousLine)
        baPreviousLine = baLine

        if (++passY >= passHeight) {
            // pass completed
            incrementPassOrComplete()
        }

        return true
    }

    // - 複数のIDATチャンクを順に読む
    // - deflate圧縮をデコード(複数のチャンクの場合連続したものとして扱う)
    // - interlace passに合わせてスキャンライン単位に分割
    // - filterをデコードして
    // - ビットマップにレンダリング
    fun addData(
        inStream: InputStream,
        size: Int,
        inBuffer: ByteArray,
        crc32: CRC32
    ) {
        var foundEnd = false
        var inRemain = size
        while (inRemain > 0 && !foundEnd) {

            // inBufferのサイズに合わせて読み込む
            var nRead = 0
            val nReadMax = min(inBuffer.size, inRemain)
            while (true) {

                val remain = nReadMax - nRead
                if (remain <= 0) break

                val delta = inStream.read(inBuffer, nRead, remain)
                if (delta < 0) {
                    foundEnd = true
                    break
                }
                nRead += delta
            }

            if (nRead <= 0) continue

            inRemain -= nRead

            // 読んだらCRC計算する
            crc32.update(inBuffer, 0, nRead)

            // チャンク末尾に余計なデータがあった場合はinflateせずに終端まで読む
            if (isCompleted) continue

            // zlibのdeflateをデコードする
            inflater.setInput(inBuffer, 0, nRead)
            while (!inflater.needsInput()) {
                val buffer = inflateBufferPool.obtain()
                val nInflated = inflater.inflate(buffer)
                if (nInflated <= 0) {
                    inflateBufferPool.recycle(buffer)
                } else {
                    inflateBufferQueue.add(ByteSequence(buffer, 0, nInflated))

                    // キューに追加したデータをScanLine単位で消費する
					@Suppress("ControlFlowWithEmptyBody")
					while (!isCompleted && readScanLine()){
					}

					if (isCompleted) {
                        inflateBufferQueue.clear()
                        break
                    }
                }
            }
        }
    }
}
