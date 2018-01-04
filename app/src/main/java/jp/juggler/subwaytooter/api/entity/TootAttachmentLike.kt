package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory
import org.json.JSONArray
import org.json.JSONException

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

	class List : ArrayList<TootAttachmentLike>(){
		fun encode() : JSONArray {
			val a = JSONArray()
			for(ta in this) {
				if( ta is TootAttachment)
				try {
					val item = ta.encodeJSON()
					a.put(item)
				} catch(ex : JSONException) {
					log.e(ex, "encode failed.")
				}
				
			}
			return a
		}
	}


}