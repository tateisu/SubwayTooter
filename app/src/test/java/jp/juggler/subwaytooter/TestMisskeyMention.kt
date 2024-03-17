package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.data.asciiPatternString
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("MemberNameEqualsClassName")
class TestMisskeyMention {

    @Test
    fun testBracket() {
        // [] 空の文字セットはパースエラーになる。
        // val re1="""[]""".toRegex() // error 空の文字クラス

        // [[] や [[]] はパースエラーになる。
        // val re1="""[[]""".toRegex() // error 閉じ括弧が足りない
        // val re1="""[[]]""".toRegex() // error 内側が空の文字クラス

        // 最低でも1文字を含む。
        assertEquals(true, """[]]""".toRegex().matches("]"))

        // 1文字あけた次からは閉じ括弧として扱われる。
        assertEquals(true, """[ ]]""".toRegex().matches(" ]"))

        // 閉じ括弧が単体で出たら文字クラスにならない。
        assertEquals(true, """]""".toRegex().matches("]"))

        // 閉じ括弧が足りないのはエラーになる。
        // val a="""[[ ]""".toRegex()

        // IDEで警告が出るが、Androidは正規表現エンジンが異なるので仕方ない
        @Suppress("RegExpRedundantNestedCharacterClass")
        assertEquals(true, """[[ ]]][ ]""".toRegex().matches(" ] "))
    }

    @Test
    @Throws(Exception::class)
    fun testAsciiPattern() {
        // \w \d \W \D 以外の文字は素通しする
        assertEquals("""ab\c\\""", """ab\c\\""".asciiPatternString())
        assertEquals("""[A-Za-z0-9_]""", """\w""".asciiPatternString())
        assertEquals("""[A-Za-z0-9_-]""", """[\w-]""".asciiPatternString())
        assertEquals("""[^A-Za-z0-9_]""", """\W""".asciiPatternString())
        assertEquals("""[0-9]""", """\d""".asciiPatternString())
        assertEquals("""[0-9:-]""", """[\d:-]""".asciiPatternString())
        assertEquals("""[^0-9]""", """\D""".asciiPatternString())

        // 文字セットの中の \W \D は変換できないので素通しする
        assertEquals("""[\W]""", """[\W]""".asciiPatternString())
        assertEquals("""[\D]""", """[\D]""".asciiPatternString())

        // エスケープ文字の後に何もない場合も素通しする
        assertEquals("""\""", """\""".asciiPatternString())
    }

    @Test
    fun testMisskeyMention() {
        fun findMention(str: String): String? {
            val m = TootAccount.reMisskeyMentionMFM.matcher(str)
            return if (m.find()) m.group(0) else null
        }
        assertEquals(null, findMention(""))
        assertEquals(null, findMention("tateisu"))
        assertEquals("@tateisu", findMention("@tateisu"))
        assertEquals("@tateisu", findMention("@tateisuほげ"))
        assertEquals("@tateisu@mastodon.juggler.jp", findMention("@tateisu@mastodon.juggler.jp"))
        assertEquals("@tateisu@mastodon.juggler.jp", findMention("@tateisu@mastodon.juggler.jpほげ"))
        assertEquals("@tateisu", findMention("@tateisu@マストドン3.juggler.jp"))
        assertEquals(
            "@tateisu@xn--3-pfuzbe6htf.juggler.jp",
            findMention("@tateisu@xn--3-pfuzbe6htf.juggler.jp")
        )
    }

    @Test
    fun testMastodonMention() {
        fun findMention(str: String): String? {
            val m = TootAccount.reCountMention.matcher(str)
            return if (m.find()) m.group(0) else null
        }
        assertEquals(null, findMention(""))
        assertEquals(null, findMention("tateisu"))
        assertEquals("@tateisu", findMention("@tateisu"))
        assertEquals("@tateisu", findMention("@tateisuほげ"))
        assertEquals("@tateisu@mastodon.juggler.jp", findMention("@tateisu@mastodon.juggler.jp"))
        assertEquals("@tateisu@mastodon.juggler.jp", findMention("@tateisu@mastodon.juggler.jpほげ"))
        assertEquals("@tateisu@マストドン3.juggler.jp", findMention("@tateisu@マストドン3.juggler.jp"))
        assertEquals(
            "@tateisu@xn--3-pfuzbe6htf.juggler.jp",
            findMention("@tateisu@xn--3-pfuzbe6htf.juggler.jp")
        )
    }
}
