package jp.juggler.apng

class ApngBitmap(var width: Int, var height: Int) {

    val colors = IntArray( width * height)

    fun reset(width: Int, height: Int) {
        val newSize = width * height
        if( newSize > colors.size )
            throw ParseError("can't resize to ${width}x${height} , it's greater than initial size")
        this.width = width
        this.height = height
        colors.fill( 0,fromIndex = 0,toIndex = newSize)
    }

    inner class Pointer(private var pos: Int) {

        fun plusX(x: Int): Pointer {
            pos += x
            return this
        }

        fun setPixel(a: Int, r: Int, g: Int, b: Int): Pointer {
            colors[pos] = ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
            return this
        }

        fun setPixel(a: Byte, r: Byte, g: Byte, b: Byte): Pointer {
            colors[pos] = ((a.toInt() and 255) shl 24) or ((r.toInt() and 255) shl 16) or ((g.toInt() and 255) shl 8) or (b.toInt() and 255)
            return this
        }
    }

    fun pointer(x: Int, y: Int) = Pointer( x + y * width )
}
