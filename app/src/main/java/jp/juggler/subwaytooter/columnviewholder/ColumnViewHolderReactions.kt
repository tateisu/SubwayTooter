package jp.juggler.subwaytooter.columnviewholder

import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexboxLayout
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.subwaytooter.util.startMargin
import org.jetbrains.anko.allCaps

fun ColumnViewHolder.addEmojiQuery(reaction: TootReaction? = null) {
    val column = this.column ?: return
    if (reaction == null) {
        EmojiPicker(activity, column.accessInfo, closeOnSelected = true) { result ->
            val newReaction = when (val emoji = result.emoji) {
                is UnicodeEmoji -> TootReaction(name = emoji.unifiedCode)
                is CustomEmoji -> TootReaction(
                    name = emoji.shortcode,
                    url = emoji.url,
                    staticUrl = emoji.staticUrl
                )
            }
            addEmojiQuery(newReaction)
        }.show()
        return
    }
    val list = TootReaction.decodeEmojiQuery(column.searchQuery).toMutableList()
    list.add(reaction)
    column.searchQuery = TootReaction.encodeEmojiQuery(list)
    updateReactionQueryView()
    activity.appState.saveColumnList()
}

private fun ColumnViewHolder.removeEmojiQuery(target: TootReaction?) {
    target ?: return
    val list = TootReaction.decodeEmojiQuery(column?.searchQuery).filter { it.name != target.name }
    column?.searchQuery = TootReaction.encodeEmojiQuery(list)
    updateReactionQueryView()
    activity.appState.saveColumnList()
}

fun ColumnViewHolder.updateReactionQueryView() {
    val column = this.column ?: return

    flEmoji.removeAllViews()

    for (invalidator in emojiQueryInvalidatorList) {
        invalidator.register(null)
    }
    emojiQueryInvalidatorList.clear()

    val options = DecodeOptions(
        activity,
        column.accessInfo,
        decodeEmoji = true,
        enlargeEmoji = 1.5f,
        enlargeCustomEmoji = 1.5f
    )

    val act = this.activity // not Button(View).getActivity()

    val buttonHeight = ActMain.boostButtonSize
    val marginBetween = (buttonHeight.toFloat() * 0.05f + 0.5f).toInt()

    val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

    val contentColor = column.getContentColor()

    TootReaction.decodeEmojiQuery(column.searchQuery).forEachIndexed { index, reaction ->
        val ssb = reaction.toSpannableStringBuilder(options, status = null)

        val b = Button(activity).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                if (index > 0) startMargin = marginBetween
            }
            minWidthCompat = buttonHeight

            background = ContextCompat.getDrawable(act, R.drawable.btn_bg_transparent_round6dp)

            setTextColor(contentColor)
            setPadding(paddingH, paddingV, paddingH, paddingV)

            text = ssb

            allCaps = false
            tag = reaction

            setOnLongClickListener {
                removeEmojiQuery(it.tag as? TootReaction)
                true
            }
            // カスタム絵文字の場合、アニメーション等のコールバックを処理する必要がある
            val invalidator = NetworkEmojiInvalidator(act.handler, this)
            invalidator.register(ssb)
            emojiQueryInvalidatorList.add(invalidator)
        }
        flEmoji.addView(b)
    }
}
