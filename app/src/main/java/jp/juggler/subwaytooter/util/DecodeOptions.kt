package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import jp.juggler.subwaytooter.api.entity.HostAndDomain
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji
import jp.juggler.subwaytooter.api.entity.TootAttachmentLike
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.data.WordTrieTree
import jp.juggler.util.data.clip
import org.jetbrains.anko.collections.forEachReversedByIndex

class DecodeOptions(
    val context: Context? = null,
    var linkHelper: LinkHelper? = null,
    var short: Boolean = false,
    var decodeEmoji: Boolean = false,
    var attachmentList: ArrayList<TootAttachmentLike>? = null,
    var linkTag: Any? = null,
    var emojiMapCustom: Map<String, CustomEmoji>? = null,
    var emojiMapProfile: Map<String, NicoProfileEmoji>? = null,
    var highlightTrie: WordTrieTree? = null,
    var unwrapEmojiImageTag: Boolean = false,
    var enlargeCustomEmoji: Float = emojiScaleMastodon,
    var enlargeEmoji: Float = emojiScaleMastodon,
    // force use HTML instead of Misskey Markdown
    var forceHtml: Boolean = false,
    var mentionFullAcct: Boolean = false,
    var mentions: ArrayList<TootMention>? = null,
    // Account.note などmentionsがない状況でメンションリンクをfull acct化するにはアカウント等からapDomainを補う必要がある
    // MFMはメンションのホスト名を補うのに閲覧者ではなく投稿作者のホスト名を必要とする
    var authorDomain: HostAndDomain? = null,
    var emojiSizeMode: EmojiSizeMode = EmojiSizeMode.Square,
) {
    companion object {
        var emojiScaleMastodon = 1f
        var emojiScaleMisskey = 1f
        var emojiScaleReaction = 1f
        var emojiScaleUserName = 1f
        fun reloadEmojiScale() {
            emojiScaleMastodon = PrefS.spEmojiSizeMastodon.toFloat().div(100f).clip(0.5f, 5f)
            emojiScaleMisskey = PrefS.spEmojiSizeMisskey.toFloat().div(100f).clip(0.5f, 5f)
            emojiScaleReaction = PrefS.spEmojiSizeReaction.toFloat().div(100f).clip(0.5f, 5f)
            emojiScaleUserName = PrefS.spEmojiSizeUserName.toFloat().div(100f).clip(0.5f, 5f)
        }
    }

    internal fun isMediaAttachment(url: String?): Boolean =
        url?.let { u -> attachmentList?.any { it.hasUrl(u) } } ?: false

    // OUTPUT
    var highlightSound: HighlightWord? = null
    var highlightSpeech: HighlightWord? = null
    var highlightAny: HighlightWord? = null

    ////////////////////////
    // decoder

    // AndroidのStaticLayoutはパラグラフ中に絵文字が沢山あると異常に遅いので、絵文字が連続して登場したら改行文字を挿入する
    private fun SpannableStringBuilder.workaroundForEmojiLineBreak(): SpannableStringBuilder {

        val maxEmojiPerLine = if (linkHelper?.isMisskey == true) 5 else 12

        val spans = getSpans(0, length, ReplacementSpan::class.java)
        if (spans != null && spans.size > maxEmojiPerLine) {

            val insertList = ArrayList<Int>()
            var emojiCount = 1
            var preEnd: Int? = null
            for (span in spans.sortedBy { getSpanStart(it) }) {
                val start = getSpanStart(span)
                if (preEnd != null) {

                    // true if specified range includes line feed
                    fun containsLineFeed(start: Int, end: Int): Boolean {
                        for (i in start until end) {
                            if (get(i) == '\n') return true
                        }
                        return false
                    }

                    if (containsLineFeed(preEnd, start)) {
                        emojiCount = 1
                    } else if (++emojiCount > maxEmojiPerLine) {
                        insertList.add(start)
                        emojiCount = 1
                    }
                }
                preEnd = getSpanEnd(span)
            }
            // 後ろから順に挿入する
            insertList.forEachReversedByIndex { insert(it, "\n") }
        }
        return this
    }

    fun decodeHTML(html: String?) =
        HTMLDecoder.decodeHTML(this, html).workaroundForEmojiLineBreak()

    fun decodeEmoji(s: String?): Spannable =
        EmojiDecoder.decodeEmoji(this, s ?: "").workaroundForEmojiLineBreak()
}
