package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.LogCategory

class TootFilterResult(src: JsonObject) {
    companion object {
        private val log = LogCategory("TootFilterResult")
        fun parseList(src: JsonArray?): List<TootFilterResult>? =
            try {
                src?.objectList()?.map { TootFilterResult(it) }
            } catch (ex: Throwable) {
                log.e(ex, "parseList failed")
                null
            }
    }

    val filter: TootFilter? =
        TootFilter.parse1(src.jsonObject("filter"))

    val keyword_matches: List<String>? =
        src.jsonArray("keyword_matches")?.stringList()

    val status_matches: EntityId? =
        EntityId.mayNull(src.string("status_matches"))

    val isHide
        get() = filter?.hide == true
}
