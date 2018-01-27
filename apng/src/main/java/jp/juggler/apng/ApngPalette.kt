@file:Suppress("JoinDeclarationAndAssignment", "MemberVisibilityCanBePrivate")

package jp.juggler.apng


class ApngPalette(rgb: ByteArray) {
    val list: ByteArray
    var hasAlpha: Boolean = false

    init {
        val entryCount = rgb.size / 3
        list = ByteArray(4 * entryCount)
        for (i in 0 until entryCount) {
            list[i * 4] = 255.toByte()
            list[i * 4 + 1] = rgb[i * 3 + 0]
            list[i * 4 + 2] = rgb[i * 3 + 1]
            list[i * 4 + 3] = rgb[i * 3 + 2]
        }
    }

    override fun toString() = "palette(${list.size} entries,hasAlpha=$hasAlpha)"

    fun parseTRNS(ba: ByteArray) {
        hasAlpha = true
        for (i in 0 until Math.min(list.size, ba.size)) {
            list[i * 4] = ba[i]
        }
    }
}
