@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

// information from IHDR chunk.
class ApngImageHeader internal constructor(src : ByteSequence) {
	
	val width : Int
	val height : Int
	val bitDepth : Int
	val colorType : ColorType
	val compressionMethod : CompressionMethod
	val filterMethod : FilterMethod
	val interlaceMethod : InterlaceMethod
	
	init {
		
		width = src.readInt32()
		height = src.readInt32()
		if(width <= 0 || height <= 0) throw ApngParseError("w=$width,h=$height is too small")
		
		bitDepth = src.readUInt8()
		
		var num : Int
		//
		num = src.readUInt8()
		colorType = ColorType.values().first { it.num == num }
		//
		num = src.readUInt8()
		compressionMethod = CompressionMethod.values().first { it.num == num }
		//
		num = src.readUInt8()
		filterMethod = FilterMethod.values().first { it.num == num }
		//
		num = src.readUInt8()
		interlaceMethod = InterlaceMethod.values().first { it.num == num }
	}
	
	override fun toString() =
		"ApngImageHeader(w=$width,h=$height,bits=$bitDepth,color=$colorType,interlace=$interlaceMethod)"
}
