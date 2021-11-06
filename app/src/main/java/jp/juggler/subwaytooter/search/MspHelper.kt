package jp.juggler.subwaytooter.search

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.column.addWithFilterStatus
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.column.ColumnTask_Loading
import jp.juggler.subwaytooter.column.ColumnTask_Refresh
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.put
import jp.juggler.util.*
import okhttp3.Request

object MspHelper {
    private const val mspTokenUrl = "https://msearch.fediverse.media/api/v1.0.1/utoken"
    private const val mspSearchUrl = "https://msearch.fediverse.media/api/v1.0.1/cross"
    private const val mspApiKey = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"

    // 検索結果からページネーション用IDを抽出する
    // MSPでは終端は分からないので、配列が空でも次回また同じ位置から読み直す
    private fun getNextId(array: JsonArray, old: String?) =
        array.lastOrNull().cast<JsonObject>()?.string("msp_id")?.notEmpty() ?: old

    private suspend fun TootApiClient.search(query: String, maxId: String?): TootApiResult? {

        // ユーザトークンを読む
        var user_token: String? = PrefS.spMspUserToken(pref)

        for (nTry in 0 until 3) {
            if (callback.isApiCancelled) return null

            // ユーザトークンがなければ取得する
            if (user_token == null || user_token.isEmpty()) {

                callback.publishApiProgress("get MSP user token...")

                val result: TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
                if (result.error != null) return result

                if (!sendRequest(result) {
                        Request.Builder()
                            .url(mspTokenUrl + "?apikey=" + mspApiKey.encodePercent())
                            .build()
                    }) return result

                val r2 = parseJson(result) { json ->
                    val error = json.string("error")
                    if (error == null) {
                        null
                    } else {
                        val type = json.string("type")
                        "error: $type $error"
                    }
                }
                val jsonObject = r2?.jsonObject ?: return r2
                user_token = jsonObject.jsonObject("result")?.string("token")
                if (user_token?.isEmpty() != false) {
                    return result.setError("Can't get MSP user token. response=${result.bodyString}")
                } else {
                    pref.edit().put(PrefS.spMspUserToken, user_token).apply()
                }
            }

            // ユーザトークンを使って検索APIを呼び出す
            val result: TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
            if (result.error != null) return result

            if (!sendRequest(result) {
                    val url = StringBuilder()
                        .append(mspSearchUrl)
                        .append("?apikey=").append(mspApiKey.encodePercent())
                        .append("&utoken=").append(user_token.encodePercent())
                        .append("&q=").append(query.encodePercent())
                        .append("&max=").append(maxId?.encodePercent() ?: "")

                    Request.Builder().url(url.toString()).build()
                }) return result

            var isUserTokenError = false
            val r2 = parseJson(result) { json ->
                val error = json.string("error")
                if (error == null) {
                    null
                } else {
                    // ユーザトークンがダメなら生成しなおす
                    val detail = json.string("detail")
                    if ("utoken" == detail) {
                        isUserTokenError = true
                    }

                    val type = json.string("type")
                    "API returns error: $type $error"
                }
            }
            if (r2 == null || !isUserTokenError) return r2
        }
        return TootApiResult("MSP user token retry exceeded.")
    }

    private fun parseList(parser: TootParser, root: JsonArray) =
        parser.apply { serviceType = ServiceType.MSP }.statusList(root)

    suspend fun ColumnTask_Loading.loadingMSP(client: TootApiClient): TootApiResult? {
        column.idOld = null
        val q = column.searchQuery.trim { it <= ' ' }
        return if (q.isEmpty()) {
            listTmp = java.util.ArrayList()
            TootApiResult()
        } else {
            client.search(column.searchQuery, column.idOld?.toString())?.also { result ->
                result.jsonArray?.let { root ->
                    column.idOld = EntityId.mayNull(getNextId(root, null))
                    listTmp = addWithFilterStatus(null, parseList(parser, root))
                }
            }
        }
    }

    suspend fun ColumnTask_Refresh.refreshMSP(client: TootApiClient): TootApiResult? {
        if (!bBottom) return TootApiResult("head of list.")

        val q = column.searchQuery.trim()
        val old = column.idOld?.toString()
        return if (q.isEmpty() || old == null) {
            listTmp = ArrayList()
            TootApiResult(context.getString(R.string.end_of_list))
        } else {
            client.search(q, old)?.also { result ->
                result.jsonArray?.let { root ->
                    column.idOld = EntityId.mayNull(getNextId(root, column.idOld?.toString()))
                    listTmp = addWithFilterStatus(listTmp, parseList(parser, root))
                }
            }
        }
    }
}
