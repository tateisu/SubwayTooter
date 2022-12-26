package jp.juggler.subwaytooter.search

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnTask_Loading
import jp.juggler.subwaytooter.column.ColumnTask_Refresh
import jp.juggler.subwaytooter.column.addWithFilterStatus
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.cast

object TootsearchHelper {

    private val log = LogCategory("TootsearchHelper")

    private fun getHits(root: JsonObject): JsonArray? {
        return root["hits"].cast<JsonObject>()?.get("hits")?.cast()
    }

    // returns the number for "from" parameter of next page.
    // returns null if no more next page.
    private fun getNextId(root: JsonObject, oldSize: Int): String? =
        getHits(root)?.size?.takeIf { it > 0 }?.let { (oldSize + it) }?.toString()

    @Suppress(
        "unused",
        "RedundantNullableReturnType",
        "RedundantSuspendModifier",
        "UNUSED_PARAMETER",
    )
    private suspend fun TootApiClient.search(
        context: Context,
        query: String,
        from: Int?,
    ): TootApiResult? {
        return TootApiResult("Tootsearch discontinued service on 2022/12/25.")

//        val result = TootApiResult.makeWithCaption("Tootsearch")
//        if (result.error != null) return result
//        if (!sendRequest(result) {
//                val url = StringBuilder().apply {
//                    append("https://tootsearch.chotto.moe/api/v1/search?sort=")
//                    append("created_at:desc".encodePercent())
//                    append("&q=").append(query.encodePercent())
//                    if (from != null) append("&from=").append(from.toString())
//                }.toString()
//
//                Request.Builder().url(url).build()
//            }) return result
//
//        return parseJson(result)
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
            client.search(
                context = context,
                query = column.searchQuery,
                from = null
            )?.also { result ->
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
            client.search(
                context = context,
                query = q,
                from = oldSize,
            )?.also { result ->
                result.jsonObject?.let { root ->
                    column.idOld = EntityId.mayNull(getNextId(root, oldSize))
                    listTmp = addWithFilterStatus(listTmp, parseList(parser, root))
                }
            }
        }
    }
}
