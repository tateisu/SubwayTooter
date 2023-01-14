package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

class TootFilterResult(src: JsonObject) {

    val filter: TootFilter? =
        TootFilter.parse1(src.jsonObject("filter"))

    val keyword_matches: List<String>? =
        src.jsonArray("keyword_matches")?.stringList()

    val status_matches: EntityId? =
        EntityId.mayNull(src.string("status_matches"))
}
