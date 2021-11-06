package jp.juggler.subwaytooter.mfm

import android.content.Context
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.SvgEmojiSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.util.*
import java.util.*


// 装飾つきテキストの出力時に使うデータの集まり
class SpanOutputEnv(
    val options: DecodeOptions,
    val sb: SpannableStringBuilderEx,
) {
    val context: Context = options.context ?: error("missing context")

    val decorationEnabled = PrefB.bpMfmDecorationEnabled(context)
    val showUnsupportedMarkup = PrefB.bpMfmDecorationShowUnsupportedMarkup(context)

    val font_bold = ActMain.timeline_font_bold
    val linkHelper: LinkHelper? = options.linkHelper
    var spanList = SpanList()

    var start = 0

    fun fireRender(node: Node): SpanList {
        val spanList = SpanList()
        this.spanList = spanList
        this.start = sb.length
        node.type.render.invoke(this, node)
        return spanList
    }

    internal fun fireRenderChildNodes(parent: Node): SpanList {
        val parent_result = this.spanList
        parent.childNodes.forEach {
            val child_result = fireRender(it)
            parent_result.addAll(child_result)
        }
        this.spanList = parent_result
        return parent_result
    }

    // 直前の文字が改行文字でなければ改行する
    fun closePreviousBlock() {
        if (start > 0 && sb[start - 1] != '\n') {
            sb.append('\n')
            start = sb.length
        }
    }

    fun closeBlock() {
        if (sb.length > 0 && sb[sb.length - 1] != '\n') {
            val start = sb.length
            sb.append('\n')
            val end = sb.length
            sb.setSpan(RelativeSizeSpan(0.1f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyHighlight(start: Int, end: Int) {
        val list = options.highlightTrie?.matchList(sb, start, end)
        if (list != null) {
            for (range in list) {
                val word = HighlightWord.load(range.word) ?: continue
                spanList.addLast(
                    range.start,
                    range.end,
                    HighlightSpan(
                        word.color_fg,
                        word.color_bg
                    )
                )

                if (word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
                    if (options.highlightSound == null) options.highlightSound = word
                }

                if (word.speech != 0) {
                    if (options.highlightSpeech == null) options.highlightSpeech = word
                }

                if (options.highlightAny == null) options.highlightAny = word
            }
        }
    }

    // テキストを追加する
    fun appendText(text: CharSequence, decodeEmoji: Boolean = false) {
        val start = sb.length
        if (decodeEmoji) {
            sb.append(options.decodeEmoji(text.toString()))
        } else {
            sb.append(text)
        }
        applyHighlight(start, sb.length)
    }

    // URL中のテキストを追加する
    private fun appendLinkText(displayUrl: String, href: String) {
        when {
            // 添付メディアのURLなら絵文字に変えてしまう
            options.isMediaAttachment(href) -> {
                // リンクの一部に絵文字がある場合、絵文字スパンをセットしてからリンクをセットする
                val start = sb.length
                sb.append(href)
                spanList.addFirst(
                    start,
                    sb.length,
                    SvgEmojiSpan(
                        context,
                        "emj_1f5bc.svg",
                        scale = 1f
                    ),
                )
            }

            else -> appendText(
                HTMLDecoder.shortenUrl(
                    displayUrl
                )
            )
        }
    }

    // リンクを追加する
    fun appendLink(
        text: String,
        url: String,
        allowShort: Boolean = false,
        mention: TootMention? = null,
    ) {
        when {
            allowShort -> appendLinkText(text, url)
            else -> appendText(text)
        }

        val fullAcct = if (!text.startsWith('@')) {
            null
            //リンクキャプションがメンション風でないならメンションとは扱わない
        } else {
            // 通称と色を調べる
            getFullAcctOrNull(
                rawAcct = Acct.parse(
                    text.substring(
                        1
                    )
                ),
                url = url,
                options.linkHelper,
                options.mentionDefaultHostDomain,
            )
        }

        val linkInfo = LinkInfo(
            caption = text,
            url = url,
            ac = fullAcct?.let {
                AcctColor.load(
                    fullAcct
                )
            },
            tag = options.linkTag,
            mention = mention
        )
        // リンクの一部にハイライトがある場合、リンクをセットしてからハイライトをセットしないとクリック判定がおかしくなる。
        spanList.addFirst(
            start, sb.length,
            MyClickableSpan(linkInfo)
        )
    }

    private fun prepareMentions(): ArrayList<TootMention> {
        var mentions = sb.mentions
        if (mentions == null) {
            mentions = ArrayList()
            sb.mentions = mentions
        }
        return mentions
    }

    fun appendMention(
        username: String,
        strHost: String?,
    ) {
        // ユーザが記述したacct
        val rawAcct = Acct.parse(username, strHost)

        val linkHelper = linkHelper
        if (linkHelper == null) {
            appendText("@${rawAcct.pretty}")
            return
        }

        // 長いacct
        // MFMでは投稿者のドメインを補うのはサーバ側の仕事の筈なので、options.mentionDefault…は見ない
        val fullAcct = rawAcct.followHost(linkHelper.apDomain)

        // mentionsメタデータに含まれるacct
        // ユーザの記述に因らず、サーバのホスト名同じなら短い、そうでなければ長いメンション
        val shortAcct = when {
            linkHelper.matchHost(fullAcct.host) -> Acct.parse(username)
            else -> fullAcct
        }

        // リンク表記はユーザの記述やアプリ設定の影響を受ける
        val caption = "@${
            when {
                PrefB.bpMentionFullAcct(
                    App1.pref
                ) -> fullAcct
                else -> rawAcct
            }.pretty
        }"

        var mention: TootMention? = null
        val url = when (strHost) {

            // https://github.com/syuilo/misskey/pull/3603

            "github.com", "twitter.com" ->
                "https://$strHost/$username" // no @

            "gmail.com" ->
                "mailto:$username@$strHost"

            else ->
                // MFMはメンションからユーザのURIを調べる正式な方法がない
                // たとえば @group_dev_jp@gup.pe の正式なURLは https://gup.pe/u/group_dev_jp
                // だが、 misskey.io ではメンションのリンク先は https://misskey.io/@group_dev_jp@gup.pe になる
                "https://${fullAcct.host?.ascii}/@$username"
                    .also { url ->
                        val mentions = prepareMentions()
                        mention = mentions.find { m -> m.acct == shortAcct }
                        if (mention == null) {
                            val newMention =
                                TootMention(
                                    EntityId.DEFAULT,
                                    url,
                                    shortAcct.ascii,
                                    username
                                )
                            mentions.add(newMention)
                            mention = newMention
                        }
                    }
        }
        appendLink(caption, url, mention = mention)
    }
}
