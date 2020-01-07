package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonObject

class TootPushSubscription(src : JsonObject){
	val id: EntityId
	val endpoint : String?
	private val alerts= HashMap<String,Boolean>()
	val server_key : String?

	init{
		id = EntityId.mayDefault(src.parseString("id"))
		endpoint = src.parseString("endpoint")
		server_key = src.parseString("server_key")
		
		src.parseJsonObject("alerts")?.let{
			for( k in it.keys ){
				alerts[k] = it.optBoolean(k)
			}
		}
	}
}
