package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

class TootPushSubscription(src: JsonObject) {
    val id: EntityId
    val endpoint: String?
    val alerts = HashMap<String, Boolean>()
    val server_key: String?

    init {
        id = EntityId.mayDefault(src.string("id"))
        endpoint = src.string("endpoint")
        server_key = src.string("server_key")

        src.jsonObject("alerts")?.let {
            for (k in it.keys) {
                alerts[k] = it.optBoolean(k)
            }
        }
    }
}
