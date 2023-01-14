package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notBlank
import jp.juggler.util.log.LogCategory

// https://docs.joinmastodon.org/entities/Filter/
// https://docs.joinmastodon.org/entities/V1_Filter/
class TootFilter(src: JsonObject) : TimelineItem() {

    companion object {
        private val log = LogCategory("TootFilter")

        fun parseList(src: JsonArray?) =
            src?.objectList()?.mapNotNull {
                try {
                    TootFilter(it)
                } catch (ex: Throwable) {
                    log.e(ex, "TootFilter parse failed.")
                    null
                }
            }

        fun parse1(src: JsonObject?) = try {
            src?.let { TootFilter(it) }
        } catch (ex: Throwable) {
            log.e(ex, "TootFilter parse failed.")
            null
        }
    }

    val id: EntityId = EntityId.mayDefault(src.string("id"))

    // v2
    val title: String? = src.string("title")

    private val contextBits: Int = TootFilterContext.parseBits(src.jsonArray("context"))

    // フィルタの適用先の名前の文字列IDのリスト
    val contextNames
        get() = TootFilterContext.bitsToNames(contextBits)

    // null is not specified, or "2018-07-06T00:59:13.161Z"
    private val expires_at: String? = src.string("expires_at")

    // 0L if not specified
    val time_expires_at: Long = TootStatus.parseTime(expires_at)

    // v2: filter_action is "warn" or "hide".
    // v1: irreversible boolean flag.
    val filter_action: String = src.string("filter_action") ?: run {
        // v1
        when (src.boolean("irreversible")) {
            true -> "hide"
            else -> "warn"
        }
    }

    val keywords: List<TootFilterKeyword>? =
        src.jsonArray("keywords")?.let { a ->
            /* v2 */ a.objectList().map { TootFilterKeyword(it) }
        } ?: src.string("phrase").notBlank()?.let {
            listOf(
                /* v1 */
                TootFilterKeyword(
                    keyword = it,
                    whole_word = src.boolean("whole_word") ?: false
                )
            )
        }

    // フィルタにマッチしたステータスのIDのリスト
    val statuses = src.jsonArray("statuses")?.objectList()?.map { TootFilterStatus(it) }

    fun hasContext(fc: TootFilterContext) =
        contextBits.and(fc.bit) != 0
}
