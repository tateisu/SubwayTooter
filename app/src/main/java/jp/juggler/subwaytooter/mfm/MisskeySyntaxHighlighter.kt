package jp.juggler.subwaytooter.mfm

import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.util.SparseBooleanArray
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.firstNonNull
import jp.juggler.util.data.groupEx
import jp.juggler.util.ui.FontSpan
import java.util.regex.Matcher
import java.util.regex.Pattern

// ```code``` マークダウン内部ではプログラムっぽい何かの文法強調表示が行われる
object MisskeySyntaxHighlighter {

    private val keywords = HashSet<String>().apply {

        val _keywords = arrayOf(
            "true",
            "false",
            "null",
            "nil",
            "undefined",
            "void",
            "var",
            "const",
            "let",
            "mut",
            "dim",
            "if",
            "then",
            "else",
            "switch",
            "match",
            "case",
            "default",
            "for",
            "each",
            "in",
            "while",
            "loop",
            "continue",
            "break",
            "do",
            "goto",
            "next",
            "end",
            "sub",
            "throw",
            "try",
            "catch",
            "finally",
            "enum",
            "delegate",
            "function",
            "func",
            "fun",
            "fn",
            "return",
            "yield",
            "async",
            "await",
            "require",
            "include",
            "import",
            "imports",
            "export",
            "exports",
            "from",
            "as",
            "using",
            "use",
            "internal",
            "module",
            "namespace",
            "where",
            "select",
            "struct",
            "union",
            "new",
            "delete",
            "this",
            "super",
            "base",
            "class",
            "interface",
            "abstract",
            "static",
            "public",
            "private",
            "protected",
            "virtual",
            "partial",
            "override",
            "extends",
            "implements",
            "constructor"
        )

        // lower
        addAll(_keywords)

        // UPPER
        addAll(_keywords.map { it.uppercase() })

        // Snake
        addAll(_keywords.map { k -> k[0].uppercase() + k.substring(1) })

        add("NaN")

        // 識別子に対して既存の名前と一致するか調べるようになったので、もはやソートの必要はない
    }

    private val symbolMap = SparseBooleanArray().apply {
        "=+-*/%~^&|><!?".forEach { put(it.code, true) }
    }

    // 文字列リテラルの開始文字のマップ
    private val stringStart = SparseBooleanArray().apply {
        "\"'`".forEach { put(it.code, true) }
    }

    private class Token(
        val length: Int,
        val color: Int = 0,
        val italic: Boolean = false,
        val comment: Boolean = false,
    )

    private class Env(
        val source: String,
        val start: Int,
        val end: Int,
    ) {

        // 出力先2
        val spanList = SpanList()

        fun push(start: Int, token: Token) {
            val end = start + token.length

            if (token.comment) {
                spanList.addLast(start, end, ForegroundColorSpan(Color.BLACK or 0x808000))
            } else {
                var c = token.color
                if (c != 0) {
                    if (c < 0x1000000) {
                        c = c or Color.BLACK
                    }
                    spanList.addLast(start, end, ForegroundColorSpan(c))
                }
                if (token.italic) {
                    spanList.addLast(
                        start,
                        end,
                        FontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
                    )
                }
            }
        }

        // スキャン位置
        var pos: Int = start

        fun remainMatcher(pattern: Pattern): Matcher =
            MatcherCache.matcher(pattern, source, pos, end)

        fun parse(): SpanList {

            var i = start

            var lastEnd = start
            fun closeTextToken(textEnd: Int) {
                val length = textEnd - lastEnd
                if (length > 0) {
                    push(lastEnd, Token(length = length))
                    lastEnd = textEnd
                }
            }

            while (i < end) {
                pos = i
                val token = elements.firstNonNull {
                    val t = this.it()
                    when {
                        t == null -> null // not match
                        i + t.length > end -> null // overrun detected
                        else -> t
                    }
                }
                if (token == null) {
                    ++i
                    continue
                }
                closeTextToken(i)
                push(i, token)
                i += token.length
                lastEnd = i
            }
            closeTextToken(end)

            return spanList
        }
    }

    private val reLineComment = """\A//.*"""
        .asciiPattern()

    private val reBlockComment = """\A/\*.*?\*/"""
        .asciiPattern(Pattern.DOTALL)

    private val reNumber = """\A[\-+]?[\d.]+"""
        .asciiPattern()

    private val reLabel = """\A@([A-Z_-][A-Z0-9_-]*)"""
        .asciiPattern(Pattern.CASE_INSENSITIVE)

    private val reKeyword = """\A([A-Z_-][A-Z0-9_-]*)([ \t]*\()?"""
        .asciiPattern(Pattern.CASE_INSENSITIVE)

    private val reContainsAlpha = """[A-Za-z_]"""
        .asciiPattern()

