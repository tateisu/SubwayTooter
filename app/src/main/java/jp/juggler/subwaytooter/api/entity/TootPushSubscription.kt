package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.parseLong
import org.json.JSONObject
import jp.juggler.subwaytooter.util.parseString

class TootPushSubscription(src : JSONObject){
	val id: EntityId?
	val endpoint : String?
	private val alerts= HashMap<String,Boolean>()
	val server_key : String?

	init{
		id = EntityId.mayNull(src.parseLong("id"))
		endpoint = src.parseString("endpoint")
		server_key = src.parseString("server_key")
		
		src.optJSONObject("alerts")?.let{
			for( k in it.keys() ){
				alerts[k] = it.optBoolean(k)
			}
		}
	}
	
}
