package jp.juggler.emoji

import androidx.annotation.DrawableRes
import java.util.ArrayList

open class EmojiBase

class UnicodeEmoji(
    // SVGの場合はasset resourceの名前
    val assetsName: String? = null,
    // PNGの場合はdrawable id
    @DrawableRes val drawableId: Int = 0,
) : EmojiBase(), Comparable<UnicodeEmoji> {

    // unified code used in picker.
    var unifiedCode = ""

    // unified name used in picker recents.
    var unifiedName = ""

    // returns true if using svg.
    val isSvg: Boolean
        get() = assetsName != null

    // parent of skin tone variation. may null.
    var toneParent: UnicodeEmoji? = null

    // list of pair of toneCode , emoji. sorted by toneCode.
    val toneChildren = ArrayList<Pair<String, UnicodeEmoji>>()

    ///////////////////////////////////////
    // overrides for hash and sort.

    override fun equals(other: Any?): Boolean =
        unifiedCode == (other as? UnicodeEmoji)?.unifiedCode

    override fun hashCode(): Int =
        unifiedCode.hashCode()

    override fun toString(): String =
        "Emoji($unifiedCode,$unifiedName)"

    override fun compareTo(other: UnicodeEmoji): Int =
        unifiedCode.compareTo(other.unifiedCode)
}