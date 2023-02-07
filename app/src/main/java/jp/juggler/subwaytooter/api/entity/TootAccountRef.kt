package jp.juggler.subwaytooter.api.entity

import android.text.Spannable
import jp.juggler.subwaytooter.api.TootAccountMap
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.DecodeOptions

class TootAccountRef private constructor(
    val mapId: Int,
    //	The account's display name
    val decoded_display_name: Spannable,
    val decoded_note: Spannable,
) : TimelineItem() {
    var _orderId: EntityId? = null

    override fun getOrderId(): EntityId = _orderId ?: get().id

    fun get() = TootAccountMap.find(this)

    companion object {
        fun notNull(parser: TootParser, account: TootAccount) =
            tootAccountRef(parser, account)

        fun mayNull(parser: TootParser, account: TootAccount?): TootAccountRef? {
            return when (account) {
                null -> null
                else -> tootAccountRef(parser, account)
            }
        }

        fun wrapList(
            parser: TootParser,
            src: Iterable<TootAccount>,
        ): ArrayList<TootAccountRef> {
            val dst = ArrayList<TootAccountRef>()
            for (a in src) {
                dst.add(tootAccountRef(parser, a))
            }
            return dst
        }

        fun tootAccountRef(parser: TootParser, account: TootAccount) =
            TootAccountRef(
                mapId = TootAccountMap.register(parser, account),
                decoded_display_name = account.decodeDisplayName(parser.context),
                decoded_note = DecodeOptions(
                    parser.context,
                    parser.linkHelper,
                    short = true,
                    decodeEmoji = true,
                    emojiMapProfile = account.profile_emojis,
                    emojiMapCustom = account.custom_emojis,
                    unwrapEmojiImageTag = true,
                    authorDomain = account,
                ).decodeHTML(account.note),
            )
    }
}
