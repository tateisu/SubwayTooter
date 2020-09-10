package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji
import jp.juggler.subwaytooter.api.entity.TootAttachmentLike
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.WordTrieTree
import java.util.*

class DecodeOptions(
	val context : Context? = null,
	var linkHelper : LinkHelper? = null,
	var short : Boolean = false,
	var decodeEmoji : Boolean = false,
	var attachmentList : ArrayList<TootAttachmentLike>? = null,
	var linkTag : Any? = null,
	var emojiMapCustom : HashMap<String, CustomEmoji>? = null,
	var emojiMapProfile : HashMap<String, NicoProfileEmoji>? = null,
	var highlightTrie : WordTrieTree? = null,
	var unwrapEmojiImageTag : Boolean = false,
	var enlargeCustomEmoji : Float = 1f,
	var enlargeEmoji : Float = 1f,
	var forceHtml : Boolean = false, // force use HTML instead of Misskey Markdown
	var mentionFullAcct : Boolean = false,
	var mentions : ArrayList<TootMention>? = null
) {
	
	internal fun isMediaAttachment(url : String?) : Boolean {
		val list_attachment = attachmentList
		if(url == null || list_attachment == null) return false
		for(a in list_attachment) {
			if(a.hasUrl(url)) return true
		}
		return false
	}
	
	// OUTPUT
	var highlightSound : HighlightWord? = null
	var highlightSpeech : HighlightWord? = null
	var highlightAny : HighlightWord? = null
	
	////////////////////////
	// decoder
	
	// AndroidのStaticLayoutはパラグラフ中に絵文字が沢山あると異常に遅いので、絵文字が連続して登場したら改行文字を挿入する
	private fun SpannableStringBuilder.workaroundForEmojiLineBreak() : SpannableStringBuilder {
		
		val spans = getSpans(0, length, ReplacementSpan::class.java)
		if(spans != null && spans.size >= 40) {
			val wrapCount = 12 // if(linkHelper?.isMisskey == true ) 6 else 12
			
			fun hasLineBreak(start : Int?, end : Int) : Boolean {
				if(start != null)
					for(i in start until end) {
						if(get(i) == '\n') return true
					}
				return false
			}
			
			val insertList = ArrayList<Int>()
			spans.sortBy { getSpanStart(it) }
			var repeatCount = 0
			var preEnd : Int? = null
			for(span in spans) {
				val start = getSpanStart(span)
				val hasLf = hasLineBreak(preEnd, start)
				if(hasLf) {
					repeatCount = 0
				} else if(++ repeatCount >= wrapCount) {
					repeatCount = 0
					insertList.add(start)
				}
				preEnd = getSpanEnd(span)
			}
			// 後ろから順に挿入する
			insertList.reversed().forEach { insert(it, "\n") }
		}
		return this
	}
	
	fun decodeHTML(html : String?) =
		HTMLDecoder.decodeHTML(this, html).workaroundForEmojiLineBreak()
	
	fun decodeEmoji(s : String?) : Spannable =
		EmojiDecoder.decodeEmoji(this, s ?: "").workaroundForEmojiLineBreak()
	
	fun decodeEmojiNullable(s : String?) = when(s) {
		null -> null
		else -> EmojiDecoder.decodeEmoji(this, s)
	}
}