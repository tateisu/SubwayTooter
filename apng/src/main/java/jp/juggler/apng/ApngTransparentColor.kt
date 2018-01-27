package jp.juggler.apng

import jp.juggler.apng.util.ByteArrayTokenizer

class ApngTransparentColor internal constructor(isGreyScale:Boolean, bat: ByteArrayTokenizer) {
    val red:Int
    val green:Int
    val blue:Int
    init{
        if( isGreyScale){
            val v = bat.readUInt16()
            red =v
            green =v
            blue =v
        }else{
            red =bat.readUInt16()
            green =bat.readUInt16()
            blue =bat.readUInt16()
        }
    }

    fun match(grey:Int) = red == grey
    fun match(r:Int,g:Int,b:Int) = (r==red && g == green && b == blue)
}