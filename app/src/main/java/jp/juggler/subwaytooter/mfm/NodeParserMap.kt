package jp.juggler.subwaytooter.mfm

import android.util.SparseArray
import android.util.SparseBooleanArray
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.LogCategory
import jp.juggler.util.asciiPattern
import jp.juggler.util.ellipsizeDot3
import jp.juggler.util.groupEx
import java.util.*
import java.util.regex.Pattern

private val log = LogCategory("NodeParserMap")

// ブロック要素は始端と終端の空行を除去したい
val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
fun trimBlock(s: String) =
    s.replace(reStartEmptyLines, "")
        .replace(reEndEmptyLines, "")

// ノードのパースを行う関数をキャプチャパラメータつきで生成する
fun simpleParser(
    pattern: Pattern,
    type: NodeType,
): NodeParseEnv.() -> NodeDetected? = {
    val matcher = remainMatcher(pattern)
    when {
        !matcher.find() -> null

        else -> {
            val textInside = matcher.groupEx(1)!!
            makeDetected(
                type,
                arrayOf(textInside),
                matcher.start(), matcher.end(),
                this.text, matcher.start(1), textInside.length
            )
        }
    }
}

private val brackets = arrayOf(
    "()",
    "()",
    "[]",
    "{}",
    "“”",
    "‘’",
    "‹›",
    "«»",
    "（）",
    "［］",
    "｛｝",
    "｟｠",
    "⦅⦆",
    "〚〛",
    "⦃⦄",
    "「」",
    "〈〉",
    "《》",
    "【】",
    "〔〕",
    "⦗⦘",
    "『』",
    "〖〗",
    "〘〙",
    "[]",
    "｢｣",
    "⟦⟧",
    "⟨⟩",
    "⟪⟫",
    "⟮⟯",
    "⟬⟭",
    "⌈⌉",
    "⌊⌋",
    "⦇⦈",
    "⦉⦊",
    "❛❜",
    "❝❞",
    "❨❩",
    "❪❫",
    "❴❵",
    "❬❭",
    "❮❯",
    "❰❱",
    "❲❳",
    "()",
    "﴾﴿",
    "〈〉",
    "⦑⦒",
    "⧼⧽",
    "﹙﹚",
    "﹛﹜",
    "﹝﹞",
    "⁽⁾",
    "₍₎",
    "⦋⦌",
    "⦍⦎",
    "⦏⦐",
    "⁅⁆",
    "⸢⸣",
    "⸤⸥",
    "⟅⟆",
    "⦓⦔",
    "⦕⦖",
    "⸦⸧",
    "⸨⸩",
    "⧘⧙",
    "⧚⧛",
    "⸜⸝",
    "⸌⸍",
    "⸂⸃",
    "⸄⸅",
    "⸉⸊",
    "᚛᚜",
    "༺༻",
    "༼༽",
    "⏜⏝",
    "⎴⎵",
    "⏞⏟",
    "⏠⏡",
    "﹁﹂",
    "﹃﹄",
    "︹︺",
    "︻︼",
    "︗︘",
    "︿﹀",
    "︽︾",
    "﹇﹈",
    "︷︸"
)

val bracketsMap = HashMap<Char, Int>().apply {
    brackets.forEach {
        put(it[0], 1)
        put(it[1], -1)
    }
}

val bracketsMapUrlSafe = HashMap<Char, Int>().apply {
    brackets.forEach {
        if ("([".contains(it[0])) return@forEach
        put(it[0], 1)
        put(it[1], -1)
    }
}

// 末尾の余計な」や（を取り除く。
// 例えば「#タグ」 とか （#タグ）
fun String.removeOrphanedBrackets(urlSafe: Boolean = false): String {
    var last = 0
    val nests = when (urlSafe) {
        true -> this.map {
            last += bracketsMapUrlSafe[it] ?: 0
            last
        }
        else -> this.map {

            last += bracketsMap[it] ?: 0
            last
        }
    }

    // first position of unmatched close
    var pos = nests.indexOfFirst { it < 0 }
    if (pos != -1) return substring(0, pos)

    // last position of unmatched open
    pos = nests.indexOfLast { it == 0 }
    return substring(0, pos + 1)
}

