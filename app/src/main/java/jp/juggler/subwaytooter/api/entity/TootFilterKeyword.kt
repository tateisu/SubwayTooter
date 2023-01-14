package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject

class TootFilterKeyword(
    var id: EntityId? = null,// v1 has no id
    var keyword: String?,
    var whole_word: Boolean,
) {
    // from Mastodon api/v2/filter
    constructor(src: JsonObject) : this(
        id = EntityId.mayNull(src.string("id")),
        keyword = src.string("keyword"),
        whole_word = src.boolean("whole_word") ?: true,
    )
}
