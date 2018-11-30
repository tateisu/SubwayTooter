package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.parseLong
import org.json.JSONObject
import jp.juggler.util.parseString

class TootPushSubscription(src : JSONObject){
	val id: EntityId
	val endpoint : String?
	private val alerts= HashMap<String,Boolean>()
	val server_key : String?

	init{
		id = EntityId.mayDefault(src.parseLong("id"))
		endpoint = src.parseString("endpoint")
		server_key = src.parseString("server_key")
		
		src.optJSONObject("alerts")?.let{
			for( k in it.keys() ){
				alerts[k] = it.optBoolean(k)
			}
		}
	}
	
}
