package jp.juggler.subwaytooter.search

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.addWithFilterStatus
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.*
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

    private fun TootApiClient.search(query: String,max_dt: String?): TootApiResult? {
        val result = TootApiResult.makeWithCaption("Notestock")
        if (result.error != null) return result
        if (!sendRequest(result) {
                val url = StringBuilder().apply {
                    append("https://notestock.osa-p.net/api/v1/search.json?q=")
                    append(query.encodePercent())
                    if (max_dt != null) append("&max_dt=").append(max_dt.encodePercent())
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
                        TootStatus.log.trace(ex)
                    }
                }
            }
        }

    fun ColumnTask_Loading.loadingNotestock(client: TootApiClient): TootApiResult? {
        column.idOld = null
        val q = column.search_query.trim { it <= ' ' }
        return if (q.isEmpty()) {
            list_tmp = java.util.ArrayList()
            TootApiResult()
        } else {
            client.search(column.search_query, null)?.also { result ->
                result.jsonObject?.let { data ->
                    column.idOld = EntityId.mayNull(getNextId(data))
                    list_tmp = addWithFilterStatus(
                        null,
                        parseList(parser, data)
                            .also {
                                if (it.isEmpty())
                                    log.d("search result is empty. %s", result.bodyString)

                            }
                    )
                }
            }
        }
    }

    fun ColumnTask_Refresh.refreshNotestock(client: TootApiClient): TootApiResult? {
        if (!bBottom) return TootApiResult("head of list.")
        val q = column.search_query.trim { it <= ' ' }
        val old = column.idOld?.toString()
        return if (q.isEmpty() || old == null) {
            list_tmp = ArrayList()
            TootApiResult(context.getString(R.string.end_of_list))
        } else {
            client.search(q, old)?.also { result ->
                result.jsonObject?.let { data ->
                    column.idOld = EntityId.mayNull(getNextId(data))
                    list_tmp = addWithFilterStatus(
                        list_tmp,
                        parseList(parser, data)
                    )
                }
            }
        }
    }
}
