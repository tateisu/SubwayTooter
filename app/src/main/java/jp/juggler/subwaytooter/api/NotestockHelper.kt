package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.cast
import jp.juggler.util.encodePercent
import okhttp3.Request

fun getNotestockStatuses(root: JsonObject): JsonArray? =
    root["statuses"].cast()

// notestock の検索結果からmax_dtを抽出します。
// データがない場合はnullを返します。
fun getNotestockMaxDt(root: JsonObject)=
    root.jsonArray("statuses")
        ?.mapNotNull{ it.cast<JsonObject>()?.string("published")}
        ?.map{ Pair(it, TootStatus.parseTime(it))}
        ?.filter { it.second != 0L }
        ?.minByOrNull { it.second }
        ?.first

fun TootApiClient.searchNotestock(
    query: String,
    max_dt: String?
): TootApiResult? {

    val result = TootApiResult.makeWithCaption("Notestock")
    if (result.error != null) return result

    if (!sendRequest(result) {
            val url = StringBuilder().apply {
                append("https://notestock.osa-p.net/api/v1/search.json?q=")
                append(query.encodePercent())
                if (max_dt != null) append("&max_dt=").append(max_dt.encodePercent())
            }.toString()

            Request.Builder()
                .url(url)
                .build()

        }) return result

    return parseJson(result)
}

