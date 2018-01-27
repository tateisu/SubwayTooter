@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteArrayTokenizer


class ApngFrameControl internal constructor(bat: ByteArrayTokenizer) {

    val width: Int
    val height: Int
    val xOffset: Int
    val yOffset: Int
    val delayNum: Int
    val delayDen: Int
    val disposeOp: DisposeOp
    val blendOp: BlendOp

    init {
        width = bat.readInt32()
        height = bat.readInt32()
        xOffset = bat.readInt32()
        yOffset = bat.readInt32()
        delayNum = bat.readUInt16()
        delayDen = bat.readUInt16().let{ if(it==0) 100 else it}

        var num:Int

        num = bat.readUInt8()
        disposeOp = DisposeOp.values().first{it.num==num}

        num = bat.readUInt8()
        blendOp = BlendOp.values().first{it.num==num}
    }

    override fun toString() ="ApngFrameControl(width=$width,height=$height,x=$xOffset,y=$yOffset,delayNum=$delayNum,delayDen=$delayDen,disposeOp=$disposeOp,blendOp=$blendOp)"
    
    val delayMilliseconds : Long
        get() = when(delayDen) {
	        1000 -> delayNum.toLong()
	        else -> (1000f * delayNum.toFloat() / delayDen.toFloat() + 0.5f).toLong()
        }
}