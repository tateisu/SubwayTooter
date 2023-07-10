package jp.juggler.subwaytooter.mfm

import android.graphics.Typeface
import jp.juggler.subwaytooter.span.BlockCodeSpan
import jp.juggler.subwaytooter.span.BlockQuoteSpan
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.util.data.encodePercent
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.FontSpan

// ノード種別および種別ごとのレンダリング関数
enum class NodeType(val render: SpanOutputEnv.(Node) -> Unit) {

    TEXT({
        appendText(it.args[0], decodeEmoji = true)
    }),

    EMOJI({
        val code = it.args[0]
        if (code.isNotEmpty()) {
            appendText(":$code:", decodeEmoji = true)
        }
    }),

    MENTION({
        appendMention(it.args[0], it.args[1].notEmpty())
    }),

    LATEX({
        if (decorationEnabled && showUnsupportedMarkup) {
            fireRenderChildNodes(it)
        }
    }),

    HASHTAG({
        val linkHelper = linkHelper
        val tag = it.args[0]
        if (tag.isNotEmpty() && linkHelper != null) {
            appendLink(
                "#$tag",
                "https://${linkHelper.apiHost.ascii}/tags/" + tag.encodePercent()
            )
        }
    }),

    CODE_INLINE({
        val text = it.args[0]
        if (!decorationEnabled) {
            appendText(text)
        } else {
            val sp = MisskeySyntaxHighlighter.parse(text)
            appendText(text)
            spanList.addWithOffset(sp, start)
            spanList.addLast(start, sb.length, android.text.style.BackgroundColorSpan(0x40808080))
            spanList.addLast(
                start, sb.length,
                FontSpan(Typeface.MONOSPACE)
            )
        }
    }),

    URL({
        val url = it.args[0]
        if (url.isNotEmpty()) {
            appendLink(url, url, allowShort = true)
        }
    }),

    CODE_BLOCK({
        if (!decorationEnabled) {
            appendText(it.args[0])
        } else {
            closePreviousBlock()
            val text = trimBlock(it.args[0])
            val sp = MisskeySyntaxHighlighter.parse(text)
            appendText(text)
            spanList.addWithOffset(sp, start)

            spanList.addLast(start, sb.length, BlockCodeSpan())
            closeBlock()
        }
    }),

    QUOTE_INLINE({
        if (!decorationEnabled) {
            appendText(it.args[0])
        } else {
            val text = trimBlock(it.args[0])
            appendText(text)
            spanList.addLast(
                start,
                sb.length,
                android.text.style.BackgroundColorSpan(0x20808080)
            )
            spanList.addLast(
                start,
                sb.length,
                FontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
            )
        }
    }),

    SEARCH({
        if (!decorationEnabled) {
            appendText(it.args[0])
            appendText(" ")
            appendText(context.getString(jp.juggler.subwaytooter.R.string.search))
        } else {
            closePreviousBlock()

            val text = it.args[0]
            val keywordStart = sb.length // キーワードの開始位置
            appendText(text)
            appendText(" ")
            start = sb.length // 検索リンクの開始位置

            appendLink(
                context.getString(jp.juggler.subwaytooter.R.string.search),
                "https://www.google.co.jp/search?q=${text.encodePercent()}"
            )
            spanList.addLast(keywordStart, sb.length, android.text.style.RelativeSizeSpan(1.2f))

            closeBlock()
        }
    }),

    BIG({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addLast(
                start, sb.length,
                jp.juggler.subwaytooter.span.MisskeyBigSpan(fontBold)
            )
        }
    }),

    BOLD({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addLast(start, sb.length, FontSpan(fontBold))
        }
    }),

    STRIKE({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addLast(start, sb.length, android.text.style.StrikethroughSpan())
        }
    }),

    SMALL({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addLast(start, sb.length, android.text.style.RelativeSizeSpan(0.7f))
        }
    }),

    FUNCTION({
        if (!decorationEnabled || !showUnsupportedMarkup) {
            fireRenderChildNodes(it)
        } else {
            val name = it.args.elementAtOrNull(0)
            appendText("[")
            appendText(name ?: "")
            appendText(" ")
            fireRenderChildNodes(it)
            appendText("]")
        }
    }),

    ITALIC({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addLast(
                start, sb.length,
                FontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
            )
        }
    }),

    MOTION({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            val start = this.start
            fireRenderChildNodes(it)
            spanList.addFirst(
                start,
                sb.length,
                jp.juggler.subwaytooter.span.MisskeyMotionSpan(jp.juggler.subwaytooter.ActMain.timelineFont)
            )
        }
    }),

    LINK({
        val url = it.args[1]
        // val silent = data?.get(2)
        // silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった

        if (url.isNotEmpty()) {
            if (!decorationEnabled) {
                fireRenderChildNodes(it)
            } else {
                val start = this.start
                fireRenderChildNodes(it)
                val linkHelper = options.linkHelper
                if (linkHelper != null) {
                    val linkInfo = jp.juggler.subwaytooter.span.LinkInfo(
                        url = url,
                        tag = options.linkTag,
                        ac = jp.juggler.subwaytooter.api.entity.TootAccount.getAcctFromUrl(url)
                            ?.let { acct -> daoAcctColor.load(acct) },
                        caption = sb.substring(start, sb.length)
                    )
                    spanList.addFirst(
                        start, sb.length,
                        jp.juggler.subwaytooter.span.MyClickableSpan(linkInfo)
                    )
                }
            }
        }
    }),

    TITLE({
        if (!decorationEnabled) {
            fireRenderChildNodes(it) // 改行を含まないことが分かっている
        } else {
            closePreviousBlock()

            val start = this.start
            fireRenderChildNodes(it) // 改行を含まないことが分かっている
            spanList.addLast(
                start,
                sb.length,
                android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER)
            )
            spanList.addLast(
                start,
                sb.length,
                android.text.style.BackgroundColorSpan(0x20808080)
            )
            spanList.addLast(start, sb.length, android.text.style.RelativeSizeSpan(1.5f))

            closeBlock()
        }
    }),

    CENTER({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            closePreviousBlock()

            val start = this.start
            fireRenderChildNodes(it)
            when {
                it.quoteNest > 0 -> {
                    // 引用ネストの内部ではセンタリングさせると引用マーカーまで動いてしまうので
                    // センタリングが機能しないようにする
                }

                else -> spanList.addLast(
                    start,
                    sb.length,
                    android.text.style.AlignmentSpan.Standard(
                        android.text.Layout.Alignment.ALIGN_CENTER
                    )
                )
            }

            closeBlock()
        }
    }),

    QUOTE_BLOCK({
        if (!decorationEnabled) {
            fireRenderChildNodes(it)
        } else {
            closePreviousBlock()

            val start = this.start

            // 末尾にある空白のテキストノードを除去する
            while (it.childNodes.isNotEmpty()) {
                val last = it.childNodes.last()
                if (last.type == TEXT && last.args[0].isBlank()) {
                    it.childNodes.removeLast()
                } else {
                    break
                }
            }

            fireRenderChildNodes(it)

            val bgColor =
                MisskeyMarkdownDecoder.quoteNestColors[it.quoteNest % MisskeyMarkdownDecoder.quoteNestColors.size]

            spanList.addLast(
                start,
                sb.length,
                BlockQuoteSpan(context = context, blockQuoteColor = bgColor)
            )
            spanList.addLast(
                start,
                sb.length,
                FontSpan(
                    Typeface.defaultFromStyle(
                        Typeface.ITALIC
                    )
                )
            )
            closeBlock()
        }
    }),

    ROOT({
        fireRenderChildNodes(it)
    }),
}
