package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

@Suppress("MemberVisibilityCanPrivate")
class NicoEnquete(
	context : Context,
	access_info : SavedAccount,
	status : TootStatus,
	list_attachment : TootAttachmentLike.List?,
	src : JSONObject
) {
	
	// one of enquete,enquete_result
	val type : String
	
	// HTML text
	val question : Spannable
	
	// array of text with emoji
	val items : ArrayList<Spannable>?
	
	// 結果の数値 // null or array of number
	val ratios : ArrayList<Float>?
	
	// 結果の数値のテキスト // null or array of string
	val ratios_text : ArrayList<String>?
	
	// 以下はJSONには存在しないが内部で使う
	val time_start : Long
	val status_id : Long
	
	init {
		this.ratios = parseFloatArray(src, "ratios")
		this.ratios_text = parseStringArray(src, "ratios_text")
		this.type = Utils.optStringX(src, "type") ?: ""
		this.time_start = status.time_created_at
		this.status_id = status.id
		
		this.question = DecodeOptions()
			.setShort(true)
			.setDecodeEmoji(true)
			.setAttachment(list_attachment)
			.setLinkTag(status)
			.setCustomEmojiMap(status.custom_emojis)
			.setProfileEmojis(status.profile_emojis)
			.decodeHTML(context, access_info, Utils.optStringX(src, "question") ?: "?")
		
		this.items = parseChoiceList(context, status, parseStringArray(src, "items"))
	}
	
	companion object {
		internal val log = LogCategory("NicoEnquete")
		
		const val ENQUETE_EXPIRE = 30000L
		
		const val TYPE_ENQUETE = "enquete"
		
		@Suppress("unused")
		const val TYPE_ENQUETE_RESULT = "enquete_result"
		
		@Suppress("HasPlatformType")
		val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		fun parse(
			context : Context,
			access_info : SavedAccount,
			status : TootStatus,
			list_attachment : TootAttachmentLike.List?,
			jsonString : String?
		) : NicoEnquete? {
			try {
				if(jsonString != null) {
					return NicoEnquete(
						context,
						access_info,
						status,
						list_attachment,
						JSONObject(jsonString)
					)
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			return null
		}
		
		private fun parseStringArray(src : JSONObject, name : String) : ArrayList<String>? {
			val array = src.optJSONArray(name)
			if(array != null) {
				val dst = ArrayList<String>()
				var i = 0
				val ie = array.length()
				while(i < ie) {
					val sv = Utils.optStringX(array, i)
					if(sv != null) dst.add(sv)
					++ i
				}
				return dst
			}
			return null
		}
		
		private fun parseFloatArray(src : JSONObject, name : String) : ArrayList<Float>? {
			val array = src.optJSONArray(name)
			if(array != null) {
				val dst = ArrayList<Float>()
				var i = 0
				val ie = array.length()
				while(i < ie) {
					val dv = array.optDouble(i)
					dst.add(dv.toFloat())
					++ i
				}
				return dst
			}
			return null
		}
		
		private fun parseChoiceList(context : Context, status : TootStatus, tmp_items : ArrayList<String>?) : ArrayList<Spannable>? {
			if(tmp_items != null) {
				val size = tmp_items.size
				if(size > 0) {
					val items = ArrayList<Spannable>(size)
					for(i in 0 until size) {
						items.add(
							DecodeOptions()
								.setCustomEmojiMap(status.custom_emojis)
								.setProfileEmojis(status.profile_emojis)
								.decodeEmoji(context,
									reWhitespace
										.matcher(Utils.sanitizeBDI(tmp_items[i]))
										.replaceAll(" ")
								)
						)
					}
					return items
					
				}
				
			}
			return null
		}
	}
}
