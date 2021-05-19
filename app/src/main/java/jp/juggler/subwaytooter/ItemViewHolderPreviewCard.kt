package jp.juggler.subwaytooter

import android.view.View
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.MediaShown
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.HTMLDecoder
import jp.juggler.util.ellipsize
import jp.juggler.util.vg

private fun addLinkAndCaption(
    sb: StringBuilder,
    header: String?,
    url: String?,
    caption: String?
) {

    if (url.isNullOrEmpty() && caption.isNullOrEmpty()) return

    if (sb.isNotEmpty()) sb.append("<br>")

    if (header?.isNotEmpty() == true) {
        sb.append(HTMLDecoder.encodeEntity(header)).append(": ")
    }

    if (url != null && url.isNotEmpty()) {
        sb.append("<a href=\"").append(HTMLDecoder.encodeEntity(url)).append("\">")
    }
    sb.append(
        HTMLDecoder.encodeEntity(
            when {
                caption != null && caption.isNotEmpty() -> caption
                url != null && url.isNotEmpty() -> url
                else -> "???"
            }
        )
    )

    if (url != null && url.isNotEmpty()) {
        sb.append("</a>")
    }

}

fun ItemViewHolder.showPreviewCard(status: TootStatus) {

    if (Pref.bpDontShowPreviewCard(activity.pref)) return

    val card = status.card ?: return

    // 会話カラムで返信ステータスなら捏造したカードを表示しない
    if (column.type == ColumnType.CONVERSATION
        && card.originalStatus != null
        && status.reply != null
    ) {
        return
    }

    var bShowOuter = false

    val sb = StringBuilder()
    fun showString() {
        if (sb.isNotEmpty()) {
            val text = DecodeOptions(
                activity, access_info,
                forceHtml = true,
                mentionDefaultHostDomain = status.account
            ).decodeHTML(sb.toString())
            if (text.isNotEmpty()) {
                tvCardText.visibility = View.VISIBLE
                tvCardText.text = text
                bShowOuter = true
            }
        }
    }

    if (status.reblog?.quote_muted == true) {
        addLinkAndCaption(
            sb,
            null,
            card.url,
            activity.getString(R.string.muted_quote)
        )
        showString()
    } else {
        addLinkAndCaption(
            sb,
            activity.getString(R.string.card_header_card),
            card.url,
            card.title
        )

        addLinkAndCaption(
            sb,
            activity.getString(R.string.card_header_author),
            card.author_url,
            card.author_name
        )

        addLinkAndCaption(
            sb,
            activity.getString(R.string.card_header_provider),
            card.provider_url,
            card.provider_name
        )

        val description = card.description
        if (description != null && description.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("<br>")

            val limit = Pref.spCardDescriptionLength.toInt(activity.pref)

            sb.append(
                HTMLDecoder.encodeEntity(
                    ellipsize(
                        description,
                        if (limit <= 0) 64 else limit
                    )
                )
            )
        }

        showString()

        val image = card.image
        if (flCardImage.vg(image?.isNotEmpty() == true) != null) {

            flCardImage.layoutParams.height = if (card.originalStatus != null) {
                activity.avatarIconSize
            } else {
                activity.app_state.media_thumb_height
            }

            val imageUrl = access_info.supplyBaseUrl(image)
            ivCardImage.setImageUrl(activity.pref, 0f, imageUrl, imageUrl)

            btnCardImageShow.blurhash = card.blurhash

            // show about card outer
            bShowOuter = true

            // show about image content
            val default_shown = when {
                column.hide_media_default -> false
                access_info.dont_hide_nsfw -> true
                else -> !status.sensitive
            }
            val is_shown = MediaShown.isShown(status, default_shown)
            llCardImage.vg(is_shown)
            btnCardImageShow.vg(!is_shown)
        }
    }

    if (bShowOuter) llCardOuter.visibility = View.VISIBLE
}
