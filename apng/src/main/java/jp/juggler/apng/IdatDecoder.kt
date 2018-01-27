@file:Suppress("JoinDeclarationAndAssignment")

package jp.juggler.apng

import jp.juggler.apng.util.BufferPool
import jp.juggler.apng.util.ByteArrayQueue
import jp.juggler.apng.util.ByteArrayRange
import java.io.InputStream
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Inflater

internal class IdatDecoder(
        apng: Apng,
        private val bitmap: ApngBitmap,
        private val inflateBufferPool: BufferPool,
        private val onCompleted: () -> Unit
) {

    class PassInfo(val xStep: Int, val xStart: Int, val yStep: Int, val yStart: Int)

    companion object {

        private val passInfoList = listOf(
                PassInfo(1, 0, 1, 0), // [0]:no interlacing
                PassInfo(8, 0, 8, 0), // Adam7 pass 1
                PassInfo(8, 4, 8, 0), // Adam7 pass 2
                PassInfo(4, 0, 8, 4), // Adam7 pass 3
                PassInfo(4, 2, 4, 0), // Adam7 pass 4
                PassInfo(2, 0, 4, 2), // Adam7 pass 5
                PassInfo(2, 1, 2, 0), // Adam7 pass 6
                PassInfo(1, 0, 2, 1) // Adam7 pass 7

        )

        private fun abs(v: Int) = if (v >= 0) v else -v

        private val dummyPaletteData = ByteArray(0)

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
            var x = 0
            while (pass_w - x >= 8) {
                val v = baLine[pos++].toInt()
                block((v shr 7) and 1)
                block((v shr 6) and 1)
                block((v shr 5) and 1)
                block((v shr 4) and 1)
                block((v shr 3) and 1)
                block((v shr 2) and 1)
                block((v shr 1) and 1)
                block(v and 1)
                x += 8
            }
            val remain = pass_w - x
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
            var x = 0
            while (pass_w - x >= 4) {
                val v = baLine[pos++].toInt()
                block((v shr 6) and 3)
                block((v shr 4) and 3)
                block((v shr 2) and 3)
                block(v and 3)
                x += 4
            }
            val remain = pass_w - x
            if (remain > 0) {
                val v = baLine[pos].toInt()
                block((v shr 6) and 3)
                if (remain > 1) block((v shr 4) and 3)
                if (remain > 2) block((v shr 2) and 3)
            }
        }

        private inline fun scanLine4(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (pass_w - x >= 2) {
                val v = baLine[pos++].toInt()
                block((v shr 4) and 15)
                block(v and 15)
                x += 2
            }
            val remain = pass_w - x
            if (remain > 0) {
                val v = baLine[pos].toInt()
                block((v shr 4) and 15)
            }
        }

        private inline fun scanLine8(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val v = baLine[pos++].toInt()
                block(v and 255)
                ++x
            }
        }

        private fun parseUInt16(ba: ByteArray, pos: Int): Int {
            val b0 = ba[pos].toInt() and 255
            val b1 = ba[pos].toInt() and 255
            return (b0 shl 8) or b1
        }

        private inline fun scanLine16(baLine: ByteArray, pass_w: Int, block: (v: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val v = parseUInt16(baLine, pos)
                pos += 2
                block(v)
                ++x
            }
        }

        private inline fun scanLineRGB8(baLine: ByteArray, pass_w: Int, block: (r: Int, g: Int, b: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val r = baLine[pos].toInt() and 255
                val g = baLine[pos + 1].toInt() and 255
                val b = baLine[pos + 2].toInt() and 255
                pos += 3
                block(r, g, b)
                ++x
            }
        }

        private inline fun scanLineRGB16(baLine: ByteArray, pass_w: Int, block: (r: Int, g: Int, b: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val r = parseUInt16(baLine, pos)
                val g = parseUInt16(baLine, pos + 2)
                val b = parseUInt16(baLine, pos + 4)
                pos += 6
                block(r, g, b)
                ++x
            }
        }

        private inline fun scanLineRGBA8(baLine: ByteArray, pass_w: Int, block: (r: Int, g: Int, b: Int, a: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val r = baLine[pos].toInt() and 255
                val g = baLine[pos + 1].toInt() and 255
                val b = baLine[pos + 2].toInt() and 255
                val a = baLine[pos + 3].toInt() and 255
                pos += 4
                block(r, g, b, a)
                ++x
            }
        }

        private inline fun scanLineRGBA16(baLine: ByteArray, pass_w: Int, block: (r: Int, g: Int, b: Int, a: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val r = parseUInt16(baLine, pos)
                val g = parseUInt16(baLine, pos + 2)
                val b = parseUInt16(baLine, pos + 4)
                val a = parseUInt16(baLine, pos + 6)
                pos += 8
                block(r, g, b, a)
                ++x
            }
        }

        private inline fun scanLineGA8(baLine: ByteArray, pass_w: Int, block: (g: Int, a: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val g = baLine[pos].toInt() and 255
                val a = baLine[pos + 1].toInt() and 255
                pos += 2
                block(g, a)
                ++x
            }
        }

        private inline fun scanLineGA16(baLine: ByteArray, pass_w: Int, block: (g: Int, a: Int) -> Unit) {
            var pos = 1
            var x = 0
            while (x < pass_w) {
                val g = parseUInt16(baLine, pos)
                val a = parseUInt16(baLine, pos + 2)
                pos += 4
                block(g, a)
                ++x
            }
        }
    }

    private val inflater = Inflater()
    private val bytesQueue = ByteArrayQueue { inflateBufferPool.recycle(it.array) }
    private val colorType: ColorType
    private val bitDepth: Int
    private val plteData: ByteArray
    private val sampleBits: Int
    private val sampleBytes: Int
    private val scanLineBytesMax: Int
    private val linePool = LinkedList<ByteArray>()
    private val transparentCheckerGrey: (v: Int) -> Int
    private val transparentCheckerRGB: (r: Int, g: Int, b: Int) -> Int
    private val renderScanLineFunc: (baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) -> Unit

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
        this.colorType = header.colorType
        this.bitDepth = header.bitDepth

        this.plteData = if (colorType == ColorType.INDEX) {
            apng.palette?.list
                    ?: throw ParseError("missing ApngPalette for index color")
        } else {
            dummyPaletteData
        }

        sampleBits = when (colorType) {
            ColorType.GREY,ColorType.INDEX -> bitDepth
            ColorType.GREY_ALPHA -> bitDepth * 2
            ColorType.RGB -> bitDepth * 3
            ColorType.RGBA -> bitDepth * 4
        }

        sampleBytes = (sampleBits + 7) / 8
        scanLineBytesMax = 1 + (bitmap.width * sampleBits + 7) / 8

        linePool.add(ByteArray(scanLineBytesMax))
        linePool.add(ByteArray(scanLineBytesMax))

        this.pass = when (header.interlaceMethod) {
            InterlaceMethod.None -> 0
            InterlaceMethod.Standard -> 1
        }

        val transparentColor = apng.transparentColor
        transparentCheckerGrey = if (transparentColor != null) {
            { v: Int -> if (transparentColor.match(v)) 0 else 255 }
        } else {
            { _: Int -> 255 }
        }

        transparentCheckerRGB = if (transparentColor != null) {
            { r: Int, g: Int, b: Int -> if (transparentColor.match(r,g,b)) 0 else 255 }
        } else {
            { _: Int, _: Int, _: Int -> 255 }
        }

        renderScanLineFunc = selectRenderFunc()

        initializePass()
    }

    private fun renderGrey1(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine1(baLine, passWidth) { v ->
            val g8 = if (v == 0) 0 else 255
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8)
                    .plusX(xStep)
        }
    }

    private fun renderGrey2(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine2(baLine, passWidth) { v ->
            val g8 = v or (v shl 2) or (v shl 4) or (v shl 6)
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8)
                    .plusX(xStep)
        }
    }

    private fun renderGrey4(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine4(baLine, passWidth) { v ->
            val g8 = v or (v shl 4)
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8)
                    .plusX(xStep)
        }
    }

    private fun renderGrey8(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine8(baLine, passWidth) { v ->
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, v, v, v)
                    .plusX(xStep)
        }
    }

    private fun renderGrey16(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine16(baLine, passWidth) { v ->
            val g8 = v shr 8
            val a8 = transparentCheckerGrey(v)
            bitmapPointer.setPixel(a8, g8, g8, g8)
                    .plusX(xStep)
        }
    }

    private fun renderRGB8(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineRGB8(baLine, passWidth) { r, g, b ->
            val a8 = transparentCheckerRGB(r, g, b)
            bitmapPointer.setPixel(a8, r, g, b)
                    .plusX(xStep)
        }
    }

    private fun renderRGB16(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineRGB16(baLine, passWidth) { r, g, b ->
            val a8 = transparentCheckerRGB(r, g, b)
            bitmapPointer.setPixel(a8, r shr 8, g shr 8, b shr 8)
                    .plusX(xStep)
        }
    }

    private fun renderIndex1(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine1(baLine, passWidth) { v ->
            val plteOffset = v * 4
            bitmapPointer.setPixel(
                    plteData[plteOffset],
                    plteData[plteOffset + 1],
                    plteData[plteOffset + 2],
                    plteData[plteOffset + 3]
            )
                    .plusX(xStep)
        }
    }

    private fun renderIndex2(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine2(baLine, passWidth) { v ->
            val plteOffset = v * 4
            bitmapPointer.setPixel(
                    plteData[plteOffset],
                    plteData[plteOffset + 1],
                    plteData[plteOffset + 2],
                    plteData[plteOffset + 3]
            )
                    .plusX(xStep)
        }
    }

    private fun renderIndex4(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine4(baLine, passWidth) { v ->
            val plteOffset = v * 4
            bitmapPointer.setPixel(
                    plteData[plteOffset],
                    plteData[plteOffset + 1],
                    plteData[plteOffset + 2],
                    plteData[plteOffset + 3]
            )
                    .plusX(xStep)
        }
    }

    private fun renderIndex8(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLine8(baLine, passWidth) { v ->
            val plteOffset = v * 4
            bitmapPointer.setPixel(
                    plteData[plteOffset],
                    plteData[plteOffset + 1],
                    plteData[plteOffset + 2],
                    plteData[plteOffset + 3]
            )
                    .plusX(xStep)
        }
    }

    private fun renderGA8(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineGA8(baLine, passWidth) { g, a ->
            bitmapPointer.setPixel(a, g, g, g)
                    .plusX(xStep)
        }
    }

    private fun renderGA16(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineGA16(baLine, passWidth) { g, a ->
            val g8 = g shr 8
            bitmapPointer.setPixel(a shr 8, g8, g8, g8)
                    .plusX(xStep)
        }
    }

    private fun renderRGBA8(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineRGBA8(baLine, passWidth) { r, g, b, a ->
            bitmapPointer.setPixel(a, r, g, b)
                    .plusX(xStep)
        }
    }

    private fun renderRGBA16(baLine: ByteArray, bitmapPointer: ApngBitmap.Pointer, xStep: Int) {
        scanLineRGBA16(baLine, passWidth) { r, g, b, a ->
            bitmapPointer.setPixel(a shr 8, r shr 8, g shr 8, b shr 8)
                    .plusX(xStep)
        }
    }

    private fun colorBitsNotSupported(): Nothing {
        throw ParseError("bitDepth $bitDepth is not supported for $colorType")
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

        baPreviousLine?.let { linePool.add(it) }
        baPreviousLine = null

        if (passWidth <= 0 || passHeight <= 0) {
            incrementPassOrComplete()
        }
    }

    private fun incrementPassOrComplete(){
        if (pass in 1..6) {
            ++pass
            initializePass()
        } else if (!isCompleted) {
            isCompleted = true
            onCompleted()
        }
    }


    private fun readScanLine(): Boolean {
        if (bytesQueue.remain < scanLineBytes) return false

        val baLine = linePool.removeFirst()
        bytesQueue.readBytes(baLine, 0, scanLineBytes)

        val filterNum = baLine[0].toInt() and 255
        val filterType = FilterType.values().first { it.num == filterNum }

        when (filterType) {
            FilterType.None -> {
            }
            FilterType.Sub -> {
                for (pos in 1 until scanLineBytes) {
                    val vLeft = if (pos <= sampleBytes) 0 else baLine[pos - sampleBytes].toInt() and 255
                    val vCur = baLine[pos].toInt() and 255
                    baLine[pos] = (vCur + vLeft).toByte()
                }
            }
            FilterType.Up -> {
                for (pos in 1 until scanLineBytes) {
                    val vUp = (baPreviousLine?.get(pos)?.toInt() ?: 0) and 255
                    val vCur = baLine[pos].toInt() and 255
                    baLine[pos] = (vCur + vUp).toByte()
                }
            }
            FilterType.Average -> {
                for (pos in 1 until scanLineBytes) {
                    val vLeft = if (pos <= sampleBytes) 0 else baLine[pos - sampleBytes].toInt() and 255
                    val vUp = (baPreviousLine?.get(pos)?.toInt() ?: 0) and 255
                    val vCur = baLine[pos].toInt() and 255
                    baLine[pos] = (vCur + ((vLeft + vUp) shr 1)).toByte()
                }
            }
            FilterType.Paeth -> {
                for (pos in 1 until scanLineBytes) {
                    val vLeft = if (pos <= sampleBytes) 0 else baLine[pos - sampleBytes].toInt() and 255
                    val vUp = (baPreviousLine?.get(pos)?.toInt() ?: 0) and 255
                    val vUpperLeft = if (pos <= sampleBytes) 0 else (baPreviousLine?.get(pos - sampleBytes)?.toInt()
                            ?: 0) and 255
                    val vCur = baLine[pos].toInt() and 255
                    baLine[pos] = (vCur + paeth(vLeft, vUp, vUpperLeft)).toByte()
                }
            }
        }

        // render scanline
        renderScanLineFunc(
                baLine,
                bitmap.pointer(
                        passInfo.xStart,
                        passInfo.yStart + passInfo.yStep * passY
                ),
                passInfo.xStep
        )
        // save previous line
        baPreviousLine?.let { linePool.add(it) }
        baPreviousLine = baLine

        // complete pass?
        if (++passY >= passHeight) {
            incrementPassOrComplete()
        }

        return true
    }


    // returns CRC32 value
    fun addData(
            inStream: InputStream,
            size: Int,
            inBuffer: ByteArray,
            crc32: CRC32
    ){
        var foundEnd = false
        var inRemain = size
        while (inRemain > 0 && !foundEnd) {
            // read from inStream( max 4096 byte)
            var nRead = 0
            val nReadMax = Math.min(inBuffer.size, inRemain)
            while (nRead < nReadMax) {
                val delta = inStream.read(inBuffer, nRead, nReadMax - nRead)
                if (delta < 0) {
                    foundEnd = true
                    break
                }
                nRead += delta
            }
            if (nRead > 0) {
                inRemain -= nRead
                crc32.update(inBuffer, 0, nRead)

                // inflate
                inflater.setInput(inBuffer, 0, nRead)
                while (!inflater.needsInput()) {
                    val inflateBuffer = inflateBufferPool.obtain()
                    val delta = inflater.inflate(inflateBuffer)
                    if (delta > 0) {
                        bytesQueue.add(ByteArrayRange(inflateBuffer, 0, delta))
                    } else {
                        inflateBufferPool.recycle(inflateBuffer)
                    }
                }

                // read scanLine
                while (!isCompleted && readScanLine()) {
                }

                if (isCompleted) {
                    bytesQueue.clear()
                }
            }
        }
    }
}
