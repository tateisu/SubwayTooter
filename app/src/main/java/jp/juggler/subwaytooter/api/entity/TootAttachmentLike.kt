package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory

interface TootAttachmentLike{
	
	val type : String?
	val description : String?
	val urlForThumbnail : String?
	
	fun hasUrl(url:String):Boolean
	
	companion object {
		private var log = LogCategory("TootAttachmentLike")
		
		const val TYPE_IMAGE = "image"
		const val TYPE_VIDEO = "video"
		const val TYPE_GIFV = "gifv"
		const val TYPE_UNKNOWN = "unknown"
	}
}

