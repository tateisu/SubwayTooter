package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootReport(src:JSONObject){
	
	val id:Long
	//	The action taken in response to the report
	val action_taken : String

	init {
		this.id = Utils.optLongX(src, "id")
		this.action_taken = Utils.optStringX(src, "action_taken") ?: ""
	}

	class List : ArrayList<TootReport>()
	
	companion object {
		
		private val log = LogCategory("TootReport")
		
		fun parse(src : JSONObject?) : TootReport? {
			src ?: return null
			return try {
				TootReport(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
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
	}
}
