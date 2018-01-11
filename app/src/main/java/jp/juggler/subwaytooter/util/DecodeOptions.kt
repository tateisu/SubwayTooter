package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder

import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji
import jp.juggler.subwaytooter.api.entity.TootAttachmentLike
import jp.juggler.subwaytooter.table.HighlightWord
import java.util.HashMap

@Suppress("MemberVisibilityCanPrivate")
class DecodeOptions(
	var short : Boolean = false,
	var decodeEmoji : Boolean = false,
	var attachmentList : ArrayList<TootAttachmentLike>? = null,
	var linkTag : Any? = null,
	var emojiMapCustom : HashMap<String, CustomEmoji>? = null,
	var emojiMapProfile : HashMap<String, NicoProfileEmoji>? = null,
	var highlightTrie : WordTrieTree? = null
) {
	
	// OUTPUT: true if highlight found
	var hasHighlight : Boolean = false
	
	// OUTPUT: highlight found with sound
	var highlight_sound : HighlightWord? = null
	
	////////////////////////
	// decoder
	
	fun decodeHTML(context : Context?, lcc : LinkClickContext?, html : String?) : SpannableStringBuilder {
		return HTMLDecoder.decodeHTML(context, lcc, html, this)
	}
	
	fun decodeEmoji(context : Context, s : String?) : Spannable {
		return EmojiDecoder.decodeEmoji(context, s ?: "", this)
	}
	
}