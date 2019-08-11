@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

class ApngFrameControl (
	val width : Int,
	val height : Int,
	val xOffset : Int,
	val yOffset : Int,
	val disposeOp : DisposeOp,
	val blendOp : BlendOp,
	val sequenceNumber:Int,
	val delayMilliseconds: Long
) {
	
	companion object{
		internal fun parse(src : ByteSequence, sequenceNumber:Int) :ApngFrameControl{
			val width = src.readInt32()
			val height = src.readInt32()
			val xOffset = src.readInt32()
			val yOffset = src.readInt32()
			val delayNum = src.readUInt16()
			val delayDen = src.readUInt16().let { if(it == 0) 100 else it }
			
			var num : Int
			
			num = src.readUInt8()
			val disposeOp = DisposeOp.values().first { it.num == num }
			
			num = src.readUInt8()
			val blendOp = BlendOp.values().first { it.num == num }

			return ApngFrameControl(
				width =width,
				height = height,
				xOffset = xOffset,
				yOffset = yOffset,
				disposeOp = disposeOp,
				blendOp = blendOp,
				sequenceNumber = sequenceNumber,
				delayMilliseconds = when(delayDen) {
					0,1000 -> delayNum.toLong()
					else -> (1000f * delayNum.toFloat() / delayDen.toFloat() + 0.5f).toLong()
				}
			)
		}
	}
	
	override fun toString() =
		"ApngFrameControl(width=$width,height=$height,x=$xOffset,y=$yOffset,delayMilliseconds=$delayMilliseconds,disposeOp=$disposeOp,blendOp=$blendOp)"

}