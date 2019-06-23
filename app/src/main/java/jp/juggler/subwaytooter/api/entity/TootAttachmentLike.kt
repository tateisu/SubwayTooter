package jp.juggler.subwaytooter.api.entity

enum class TootAttachmentType(val id:String){
	Unknown("unknown"),
	Image( "image"),
	Video("video"),
	GIFV("gifv"),
	Audio("audio")
}

interface TootAttachmentLike{

	val type : TootAttachmentType
	val description : String?
	val urlForThumbnail : String?
	
	val focusX : Float
	val focusY : Float
	
	fun hasUrl(url:String):Boolean
	
	fun getUrlString() :String?
	
	
	val isAudio : Boolean
		get()= type == TootAttachmentType.Audio
	
	// GIFVの考慮漏れに注意？
	val isVideo : Boolean
		get()= type == TootAttachmentType.Video
	
}

