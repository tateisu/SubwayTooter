package jp.juggler.subwaytooter

import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexboxLayout
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.subwaytooter.util.startMargin
import org.jetbrains.anko.allCaps




fun ColumnViewHolder.addEmojiQuery(reaction:TootReaction? =null){
    val column = this.column?:return
    if(reaction==null){
        EmojiPicker(activity, column.access_info, closeOnSelected = true) { result ->
            val newReaction = when (val emoji = result.emoji) {
                is UnicodeEmoji -> TootReaction(name = emoji.unifiedCode)
                is CustomEmoji -> TootReaction(
                    name=emoji.shortcode,
                    url = emoji.url,
                    static_url = emoji.static_url
                )
            }
            addEmojiQuery(newReaction)
        }.show()
        return
    }
    val list = TootReaction.decodeEmojiQuery(column.search_query).toMutableList()
    list.add(reaction)
    column.search_query = TootReaction.  encodeEmojiQuery(list)
    updateReactionQueryView()
    activity.app_state.saveColumnList()
}

private fun ColumnViewHolder.removeEmojiQuery(target:TootReaction?){
    target ?: return
    val list = TootReaction.decodeEmojiQuery(column?.search_query).filter { it.name != target.name }
    column?.search_query = TootReaction.encodeEmojiQuery(list)
    updateReactionQueryView()
    activity.app_state.saveColumnList()
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
        column.access_info,
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

    TootReaction.decodeEmojiQuery(column.search_query).forEachIndexed { index, reaction ->
        val ssb = reaction.toSpannableStringBuilder(options, status=null)

        val b = Button(activity).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                if(index >0 ) startMargin = marginBetween
            }
            minWidthCompat = buttonHeight

            background = ContextCompat.getDrawable(act,R.drawable.btn_bg_transparent_round6dp)

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