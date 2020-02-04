package jp.juggler.subwaytooter.util

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.util.neatSpaces
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("MemberVisibilityCanPrivate")
@RunWith(AndroidJUnit4::class)
class TestHtmlDecoder {

	class SpanMeta(
		val span:Any,
		val start:Int,
		val end:Int,
		val flags:Int,
		val text :String
	){
		override fun toString() = "[$start..$end) $flags ${span.javaClass.simpleName} $text"
	}
	
	@Test fun test1(){
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		
		val options = DecodeOptions(appContext,LinkHelper.newLinkHelper(Host.parse("instance.test")))
		
		val html = """
			日本語で楽しめるMastodonサーバを提供しています。
			 <a href="https://mastodon.juggler.jp/terms">利用規約</a>を読んでからサインアップしてください。
			 <a href="https://play.google.com/store/search?q=%E3%83%9E%E3%82%B9%E3%83%88%E3%83%89%E3%83%B3&amp;c=apps"><img alt="Androidアプリ" src='https://m1j.zzz.ac/ja_badge_web_generic.png' height="48"></a>
			 <a href="https://theappstore.org/search.php?search=mastodon&amp;platform=software"><img alt="iOSアプリ" src="https://linkmaker.itunes.apple.com/ja-jp/badge-lrg.svg?releaseDate=2017-08-09&amp;kind=iossoftware&amp;bubble=ios_apps" height="48"></a>
			long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text long text
		""".trimIndent()
		val text =  options.decodeHTML( html ).neatSpaces()
		val spanArray = text.getSpans(0,text.length,Any::class.java).map {
			val start = text.getSpanStart(it)
			val end = text.getSpanEnd(it)
			SpanMeta(
				span = it,
				start = start,
				end = end,
				flags = text.getSpanFlags(it),
				text = text.subSequence(start, end).toString()
			)
		}
		
		spanArray.forEach{ println(it)}
		assertEquals(3,spanArray.size)
		assertEquals( "利用規約", spanArray[0].text)
		assertEquals( "<img/>", spanArray[1].text)
		assertEquals( "<img/>", spanArray[2].text)
	}
}