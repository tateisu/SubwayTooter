@file:Suppress("JoinDeclarationAndAssignment")

package jp.juggler.apng

import jp.juggler.apng.util.BufferPool
import jp.juggler.apng.util.ByteArrayTokenizer
import jp.juggler.apng.util.StreamTokenizer
import java.io.InputStream
import java.util.zip.CRC32

object ApngDecoder {

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0a)

    fun parseStream(
            _inStream: InputStream,
            callback: ApngDecoderCallback
    ) {
        val apng = Apng()
        val tokenizer = StreamTokenizer(_inStream)

        val pngHeader = tokenizer.readBytes(8)
        if (!pngHeader.contentEquals(PNG_SIGNATURE)) {
            throw ParseError("header not match")
        }

        var lastSequenceNumber: Int? = null
        fun checkSequenceNumber(n: Int) {
            val last = lastSequenceNumber
            if (last != null && n <= last) {
                throw ParseError("incorrect sequenceNumber. last=$lastSequenceNumber,current=$n")
            }
            lastSequenceNumber = n
        }

        val inBuffer = ByteArray(4096)
        val inflateBufferPool = BufferPool(8192)
        var idatDecoder: IdatDecoder? = null
        var fdatDecoder: IdatDecoder? = null
        val crc32 = CRC32()
        var lastFctl: ApngFrameControl? = null
        var bitmap: ApngBitmap? = null

        loop@ while (true) {
            crc32.reset()
            val chunk = ApngChunk(crc32, tokenizer)
            when (chunk.type) {

                "IEND" -> break@loop

                "IHDR" -> {
                    val header = ApngImageHeader(ByteArrayTokenizer(chunk.readBody(crc32, tokenizer)))
                    bitmap = ApngBitmap(header.width, header.height)
                    apng.header = header
                    callback.onHeader(apng, header)
                }

                "PLTE" -> apng.palette = ApngPalette(chunk.readBody(crc32, tokenizer))

                "bKGD" -> {
                    val header = apng.header ?: throw ParseError("missing IHDR")
                    apng.background = ApngBackground(header.colorType, ByteArrayTokenizer(chunk.readBody(crc32, tokenizer)))
                }

                "tRNS" -> {
                    val header = apng.header ?: throw ParseError("missing IHDR")
                    val body = chunk.readBody(crc32, tokenizer)
                    when (header.colorType) {
                        ColorType.GREY -> apng.transparentColor = ApngTransparentColor(true, ByteArrayTokenizer(body))
                        ColorType.RGB -> apng.transparentColor = ApngTransparentColor(false, ByteArrayTokenizer(body))
                        ColorType.INDEX -> apng.palette?.parseTRNS(body) ?: throw ParseError("missing palette")
                        else -> callback.log("tRNS ignored. colorType =${header.colorType}")
                    }
                }

                "IDAT" -> {
                    val header = apng.header ?: throw ParseError("missing IHDR")
                    if (idatDecoder == null) {
                        bitmap ?: throw ParseError("missing bitmap")
                        bitmap.reset(header.width, header.height)
                        idatDecoder = IdatDecoder(apng, bitmap, inflateBufferPool) {
                            callback.onDefaultImage(apng, bitmap)
                            val fctl = lastFctl
                            if (fctl != null) {
                                // IDATより前にfcTLが登場しているなら、そのfcTLの画像はIDATと同じ
                                callback.onAnimationFrame(apng, fctl, bitmap)
                            }
                        }
                    }
                    idatDecoder.addData(
                            tokenizer.inStream,
                            chunk.size,
                            inBuffer,
                            crc32
                    )
                    chunk.checkCRC(tokenizer, crc32.value)
                }

                "acTL" -> {
                    val animationControl = ApngAnimationControl(ByteArrayTokenizer(chunk.readBody(crc32, tokenizer)))
                    apng.animationControl = animationControl
                    callback.onAnimationInfo(apng, animationControl)
                }

                "fcTL" -> {
                    val bat = ByteArrayTokenizer(chunk.readBody(crc32, tokenizer))
                    checkSequenceNumber(bat.readInt32())
                    lastFctl = ApngFrameControl(bat)
                    fdatDecoder = null
                }

                "fdAT" -> {
                    val fctl = lastFctl ?: throw ParseError("missing fCTL before fdAT")
                    if (fdatDecoder == null) {
                        bitmap ?: throw ParseError("missing bitmap")
                        bitmap.reset(fctl.width, fctl.height)
                        fdatDecoder = IdatDecoder(apng, bitmap, inflateBufferPool) {
                            callback.onAnimationFrame(apng, fctl, bitmap)
                        }
                    }
                    checkSequenceNumber(tokenizer.readInt32(crc32))
                    fdatDecoder.addData(
                            tokenizer.inStream,
                            chunk.size - 4,
                            inBuffer,
                            crc32
                    )
                    chunk.checkCRC(tokenizer, crc32.value)
                }

            // 無視するチャンク
                "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", // color space information
                "tEXt", "zTXt", "iTXt", // text information
                "tIME", // timestamp
                "hIST", // histogram
                "pHYs", // Physical pixel dimensions
                "sPLT" //  Suggested palette (おそらく減色用?)
                -> chunk.skipBody(tokenizer)

                else -> {
                    callback.log("unknown chunk: type=%s,size=0x%x".format(chunk.type, chunk.size))
                    chunk.skipBody(tokenizer)
                }
            }
        }
    }
}
