package jp.juggler.subwaytooter.itemviewholder

import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.isConversation
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.daoMediaShown
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.HTMLDecoder
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.util.data.ellipsize
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.ui.textOrGone
import jp.juggler.util.ui.vg

private fun addLinkAndCaption(
    sb: StringBuilder,
    header: String?,
    url: String?,
    caption: String?,
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

    if (PrefB.bpDontShowPreviewCard.value) return

    val card = status.card ?: return

    // 会話カラムで返信ステータスなら捏造したカードを表示しない
    if (column.isConversation &&
        card.originalStatus != null &&
        status.reply != null
    ) {
        return
    }

    var bShowOuter = false

    val limit = PrefS.spCardDescriptionLength.toInt().takeIf { it > 0 } ?: 64

    val sb = StringBuilder()
    fun showString() {
        if (sb.isNotEmpty()) {
            val text = DecodeOptions(
                activity, accessInfo,
                forceHtml = true,
                authorDomain = status.account,
                emojiSizeMode = accessInfo.emojiSizeMode(),
            ).decodeHTML(sb.toString())

            if (text.isNotEmpty()) {
                tvCardText.textOrGone = text
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
            card.title?.ellipsizeDot3(limit)
        )

        addLinkAndCaption(
            sb,
            activity.getString(R.string.card_header_author),
            card.author_url,
            card.author_name?.ellipsizeDot3(limit)
        )

        addLinkAndCaption(
            sb,
            activity.getString(R.string.card_header_provider),
            card.provider_url,
            card.provider_name?.ellipsizeDot3(limit)
        )

        val description = card.description
        if (description != null && description.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("<br>")
            sb.append(HTMLDecoder.encodeEntity(ellipsize(description, limit)))
        }

        showString()

        val image = card.image
        if (flCardImage.vg(image?.isNotEmpty() == true) != null) {

            flCardImage.layoutParams.height = if (card.originalStatus != null) {
                activity.avatarIconSize
            } else {
                activity.appState.mediaThumbHeight
            }

            val imageUrl = accessInfo.supplyBaseUrl(image)
            ivCardImage.setImageUrl(0f, imageUrl, imageUrl)

            btnCardImageShow.blurhash = card.blurhash
            btnCardImageHide.imageTintList = contentColorCsl

            // show about card outer
            bShowOuter = true

            // show about image content
            val defaultShown = when {
                column.hideMediaDefault -> false
                accessInfo.dontHideNsfw -> true
                else -> !status.sensitive
            }
            val isShown = daoMediaShown.isShown(status, defaultShown)
            llCardImage.vg(isShown)
            btnCardImageShow.vg(!isShown)
        }
    }

    if (bShowOuter) llCardOuter.visibility = View.VISIBLE
}
