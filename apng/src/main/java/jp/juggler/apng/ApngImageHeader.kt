@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteArrayTokenizer


class ApngImageHeader internal constructor(bat: ByteArrayTokenizer) {
    val width: Int
    val height: Int
    val bitDepth: Int
    val colorType: ColorType
    val compressionMethod: CompressionMethod
    val filterMethod: FilterMethod
    val interlaceMethod: InterlaceMethod

    init {

        width = bat.readInt32()
        height = bat.readInt32()
        bitDepth = bat.readUInt8()

        var num:Int
        //
        num =bat.readUInt8()
        colorType = ColorType.values().first { it.num==num }
        //
        num =bat.readUInt8()
        compressionMethod = CompressionMethod.values().first { it.num==num }
        //
        num =bat.readUInt8()
        filterMethod = FilterMethod.values().first { it.num==num }
        //
        num =bat.readUInt8()
        interlaceMethod = InterlaceMethod.values().first { it.num==num }
    }

    override fun toString() = "ApngImageHeader(w=$width,h=$height,bits=$bitDepth,color=$colorType,interlace=$interlaceMethod)"
}
