package jp.juggler.subwaytooter.columnviewholder

import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexboxLayout
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.dialog.launchEmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.coroutine.launchAndShowError
import org.jetbrains.anko.allCaps

fun ColumnViewHolder.addEmojiQuery(reaction: TootReaction? = null) {
    val column = this.column ?: return
    if (reaction == null) {
        launchEmojiPicker(activity, column.accessInfo, closeOnSelected = true) { emoji, _ ->
            val newReaction = when (emoji) {
                is UnicodeEmoji -> TootReaction(name = emoji.unifiedCode)
                is CustomEmoji -> TootReaction(
                    name = emoji.shortcode,
                    url = emoji.url,
                    staticUrl = emoji.staticUrl
                )
            }
            addEmojiQuery(newReaction)
        }
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
    val act = this.activity // not Button(View).getActivity()
    act.launchAndShowError {

        flEmoji.removeAllViews()

        for (invalidator in emojiQueryInvalidatorList) {
            invalidator.register(null)
        }
        emojiQueryInvalidatorList.clear()

        val options = DecodeOptions(
            act,
            column.accessInfo,
            decodeEmoji = true,
            enlargeEmoji = DecodeOptions.emojiScaleReaction,
            enlargeCustomEmoji = DecodeOptions.emojiScaleReaction,
            emojiSizeMode = column.accessInfo.emojiSizeMode(),
        )

        val buttonHeight = ActMain.boostButtonSize
        val marginBetween = (buttonHeight.toFloat() * 0.05f + 0.5f).toInt()

        val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
        val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

        val contentColor = column.getContentColor()

        TootReaction.decodeEmojiQuery(column.searchQuery).forEachIndexed { index, reaction ->
            val ssb = reaction.toSpannableStringBuilder(options, status = null)

            val b = AppCompatButton(activity).apply {
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

                allCaps = false
                tag = reaction

                setOnLongClickListener {
                    removeEmojiQuery(it.tag as? TootReaction)
                    true
                }
                // カスタム絵文字の場合、アニメーション等のコールバックを処理する必要がある
                val invalidator = NetworkEmojiInvalidator(act.handler, this)
                invalidator.text = ssb
                emojiQueryInvalidatorList.add(invalidator)
            }
            flEmoji.addView(b)
        }
    }
}
