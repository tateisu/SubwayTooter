package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.Spannable
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
	var mentions: ArrayList<TootMention>? = null
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
	var highlightSound: HighlightWord? = null
	var highlightSpeech: HighlightWord? = null
	var highlightAny: HighlightWord? = null
	
	////////////////////////
	// decoder
	
	fun decodeHTML(html : String?) =
		HTMLDecoder.decodeHTML(this, html)
	
	fun decodeEmoji(s : String?) : Spannable =
		EmojiDecoder.decodeEmoji(this, s ?: "")
	
	fun decodeEmojiNullable(s : String?) = when(s) {
		null -> null
		else -> EmojiDecoder.decodeEmoji(this, s)
	}
	
	
}