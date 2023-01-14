package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

// https://docs.joinmastodon.org/entities/FilterStatus/
// Represents a status ID that, if matched, should cause the filter action to be taken.
class TootFilterStatus(src: JsonObject) {
    val id = EntityId.mayDefault(src.string("id"))
    val status_id = EntityId.mayDefault(src.string("status_id"))
}
