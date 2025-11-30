package jp.juggler.apng

import java.io.InputStream
import kotlin.math.min

// https://raw.githubusercontent.com/rtyley/animated-gif-lib-for-java/master/src/main/java/com/madgag/gif/fmsware/GifDecoder.java
// original code author is Kevin Weiner, FM Software.
// LZW decoder adapted from John Cristy's ImageMagick.

// http://www.theimage.com/animation/pages/disposal3.html
// great sample images.

class MyGifDecoder(val callback: MyGifDecoderCallback) {

    private class Rectangle(var x: Int = 0, var y: Int = 0, var w: Int = 0, var h: Int = 0) {

        fun set(src: Rectangle) {
            this.x = src.x
            this.y = src.y
            this.w = src.w
            this.h = src.h
        }
    }

    private class Reader(val bis: InputStream) {

        var block = ByteArray(256) // current data block
        var blockSize = 0 // block size

        // Reads a single byte from the input stream.
        fun byte(): Int = bis.read()

        // Reads next 16-bit value, LSB first
        fun uInt16() = byte() or (byte() shl 8)

        fun array(ba: ByteArray, offset: Int = 0, length: Int = ba.size - offset) {
            var nRead = 0
            while (nRead < length) {
                val delta = bis.read(ba, offset + nRead, length - nRead)
                if (delta == -1) error("unexpected End of Stream")
                nRead += delta
            }
        }

        // Reads specified bytes and compose it to ascii string
        fun string(n: Int): String {
            return StringBuilder(n).apply {
                ByteArray(n)
                    .also { array(it) }
                    .forEach { append(Char(it.toInt() and 255)) }
            }.toString()
        }

        // Reads next variable length block
        fun block(): ByteArray {
            blockSize = byte()
            array(block, 0, blockSize)
            return block
        }

        // Skips variable length blocks up to and including next zero length block.
        fun skipBlock() {
            do {
                block()
            } while (blockSize > 0)
        }
    }

    // 0=no action; 1=leave in place; 2=restore to bg; 3=restore to prev
    enum class Dispose(val num: Int) {

        Unspecified(0),
        DontDispose(1),
        RestoreBackground(2),
        RestorePrevious(3)
    }

    companion object {
        private const val MaxStackSize = 4096
        private const val NullCode = -1
        private const val b0 = 0.toByte()
        private const val OPAQUE = 255 shl 24
    }

    private var width = 0 // full image width
    private var height = 0 // full image height
    private var gctSize = 0  // size of global color table
    private var loopCount = 1 // iterations; 0 = repeat forever

    private var gct: IntArray? = null // global color table

    private var bgIndex = 0 // background color index
    private var bgColor = 0 // background color
    private var lastBgColor = 0 // previous bg color
    private var pixelAspect = 0 // pixel aspect ratio

    private var interlace = false // interlace flag

    private var lctFlag = false  // local color table flag
    private var lctSize = 0 // local color table size

    private val srcRect = Rectangle() // current image position and size
    private val lastRect = Rectangle() // last image rect

    // last graphic control extension info
    private var dispose = Dispose.Unspecified
    private var lastDispose = Dispose.Unspecified
    private var transparency = false // use transparent color
    private var delay = 0 // delay in milliseconds
    private var transIndex = 0 // transparent color index

    // LZW decoder working arrays
    private var prefix: ShortArray? = null
    private var suffix: ByteArray? = null
    private var pixelStack: ByteArray? = null
    private var pixels: ByteArray? = null

    private val frames = ArrayList<Pair<ApngFrameControl, ApngBitmap>>()

    private var previousImage: ApngBitmap? = null

    // 現在のdispose指定と描画結果を覚えておく
    private fun memoryLastDispose(image: ApngBitmap) {
        if (dispose != Dispose.RestorePrevious) previousImage = image
        lastDispose = dispose
        lastRect.set(srcRect)
        lastBgColor = bgColor
    }

