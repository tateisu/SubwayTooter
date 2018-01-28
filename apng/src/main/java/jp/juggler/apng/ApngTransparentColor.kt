@file:Suppress("MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

class ApngTransparentColor internal constructor(isGreyScale:Boolean, src: ByteSequence) {
    val red:Int
    val green:Int
    val blue:Int
    init{
        if( isGreyScale){
            val v = src.readUInt16()
            red =v
            green =v
            blue =v
        }else{
            red =src.readUInt16()
            green =src.readUInt16()
            blue =src.readUInt16()
        }
    }

    fun match(grey:Int) = red == grey
    fun match(r:Int,g:Int,b:Int) = (r==red && g == green && b == blue)
}