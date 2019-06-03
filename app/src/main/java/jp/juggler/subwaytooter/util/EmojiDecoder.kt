package jp.juggler.subwaytooter.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.SparseBooleanArray
import jp.juggler.emoji.EmojiMap201709
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.codePointBefore
import java.util.*
import java.util.regex.Pattern

object EmojiDecoder {
	
	private const val cpColon = ':'.toInt()
	
	private const val cpZwsp = '\u200B'.toInt()
	
	fun customEmojiSeparator(pref : SharedPreferences) = if(Pref.bpCustomEmojiSeparatorZwsp(pref)) {
		'\u200B'
	} else {
		' '
	}
	
	// タンス側が落ち着いたら [^[:almun:]_] から [:space:]に切り替える
	//	private fun isHeadOrAfterWhitespace( s:CharSequence,index:Int):Boolean {
	//		val cp = s.codePointBefore(index)
	//		return cp == -1 || CharacterGroup.isWhitespace(cp)
	//	}
	
	fun canStartShortCode(s : CharSequence, index : Int) : Boolean {
		val cp = s.codePointBefore(index)
		return when(cp) {
			- 1 -> true
			cpColon -> false
			cpZwsp -> true
			// rubyの (Letter | Mark | Decimal_Number) はNG
			// ftp://unicode.org/Public/5.1.0/ucd/UCD.html#General_Category_Values
			else -> when(java.lang.Character.getType(cp).toByte()) {
				// Letter
				// LCはエイリアスなので文字から得られることはないはず
				Character.UPPERCASE_LETTER,
				Character.LOWERCASE_LETTER,
				Character.TITLECASE_LETTER,
				Character.MODIFIER_LETTER,
				Character.OTHER_LETTER -> false
				// Mark
				Character.NON_SPACING_MARK,
				Character.COMBINING_SPACING_MARK,
				Character.ENCLOSING_MARK -> false
				// Decimal_Number
				Character.DECIMAL_DIGIT_NUMBER -> false
				
				else -> true
			}
		}
		// https://mastodon.juggler.jp/@tateisu/99727683089280157
		// https://github.com/tootsuite/mastodon/pull/5570 がマージされたらこっちに切り替える
		// return cp == -1 || CharacterGroup.isWhitespace(cp)
	}
	
	private class EmojiStringBuilder(internal val options : DecodeOptions) {
		
		internal val sb = SpannableStringBuilder()
		internal var normal_char_start = - 1
		
		private fun openNormalText() {
			if(normal_char_start == - 1) {
				normal_char_start = sb.length
			}
		}
		
		internal fun closeNormalText() {
			if(normal_char_start != - 1) {
				val end = sb.length
				applyHighlight(normal_char_start, end)
				normal_char_start = - 1
			}
		}
		
