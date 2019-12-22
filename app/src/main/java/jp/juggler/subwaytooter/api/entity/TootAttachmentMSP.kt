package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.notEmpty
import jp.juggler.util.parseString
import org.json.JSONArray

class TootAttachmentMSP(
	val preview_url : String
) : TootAttachmentLike {
	
	override val type : TootAttachmentType
		get() = TootAttachmentType.Unknown
	
	override val description : String?
		get() = null
	
	override val urlForThumbnail : String?
		get() = preview_url
	
	override val urlForDescription : String?
		get() = preview_url

	override val focusX : Float
		get() = 0f
	
	override val focusY : Float
		get() = 0f
	
	override fun hasUrl(url : String) : Boolean = (url == this.preview_url)
	
	companion object {
		fun parseList(array : JSONArray?) : ArrayList<TootAttachmentLike>? {
			if(array != null) {
				val array_size = array.length()
				if(array_size > 0) {
					val result = ArrayList<TootAttachmentLike>()
					result.ensureCapacity(array_size)
					for(i in 0 until array_size) {
						val sv = array.parseString(i)
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