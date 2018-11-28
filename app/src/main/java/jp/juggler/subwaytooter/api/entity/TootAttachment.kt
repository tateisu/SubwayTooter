package jp.juggler.subwaytooter.api.entity

import android.content.SharedPreferences

import org.json.JSONObject

import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.clipRange
import jp.juggler.subwaytooter.util.jsonObject
import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString

class TootAttachment : TootAttachmentLike {
	
	companion object {
		private fun parseFocusValue(parent : JSONObject?, key : String) : Float {
			if(parent != null) {
				val dv = parent.optDouble(key)
				if(dv.isFinite()) return clipRange(- 1f, 1f, dv.toFloat())
			}
			return 0f
		}
		
		// 下書きからの復元などに使うパラメータ
		// 後方互換性的な理由で概ねマストドンに合わせている
		private const val KEY_IS_STRING_ID = "isStringId"
		private const val KEY_ID = "id"
		private const val KEY_TYPE = "type"
		private const val KEY_URL = "url"
		private const val KEY_REMOTE_URL = "remote_url"
		private const val KEY_PREVIEW_URL = "preview_url"
		private const val KEY_TEXT_URL = "text_url"
		private const val KEY_DESCRIPTION = "description"
		private const val KEY_IS_SENSITIVE = "isSensitive"
		private const val KEY_META = "meta"
		private const val KEY_FOCUS = "focus"
		private const val KEY_X = "x"
		private const val KEY_Y = "y"
		private const val KEY_TIME_START_UPLOAD = "KEY_TIME_START_UPLOAD"
		var timeSeed : Long = 0L
		
		fun decodeJson(src : JSONObject) = TootAttachment(src, decode = true)
	}
	
	constructor(parser : TootParser, src : JSONObject) : this(parser.serviceType, src)
	
	//	ID of the attachment
	val id : EntityId
	
	//One of: "image", "video", "gifv". or may null ? may "unknown" ?
	override val type : String?
	
	//URL of the locally hosted version of the image
	val url : String?
	
	//For remote images, the remote URL of the original image
	val remote_url : String?
	
	//	URL of the preview image
	val preview_url : String?
	
	//	Shorter URL for the image, for insertion into text (only present on local images)
	val text_url : String?
	
	// ALT text (Mastodon 2.0.0 or later)
	override val description : String?
	
	override val focusX : Float
	override val focusY : Float
	
	// 内部フラグ: 再編集で引き継いだ添付メディアなら真
	var redraft : Boolean = false
	
	// MisskeyはメディアごとにNSFWフラグがある
	val isSensitive : Boolean
	
	var timeStartUpload : Long = 0L
	
	///////////////////////////////
	
	override fun hasUrl(url : String) : Boolean = when(url) {
		this.preview_url, this.remote_url, this.url, this.text_url -> true
		else -> false
	}
	
	constructor(serviceType : ServiceType, src : JSONObject) {
		
		when(serviceType) {
			ServiceType.MISSKEY -> {
				id = EntityId.mayDefault(src.parseString("id"))
				
				val mimeType = src.parseString("type") ?: "?"
				this.type = when {
					mimeType.startsWith("image/") -> TootAttachmentLike.TYPE_IMAGE
					mimeType.startsWith("video/") -> TootAttachmentLike.TYPE_VIDEO
					mimeType.startsWith("audio/") -> TootAttachmentLike.TYPE_VIDEO
					else -> TootAttachmentLike.TYPE_UNKNOWN
				}
				
				url = src.parseString("url")
				preview_url = src.parseString("thumbnailUrl")
				remote_url = url
				text_url = url
				
				description = arrayOf(
					src.parseString("name"),
					src.parseString("comment")
				)
					.filterNotNull()
					.joinToString(" / ")
				
				focusX = 0f
				focusY = 0f
				isSensitive = src.optBoolean("isSensitive", false)
				
			}
			
			else -> {
				id = EntityId.mayDefault(src.parseLong("id"))
				type = src.parseString("type")
				url = src.parseString("url")
				remote_url = src.parseString("remote_url")
				preview_url = src.parseString("preview_url")
				text_url = src.parseString("text_url")
				description = src.parseString("description")
				isSensitive = false
				
				val focus = src.optJSONObject("meta")?.optJSONObject("focus")
				focusX = parseFocusValue(focus, "x")
				focusY = parseFocusValue(focus, "y")
			}
		}
		
		// internal use
		// muse be > 0
		this.timeStartUpload = src.parseLong(KEY_TIME_START_UPLOAD) ?: ++ timeSeed
	}
	
