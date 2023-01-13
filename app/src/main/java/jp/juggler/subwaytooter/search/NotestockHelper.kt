package jp.juggler.subwaytooter.search

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
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.cast
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.LogCategory
import okhttp3.Request

/*
notestockの検索API。
https://notestock.docs.apiary.io/#reference/0/search/search-of-notestock
「APIは公開なので、そのまま呼び出してもらえばOKです。」とのこと。
*/

object NotestockHelper {
    private val log = LogCategory("NotestockHelper")

    // notestock の検索結果からmax_dtを抽出します。
    // データがない場合はnullを返します。
    private fun getNextId(root: JsonObject) =
        root.jsonArray("statuses")
            ?.mapNotNull { it.cast<JsonObject>()?.string("published") }
            ?.map { Pair(it, TootStatus.parseTime(it)) }
            ?.filter { it.second != 0L }
            ?.minByOrNull { it.second }
            ?.first

    private suspend fun TootApiClient.search(query: String, nextId: String?): TootApiResult? {
        val result = TootApiResult.makeWithCaption("Notestock")
        if (result.error != null) return result
        if (!sendRequest(result) {
                val url = StringBuilder().apply {
                    append("https://notestock.osa-p.net/api/v1/search.json?q=")
                    append(query.encodePercent())
                    if (nextId != null) append("&max_dt=").append(nextId.encodePercent())
                }.toString()

                Request.Builder().url(url).build()
            }) return result

        return parseJson(result)
    }

    private fun parseList(parser: TootParser, root: JsonObject) =
        ArrayList<TootStatus>().apply {
            root.jsonArray("statuses")?.let { array ->
                ensureCapacity(array.size)
                parser.serviceType = ServiceType.NOTESTOCK
                for (src in array) {
                    try {
                        if (src !is JsonObject) continue
                        add(TootStatus(parser, src))
                    } catch (ex: Throwable) {
                        log.e(ex, "parse item failed.")
                    }
                }
            }
        }

    suspend fun ColumnTask_Loading.loadingNotestock(client: TootApiClient): TootApiResult? {
        column.idOld = null
        val q = column.searchQuery.trim { it <= ' ' }
        return if (q.isEmpty()) {
            listTmp = java.util.ArrayList()
            TootApiResult()
        } else {
            client.search(column.searchQuery, null)?.also { result ->
                result.jsonObject?.let { data ->
                    column.idOld = EntityId.mayNull(getNextId(data))
                    listTmp = addWithFilterStatus(
                        null,
                        parseList(parser, data)
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

    suspend fun ColumnTask_Refresh.refreshNotestock(client: TootApiClient): TootApiResult? {
        if (!bBottom) return TootApiResult("head of list.")
        val q = column.searchQuery.trim { it <= ' ' }
        val old = column.idOld?.toString()
        return if (q.isEmpty() || old == null) {
            listTmp = ArrayList()
            TootApiResult(context.getString(R.string.end_of_list))
        } else {
            client.search(q, old)?.also { result ->
                result.jsonObject?.let { data ->
                    column.idOld = EntityId.mayNull(getNextId(data))
                    listTmp = addWithFilterStatus(
                        listTmp,
                        parseList(parser, data)
                    )
                }
            }
        }
    }
}
