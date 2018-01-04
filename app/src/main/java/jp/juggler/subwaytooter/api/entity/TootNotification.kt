package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootNotification(parser : TootParser, src : JSONObject){
	
	val id:Long
	
	//	One of: "mention", "reblog", "favourite", "follow"
	val type : String
	
	//	The time the notification was created
	private val created_at : String
	
	//	The Account sending the notification to the user
	val account : TootAccount?
	
	//	The Status associated with the notification, if applicable
	val status : TootStatus?
	
	val time_created_at : Long
	
	val json : JSONObject
	
	init {
		this.json = src
		this.id = Utils.optLongX(src, "id")
		this.type = Utils.optStringX(src, "type") ?: ""
		this.created_at = Utils.optStringX(src, "created_at") ?: ""
		this.account = TootAccount.parse(parser.context, parser.accessInfo, src.optJSONObject("account"))
		this.status = TootStatus.parse(parser, src.optJSONObject("status"))
		this.time_created_at = TootStatus.parseTime(this.created_at)
	}
	
	class List : ArrayList<TootNotification>()
	
	companion object {
		private val log = LogCategory("TootNotification")
		
		const val TYPE_MENTION = "mention"
		const val TYPE_REBLOG = "reblog"
		const val TYPE_FAVOURITE = "favourite"
		const val TYPE_FOLLOW = "follow"
		
		fun parse(parser : TootParser, src : JSONObject?) : TootNotification? {
			src ?: return null
			return try {
				TootNotification(parser, src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootNotification.parse failed.")
				null
			}
		}
		
		fun parseList(parser : TootParser, array : JSONArray?) : List {
			val result = List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val src = array.optJSONObject(i) ?: continue
					val item = parse(parser, src)
					if(item != null) result.add(item)
				}
			}
			return result
		}
	}
	
}