	override val urlForThumbnail : String?
		get() = when {
			preview_url?.isNotEmpty() == true -> preview_url
			remote_url?.isNotEmpty() == true -> remote_url
			url?.isNotEmpty() == true -> url
			else -> null
		}
	
	fun getLargeUrl(pref : SharedPreferences) : String? {
		return if(Pref.bpPriorLocalURL(pref)) {
			if(url?.isNotEmpty() == true) url else remote_url
		} else {
			if(remote_url?.isNotEmpty() == true) remote_url else url
		}
	}
	
	fun getLargeUrlList(pref : SharedPreferences) : ArrayList<String> {
		val result = ArrayList<String>()
		if(Pref.bpPriorLocalURL(pref)) {
			if(url?.isNotEmpty() == true) result.add(url)
			if(remote_url?.isNotEmpty() == true) result.add(remote_url)
		} else {
			if(remote_url?.isNotEmpty() == true) result.add(remote_url)
			if(url?.isNotEmpty() == true) result.add(url)
		}
		return result
	}
	
	fun encodeJson() = jsonObject {
		put(KEY_IS_STRING_ID, id is EntityIdString)
		put(KEY_ID, id.toString())
		put(KEY_TYPE, type)
		put(KEY_URL, url)
		put(KEY_REMOTE_URL, remote_url)
		put(KEY_PREVIEW_URL, preview_url)
		put(KEY_TEXT_URL, text_url)
		put(KEY_DESCRIPTION, description)
		put(KEY_IS_SENSITIVE, isSensitive)
		put(KEY_TIME_START_UPLOAD, timeStartUpload)
		
		if(focusX != 0f || focusY != 0f) {
			put(KEY_META, jsonObject {
				put(KEY_FOCUS, jsonObject {
					put(KEY_X, focusX)
					put(KEY_Y, focusY)
				})
			})
		}
		
	}
	
	constructor(src : JSONObject, decode : Boolean) {

		id = if( src.optBoolean(KEY_IS_STRING_ID) ) {
			EntityId.mayDefault(src.parseString(KEY_ID))
		} else {
			EntityId.mayDefault(src.parseLong(KEY_ID))
		}

		type = src.parseString(KEY_TYPE)
		url = src.parseString(KEY_URL)
		remote_url = src.parseString(KEY_REMOTE_URL)
		preview_url = src.parseString(KEY_PREVIEW_URL)
		text_url = src.parseString(KEY_TEXT_URL)
		description = src.parseString(KEY_DESCRIPTION)
		isSensitive = src.optBoolean(KEY_IS_SENSITIVE)
		
		val focus = src.optJSONObject(KEY_META)?.optJSONObject(KEY_FOCUS)
		focusX = parseFocusValue(focus, KEY_X)
		focusY = parseFocusValue(focus, KEY_Y)
		
		timeStartUpload = src.parseLong(KEY_TIME_START_UPLOAD) ?: ++ timeSeed
	}
}

// v1.3 から 添付ファイルの画像のピクセルサイズが取得できるようになった
// https://github.com/tootsuite/mastodon/issues/1985
// "media_attachments" : [
//	 {
//	 "id" : 4,
//	 "type" : "image",
//	 "remote_url" : "",
//	 "meta" : {
//	 "original" : {
//	 "width" : 600,
//	 "size" : "600x400",
//	 "height" : 400,
//	 "aspect" : 1.5
//	 },
//	 "small" : {
//	 "aspect" : 1.49812734082397,
//	 "height" : 267,
//	 "size" : "400x267",
//	 "width" : 400
//	 }
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/original/3416fc5188c656da.jpg?1493138517",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/small/3416fc5188c656da.jpg?1493138517",
//	 "text_url" : "http://127.0.0.1:3000/media/4hfW3Kt4U9UxDvV_xug"
//	 },
//	 {
//	 "text_url" : "http://127.0.0.1:3000/media/0vTH_B1kjvIvlUBhGBw",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/small/23519a5e64064e32.png?1493138030",
//	 "meta" : {
//	 "fps" : 15,
//	 "duration" : 5.06,
//	 "width" : 320,
//	 "size" : "320x180",
//	 "height" : 180,
//	 "length" : "0:00:05.06",
//	 "aspect" : 1.77777777777778
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/original/23519a5e64064e32.mp4?1493138030",
//	 "remote_url" : "",
//	 "type" : "gifv",
//	 "id" : 3
//	 }
//	 ],