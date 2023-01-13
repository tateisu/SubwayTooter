package jp.juggler.subwaytooter.emoji

import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.Mappable
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty

sealed interface EmojiBase

class UnicodeEmoji(
    // SVGの場合はasset resourceの名前
    val assetsName: String? = null,
    // PNGの場合はdrawable id
    @DrawableRes val drawableId: Int = 0,
) : EmojiBase, Comparable<UnicodeEmoji> {

    val namesLower = ArrayList<String>()

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

class CustomEmoji(
    val apDomain: Host,
    val shortcode: String, // shortcode (コロンを含まない)
    val url: String, // 画像URL
    val staticUrl: String?, // アニメーションなしの画像URL
    val aliases: ArrayList<String>? = null,
    val alias: String? = null,
    val visibleInPicker: Boolean = true,
    val category: String? = null,
) : EmojiBase, Mappable<String> {

    fun makeAlias(alias: String) = CustomEmoji(
        apDomain = apDomain,
        shortcode = shortcode,
        url = url,
        staticUrl = staticUrl,
        alias = alias
    )

    override val mapKey: String
        get() = shortcode

    fun chooseUrl() = when {
        PrefB.bpDisableEmojiAnimation() -> staticUrl
        else -> url
    }

    companion object {

        val decode: (Host, Host, JsonObject) -> CustomEmoji = { apDomain, _, src ->
            CustomEmoji(
                apDomain = apDomain,
                shortcode = src.stringOrThrow("shortcode"),
                url = src.stringOrThrow("url"),
                staticUrl = src.string("static_url"),
                visibleInPicker = src.optBoolean("visible_in_picker", true),
                category = src.string("category")
            )
        }

        val decodeMisskey: (Host, Host, JsonObject) -> CustomEmoji = { apDomain, _, src ->
            val url = src.string("url") ?: error("missing url")

            CustomEmoji(
                apDomain = apDomain,
                shortcode = src.string("name") ?: error("missing name"),
                url = url,
                staticUrl = url,
                aliases = parseAliases(src.jsonArray("aliases")),
                category = src.string("category"),
            )
        }

        val decodeMisskey13: (Host, Host, JsonObject) -> CustomEmoji = { apDomain, apiHost, src ->
            val name = src.string("name") ?: error("missing name")
            val url = "https://${apiHost.ascii}/emoji/$name.webp"
            CustomEmoji(
                apDomain = apDomain,
                shortcode = name,
                url = url,
                staticUrl = url,
                aliases = parseAliases(src.jsonArray("aliases")),
                category = src.string("category"),
            )
        }

        private fun parseAliases(src: JsonArray?): ArrayList<String>? {
            var dst = null as ArrayList<String>?
            if (src != null) {
                val size = src.size
                if (size > 0) {
                    dst = ArrayList(size)
                    src.forEach {
                        val str = it?.toString()?.notEmpty()
                        if (str != null) dst.add(str)
                    }
                }
            }
            return if (dst?.isNotEmpty() == true) dst else null
        }
    }
}
