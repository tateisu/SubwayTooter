@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

class ApngFrameControl internal constructor(src : ByteSequence,var sequenceNumber:Int) {
	
	val width : Int
	val height : Int
	val xOffset : Int
	val yOffset : Int
	val delayNum : Int
	val delayDen : Int
	val disposeOp : DisposeOp
	val blendOp : BlendOp
	
	init {
		width = src.readInt32()
		height = src.readInt32()
		xOffset = src.readInt32()
		yOffset = src.readInt32()
		delayNum = src.readUInt16()
		delayDen = src.readUInt16().let { if(it == 0) 100 else it }
		
		var num : Int
		
		num = src.readUInt8()
		disposeOp = DisposeOp.values().first { it.num == num }
		
		num = src.readUInt8()
		blendOp = BlendOp.values().first { it.num == num }
	}
	
	override fun toString() =
		"ApngFrameControl(width=$width,height=$height,x=$xOffset,y=$yOffset,delayNum=$delayNum,delayDen=$delayDen,disposeOp=$disposeOp,blendOp=$blendOp)"
	
	val delayMilliseconds : Long
		get() = when(delayDen) {
			1000 -> delayNum.toLong()
			else -> (1000f * delayNum.toFloat() / delayDen.toFloat() + 0.5f).toLong()
		}
}