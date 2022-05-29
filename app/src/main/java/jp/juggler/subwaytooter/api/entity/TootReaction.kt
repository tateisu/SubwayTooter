package jp.juggler.subwaytooter.api.entity

import android.text.Spannable
import android.text.SpannableStringBuilder
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.EmojiDecoder
import jp.juggler.util.*
import java.util.*

class TootReaction(
    // (fedibird絵文字リアクション)  unicode絵文字はunicodeそのまま、 カスタム絵文字はコロンなしのshortcode
    // (Misskey)カスタム絵文字は前後にコロンがつく
    val name: String,

    // カスタム絵文字の場合は定義される
    val url: String? = null,
    val staticUrl: String? = null,

    // (fedibird絵文字リアクション) 通知オブジェクト直下ではcountは常に0)
    var count: Long = 0,

    // (fedibird絵文字リアクション) 通知オブジェクト直下ではmeは常にfalse
    // 告知のストリーミングイベントではmeは定義されない
    var me: Boolean = false,

    // 告知のストリーミングイベントでは告知IDが定義される
    val announcement_id: EntityId? = null,

    // (fedibird絵文字リアクション) userストリームのemoji_reactionイベントで設定される。
    val status_id: EntityId? = null,
) {
    companion object {

        private fun appendDomain(name: String, domain: String?) =
            if (domain?.isNotEmpty() == true) {
                "$name@$domain"
            } else {
                name
            }

        // Fedibirdの投稿や通知に含まれる
        fun parseFedibird(src: JsonObject) = TootReaction(
            // (fedibird絵文字リアクション)  リモートのカスタム絵文字の場合はdomainが指定される
            name = appendDomain(src.string("name") ?: "?", src.string("domain")),
            url = src.string("url"),
            staticUrl = src.string("static_url"),
            count = src.long("count") ?: 0,
            me = src.boolean("me") ?: false,
            announcement_id = EntityId.mayNull(src.string("announcement_id")),
            status_id = EntityId.mayNull(src.string("status_id")),
        )

        // Misskeyの通知にあるreaction文字列
        fun parseMisskey(name: String?, count: Long = 0L) =
            name?.let {
                TootReaction(name = it, count = count)
            }

        private val misskeyOldReactions = mapOf(
            "like" to "\ud83d\udc4d",
            "love" to "\u2665",
            "laugh" to "\ud83d\ude06",
            "hmm" to "\ud83e\udd14",
            "surprise" to "\ud83d\ude2e",
            "congrats" to "\ud83c\udf89",
            "angry" to "\ud83d\udca2",
            "confused" to "\ud83d\ude25",
            "rip" to "\ud83d\ude07",
            "pudding" to "\ud83c\udf6e",
            "star" to "\u2B50", // リモートからのFavを示す代替リアクション。ピッカーには表示しない
        )

        private val reCustomEmoji = """\A:([^:]+):\z""".toRegex()

        fun getAnotherExpression(reaction: String): String? {
            val customCode =
                reCustomEmoji.find(reaction)?.groupValues?.elementAtOrNull(1) ?: return null
            val cols = customCode.split("@")
            val name = cols.elementAtOrNull(0)
            val domain = cols.elementAtOrNull(1)
            return if (domain == null) ":$name@.:" else if (domain == ".") ":$name:" else null
        }

        fun equalsName(a: String?, b: String?) = when {
            a == null -> b == null
            b == null -> false
            else -> a == b || getAnotherExpression(a) == b || a == getAnotherExpression(b)
        }

        val UNKNOWN = TootReaction(name = "?")

        private fun isUnicodeEmoji(code: String): Boolean =
            code.any { it.code >= 0x7f }

        fun isCustomEmoji(code: String): Boolean = !isUnicodeEmoji(code)

        fun splitEmojiDomain(code: String): Pair<String?, String?> {
            // unicode絵文字ならnull,nullを返す
            if (isUnicodeEmoji(code)) return Pair(null, null)
            // Misskeyカスタム絵文字リアクションのコロンを除去
            val a = code.replace(":", "")
            val idx = a.indexOf("@")
            return if (idx == -1) Pair(a, null) else Pair(a.substring(0, idx), a.substring(idx + 1))
        }

        fun canReaction(
            accessInfo: SavedAccount,
            ti: TootInstance? = TootInstance.getCached(accessInfo),
        ) = InstanceCapability.emojiReaction(accessInfo, ti)

        fun decodeEmojiQuery(jsonText: String?): List<TootReaction> =
            jsonText.notEmpty()?.let { src ->
                try {
                    src.decodeJsonArray().objectList().map { parseFedibird(it) }
                } catch (ex: Throwable) {
                    ColumnViewHolder.log.e(ex, "updateReactionQueryView: decode failed.")
                    null
                }
            } ?: emptyList()

        fun encodeEmojiQuery(src: List<TootReaction>): String =
            jsonArray {
                for (reaction in src) {
                    add(reaction.jsonFedibird())
                }
            }.toString()

        fun urlToSpan(options: DecodeOptions, code: String, url: String) =
            SpannableStringBuilder(code).apply {
                setSpan(
                    NetworkEmojiSpan(url, scale = options.enlargeCustomEmoji),
                    0,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

        fun toSpannableStringBuilder(
            options: DecodeOptions,
            code: String,
            url: String?,
        ): SpannableStringBuilder {

            url?.let { return urlToSpan(options, code, url) }

            if (options.linkHelper?.isMisskey == true) {
                // 古い形式の絵文字はUnicode絵文字にする
                misskeyOldReactions[code]?.let {
                    return EmojiDecoder.decodeEmoji(options, it)
                }
            }

            return EmojiDecoder.decodeEmoji(options, code)
        }

        private fun isSameDomain(accessInfo: SavedAccount, domain: String?) = when (domain) {
            null, "", ".", accessInfo.apDomain.ascii -> true
            else -> false
        }
    }

    private fun chooseUrl() = when {
        PrefB.bpDisableEmojiAnimation() -> staticUrl
        else -> url
    }

    fun splitEmojiDomain() =
        splitEmojiDomain(name)

    override fun equals(other: Any?): Boolean =
        when (other) {
            is TootReaction -> equalsName(this.name, other.name)
            is String -> equalsName(this.name, other)
            else -> false
        }

    override fun hashCode(): Int =
        name.hashCode()

    fun toSpannableStringBuilder(
        options: DecodeOptions,
        status: TootStatus?,
    ): SpannableStringBuilder {

        val code = this.name

        if (options.linkHelper?.isMisskey == true) {

            // 古い形式の絵文字はUnicode絵文字にする
            misskeyOldReactions[code]?.let {
                return EmojiDecoder.decodeEmoji(options, it)
            }

            // カスタム絵文字
            val customCode = reCustomEmoji.find(code)?.groupValues?.elementAtOrNull(1)
            if (customCode != null) {
                // 投稿中に絵文字の情報があればそれを使う
                status?.custom_emojis?.get(customCode)
                    ?.chooseUrl()
                    ?.notEmpty()
                    ?.let { return urlToSpan(options, code, it) }

                // ストリーミングからきた絵文字などの場合は情報がない
                val accessInfo = options.linkHelper as? SavedAccount
                val cols = customCode.split("@", limit = 2)
                val key = cols.elementAtOrNull(0)
                val domain = cols.elementAtOrNull(1)
                if (key != null && accessInfo != null && isSameDomain(accessInfo, domain)) {
                    // デコードオプションのアカウント情報と同じドメインの絵文字なら、
                    // そのドメインの絵文字一覧を取得済みなら
                    // それを使う
                    App1.custom_emoji_lister
                        .getMapNonBlocking(accessInfo)
                        ?.get(key)
                        ?.chooseUrl()
                        ?.notEmpty()
                        ?.let { return urlToSpan(options, code, it) }
                }
                // 見つからない場合もある
            }
        } else {
            chooseUrl()?.notEmpty()?.let { return urlToSpan(options, code, it) }
            // 見つからない場合はあるのだろうか…？
        }
        // フォールバック
        // unicode絵文字、もしくは :xxx: などのshortcode表現
        return EmojiDecoder.decodeEmoji(options, code)
    }

    // リアクションカラムの絵文字絞り込み用
    fun jsonFedibird() = jsonObject {
        val (basename, domain) = splitEmojiDomain()
        put("name", basename ?: name)
        putNotNull("domain", domain)
        putNotNull("url", url)
        putNotNull("static_url", staticUrl)
    }
}

class TootReactionSet(val isMisskey: Boolean) : LinkedList<TootReaction>() {

    fun isMyReaction(reaction: TootReaction?): Boolean {
        return reaction?.me == true
    }

    fun hasReaction() = any { it.count > 0 }

    fun hasMyReaction() = any { it.count > 0 && isMyReaction(it) }

    private fun getRaw(name: String?): TootReaction? =
        find { it.name == name }

    operator fun get(name: String?): TootReaction? = when {
        name == null || name.isEmpty() -> null
        isMisskey -> getRaw(name) ?: getRaw(TootReaction.getAnotherExpression(name))
        else -> getRaw(name)
    }

    companion object {
        fun parseMisskey(src: JsonObject?, myReactionCode: String? = null) =
            if (src == null) {
                null
            } else TootReactionSet(isMisskey = true).apply {
                for (entry in src.entries) {
                    val key = entry.key.notEmpty() ?: continue
                    val v = src.long(key)?.notZero() ?: continue
                    add(TootReaction(name = key, count = v))
                }
                if (myReactionCode != null) {
                    forEach { it.me = (it.name == myReactionCode) }
                }
            }.notEmpty()

        fun parseFedibird(src: JsonArray? = null) =
            if (src == null) {
                null
            } else TootReactionSet(isMisskey = false).apply {
                src.objectList().forEach {
                    val tr = TootReaction.parseFedibird(it)
                    if (tr.count > 0) add(tr)
                }
            }.notEmpty()
    }
}
