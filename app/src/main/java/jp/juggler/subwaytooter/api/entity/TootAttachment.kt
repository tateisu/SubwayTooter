package jp.juggler.subwaytooter.api.entity

import android.content.SharedPreferences

import org.json.JSONObject

import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.util.Utils

class TootAttachment(src : JSONObject) : TootAttachmentLike {
	
	val json : JSONObject

	//	ID of the attachment
	val id : Long
	
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
	
	override fun hasUrl(url : String) : Boolean = when(url) {
		this.preview_url, this.remote_url, this.url, this.text_url -> true
		else -> false
	}
	
	init {
		json = src
		id = Utils.optLongX(src, "id")
		type = Utils.optStringX(src, "type")
		url = Utils.optStringX(src, "url")
		remote_url = Utils.optStringX(src, "remote_url")
		preview_url = Utils.optStringX(src, "preview_url")
		text_url = Utils.optStringX(src, "text_url")
		description = Utils.optStringX(src, "description")
	}
	
	override val urlForThumbnail : String?
		get() = when {
			preview_url?.isNotEmpty() == true -> preview_url
			remote_url?.isNotEmpty() == true -> remote_url
			url?.isNotEmpty() == true -> url
			else -> null
		}
	
	fun getLargeUrl(pref : SharedPreferences) : String? {
		return if( pref.getBoolean(Pref.KEY_PRIOR_LOCAL_URL, false) ){
			if( url?.isNotEmpty() ==true) url else remote_url
		} else {
			if( remote_url?.isNotEmpty() == true ) remote_url else url
		}
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