package jp.juggler.util

import org.intellij.lang.annotations.Language
import java.util.regex.Pattern

/*
java.util.regex.Pattern は Oracle JVM と Android で大きく異なる。
Androidの正規表現エンジンはICUベースで、
AndroidのAPIリファレンスで UNICODE_CHARACTER_CLASS を見ると
"This flag has no effect on Android, unicode character classes are always used." と書いてある。
\w \d \s などの文字クラスの扱いに違いがある。

JVMでUNICODE_CHARACTER_CLASSフラグなしの場合はこう
\s	空白文字: [\t\n\x0B\f\r]
\d	数字: [0-9]
\w	単語構成文字: [a-zA-Z_0-9]

JVMでUNICODE_CHARACTER_CLASSフラグありの場合はこう
\s	空白文字: \p{IsWhite_Space}
\d	数字: \p{IsDigit}
\w	単語構成文字: [\p{Alpha}\p{gc=Mn}\p{gc=Me}\p{gc=Mc}\p{Digit}\p{gc=Pc}\p{IsJoin_Control}]

ICUの場合はこう
http://userguide.icu-project.org/strings/regexp
\s Match a white space character. White space is defined as [\t\n\f\r\p{Z}].
\w Match a word character. Word characters are [\p{Alphabetic}\p{Mark}\p{Decimal_Number}\p{Connector_Punctuation}\u200c\u200d].
\d Match any character with the Unicode General Category of Nd (Number, Decimal Digit.)

とりあえず \d \D と \w と \W は凄く困るので 正規表現を書き換えてなんとかしたい。
なおJVMもICUも [A-Z[a-z]] と書くと [A-Za-z]と同じ事になる。
よって [^\w.-] を [^[A-Za-z0-9].-] に変換しても問題ない。
困るのは\W や \D の方だが、STのコードを見た感じ\Wは使っていないし、￥Dを文字クラスの中で使っていることもなかった

*/

fun @receiver:Language("RegExp") String.asciiPattern(flags: Int = 0): Pattern =
    Pattern.compile(this.asciiPatternString(), flags)

fun @receiver:Language("RegExp") String.asciiPatternString(): String {
    val dst = StringBuilder()
    dst.ensureCapacity(this.length)
    var escaped = false
    var insideSet = 0
    var lastOpen = 0
    for (i in this.indices) {
        val c = this[i]
        when {
            escaped -> {
                escaped = false
                when (c) {
                    'w' -> dst.append(if (insideSet > 0) "A-Za-z0-9_" else "[A-Za-z0-9_]")
                    'd' -> dst.append(if (insideSet > 0) "0-9" else "[0-9]")

                    // W,Dは文字セット内では対応できないのでそのまま通す
                    'W' -> if (insideSet > 0) dst.append('\\').append(c) else dst.append("[^A-Za-z0-9_]")
                    'D' -> if (insideSet > 0) dst.append('\\').append(c) else dst.append("[^0-9]")

                    else -> dst.append('\\').append(c)
                }
            }

            c == '\\' -> escaped = true

            else -> {
                dst.append(c)
                if (c == '[') {
                    insideSet++
                    lastOpen = i
                } else if (c == ']' && insideSet > 0 && i > lastOpen + 1) {
                    insideSet--

                    // [] のようなカラの文字クラスは正規表現のエラーになる。
                    // つまり文字クラスは最低でも1文字を含むので、
                    // []] のような記述は ] のみを示す文字クラスになる。
                    // [ ]] のような記述は 空白に続いて文字']'が出現する入力にマッチする。
                    // 1文字あけた次からは閉じ括弧として扱われて、
                    // 続いて開いてない時に登場した ] は普通の文字として扱われる。

                    // [[ABC][DEF]]は [ABCDEF]と同じ。入れ子になっている
                    // JVMのPatternでは[A-Z&&[^D-F]]のような記述は「A-ZのうちD-F以外」と解釈される
                    // ICUには [\p{Letter}&&\p{script=cyrillic}] や [\p{Letter}--\p{script=latin}]
                    // JVMと同じような記述ができるかどうかは分からない。
                }
            }
        }
    }
    if (escaped) dst.append('\\')
    return dst.toString()
}