    private const val charH80 = 0x80.toChar()

    private val elements = arrayOf<Env.() -> Token?>(

        // マルチバイト文字をまとめて読み飛ばす
        {
            var s = pos
            while (s < end && source[s] >= charH80) {
                ++s
            }
            when {
                s > pos -> Token(length = s - pos)
                else -> null
            }
        },

        // 空白と改行をまとめて読み飛ばす
        {
            var s = pos
            while (s < end && source[s] <= ' ') {
                ++s
            }
            when {
                s > pos -> Token(length = s - pos)
                else -> null
            }
        },

        // comment
        {
            val match = remainMatcher(reLineComment)
            when {
                !match.find() -> null
                else -> Token(length = match.end() - match.start(), comment = true)
            }
        },

        // block comment
        {
            val match = remainMatcher(reBlockComment)
            when {
                !match.find() -> null
                else -> Token(length = match.end() - match.start(), comment = true)
            }
        },

        // string
        {
            val beginChar = source[pos]
            if (!stringStart[beginChar.code]) return@arrayOf null
            var i = pos + 1
            while (i < end) {
                val char = source[i++]
                if (char == beginChar) {
                    break // end
                } else if (char == '\n' || i >= end) {
                    i = 0 // not string literal
                    break
                } else if (char == '\\' && i < end) {
                    ++i // \" では閉じないようにする
                }
            }
            when {
                i <= pos -> null
                else -> Token(length = i - pos, color = 0xe96900)
            }
        },

        // regexp
        {
            if (source[pos] != '/') return@arrayOf null
            val regexp = StringBuilder()
            var i = pos + 1
            while (i < end) {
                val char = source[i++]
                if (char == '/') {
                    break
                } else if (char == '\n' || i >= end) {
                    i = 0 // not closed
                    break
                } else {
                    regexp.append(char)
                    if (char == '\\' && i < end) {
                        regexp.append(source[i++])
                    }
                }
            }
            when {
                i == 0 -> null
                regexp.isEmpty() -> null
                regexp.first() == ' ' && regexp.last() == ' ' -> null
                else -> Token(length = regexp.length + 2, color = 0xe9003f)
            }
        },

        // label
        {
            // 直前に識別子があればNG
            val prev = if (pos <= 0) null else source[pos - 1]
            if (prev?.isLetterOrDigit() == true) return@arrayOf null

            val match = remainMatcher(reLabel)
            if (!match.find()) return@arrayOf null

            val matchEnd = match.end()
            when {
                // @user@host のように直後に@が続くのはNG
                matchEnd < end && source[matchEnd] == '@' -> null
                else -> Token(length = match.end() - pos, color = 0xe9003f)
            }
        },

        // number
        {
            val prev = if (pos <= 0) null else source[pos - 1]
            if (prev?.isLetterOrDigit() == true) return@arrayOf null
            val match = remainMatcher(reNumber)
            when {
                !match.find() -> null
                else -> Token(length = match.end() - pos, color = 0xae81ff)
            }
        },

        // method, property, keyword
        {
            // 直前の文字が識別子に使えるなら識別子の開始とはみなさない
            val prev = if (pos <= 0) null else source[pos - 1]
            if (prev?.isLetterOrDigit() == true || prev == '_') return@arrayOf null

            val match = remainMatcher(reKeyword)
            if (!match.find()) return@arrayOf null
            val kw = match.groupEx(1)!!
            val bracket = match.groupEx(2) // may null

            when {
                // 英数字や_を含まないキーワードは無視する
                // -moz-foo- や __ はキーワードだが、 - や -- はキーワードではない
                !reContainsAlpha.matcher(kw).find() -> null

                // メソッド呼び出しは対象が変数かプロパティかに関わらずメソッドの色になる
                bracket?.isNotEmpty() == true ->
                    Token(length = kw.length, color = 0x8964c1, italic = true)

                // 変数や定数ではなくプロパティならプロパティの色になる
                prev == '.' -> Token(length = kw.length, color = 0xa71d5d)

                // 予約語ではない
                // 強調表示しないが、識別子単位で読み飛ばす
                !keywords.contains(kw) -> Token(length = kw.length)

                else -> when (kw) {

                    // 定数
                    "true", "false", "null", "nil", "undefined", "NaN" ->
                        Token(length = kw.length, color = 0xae81ff)

                    // その他の予約語
                    else -> Token(length = kw.length, color = 0x2973b7)
                }
            }
        },

        // symbol
        {
            val c = source[pos]
            when {
                symbolMap.get(c.code, false) ->
                    Token(length = 1, color = 0x42b983)
                c == '-' ->
                    Token(length = 1, color = 0x42b983)
                else -> null
            }
        }
    )

    fun parse(source: String) = Env(source, 0, source.length).parse()
}
