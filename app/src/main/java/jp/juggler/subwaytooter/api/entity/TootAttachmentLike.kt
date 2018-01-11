package jp.juggler.subwaytooter.api.entity

interface TootAttachmentLike{
	
	val type : String?
	val description : String?
	val urlForThumbnail : String?
	
	fun hasUrl(url:String):Boolean
	
	companion object {
		const val TYPE_IMAGE = "image"
		const val TYPE_VIDEO = "video"
		const val TYPE_GIFV = "gifv"
		const val TYPE_UNKNOWN = "unknown"
	}
}

