package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.pref.pref
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.JsonObject
import jp.juggler.util.filterNotEmpty

class TootCard(

    //	The url associated with the card
    val url: String?,

    //	The title of the card
    val title: String?,

    //	The card description
    val description: String?,

    //	The image associated with the card, if any
    val image: String?,

    val type: String?,
    val author_name: String? = null,
    val author_url: String? = null,
    val provider_name: String? = null,
    val provider_url: String? = null,

    val blurhash: String? = null,

    val originalStatus: TootStatus? = null,
) {

    constructor(src: JsonObject) : this(
        url = src.string("url"),
        title = src.string("title"),
        description = src.string("description"),
        image = src.string("image"),

        type = src.string("type"),
        author_name = src.string("author_name"),
        author_url = src.string("author_url"),
        provider_name = src.string("provider_name"),
        provider_url = src.string("provider_url"),
        blurhash = src.string("blurhash")
    )

    constructor(parser: TootParser, src: TootStatus) : this(
        originalStatus = src,
        url = src.url,
        title = "${src.account.display_name} @${parser.getFullAcct(src.account.acct).pretty}",
        description = src.spoiler_text.filterNotEmpty()
            ?: if (parser.serviceType == ServiceType.MISSKEY) {
                src.content
            } else {
                DecodeOptions(
                    context = parser.context,
                    decodeEmoji = true,
                    mentionDefaultHostDomain = src.account
                ).decodeHTML(src.content ?: "").toString()
            },
        image = src.media_attachments
            ?.firstOrNull()
            ?.urlForThumbnail(parser.context.pref())
            ?: src.account.avatar_static,
        type = "photo"
    )
}
