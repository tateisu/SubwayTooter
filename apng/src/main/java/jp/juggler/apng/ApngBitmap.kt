@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package jp.juggler.apng

class ApngBitmap(var width: Int, var height: Int) {

    // each int value contains 0xAARRGGBB
    val colors = IntArray(width * height)

    // widthとheightを再指定する。ビットマップはそのまま再利用する
    fun reset(width: Int, height: Int) {
        val newSize = width * height
        if (newSize > colors.size)
            throw ApngParseError("can't resize to $width x $height , it's greater than initial size")
        this.width = width
        this.height = height
        // 透明な黒で初期化する
        colors.fill(0, fromIndex = 0, toIndex = newSize)
    }

    // ビットマップ中の位置を保持して、ピクセルへの書き込みと位置の更新を行う
    inner class Pointer {

        private var pos: Int = 0
        var step: Int = 1

        fun setPixel(argb: Int) = apply { colors[pos] = argb }

        fun setPixel(a: Int, r: Int, g: Int, b: Int) = setPixel(
            ((a and 255) shl 24) or
                    ((r and 255) shl 16) or
                    ((g and 255) shl 8) or
                    (b and 255)
        )

        fun setOffset(pos: Int = 0, step: Int = 1) = apply {
            this.pos = pos
            this.step = step
        }

        fun setXY(x: Int, y: Int, step: Int = 1) = setOffset(x + y * width, step)

        fun plus(x: Int) = apply { pos += x }

        fun next() = plus(step)

        val color: Int
            get() = colors[pos]

        val alpha: Int
            get() = (colors[pos] shr 24) and 255

        val red: Int
            get() = (colors[pos] shr 16) and 255

        val green: Int
            get() = (colors[pos] shr 8) and 255

        val blue: Int
            get() = (colors[pos]) and 255
    }

    fun pointer() = Pointer()
}
