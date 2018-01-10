package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder

import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji
import jp.juggler.subwaytooter.api.entity.TootAttachmentLike
import jp.juggler.subwaytooter.table.HighlightWord

@Suppress("MemberVisibilityCanPrivate")
class DecodeOptions(
	var short : Boolean = false,
	var decodeEmoji : Boolean = false,
	var attachmentList : ArrayList<TootAttachmentLike>? = null,
	var linkTag : Any? = null,
	var emojiMapCustom : CustomEmoji.Map? = null,
	var emojiMapProfile : NicoProfileEmoji.Map? = null

) {
	
	// true if highlight found
	var hasHighlight : Boolean = false
	
	// highlight found with sound
	var highlight_sound : HighlightWord? = null
	
	var highlight_trie : WordTrieTree? = null
	
	fun setShort(b : Boolean) : DecodeOptions {
		short = b
		return this
	}
	
	fun setDecodeEmoji(b : Boolean) : DecodeOptions {
		decodeEmoji = b
		return this
	}
	
	fun setAttachment(list_attachment : ArrayList<TootAttachmentLike>?) : DecodeOptions {
		this.attachmentList = list_attachment
		return this
	}
	
	fun setLinkTag(link_tag : Any?) : DecodeOptions {
		this.linkTag = link_tag
		return this
	}
	
	fun setCustomEmojiMap(customEmojiMap : CustomEmoji.Map?) : DecodeOptions {
		this.emojiMapCustom = customEmojiMap
		return this
	}
	
	fun setProfileEmojis(profile_emojis : NicoProfileEmoji.Map?) : DecodeOptions {
		this.emojiMapProfile = profile_emojis
		return this
	}
	
	fun setHighlightTrie(highlight_trie : WordTrieTree?) : DecodeOptions {
		this.highlight_trie = highlight_trie
		return this
	}
	
	////////////////////////
	// decoder
	
	fun decodeHTML(context : Context?, lcc : LinkClickContext?, html : String?) : SpannableStringBuilder {
		return HTMLDecoder.decodeHTML(context, lcc, html, this)
	}
	
	fun decodeEmoji(context : Context, s : String?) : Spannable {
		return EmojiDecoder.decodeEmoji(context, s ?: "", this)
	}
	
}