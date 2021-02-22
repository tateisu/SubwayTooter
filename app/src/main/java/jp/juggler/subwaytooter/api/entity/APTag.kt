package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.cast
import java.util.*

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
            ?.forEach { it ->
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
                                    apDomain = parser.apDomain,
                                    shortcode = shortcode,
                                    url = iconUrl,
                                    static_url = iconUrl,
                                )
                            }
                        }

                        "Hashtag" -> hashtags.add(TootTag.parse(parser, it))

                        "Mention" ->
                            Acct.parse(it.string("name")!!)
                                .let { acct ->
                                    mentions.add(TootMention(
                                        id = EntityId.DEFAULT,
                                        url = it.string("href")!!,
                                        acct = acct,  // may local
                                        username = acct.username
                                    ))

                                }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
    }
}