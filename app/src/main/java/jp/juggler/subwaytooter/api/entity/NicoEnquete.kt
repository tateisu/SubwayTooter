package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*

@Suppress("MemberVisibilityCanPrivate")
class NicoEnquete(
	context : Context,
	access_info : SavedAccount,
	status : TootStatus,
	list_attachment : ArrayList<TootAttachmentLike>?,
	src : JSONObject
) {
	
	// one of enquete,enquete_result
	val type : String?
	
	// HTML text
	val question : Spannable // 表示用にデコードしてしまうのでNonNullになる
	
	// array of text with emoji
	val items : ArrayList<Spannable>?
	
	// 結果の数値 // null or array of number
	private val ratios : ArrayList<Float>?
	
	// 結果の数値のテキスト // null or array of string
	private val ratios_text : ArrayList<String>?
	
	// 以下はJSONには存在しないが内部で使う
	val time_start : Long
	val status_id : Long
	
	init {
		this.type = src.parseString( "type")
		
		this.question = DecodeOptions(
			short = true,
			decodeEmoji = true,
			attachmentList = list_attachment,
			linkTag = status,
			emojiMapCustom = status.custom_emojis,
			emojiMapProfile = status.profile_emojis
		).decodeHTML(context, access_info, src.parseString( "question") ?: "?")
		
		this.items = parseChoiceList(context, status, parseStringArray(src, "items"))
		
		this.ratios = parseFloatArray(src, "ratios")
		this.ratios_text = parseStringArray(src, "ratios_text")
		
		this.time_start = status.time_created_at
		this.status_id = status.id
		
	}
	
	companion object {
		internal val log = LogCategory("NicoEnquete")
		
		const val ENQUETE_EXPIRE = 30000L
		
		const val TYPE_ENQUETE = "enquete"
		
		@Suppress("unused")
		const val TYPE_ENQUETE_RESULT = "enquete_result"
		
		@Suppress("HasPlatformType")
		private val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		fun parse(
			context : Context,
			access_info : SavedAccount,
			status : TootStatus,
			list_attachment : ArrayList<TootAttachmentLike>?,
			jsonString : String?
		) : NicoEnquete? {
			jsonString ?: return null
			return try {
				NicoEnquete(
					context,
					access_info,
					status,
					list_attachment,
					jsonString.toJsonObject()
				)
			} catch(ex : Throwable) {
				log.trace(ex)
				null
			}
		}
		
		private fun parseStringArray(src : JSONObject, name : String) :ArrayList<String>?{
			val array = src.optJSONArray(name)
			if(array != null) {
				val dst = array.toStringArrayList()
				if(dst.isNotEmpty()) return dst
			}
			return null
		}
		
		private fun parseFloatArray(src : JSONObject, name : String) : ArrayList<Float>? {
			val array = src.optJSONArray(name)
			if(array != null) {
				val size = array.length()
				val dst = ArrayList<Float>(size)
				for(i in 0 until size) {
					val dv = array.optDouble(i)
					dst.add(dv.toFloat())
				}
				if(dst.isNotEmpty()) return dst
			}
			return null
		}
		
		private fun parseChoiceList(
			context : Context,
			status : TootStatus,
			stringArray : ArrayList<String>?
		) : ArrayList<Spannable>? {
			if(stringArray != null) {
				val size = stringArray.size
				val items = ArrayList<Spannable>(size)
				for(i in 0 until size) {
					items.add(
						DecodeOptions(
							emojiMapCustom = status.custom_emojis,
							emojiMapProfile = status.profile_emojis
						).decodeEmoji(context,
							reWhitespace
								.matcher(stringArray[i].sanitizeBDI())
								.replaceAll(" ")
						)
					)
				}
				if(items.isNotEmpty()) return items
			}
			return null
		}
	}
}
