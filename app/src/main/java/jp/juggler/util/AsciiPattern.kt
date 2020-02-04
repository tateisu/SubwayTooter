package jp.juggler.util

import java.util.regex.Pattern

/*
java.util.regex.Patternは Oracle JVM と Android で大きく異なる。
Androidの正規表現エンジンはICUベースで、文字クラスは常にUnicodeで扱われる

AndroidのAPIリファレンスで UNICODE_CHARACTER_CLASS を見ると
"This flag has no effect on Android, unicode character classes are always used." と書いてある。

JVMでUNICODE_CHARACTER_CLASSフラグなしの場合はこう
\s	空白文字: [\t\n\x0B\f\r]
\d	数字: [0-9]
\w	単語構成文字: [a-zA-Z_0-9]

JVMでJVMでUNICODE_CHARACTER_CLASSフラグありの場合はこう
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

fun String.asciiPattern(flags : Int = 0) : Pattern =
	Pattern.compile(this.asciiPatternString(), flags)

fun String.asciiPatternString() : String {
	val dst = StringBuilder()
	dst.ensureCapacity(this.length)
	var escaped = false
	var insideSet = false
	for(c in this) {
		if(escaped) {
			escaped = false
			when(c) {
				'w' -> if(insideSet) {
					dst.append("A-Za-z0-9_")
				} else {
					dst.append("[A-Za-z0-9_]")
				}
				'd' -> if(insideSet) {
					dst.append("0-9")
				} else {
					dst.append("[0-9]")
				}
				
				'W' -> {
					if(insideSet) {
						// 対応できないのでそのまま通す
						dst.append('\\')
						dst.append(c)
					} else {
						dst.append("[^A-Za-z0-9_]")
					}
				}
				
				'D' -> {
					if(insideSet) {
						// 対応できないのでそのまま通す
						dst.append('\\')
						dst.append(c)
					} else {
						dst.append("[^0-9]")
					}
				}
				
				else -> {
					dst.append('\\')
					dst.append(c)
				}
			}
		} else if(c == '\\') {
			escaped = true
		} else {
			dst.append(c)
			if(c == '[') {
				insideSet = true
			} else if(c == ']' && insideSet) {
				insideSet = false
			}
		}
	}
	if(escaped) dst.append('\\')
	return dst.toString()
}
