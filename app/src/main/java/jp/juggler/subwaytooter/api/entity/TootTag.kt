package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootTag {
	
	// The hashtag, not including the preceding #
	var name : String? = null
	
	// The URL of the hashtag
	var url : String? = null
	
	class List : ArrayList<TootTag>()
	
	companion object {
		
		private val log = LogCategory("TootTag")
		
		fun parse(src : JSONObject?) : TootTag? {
			if(src == null) return null
			try {
				val dst = TootTag()
				dst.name = Utils.optStringX(src, "name")
				dst.url = Utils.optStringX(src, "url")
				return dst
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				return null
			}
			
		}
		
		fun parseList(array : JSONArray?) : List {
			val result = List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val src = array.optJSONObject(i) ?: continue
					val item = parse(src)
					if(item != null) result.add(item)
				}
			}
			return result
		}
		
		fun parseListOrNull(array : JSONArray?) : List? {
			array ?: return null
			val result = parseList(array)
			return if( result.isEmpty() ) null else result
		}
	}
}
