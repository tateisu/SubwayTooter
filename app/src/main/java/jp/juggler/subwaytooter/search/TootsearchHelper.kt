package jp.juggler.subwaytooter.search

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.column.addWithFilterStatus
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnTask_Loading
import jp.juggler.subwaytooter.column.ColumnTask_Refresh
import jp.juggler.util.*
import okhttp3.Request

object TootsearchHelper {

    private val log = LogCategory("TootsearchHelper")

    private fun getHits(root: JsonObject): JsonArray? {
        return root["hits"].cast<JsonObject>()?.get("hits")?.cast()
    }

    // returns the number for "from" parameter of next page.
    // returns null if no more next page.
    private fun getNextId(root: JsonObject, oldSize: Int): String? =
        getHits(root)?.size?.takeIf { it > 0 }?.let { (oldSize + it) }?.toString()

    private suspend fun TootApiClient.search(query: String, from: Int?): TootApiResult? {
        val result = TootApiResult.makeWithCaption("Tootsearch")
        if (result.error != null) return result
        if (!sendRequest(result) {
                val url = StringBuilder().apply {
                    append("https://tootsearch.chotto.moe/api/v1/search?sort=")
                    append("created_at:desc".encodePercent())
                    append("&q=").append(query.encodePercent())
                    if (from != null) append("&from=").append(from.toString())
                }.toString()

                Request.Builder().url(url).build()
            }) return result

        return parseJson(result)
    }

    private fun parseList(parser: TootParser, root: JsonObject) =
        ArrayList<TootStatus>().apply {
            getHits(root)?.let { array ->
                ensureCapacity(array.size)
                parser.serviceType = ServiceType.TOOTSEARCH
                for (src in array) {
                    try {
                        val source = src.cast<JsonObject>()?.jsonObject("_source") ?: continue
                        add(TootStatus(parser, source))
                    } catch (ex: Throwable) {
                        log.trace(ex)
                    }
                }
            }
        }

    suspend fun ColumnTask_Loading.loadingTootsearch(client: TootApiClient): TootApiResult? {
        column.idOld = null
        val q = column.searchQuery.trim { it <= ' ' }
        return if (q.isEmpty()) {
            listTmp = java.util.ArrayList()
            TootApiResult()
        } else {
            client.search(column.searchQuery, null)?.also { result ->
                result.jsonObject?.let { root ->
                    column.idOld = EntityId.mayNull(getNextId(root, 0))
                    listTmp = addWithFilterStatus(
                        null,
                        parseList(parser, root)
                            .also {
                                if (it.isEmpty()) {
                                    log.d("search result is empty. ${result.bodyString}")
                                }
                            }
                    )
                }
            }
        }
    }

    suspend fun ColumnTask_Refresh.refreshTootsearch(client: TootApiClient): TootApiResult? {
        if (!bBottom) return TootApiResult("head of list.")

        val q = column.searchQuery.trim { it <= ' ' }
        val oldSize = column.idOld?.toString()?.toInt()
        return if (q.isEmpty() || oldSize == null) {
            listTmp = ArrayList()
            TootApiResult(context.getString(R.string.end_of_list))
        } else {
            client.search(q, oldSize)?.also { result ->
                result.jsonObject?.let { root ->
                    column.idOld = EntityId.mayNull(getNextId(root, oldSize))
                    listTmp = addWithFilterStatus(listTmp, parseList(parser, root))
                }
            }
        }
    }
}
