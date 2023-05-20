package jp.juggler.subwaytooter.emoji

import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.Mappable
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.jsonObjectOf
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.toMutableMap

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
    val shortcode: String, // shortcode (コロンを含まない)
    val url: String, // 画像URL
    val staticUrl: String?, // アニメーションなしの画像URL
    val aliases: List<String>? = null,
    val alias: String? = null,
    val visibleInPicker: Boolean = true,
    val category: String? = null,
    val aspect: Float? = null,
    val json: JsonObject,
) : EmojiBase, Mappable<String> {

    fun makeAlias(alias: String) = CustomEmoji(
        shortcode = shortcode,
        url = url,
        staticUrl = staticUrl,
        alias = alias,
        json = json,
    )

    override val mapKey: String
        get() = shortcode

    fun chooseUrl() = when {
        PrefB.bpDisableEmojiAnimation.value -> staticUrl
        else -> url
    }

    companion object {

        fun decodeMastodon(src: JsonObject): CustomEmoji {
            val w = src.double("width")
            val h = src.double("height")
            val aspect = when {
                w == null || h == null || w < 1.0 || h < 1.0 -> null
                else -> (w / h).toFloat()
            }
            return CustomEmoji(
                shortcode = src.stringOrThrow("shortcode"),
                url = src.stringOrThrow("url"),
                staticUrl = src.string("static_url"),
                visibleInPicker = src.optBoolean("visible_in_picker", true),
                category = src.string("category"),
                aspect = aspect,
                json = src,
            )
        }

        fun decodeMisskey(src: JsonObject): CustomEmoji {
            val url = src.string("url") ?: error("missing url")
            return CustomEmoji(
                shortcode = src.string("name") ?: error("missing name"),
                url = url,
                staticUrl = url,
                category = src.string("category"),
                json = src,
            )
        }

        fun decodeMisskey13(apiHost: Host, src: JsonObject): CustomEmoji {
            val name = src.string("name") ?: error("missing name")
            val url = "https://${apiHost.ascii}/emoji/$name.webp"
            return CustomEmoji(
                shortcode = name,
                url = url,
                staticUrl = url,
                aliases = src.jsonArray("aliases")?.stringList()?.filter { it.isNotEmpty() }
                    ?.notEmpty(),
                category = src.string("category"),
                json = src,
            )
        }

        // 入力は name→URLの単純なマップ
        fun decodeMisskey12ReactionEmojis(src: JsonObject?): MutableMap<String, CustomEmoji>? =
            src?.entries?.mapNotNull {
                val (k, v) = it
                when (val url = (v as? String)) {
                    null, "" -> null
                    else -> k to CustomEmoji(
                        shortcode = k,
                        url = url,
                        staticUrl = url + (if (url.contains('?')) '&' else '?') + "static=1",
                        json = jsonObjectOf("name" to k, "url" to url)
                    )
                }
            }?.notEmpty()?.toMutableMap()
    }
}