    // 前回のdispose指定を反映する
    private fun applyLastDispose(destImage: ApngBitmap) {

        if (lastDispose == Dispose.Unspecified) return

        // restore previous image
        val previousImage = this.previousImage
        if (previousImage != null) {
            System.arraycopy(previousImage.colors, 0, destImage.colors, 0, destImage.colors.size)
        }

        if (lastDispose == Dispose.RestoreBackground) {

            // fill lastRect

            val fillColor = if (transparency) {
                0 // assume background is transparent
            } else {
                lastBgColor // use given background color
            }

            for (y in lastRect.y until lastRect.y + lastRect.h) {
                val fillStart = y * destImage.width + lastRect.x
                val fillWidth = lastRect.w
                destImage.colors.fill(
                    fillColor,
                    fromIndex = fillStart,
                    toIndex = fillStart + fillWidth
                )
            }
        }
    }

    // render to ApngBitmap
    // may use some previous frame.
    private fun render(destImage: ApngBitmap, act: IntArray) {
        // expose destination image's pixels as int array
        val dest = destImage.colors

        // copy each source line to the appropriate place in the destination
        var pass = 1
        var inc = 8
        var iLine = 0
        for (i in 0 until srcRect.h) {
            var line = i
            if (interlace) {
                if (iLine >= srcRect.h) {
                    when (++pass) {
                        2 -> {
                            iLine = 4
                        }
                        3 -> {
                            iLine = 2
                            inc = 4
                        }
                        4 -> {
                            iLine = 1
                            inc = 2
                        }
                    }
                }
                line = iLine
                iLine += inc
            }
            line += srcRect.y
            if (line < height) {

                // start of line in source
                var sx = i * srcRect.w

                //
                val k = line * width

                // loop for dest line.
                for (dx in k + srcRect.x until min(k + width, k + srcRect.x + srcRect.w)) {
                    // map color and insert in destination
                    val index = pixels!![sx++].toInt() and 0xff
                    val c = act[index]
                    if (c != 0) dest[dx] = c
                }
            }
        }
    }

    /**
     * Decodes LZW image data into pixel array.
     * Adapted from John Cristy's ImageMagick.
     */
    private fun decodeImageData(reader: Reader) {

        // allocate pixel array if need
        val nPixels = srcRect.w * srcRect.h
        if ((pixels?.size ?: 0) < nPixels) pixels = ByteArray(nPixels)
        val pixels = this.pixels!!

        if (prefix == null) prefix = ShortArray(MaxStackSize)
        if (suffix == null) suffix = ByteArray(MaxStackSize)
        if (pixelStack == null) pixelStack = ByteArray(MaxStackSize + 1)
        val prefix = this.prefix!!
        val suffix = this.suffix!!
        val pixelStack = this.pixelStack!!

        //  Initialize GIF data stream decoder.
        val dataSize = reader.byte()
        val clear = 1 shl dataSize
        val endOfInformation = clear + 1

        var available = clear + 2
        var oldCode = NullCode
        var codeSize = dataSize + 1
        var codeMask = (1 shl codeSize) - 1

        for (code in 0 until clear) {
            prefix[code] = 0
            suffix[code] = code.toByte()
        }

        //  Decode GIF pixel stream.
        var datum = 0
        var bits = 0
        var count = 0
        var first = 0
        var top = 0
        var bi = 0
        var pi = 0

        var i = 0
        while (i < nPixels) {
            if (top == 0) {
                if (bits < codeSize) {
                    //  Load bytes until there are enough bits for a code.
                    if (count == 0) {
                        // Read a new data block.
                        reader.block()
                        count = reader.blockSize
                        if (count <= 0) break
                        bi = 0
                    }
                    datum += (reader.block[bi].toInt() and 0xff) shl bits
                    bits += 8
                    bi++
                    count--
                    continue
                }

                //  Get the next code.
                var code = datum and codeMask
                datum = datum shr codeSize
                bits -= codeSize

                //  Interpret the code
                if ((code > available) || (code == endOfInformation)) break

                if (code == clear) {
                    //  Reset decoder.
                    codeSize = dataSize + 1
                    codeMask = (1 shl codeSize) - 1
                    available = clear + 2
                    oldCode = NullCode
                    continue
                }
                if (oldCode == NullCode) {
                    pixelStack[top++] = suffix[code]
                    oldCode = code
                    first = code
                    continue
                }
                val inCode = code

                if (code == available) {
                    pixelStack[top++] = first.toByte()
                    code = oldCode
                }
                while (code > clear) {
                    pixelStack[top++] = suffix[code]
                    code = prefix[code].toInt()
                }
                first = suffix[code].toInt() and 0xff

                //  Add a new string to the string table,

                if (available >= MaxStackSize) {
                    pixelStack[top++] = first.toByte()
                    continue
                }
                pixelStack[top++] = first.toByte()
                prefix[available] = oldCode.toShort()
                suffix[available] = first.toByte()
                available++

                if ((available and codeMask) == 0 && available < MaxStackSize) {
                    codeSize++
                    codeMask += available
                }

                oldCode = inCode
            }

            //  Pop a pixel off the pixel stack.
            top--
            pixels[pi++] = pixelStack[top]
            i++
        }

        // clear missing pixels
        for (n in pi until nPixels) {
            pixels[n] = b0
        }
    }

