@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteArrayTokenizer

class ApngBackground internal constructor(colorType: ColorType, bat: ByteArrayTokenizer) {

    val red: Int
    val green: Int
    val blue: Int
    val index: Int

    init {
        when (colorType) {
            ColorType.GREY, ColorType.GREY_ALPHA -> {
                val v = bat.readUInt16()
                red = v
                green = v
                blue = v
                index = -1
            }
            ColorType.RGB, ColorType.RGBA -> {
                red = bat.readUInt16()
                green = bat.readUInt16()
                blue = bat.readUInt16()
                index = -1
            }
            ColorType.INDEX -> {
                red = -1
                green = -1
                blue = -1
                index = bat.readUInt8()
            }
        }
    }
}