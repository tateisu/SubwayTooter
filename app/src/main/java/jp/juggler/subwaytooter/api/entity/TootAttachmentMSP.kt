package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.Utils
import org.json.JSONArray

class TootAttachmentMSP(
	val preview_url : String
) : TootAttachmentLike {
	
	
	override val type : String?
		get() = null
	
	override val description : String?
		get() = null
	
	override val urlForThumbnail : String?
		get() = preview_url
	
	override fun hasUrl(url:String):Boolean = (url == this.preview_url)
	
	companion object {
		fun parseList(array : JSONArray?) : TootAttachmentLike.List? {
			if(array != null) {
				val array_size = array.length()
				if(array_size > 0) {
					val result = TootAttachmentLike.List()
					result.ensureCapacity(array_size)
					for(i in 0 until array_size) {
						val sv = Utils.optStringX(array, i)
						if(sv != null && sv.isNotBlank()) {
							result.add(TootAttachmentMSP(sv))
						}
					}
					if(result.isNotEmpty()) return result
				}
				
			}
			return null
		}
		
	}
	
}