    /**
     * Reads color table as 256 RGB integer values
     *
     * @param nColors int number of colors to read
     * @return int array containing 256 colors (packed ARGB with full alpha)
     */
    private fun parseColorTable(reader: Reader, nColors: Int): IntArray {
        val nBytes = 3 * nColors
        val c = ByteArray(nBytes)
        reader.array(c)

        // max size to avoid bounds checks
        val tab = IntArray(256)
        var i = 0
        var j = 0
        while (i < nColors) {
            val r = c[j].toInt() and 255
            val g = c[j + 1].toInt() and 255
            val b = c[j + 2].toInt() and 255
            j += 3
            tab[i++] = (OPAQUE or (r shl 16) or (g shl 8) or b)
        }
        return tab
    }

    private fun parseDispose(num: Int) =
        Dispose.values().find { it.num == num } ?: error("unknown dispose $num")

    /**
     * Reads Graphics Control Extension values
     */
    private fun parseGraphicControlExt(reader: Reader) {
        reader.byte() // block size
        val packed = reader.byte() // packed fields
        dispose = parseDispose((packed and 0x1c) shr 2) // disposal method
        if (callback.canGifDebug()) callback.onGifDebug("parseGraphicControlExt: frame=${frames.size} dispose=$dispose")
        // elect to keep old image if discretionary
        if (dispose == Dispose.Unspecified) dispose = Dispose.DontDispose

        transparency = (packed and 1) != 0
        // delay in milliseconds
        delay = reader.uInt16() * 10
        // transparent color index
        transIndex = reader.byte()
        // block terminator
        reader.byte()
    }

    // Reads Netscape extension to obtain iteration count
    private fun readNetscapeExt(reader: Reader) {
        do {
            val block = reader.block()
            if (block[0].toInt() == 1) {
                // loop count sub-block
                val b1 = block[1].toInt() and 255
                val b2 = block[2].toInt() and 255
                loopCount = ((b2 shl 8) and b1)
            }
        } while (reader.blockSize > 0)
    }

    // Reads next frame image
    private fun parseFrame(reader: Reader) {
        // (sub)image position & size
        srcRect.x = reader.uInt16()
        srcRect.y = reader.uInt16()
        srcRect.w = reader.uInt16()
        srcRect.h = reader.uInt16()

        val packed = reader.byte()
        lctFlag = (packed and 0x80) != 0 // 1 - local color table flag
        interlace = (packed and 0x40) != 0 // 2 - interlace flag
        // 3 - sort flag
        // 4-5 - reserved
        lctSize = 2 shl (packed and 7) // 6-8 - local color table size

        val act = if (lctFlag) {
            // make local table active
            parseColorTable(reader, lctSize)
        } else {
            // make global table active
            if (bgIndex == transIndex) bgColor = 0
            gct!!
        }

        var save = 0
        if (transparency) {
            save = act[transIndex]
            act[transIndex] = 0 // set transparent color if specified
        }

        decodeImageData(reader) // decode pixel data
        reader.skipBlock()

        // add image to frame list
        frames.add(
            Pair(
                ApngFrameControl(
                    width = width,
                    height = height,
                    xOffset = 0,
                    yOffset = 0,
                    disposeOp = DisposeOp.None,
                    blendOp = BlendOp.Source,
                    sequenceNumber = frames.size,
                    delayMilliseconds = delay.toLong()
                ),
                ApngBitmap(width, height).also {
                    applyLastDispose(it)
                    render(it, act) // transfer pixel data to image
                    memoryLastDispose(it)
                }
            )
        )

        if (transparency) {
            act[transIndex] = save
        }

        /**
         * Resets frame state for reading next image.
         */
        dispose = Dispose.Unspecified
        transparency = false
        delay = 0
    }

