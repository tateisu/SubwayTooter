package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonObject

class TootReport(src: JsonObject) : TimelineItem() {

    val id = EntityId.mayDefault(src.string("id"))

    // The action taken in response to the report
    @Suppress("unused")
    val action_taken = src.string("action_taken")

    override fun getOrderId() = id
}
