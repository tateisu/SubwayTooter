package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

class TootPushSubscription(src: JsonObject) {
    val id = src.string("id")
    val endpoint = src.string("endpoint")
    val serverKey: String? = src.string("server_key")
    val alerts = HashMap<String, Boolean>().apply {
        src.jsonObject("alerts")?.let {
            for (k in it.keys) {
                put(k, it.optBoolean(k))
            }
        }
    }
}
