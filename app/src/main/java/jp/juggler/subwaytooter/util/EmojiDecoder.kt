package jp.juggler.subwaytooter.util

import android.content.Context
import android.support.annotation.DrawableRes
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import jp.juggler.emoji.EmojiMap201709

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.HighlightWord

object EmojiDecoder {
	
	private val rangeShortcodeAlphabetUpper = IntRange('A'.toInt(), 'Z'.toInt())
	private val rangeShortcodeAlphabetLower = IntRange('a'.toInt(), 'z'.toInt())
	private val rangeShortcodeNumber = IntRange('0'.toInt(), '9'.toInt())
	private const val intCharMinus = '-'.toInt()
	private const val intCharPlus = '+'.toInt()
	private const val intCharUnder = '_'.toInt()
	private const val intCharAt = '@'.toInt()
	private const val intCharColon = ':'.toInt()
	
	internal interface ShortCodeSplitterCallback {
		fun onString(part : String)
		fun onShortCode(part : String, name : String)
	}
	
	private fun isShortCodeCharacter(cp : Int) : Boolean {
		return when(cp) {
			in rangeShortcodeAlphabetUpper,
			in rangeShortcodeAlphabetLower,
			in rangeShortcodeNumber,
			intCharMinus,
			intCharPlus,
			intCharUnder,
			intCharAt -> true
			
			else -> false
		}
	}
	
	private val reNicoru = Pattern.compile("\\Anicoru\\d*\\z", Pattern.CASE_INSENSITIVE)
	private val reHohoemi = Pattern.compile("\\Ahohoemi\\d*\\z", Pattern.CASE_INSENSITIVE)
	
	private class DecodeEnv internal constructor(internal val context : Context, internal val options : DecodeOptions) {
		internal val sb = SpannableStringBuilder()
		internal var normal_char_start = - 1
		
		internal fun closeNormalText() {
			if(normal_char_start != - 1) {
				val end = sb.length
				applyHighlight(normal_char_start, end)
				normal_char_start = - 1
			}
		}
		
