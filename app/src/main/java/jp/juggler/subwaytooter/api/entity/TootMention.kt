package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootMention( src: JSONObject ) {
	
	//	URL of user's profile (can be remote)
	val url : String
	
	//	The username of the account
	val username : String
	
	//	Equals username for local users, includes @domain for remote ones
	val acct : String
	
	//	Account ID
	val id : Long
	
	init{
		this.url = Utils.optStringX(src, "url") ?: ""
		this.username = Utils.optStringX(src, "username") ?:"?"
		this.acct = Utils.optStringX(src, "acct") ?: "?"
		this.id = Utils.optLongX(src, "id")
	}
	
	class List : ArrayList<TootMention>()
	
	companion object {
		private val log = LogCategory("TootMention")
		
		fun parse(src : JSONObject?) : TootMention? {
			src ?: return null
			return try {
				TootMention(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootMention.parse failed.")
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
		fun parseListOrNull(array : JSONArray?) : List? {
			array ?: return null
			val result = parseList(array)
			return if( result.isEmpty() ) null else result
		}
	}
}
