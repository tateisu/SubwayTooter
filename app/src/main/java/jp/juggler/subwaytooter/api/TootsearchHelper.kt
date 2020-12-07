package jp.juggler.subwaytooter.api

import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.cast
import jp.juggler.util.encodePercent
import okhttp3.Request


fun getTootsearchHits(root: JsonObject): JsonArray? {
    return root["hits"].cast<JsonObject>()?.get("hits")?.cast()
}

// returns the number for "from" parameter of next page.
// returns null if no more next page.
fun getTootsearchMaxId(root: JsonObject, old: Long?): Long? {
    val size = getTootsearchHits(root)?.size ?: 0
    return when {
        size <= 0 -> null
        else -> (old ?: 0L) + size.toLong()
    }
}

fun TootApiClient.searchTootsearch(
    query: String,
    from: Long?
): TootApiResult? {

    val result = TootApiResult.makeWithCaption("Tootsearch")
    if (result.error != null) return result

    if (!sendRequest(result) {
            val sb = StringBuilder()
                .append("https://tootsearch.chotto.moe/api/v1/search?sort=")
                .append("created_at:desc".encodePercent())
                .append("&q=").append(query.encodePercent())
            if (from != null) {
                sb.append("&from=").append(from.toString().encodePercent())
            }

            Request.Builder()
                .url(sb.toString())
                .build()

        }) return result

    return parseJson(result)
}