// [title] 【title】
// 直後に改行が必要だったが文末でも良いことになった https://github.com/syuilo/misskey/commit/79ffbf95db9d0cc019d06ab93b1bfa6ba0d4f9ae
//		val titleParser = simpleParser(
//			"""\A[【\[](.+?)[】\]](\n|\z)""".asciiPattern()
//			, NodeType.TITLE
//		)
private val reTitle = """\A[【\[](.+?)[】\]](\n|\z)""".asciiPattern()
private val reFunction = """\A\$?\[([^\s\n\[\]]+) \s*([^\n\[\]]+)\]""".asciiPattern()
private fun NodeParseEnv.titleParserImpl(): NodeDetected? {
    if (useFunction) {
        val type = NodeType.FUNCTION
        val matcher = remainMatcher(reFunction)
        if (matcher.find()) {
            val name = matcher.groupEx(1)?.ellipsizeDot3(3) ?: "???"
            val textInside = matcher.groupEx(2)!!
            return makeDetected(
                type,
                arrayOf(name),
                matcher.start(), matcher.end(),
                this.text, matcher.start(2), textInside.length
            )
        }
    }
    val type = NodeType.TITLE
    val matcher = remainMatcher(reTitle)
    if (matcher.find()) {
        val textInside = matcher.groupEx(1)!!
        return makeDetected(
            type,
            arrayOf(textInside),
            matcher.start(), matcher.end(),
            this.text, matcher.start(1), textInside.length
        )
    }

    return null
}

@Suppress("SpellCheckingInspection")
private val latexEscape = listOf(
    "\\#" to "#",
    "\\$" to "$",
    "\\%" to "%",
    "\\&" to "&",
    "\\_" to "_",
    "\\{" to "{",
    "\\}" to "}",
    "\\;" to "",
    "\\!" to "",

    "\\textbackslash" to "\\",
    "\\backslash" to "\\",
    "\\textasciitilde" to "~",
    "\\textasciicircum" to "^",
    "\\textbar" to "|",
    "\\textless" to "<",
    "\\textgreater" to ">",
).sortedByDescending { it.first.length }

private fun partialEquals(src: String, start: Int, needle: String): Boolean {
    for (i in needle.indices) {
        if (src.elementAtOrNull(start + i) != needle[i]) return false
    }
    return true
}

private fun String.unescapeLatex(): String {
    val sb = StringBuilder(length)
    val end = length
    var i = 0
    while (i < end) {
        val c = this[i]
        if (c == '\\') {
            val pair = latexEscape.find { partialEquals(this, i, it.first) }
            if (pair != null) {
                sb.append(pair.second)
                i += pair.first.length
                continue
            }
        }
        sb.append(c)
        ++i
    }
    return sb.toString()
}

// \} \]はムダなエスケープに見えるが、androidでは必要なので削ってはいけない
@Suppress("RegExpRedundantEscape")
private val reLatexRemove =
    """\\(?:quad|Huge|atop|sf|scriptsize|bf|small|tiny|underline|large|(?:color)\{[^}]*\})""".toRegex()

@Suppress("RegExpRedundantEscape")
private val reLatex1 =
    """\\(?:(?:url)|(?:textcolor|colorbox)\{[^}]*\}|(?:fcolorbox|raisebox)\{[^}]*\}\{[^}]*\}|includegraphics\[[^]]*\])\{([^}]*)\}""".toRegex()

@Suppress("RegExpRedundantEscape")
private val reLatex2reversed = """\\(?:overset|href)\{([^}]+)\}\{([^}]+)\}""".toRegex()

private fun String.removeLatex(): String {
    return this
        .replace(reLatexRemove, "")
        .replace(reLatex1, "$1")
        .replace(reLatex2reversed, "$2 $1")
        .unescapeLatex()
}

private val reLatexBlock =
    """^\\\[(.+?)\\\]""".asciiPattern(Pattern.MULTILINE or Pattern.DOTALL)
private val reLatexInline = """\A\\\((.+?)\\\)""".asciiPattern()
private fun NodeParseEnv.latexParserImpl(): NodeDetected? {
    val type = NodeType.LATEX
    var matcher = remainMatcher(reLatexBlock)
    if (matcher.find()) {
        val textInside = matcher.groupEx(1)!!.removeLatex().trim()
        return makeDetected(
            type,
            arrayOf(textInside),
            matcher.start(), matcher.end(),
            textInside, 0, textInside.length
        )
    }
    matcher = remainMatcher(reLatexInline)
    if (matcher.find()) {
        val textInside = matcher.groupEx(1)!!.removeLatex()
        return makeDetected(
            type,
            arrayOf(textInside),
            matcher.start(), matcher.end(),
            textInside, 0, textInside.length
        )
    }
    return null
}

