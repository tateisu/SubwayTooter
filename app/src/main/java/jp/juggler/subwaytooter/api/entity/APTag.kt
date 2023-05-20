package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory

class APTag(parser: TootParser, jsonArray: JsonArray?) {

    companion object {
        private val log = LogCategory("APTag")
    }

    val emojiList = HashMap<String, CustomEmoji>()
    val profileEmojiList = HashMap<String, NicoProfileEmoji>()
    val mentions = ArrayList<TootMention>()
    val hashtags = ArrayList<TootTag>()

    init {
        jsonArray
            ?.mapNotNull { it.cast<JsonObject>() }
            ?.forEach {
                try {
                    when (it.string("type")) {

                        "Emoji" -> {
                            val shortcode = it.string("name")!!.replace(":", "")
                            val iconUrl = it.jsonObject("icon")?.string("url")!!
                            // static iconは各サーバが生成してるのでAPレベルでは存在しない
                            if (shortcode.startsWith('@')) {
                                profileEmojiList[shortcode] = NicoProfileEmoji(
                                    url = iconUrl,
                                    shortcode = shortcode,
                                    account_url = null,
                                    account_id = EntityId.DEFAULT,
                                )
                            } else {
                                emojiList[shortcode] = CustomEmoji(
                                    shortcode = shortcode,
                                    url = iconUrl,
                                    staticUrl = iconUrl,
                                    json = it,
                                )
                            }
                        }

                        "Hashtag" -> hashtags.add(TootTag.parse(parser, it))

                        "Mention" ->
                            it.string("name")?.trimStart('@')?.let { rawAcct ->
                                val acct = Acct.parse(rawAcct)
                                mentions.add(
                                    TootMention(
                                        id = EntityId.DEFAULT,
                                        url = it.string("href")!!,
                                        acct = acct,  // may local
                                        username = acct.username
                                    )
                                )
                            }
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "APTag ctor failed.")
                }
            }
    }
}