		private fun applyHighlight(start : Int, end : Int) {
			val list = options.highlight_trie?.matchList(sb, start, end)
			if(list != null) {
				for(range in list) {
					val word = HighlightWord.load(range.word)
					if(word != null) {
						options.hasHighlight = true
						sb.setSpan(HighlightSpan(word.color_fg, word.color_bg), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						if(word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
							options.highlight_sound = word
						}
					}
				}
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
					image_id = EmojiMap201709.sUTF16ToImageId[check]
					if(image_id != null) {
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
				}
				
				if(image_id != null && emoji != null) {
					if(image_id == 0) {
						// 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
						// その文字を絵文字化しない
						if(normal_char_start == - 1) {
							normal_char_start = sb.length
						}
						sb.append(emoji)
					} else {
						addImageSpan(emoji, image_id)
					}
					i += emoji.length
					continue
				}
				
				if(normal_char_start == - 1) {
					normal_char_start = sb.length
				}
				val length = Character.charCount(s.codePointAt(i))
				if(length == 1) {
					sb.append(s[i])
					++ i
				} else {
					sb.append(s.substring(i, i + length))
					i += length
				}
			}
		}
		
		internal fun addImageSpan(text : String?, @DrawableRes res_id : Int) {
			closeNormalText()
			val start = sb.length
			sb.append(text)
			val end = sb.length
			sb.setSpan(EmojiImageSpan(context, res_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		
		internal fun addNetworkEmojiSpan(text : String, url : String) {
			closeNormalText()
			val start = sb.length
			sb.append(text)
			val end = sb.length
			sb.setSpan(NetworkEmojiSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
	}
	
	private fun splitShortCode(
		s : String, startArg : Int, end : Int, callback : ShortCodeSplitterCallback
	) {
		var start = startArg
		var i = start
		while(i < end) {
			// 絵文字パターンの開始位置を探索する
			start = i
			while(i < end) {
				val c = s.codePointAt(i)
				val width = Character.charCount(c)
				if(c == intCharColon) {
					if(App1.allow_non_space_before_emoji_shortcode) {
						// アプリ設定により、: の手前に空白を要求しない
						break
					} else if(i + width < end && s.codePointAt(i + width) == intCharAt) {
						// フレニコのプロフ絵文字 :@who: は手前の空白を要求しない
						break
					} else if(i == 0 || CharacterGroup.isWhitespace(s.codePointBefore(i))) {
						// ショートコードの手前は始端か改行か空白文字でないとならない
						// 空白文字の判定はサーバサイドのそれにあわせる
						break
					}
					// shortcodeの開始とみなせないケースだった
				}
				i += width
			}
			if(i > start) {
				callback.onString(s.substring(start, i))
			}
			if(i >= end) break
			
			start = i ++ // start=コロンの位置 i=その次の位置
			
			var emoji_end = - 1
			while(i < end) {
				val c = s.codePointAt(i)
				if(c == intCharColon) {
					emoji_end = i
					break
				}
				if(! isShortCodeCharacter(c)) {
					break
				}
				i += Character.charCount(c)
			}
			
			// 絵文字がみつからなかったら、startの位置のコロンだけを処理して残りは次のループで処理する
			if(emoji_end == - 1 || emoji_end - start < 3) {
				callback.onString(":")
				i = start + 1
				continue
			}
			
			callback.onShortCode(
				s.substring(start, emoji_end + 1) // ":shortcode:"
				, s.substring(start + 1, emoji_end) // "shortcode"
			)
			
			i = emoji_end + 1 // コロンの次の位置
		}
	}
	
	fun decodeEmoji(
		context : Context, s : String, options : DecodeOptions
	) : Spannable {
		val decode_env = DecodeEnv(context, options)
		val custom_map = options.emojiMapCustom
		val profile_emojis = options.emojiMapProfile
		
		splitShortCode(s, 0, s.length, object : ShortCodeSplitterCallback {
			override fun onString(part : String) {
				decode_env.addUnicodeString(part)
			}
			
			override fun onShortCode(part : String, name : String) {
				val info = EmojiMap201709.sShortNameToImageId[name.toLowerCase().replace('-', '_')]
				if(info != null) {
					decode_env.addImageSpan(part, info.image_id)
					return
				}
				
				// part=":@name:" name="@name"
				if(name.length >= 2 && name[0] == '@') {
					if(profile_emojis != null) {
						var emoji : NicoProfileEmoji? = profile_emojis[name]
						if(emoji == null) emoji = profile_emojis[name.substring(1)]
						if(emoji != null) {
							val url = emoji.url
							if(url.isNotEmpty()) decode_env.addNetworkEmojiSpan(part, url)
						}
					}
					return
				}
				
				val emoji = custom_map?.get(name)
				if(emoji != null) {
					val url = if(App1.disable_emoji_animation && emoji.static_url?.isNotEmpty() == true) emoji.static_url else emoji.url
					decode_env.addNetworkEmojiSpan(part, url)
					return
				}
				
				when {
					reHohoemi.matcher(name).find() -> decode_env.addImageSpan(part, R.drawable.emoji_hohoemi)
					reNicoru.matcher(name).find() -> decode_env.addImageSpan(part, R.drawable.emoji_nicoru)
					else -> decode_env.addUnicodeString(part)
				}
			}
		})
		
		decode_env.closeNormalText()
		
		return decode_env.sb
	}
	
	// 投稿などの際、表示は不要だがショートコード=>Unicodeの解決を行いたい場合がある
	// カスタム絵文字の変換も行わない
	fun decodeShortCode(s : String) : String {
		
		val sb = StringBuilder()
		
		splitShortCode(s, 0, s.length, object : ShortCodeSplitterCallback {
			override fun onString(part : String) {
				sb.append(part)
			}
			
			override fun onShortCode(part : String, name : String) {
				val info = EmojiMap201709.sShortNameToImageId[name.toLowerCase().replace('-', '_')]
				sb.append(info?.unified ?: part)
			}
		})
		
		return sb.toString()
	}
	
	// 入力補完用。絵文字ショートコード一覧を部分一致で絞り込む
	internal fun searchShortCode(context : Context, prefix : String, limit : Int) : ArrayList<CharSequence> {
		val dst = ArrayList<CharSequence>()
		for(shortCode in EmojiMap201709.sShortNameList) {
			if(dst.size >= limit) break
			if(! shortCode.contains(prefix)) continue
			
			val info = EmojiMap201709.sShortNameToImageId[shortCode] ?: continue
			
			val sb = SpannableStringBuilder()
			sb.append(' ')
			val start = 0
			val end = sb.length
			
			sb.setSpan(EmojiImageSpan(context, info.image_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			sb.append(' ')
			sb.append(':')
			sb.append(shortCode)
			sb.append(':')
			dst.add(sb)
		}
		return dst
	}
	
}