		private fun applyHighlight(start : Int, end : Int) {
			val list = options.highlightTrie?.matchList(sb, start, end) ?: return
			for(range in list) {
				val word = HighlightWord.load(range.word)
				if(word != null) {
					options.hasHighlight = true
					sb.setSpan(
						HighlightSpan(word.color_fg, word.color_bg),
						range.start,
						range.end,
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
					if(word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
						options.highlight_sound = word
					}
				}
			}
		}
		
		internal fun addNetworkEmojiSpan(text : String, url : String) {
			closeNormalText()
			val start = sb.length
			sb.append(text)
			val end = sb.length
			sb.setSpan(
				NetworkEmojiSpan(url, scale = options.enlargeCustomEmoji),
				start,
				end,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
		}
		
		internal fun addImageSpan(text : String, @DrawableRes res_id : Int) {
			val context = options.context
			if(context == null) {
				openNormalText()
				sb.append(text)
			} else {
				closeNormalText()
				val start = sb.length
				sb.append(text)
				val end = sb.length
				sb.setSpan(
					EmojiImageSpan(context, res_id),
					start,
					end,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}
		}
		
		internal fun addUnicodeString(s : String) {
			var i = 0
			val end = s.length
			while(i < end) {
				val remain = end - i
				var emoji : String? = null
				var image_id : Int? = null
				
				for(j in EmojiMap201709.utf16_max_length downTo 1) {
					
					if(j > remain) continue
					
					val check = s.substring(i, i + j)
					
					image_id = EmojiMap201709.sUTF16ToImageId[check] ?: continue
					
					emoji = if(j < remain && s[i + j].toInt() == 0xFE0E) {
						// 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
						// その文字を絵文字化しない
						image_id = 0
						s.substring(i, i + j + 1)
					} else {
						check
					}
					
					break
				}
				
				if(image_id != null && emoji != null) {
					if(image_id == 0) {
						// 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
						// その文字を絵文字化しない
						openNormalText()
						sb.append(emoji)
					} else {
						addImageSpan(emoji, image_id)
					}
					i += emoji.length
				} else {
					openNormalText()
					val length = Character.charCount(s.codePointAt(i))
					if(length == 1) {
						val c = s[i ++]
						sb.append(
							when(c) {
								// https://github.com/tateisu/SubwayTooter/issues/69
								'\u00AD' -> '-'
								else -> c
							}
						)
					} else {
						sb.append(s.substring(i, i + length))
						i += length
					}
				}
			}
		}
	}
	
	private const val codepointColon = ':'.toInt()
	
	private val shortCodeCharacterSet : SparseBooleanArray by lazy {
		val set = SparseBooleanArray()
		for(c in 'A' .. 'Z') set.put(c.toInt(), true)
		for(c in 'a' .. 'z') set.put(c.toInt(), true)
		for(c in '0' .. '9') set.put(c.toInt(), true)
		for(c in "+-_@:") set.put(c.toInt(), true)
		set
	}
	
	private interface ShortCodeSplitterCallback {
		fun onString(part : String) // shortcode以外の文字列
		fun onShortCode(
			prevCodePoint : Int,
			part : String,
			name : String
		) // part : ":shortcode:", name : "shortcode"
	}
	
	private fun splitShortCode(
		s : String,
		startArg : Int,
		end : Int,
		callback : ShortCodeSplitterCallback
	) {
		var start = startArg
		var i = start
		while(i < end) {
			
			// 絵文字パターンの開始位置を探索する
			start = i
			loop@ while(i < end) {
				val c = s.codePointAt(i)
				val width = Character.charCount(c)
				if(c == codepointColon) {
					break@loop
				}
				i += width
			}
			
			if(i > start) {
				callback.onString(s.substring(start, i))
			}
			
			if(i >= end) break
			
			start = i ++ // start=コロンの位置 i=その次の位置
			
			// 閉じるコロンを探す
			var posEndColon = - 1
			while(i < end) {
				val cp = s.codePointAt(i)
				if(cp == codepointColon) {
					posEndColon = i
					break
				}
				if(! shortCodeCharacterSet.get(cp, false)) {
					break
				}
				i += Character.charCount(cp)
			}
			
			// 閉じるコロンが見つからないか、shortcodeが短すぎるなら
			// startの位置のコロンだけを処理して残りは次のループで処理する
			if(posEndColon == - 1 || posEndColon - start < 3) {
				callback.onString(":")
				i = start + 1
				continue
			}
			
			val prevCodePoint = if(start > 0) {
				s.codePointBefore(start)
			} else {
				0x20
			}
			
			callback.onShortCode(
				prevCodePoint,
				s.substring(start, posEndColon + 1), // ":shortcode:"
				s.substring(start + 1, posEndColon) // "shortcode"
			)
			
			i = posEndColon + 1 // コロンの次の位置
		}
	}
	
	private val reNicoru = Pattern.compile("\\Anicoru\\d*\\z", Pattern.CASE_INSENSITIVE)
	private val reHohoemi = Pattern.compile("\\Ahohoemi\\d*\\z", Pattern.CASE_INSENSITIVE)
	
	fun decodeEmoji(options : DecodeOptions, s : String) : Spannable {
		
		val builder = EmojiStringBuilder(options)
		
		val emojiMapCustom = options.emojiMapCustom
		val emojiMapProfile = options.emojiMapProfile
		
		splitShortCode(s, 0, s.length, object : ShortCodeSplitterCallback {
			override fun onString(part : String) {
				builder.addUnicodeString(part)
			}
			
			override fun onShortCode(prevCodePoint : Int, part : String, name : String) {
				
				// フレニコのプロフ絵文字
				if(emojiMapProfile != null && name.length >= 2 && name[0] == '@') {
					val emojiProfile = emojiMapProfile[name] ?: emojiMapProfile[name.substring(1)]
					if(emojiProfile != null) {
						val url = emojiProfile.url
						if(url.isNotEmpty()) {
							builder.addNetworkEmojiSpan(part, url)
							return
						}
					}
				}
				
				// カスタム絵文字
				val emojiCustom = emojiMapCustom?.get(name)
				if(emojiCustom != null) {
					val url = when {
						Pref.bpDisableEmojiAnimation(App1.pref) && emojiCustom.static_url?.isNotEmpty() == true -> emojiCustom.static_url
						else -> emojiCustom.url
					}
					builder.addNetworkEmojiSpan(part, url)
					return
				}
				
				// 通常の絵文字
				val info = EmojiMap201709.sShortNameToImageId[name.toLowerCase().replace('-', '_')]
				if(info != null) {
					builder.addImageSpan(part, info.image_id)
					return
				}
				
				when {
					reHohoemi.matcher(name).find() -> builder.addImageSpan(
						part,
						R.drawable.emoji_hohoemi
					)
					reNicoru.matcher(name).find() -> builder.addImageSpan(
						part,
						R.drawable.emoji_nicoru
					)
					else -> builder.addUnicodeString(part)
				}
				
			}
		})
		
		builder.closeNormalText()
		
		return builder.sb
	}
	
	// 投稿などの際、表示は不要だがショートコード=>Unicodeの解決を行いたい場合がある
	// カスタム絵文字の変換も行わない
	fun decodeShortCode(
		s : String,
		emojiMapCustom : HashMap<String, CustomEmoji>? = null
	) : String {
		
		val sb = StringBuilder()
		
		splitShortCode(s, 0, s.length, object : ShortCodeSplitterCallback {
			override fun onString(part : String) {
				sb.append(part)
			}
			
			override fun onShortCode(prevCodePoint : Int, part : String, name : String) {
				
				// カスタム絵文字にマッチするなら変換しない
				val emojiCustom = emojiMapCustom?.get(name)
				if(emojiCustom != null) {
					sb.append(part)
					return
				}
				
				// カスタム絵文字ではなく通常の絵文字のショートコードなら絵文字に変換する
				val info = EmojiMap201709.sShortNameToImageId[name.toLowerCase().replace('-', '_')]
				sb.append(info?.unified ?: part)
			}
		})
		
		return sb.toString()
	}
	
	// 入力補完用。絵文字ショートコード一覧を部分一致で絞り込む
	internal fun searchShortCode(
		context : Context,
		prefix : String,
		limit : Int
	) : ArrayList<CharSequence> {
		val dst = ArrayList<CharSequence>()
		for(shortCode in EmojiMap201709.sShortNameList) {
			if(dst.size >= limit) break
			if(! shortCode.contains(prefix)) continue
			
			val info = EmojiMap201709.sShortNameToImageId[shortCode] ?: continue
			
			val sb = SpannableStringBuilder()
			val start = 0
			sb.append(' ')
			val end = sb.length
			
			sb.setSpan(
				EmojiImageSpan(context, info.image_id),
				start,
				end,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			
			sb.append(' ')
				.append(':')
				.append(shortCode)
				.append(':')
			
			dst.add(sb)
		}
		return dst
	}
}
