@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

// information from IHDR chunk.
class ApngImageHeader(
    val width: Int,
    val height: Int,
    val bitDepth: Int,
    val colorType: ColorType,
    val compressionMethod: CompressionMethod,
    val filterMethod: FilterMethod,
    val interlaceMethod: InterlaceMethod,
) {
    companion object {
        internal fun parse(src: ByteSequence): ApngImageHeader {
            val width = src.readInt32()
            val height = src.readInt32()
            if (width <= 0 || height <= 0) throw ApngParseError("w=$width,h=$height is too small")

            val bitDepth = src.readUInt8()

            var num: Int
            //
            num = src.readUInt8()
            val colorType = ColorType.values().first { it.num == num }
            //
            num = src.readUInt8()
            val compressionMethod = CompressionMethod.values().first { it.num == num }
            //
            num = src.readUInt8()
            val filterMethod = FilterMethod.values().first { it.num == num }
            //
            num = src.readUInt8()
            val interlaceMethod = InterlaceMethod.values().first { it.num == num }

            return ApngImageHeader(
                width = width,
                height = height,
                bitDepth = bitDepth,
                colorType = colorType,
                compressionMethod = compressionMethod,
                filterMethod = filterMethod,
                interlaceMethod = interlaceMethod,
            )
        }
    }

    override fun toString() =
        "ApngImageHeader(w=$width,h=$height,bits=$bitDepth,color=$colorType,interlace=$interlaceMethod)"
}
