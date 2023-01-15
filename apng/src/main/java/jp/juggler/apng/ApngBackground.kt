@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng

import jp.juggler.apng.util.ByteSequence

class ApngBackground internal constructor(colorType: ColorType, src: ByteSequence) {

    val red: Int
    val green: Int
    val blue: Int
    val index: Int

    init {
        when (colorType) {
            ColorType.GREY, ColorType.GREY_ALPHA -> {
                val v = src.readUInt16()
                red = v
                green = v
                blue = v
                index = -1
            }

            ColorType.RGB, ColorType.RGBA -> {
                red = src.readUInt16()
                green = src.readUInt16()
                blue = src.readUInt16()
                index = -1
            }

            ColorType.INDEX -> {
                red = -1
                green = -1
                blue = -1
                index = src.readUInt8()
            }
        }
    }
}