    // read GIF content blocks
    private fun readContents(reader: Reader): ApngAnimationControl {
        loopBlocks@ while (true) {
            when (val blockCode = reader.byte()) {
                // image separator
                0x2C -> parseFrame(reader)
                // extension
                0x21 -> when (reader.byte()) {
                    // graphics control extension
                    0xf9 -> parseGraphicControlExt(reader)
                    // application extension
                    0xff -> {
                        val block = reader.block()
                        val app = StringBuilder(12)
                        for (i in 0 until 11) {
                            app.append(Char(block[i].toInt() and 255))
                        }
                        if (app.toString() == "NETSCAPE2.0") {
                            readNetscapeExt(reader)
                        } else {
                            reader.skipBlock() // don't care
                        }
                    }

                    else -> {
                        // uninteresting extension
                        reader.skipBlock()
                    }
                }

                // terminator
                0x3b -> break@loopBlocks

                // bad byte, but keep going and see what happens
                0x00 -> {
                }

                else -> error("unknown block code $blockCode")
            }
        }

        return ApngAnimationControl(numFrames = frames.size, numPlays = loopCount)
    }

    /**
     * Initializes or re-initializes reader
     */
    private fun reset() {
        frames.clear()
        loopCount = ApngAnimationControl.PLAY_INDEFINITELY
        gct = null
        prefix = null
        suffix = null
        pixelStack = null
        pixels = null
        previousImage = null
    }

    /**
     * Reads GIF file header information.
     */
    private fun parseImageHeader(reader: Reader): ApngImageHeader {

        val id = reader.string(6)
        if (!id.startsWith("GIF"))
            error("file header not match to GIF.")

        /**
         * Reads Logical Screen Descriptor
         */

        // logical screen size
        width = reader.uInt16()
        height = reader.uInt16()
        if (width < 1 || height < 1) error("too small size. $width*$height")

        // packed fields
        val packed = reader.byte()

        // global color table used
        val gctFlag = (packed and 0x80) != 0 // 1   : global color table flag
        // 2-4 : color resolution
        // 5   : gct sort flag
        gctSize = 2 shl (packed and 7) // 6-8 : gct size

        bgIndex = reader.byte() // background color index
        pixelAspect = reader.byte() // pixel aspect ratio

        gct = if (gctFlag) {
            val table = parseColorTable(reader, gctSize)
            bgColor = table[bgIndex]
            table
        } else {
            bgColor = 0
            null
        }

        return ApngImageHeader(
            width = this.width,
            height = this.height,
            bitDepth = 8,
            colorType = ColorType.INDEX,
            compressionMethod = CompressionMethod.Standard,
            filterMethod = FilterMethod.Standard,
            interlaceMethod = InterlaceMethod.None
        )
    }

    fun parse(src: InputStream) {

        reset()

        val reader = Reader(src)
        val header = parseImageHeader(reader)
        val animationControl = readContents(reader)

        // GIFは最後まで読まないとフレーム数が分からない

        if (frames.isEmpty()) error("there is no frame.")
        callback.onGifHeader(header)
        callback.onGifAnimationInfo(header, animationControl)
        for (frame in frames) {
            callback.onGifAnimationFrame(frame.first, frame.second)
        }

        reset()
    }
}