// (マークダウン要素の特徴的な文字)と(パーサ関数の配列)のマップ
val nodeParserMap = SparseArray<Array<out NodeParseEnv.() -> NodeDetected?>>().apply {

    fun addParser(
        firstChars: String,
        vararg nodeParsers: NodeParseEnv.() -> NodeDetected?,
    ) {
        for (s in firstChars) {
            put(s.code, nodeParsers)
        }
    }

    // Strike ~~...~~
    addParser(
        "~", simpleParser(
            """\A~~(.+?)~~""".asciiPattern(), NodeType.STRIKE
        )
    )

    // Quote "..."
    addParser(
        "\"", simpleParser(
            """\A"([^\x0d\x0a]+?)\n"[\x0d\x0a]*""".asciiPattern(), NodeType.QUOTE_INLINE
        )
    )

    // Quote (行頭)>...(改行)
    // この正規表現の場合は \A ではなく ^ で各行の始端にマッチさせる
    val reQuoteBlock = """^>(?:[ 　]?)([^\x0d\x0a]*)(\x0a|\x0d\x0a?)?"""
        .asciiPattern(Pattern.MULTILINE)

    addParser(">", {
        if (pos > 0) {
            val c = text[pos - 1]
            if (c != '\r' && c != '\n') {
                //直前が改行文字ではない
                if (MisskeyMarkdownDecoder.DEBUG) log.d("QUOTE: previous char is not line end. $c pos=$pos text=$text")
                return@addParser null
            }
        }

        var p = pos
        val content = StringBuilder()
        val matcher = remainMatcher(reQuoteBlock)
        while (true) {
            if (!matcher.find(p)) break
            p = matcher.end()
            if (content.isNotEmpty()) content.append('\n')
            content.append(matcher.groupEx(1))
            // 改行の直後なので次回マッチの ^ は大丈夫なはず…
        }
        if (content.isNotEmpty()) content.append('\n')

        if (p <= pos) {
            // > のあとに全く何もない
            if (MisskeyMarkdownDecoder.DEBUG) log.d("QUOTE: not a quote")
            return@addParser null
        }
        val textInside = content.toString()

        makeDetected(
            NodeType.QUOTE_BLOCK,
            emptyArray(),
            pos, p,
            textInside, 0, textInside.length
        )
    })

    // 絵文字 :emoji:
    addParser(
        ":",
        simpleParser(
            """\A:([a-zA-Z0-9+-_@]+):""".asciiPattern(), NodeType.EMOJI
        )
    )

    // モーション
    addParser(
        "(", simpleParser(
            """\A\Q(((\E(.+?)\Q)))\E""".asciiPattern(Pattern.DOTALL), NodeType.MOTION
        )
    )

    val reHtmlTag = """\A<([a-z]+)>(.+?)</\1>""".asciiPattern(Pattern.DOTALL)

    addParser("<", {
        val matcher = remainMatcher(reHtmlTag)
        when {
            !matcher.find() -> null

            else -> {
                val tagName = matcher.groupEx(1)!!
                val textInside = matcher.groupEx(2)!!

                fun a(type: NodeType) = makeDetected(
                    type,
                    arrayOf(textInside),
                    matcher.start(), matcher.end(),
                    this.text, matcher.start(2), textInside.length
                )

                when (tagName) {
                    "motion" -> a(NodeType.MOTION)
                    "center" -> a(NodeType.CENTER)
                    "small" -> a(NodeType.SMALL)
                    "i" -> a(NodeType.ITALIC)
                    else -> null
                }
            }
        }
    })

    // ***big*** **bold**
    addParser(
        "*",
        // 処理順序に意味があるので入れ替えないこと
        // 記号列が長い順にパースを試す
        simpleParser(
            """^\Q***\E(.+?)\Q***\E""".asciiPattern(),
            NodeType.BIG
        ),
        simpleParser(
            """^\Q**\E(.+?)\Q**\E""".asciiPattern(),
            NodeType.BOLD
        ),
    )

    val reAlnum = """[A-Za-z0-9]""".asciiPattern()

    // http(s)://....
    val reUrl = """\A(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)"""
        .asciiPattern()

    addParser("h", {

        // 直前の文字が英数字ならURLの開始とはみなさない
        if (pos > 0 && MatcherCache.matcher(reAlnum, text, pos - 1, pos).find()) {
            return@addParser null
        }

        val matcher = remainMatcher(reUrl)
        if (!matcher.find()) {
            return@addParser null
        }

        val url = matcher.groupEx(1)!!.removeOrphanedBrackets(urlSafe = true)
        makeDetected(
            NodeType.URL,
            arrayOf(url),
            matcher.start(), matcher.start() + url.length,
            "", 0, 0
        )
    })

    // 検索
    val reSearchButton = """\A(検索|\[検索]|Search|\[Search])(\n|\z)"""
        .asciiPattern(Pattern.CASE_INSENSITIVE)

    fun NodeParseEnv.parseSearchPrev(): String? {
        val prev = text.substring(lastEnd, pos)
        val delm = prev.lastIndexOf('\n')
        val end = prev.length
        return when {
            end <= 1 -> null // キーワードを含まないくらい短い
            delm + 1 >= end - 1 -> null // 改行より後の部分が短すぎる
            !" 　".contains(prev.last()) -> null // 末尾が空白ではない
            else -> prev.substring(delm + 1, end - 1) // キーワード部分を返す
        }
    }

    val searchParser: NodeParseEnv.() -> NodeDetected? = {
        val matcher = remainMatcher(reSearchButton)
        when {
            !matcher.find() -> null

            else -> {
                val keyword = parseSearchPrev()
                when {
                    keyword?.isEmpty() != false -> null

                    else -> makeDetected(
                        NodeType.SEARCH,
                        arrayOf(keyword),
                        pos - (keyword.length + 1), matcher.end(),
                        this.text, pos - (keyword.length + 1), keyword.length
                    )
                }
            }
        }
    }

    val titleParser: NodeParseEnv.() -> NodeDetected? = { titleParserImpl() }

    // Link
    val reLink = """\A\??\[([^\n\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)"""
        .asciiPattern()

    val linkParser: NodeParseEnv.() -> NodeDetected? = {
        val matcher = remainMatcher(reLink)
        when {
            !matcher.find() -> null

            else -> {
                val title = matcher.groupEx(1)!!
                makeDetected(
                    NodeType.LINK,
                    arrayOf(
                        title,
                        matcher.groupEx(2)!!, // url
                        text[pos].toString() // silent なら "?" になる
                    ),
                    matcher.start(), matcher.end(),
                    this.text, matcher.start(1), title.length
                )
            }
        }
    }

    // [ はいろんな要素で使われる
    // searchの判定をtitleより前に行うこと。 「abc [検索] 」でtitleが優先されるとマズい
    // v10でもv12でもlinkの優先度はtitleやfunctionより高い
    addParser("[", searchParser, linkParser, titleParser)
    addParser("$", titleParser)
    // その他の文字でも判定する
    addParser("【", titleParser)
    addParser("検Ss", searchParser)
    addParser("?", linkParser)

    // \(…\) \{…\}
    addParser("\\", { latexParserImpl() })

    // メールアドレスの@の手前に使える文字なら真
    val mailChars = SparseBooleanArray().apply {
        for (it in '0'..'9') {
            put(it.code, true)
        }
        for (it in 'A'..'Z') {
            put(it.code, true)
        }
        for (it in 'a'..'z') {
            put(it.code, true)
        }
        """${'$'}!#%&'`"*+-/=?^_{|}~""".forEach { put(it.code, true) }
    }

    addParser("@", {

        val matcher = remainMatcher(TootAccount.reMisskeyMentionMFM)

        when {
            !matcher.find() -> null

            else -> when {
                // 直前の文字がメールアドレスの@の手前に使える文字ならメンションではない
                pos > 0 && mailChars.get(text.codePointBefore(pos)) -> null

                else -> {
//						log.d(
//							"mention detected: ${matcher.group(1)},${matcher.group(2)},${
//								matcher.group(
//									0
//								)
//							}"
//						)
                    makeDetected(
                        NodeType.MENTION,
                        arrayOf(
                            matcher.groupEx(1)!!,
                            matcher.groupEx(2) ?: "" // username, host
                        ),
                        matcher.start(), matcher.end(),
                        "", 0, 0
                    )
                }
            }
        }
    })

    // Hashtag
    val reHashtag = """\A#([^\s.,!?#:]+)""".asciiPattern()
    val reDigitsOnly = """\A\d*\z""".asciiPattern()

    addParser("#", {

        if (pos > 0 && MatcherCache.matcher(reAlnum, text, pos - 1, pos).find()) {
            // 直前に英数字があるならタグにしない
            return@addParser null
        }

        val matcher = remainMatcher(reHashtag)
        if (!matcher.find()) {
            // タグにマッチしない
            return@addParser null
        }

        // 先頭の#を含まないタグテキスト
        val tag = matcher.groupEx(1)!!.removeOrphanedBrackets()

        if (tag.isEmpty() || tag.length > 50 || reDigitsOnly.matcher(tag).find()) {
            // 空文字列、50文字超過、数字だけのタグは不許可
            return@addParser null
        }

        makeDetected(
            NodeType.HASHTAG,
            arrayOf(tag),
            matcher.start(), matcher.start() + 1 + tag.length,
            "", 0, 0
        )
    })

    // code (ブロック、インライン)
    addParser(
        "`", simpleParser(
            """\A```(?:.*)\n([\s\S]+?)\n```(?:\n|$)""".asciiPattern(), NodeType.CODE_BLOCK
            /*
            (A)
                ```code``` は 閉じる部分の前後に改行がないのでダメ
            (B)
                ```lang
                code
                code
                code
                ```
                はlang部分は表示されない
            (C)
                STの表示上の都合で閉じる部分の後の改行が複数あっても全て除去する
             */
        ), simpleParser(
            // インラインコードは内部にとある文字を含むと認識されない。理由は顔文字と衝突するからだとか
            """\A`([^`´\x0d\x0a]+)`""".asciiPattern(), NodeType.CODE_INLINE
        )
    )
}
