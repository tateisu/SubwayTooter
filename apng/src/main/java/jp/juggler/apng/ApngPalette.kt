@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.getUInt8

class ApngPalette(
	src : ByteArray // repeat of R,G,B
) {
	companion object {
		// full opaque black
		const val OPAQUE = 255 shl 24
	}
	
	val list : IntArray // repeat of 0xAARRGGBB
	
	var hasAlpha : Boolean = false
	
	init {
		val entryCount = src.size / 3
		list = IntArray( entryCount)
		var pos = 0
		for(i in 0 until entryCount) {
			list[i] = OPAQUE or
				(src.getUInt8(pos) shl 16) or
				(src.getUInt8(pos+1) shl 8) or
				src.getUInt8(pos+2)
			pos+=3
		}
	}
	
	override fun toString() = "palette(${list.size} entries,hasAlpha=$hasAlpha)"
	
	// update alpha value from tRNS chunk data
	fun parseTRNS(ba : ByteArray) {
		hasAlpha = true
		for(i in 0 until Math.min(list.size, ba.size)) {
			list[i] = (list[i] and 0xffffff) or (ba.getUInt8(i) shl 24)
		}
	}
}
