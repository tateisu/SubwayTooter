package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.put
import jp.juggler.util.*
import okhttp3.Request


private const val mspTokenUrl = "http://mastodonsearch.jp/api/v1.0.1/utoken"
private const val mspSearchUrl = "http://mastodonsearch.jp/api/v1.0.1/cross"
private const val mspApiKey = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"

fun getMspMaxId(array: JsonArray, old: String?): String? {
    // max_id の更新
    val size = array.size
    if (size > 0) {
        val sv = array[size - 1].cast<JsonObject>()?.string("msp_id")?.notEmpty()
        if (sv != null) return sv
    }
    // MSPでは終端は分からず、何度もリトライする
    return old
}


fun TootApiClient.searchMsp(query: String, max_id: String?): TootApiResult? {

    // ユーザトークンを読む
    var user_token: String? = Pref.spMspUserToken(pref)

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
                pref.edit().put(Pref.spMspUserToken, user_token).apply()
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
                    .append("&max=").append(max_id?.encodePercent() ?: "")

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
