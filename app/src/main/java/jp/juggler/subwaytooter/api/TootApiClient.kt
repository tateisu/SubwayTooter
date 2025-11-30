package jp.juggler.subwaytooter.api

import android.content.Context
import android.net.Uri
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRefOrNull
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootResults
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.SimpleHttpClient
import jp.juggler.subwaytooter.util.SimpleHttpClientImpl
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.data.CharacterGroup
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.asciiRegex
import jp.juggler.util.data.decodeJsonArray
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.decodePercent
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.encodePercent
import jp.juggler.util.data.groupEx
import jp.juggler.util.data.letNotEmpty
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.network.toPostRequestBuilder
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.closeQuietly

class TootApiClient(
    val context: Context,
    val httpClient: SimpleHttpClient =
        SimpleHttpClientImpl(context, App1.ok_http_client),
    val callback: TootApiCallback,
) {
    companion object {

        private val log = LogCategory("TootApiClient")

        private const val NO_INFORMATION = "(no information)"

        val reStartJsonArray = """\A\s*\[""".asciiRegex()
        val reStartJsonObject = """\A\s*\{""".asciiRegex()

        val DEFAULT_JSON_ERROR_PARSER =
            { json: JsonObject -> json["error"]?.toString() }

        fun formatResponse(
            response: Response,
            caption: String = "?",
            bodyString: String? = null,
        ) = TootApiResult(
            response = response,
            caption = caption,
            bodyString = bodyString
        ).apply { parseErrorResponse() }.error ?: "(null)"

        fun simplifyErrorHtml(
            response: Response,
            caption: String = "?",
            bodyString: String = response.body.string(),
            jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
        ) = TootApiResult(
            response = response,
            caption = caption,
        ).simplifyErrorHtml(bodyString, jsonErrorParser)
    }

    // 認証に関する設定を保存する

    // インスタンスのホスト名
    var apiHost: Host? = null

    // アカウントがある場合に使用する
    var account: SavedAccount? = null
        set(value) {
            apiHost = value?.apiHost
            field = value
        }

    var currentCallCallback: (Call) -> Unit
        get() = httpClient.onCallCreated
        set(value) {
            httpClient.onCallCreated = value
        }

    internal suspend fun isApiCancelled() = callback.isApiCancelled()

    suspend fun publishApiProgress(s: String) {
        callback.publishApiProgress(s)
    }

    suspend fun publishApiProgressRatio(value: Int, max: Int) {
        callback.publishApiProgressRatio(value, max)
    }

    //////////////////////////////////////////////////////////////////////
    // ユーティリティ

    // リクエストをokHttpに渡してレスポンスを取得する
//    internal inline fun sendRequest(
//        result: TootApiResult,
//        progressPath: String? = null,
//        tmpOkhttpClient: OkHttpClient? = null,
//        block: () -> Request
//    ): Boolean {
//        return try {
//            result.response = null
//            result.bodyString = null
//            result.data = null
//
//            val request = block()
//
//            result.requestInfo = "${request.method} ${progressPath ?: request.url.encodedPath}"
//
//            callback.publishApiProgress(
//                context.getString(
//                    R.string.request_api, request.method, progressPath ?: request.url.encodedPath
//                )
//            )
//
//            val response = httpClient.getResponse(request, tmpOkhttpClient = tmpOkhttpClient)
//            result.response = response
//
//            null == result.error
//
//        } catch (ex: Throwable) {
//            result.setError(
//                "${result.caption}: ${
//                    ex.withCaption(
//                        context.resources,
//                        R.string.network_error
//                    )
//                }"
//            )
//            false
//        }
//    }

    // リクエストをokHttpに渡してレスポンスを取得する
    suspend inline fun sendRequest(
        result: TootApiResult,
        progressPath: String? = null,
        overrideClient: OkHttpClient? = null,
        block: () -> Request,
    ): Boolean {

        result.response = null
        result.bodyString = null
        result.data = null
        val request = block()

        return try {
            result.requestInfo = "${request.method} ${progressPath ?: request.url.encodedPath}"

            callback.publishApiProgress(
                context.getString(
                    R.string.request_api, request.method, progressPath ?: request.url.encodedPath
                )
            )

            val response = httpClient.getResponse(request, overrideClient = overrideClient)
            result.response = response

            null == result.error
        } catch (ex: Throwable) {
            result.setError(
                "${result.caption}: ${
                    ex.withCaption(
                        context.resources,
                        R.string.network_error
                    )
                }"
            )
            false
        }
    }

    // レスポンスがエラーかボディがカラならエラー状態を設定する
    // 例外を出すかも
    internal suspend fun readBodyString(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
    ): String? {
        val response = result.response ?: return null
        try {
            if (isApiCancelled()) return null

            val request = response.request
            publishApiProgress(
                context.getString(
                    R.string.reading_api,
                    request.method,
                    progressPath ?: result.caption
                )
            )

            val bodyString = response.body.string()
            if (isApiCancelled()) return null

            // Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
            if (bodyString.isEmpty() && response.code in 200 until 300) {
                result.bodyString = ""
                return ""
            }

            if (!response.isSuccessful || bodyString.isEmpty()) {
                result.parseErrorResponse(
                    bodyString.notEmpty() ?: NO_INFORMATION,
                    jsonErrorParser
                )
            }

            return if (result.error != null) {
                null
            } else {
                publishApiProgress(context.getString(R.string.parsing_response))
                result.bodyString = bodyString
                bodyString
            }
        } finally {
            response.body.closeQuietly()
        }
    }

    // レスポンスがエラーかボディがカラならエラー状態を設定する
    // 例外を出すかも
    private suspend fun readBodyBytes(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
    ): ByteArray? {

        if (isApiCancelled()) return null

        val response = result.response!!

        val request = response.request
        publishApiProgress(
            context.getString(
                R.string.reading_api,
                request.method,
                progressPath ?: result.caption
            )
        )

        val bodyBytes = response.body.bytes()
        if (isApiCancelled()) return null

        if (!response.isSuccessful || bodyBytes.isEmpty()) {
            result.parseErrorResponse(
                bodyBytes.notEmpty()?.decodeUTF8() ?: NO_INFORMATION,
                jsonErrorParser
            )
        }

        return if (result.error != null) {
            null
        } else {
            result.bodyString = "(binary data)"
            result.data = bodyBytes
            bodyBytes
        }
    }

    private suspend fun parseBytes(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
    ): TootApiResult? {
        try {
            readBodyBytes(result, progressPath, jsonErrorParser)
                ?: return if (isApiCancelled()) null else result
        } catch (ex: Throwable) {
            log.e(ex, "parseBytes failed.")
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result
    }

    internal suspend fun parseString(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
    ): TootApiResult? {
        try {
            val bodyString = readBodyString(result, progressPath, jsonErrorParser)
                ?: return if (isApiCancelled()) null else result

            result.data = bodyString
        } catch (ex: Throwable) {
            log.e(ex, "parseString failed.")
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result
    }

    // レスポンスからJSONデータを読む
    // 引数に指定したresultそのものか、キャンセルされたらnull を返す
    internal suspend fun parseJson(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER,
    ): TootApiResult? {
        try {
            var bodyString = readBodyString(result, progressPath, jsonErrorParser)

            when {
                bodyString == null ->
                    return if (isApiCancelled()) null else result

                // 204 no content は 空オブジェクトと解釈する
                bodyString.isEmpty() -> {
                    result.data = JsonObject()
                }

                reStartJsonArray.find(bodyString) != null -> {
                    result.data = bodyString.decodeJsonArray()
                }

                reStartJsonObject.find(bodyString) != null -> {
                    val json = bodyString.decodeJsonObject()
                    val errorMessage = jsonErrorParser(json)
                    if (errorMessage != null) {
                        result.error = errorMessage
                    } else {
                        result.data = json
                    }
                }

                else -> {
                    val response = result.response!! // nullにならないはず

                    // HTMLならタグを除去する
                    val ct = response.body.contentType()
                    if (ct?.subtype == "html") {
                        val decoded = DecodeOptions().decodeHTML(bodyString).toString()
                            .replace("""[\s　]+""".toRegex(), " ")
                        bodyString = decoded
                    }

                    val sb = StringBuilder()
                        .append(context.getString(R.string.response_not_json))
                        .append(' ')
                        .append(bodyString)

                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append("(HTTP ").append(response.code.toString())

                    val message = response.message
                    if (message.isNotEmpty()) sb.append(' ').append(message)

                    sb.append(")")

                    val url = response.request.url.toString()
                    if (url.isNotEmpty()) sb.append(' ').append(url)

                    result.error = sb.toString()
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "parseJson failed.")
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result
    }

    //////////////////////////////////////////////////////////////////////

    //    fun request(
//        path: String,
//        request_builder: Request.Builder = Request.Builder()
//    ): TootApiResult? {
//        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
//        if (result.error != null) return result
//
//        val account = this.account // may null
//
//        try {
//            if (!sendRequest(result) {
//
//                    log.d("request: $path")
//
//                    request_builder.url("https://${apiHost?.ascii}$path")
//
//                    val access_token = account?.getAccessToken()
//                    if (access_token?.isNotEmpty() == true) {
//                        request_builder.header("Authorization", "Bearer $access_token")
//                    }
//
//                    request_builder.build()
//
//                }) return result
//
//            return parseJson(result)
//        } finally {
//            val error = result.error
//            if (error != null) log.d("error: $error")
//        }
//    }
//

    /**
     * requestと同じだがキャンセルやエラー発生時に例外を投げる
     */
    suspend fun requestOrThrow(
        path: String,
        requestBuilder: Request.Builder = Request.Builder(),
        forceAccessToken: String? = null,
    ): TootApiResult {
        val result = request(
            path = path,
            requestBuilder = requestBuilder,
            forceAccessToken = forceAccessToken,
        )
        when {
            result == null -> throw CancellationException()
            !result.error.isNullOrBlank() -> errorApiResult(result)
            else -> return result
        }
    }

    suspend fun request(
        path: String,
        requestBuilder: Request.Builder = Request.Builder(),
        forceAccessToken: String? = null,
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost)
        if (result.error != null) return result

        val account = this.account // may null

        if (account?.isMisskey == true && !PrefB.bpEnableDeprecatedSomething.value) {
            return result.setError(context.getString(R.string.misskey_support_end))
        }

        try {
            if (!sendRequest(result) {
                    val url = "https://${apiHost?.ascii}$path"

                    requestBuilder.url(url)

                    (forceAccessToken ?: account?.bearerAccessToken)?.notEmpty()?.let {
                        requestBuilder.header("Authorization", "Bearer $it")
                    }

                    requestBuilder.build()
                        .also { log.d("request: ${it.method} $url") }
                }
            ) return result

            return parseJson(result)
        } finally {
            val error = result.error
            if (error != null) log.d("error: $error")
        }
    }

    /**
     * クライアントを登録してブラウザで開くURLを生成する
     * 成功したら TootApiResult.data にURL文字列を格納すること
     */
    suspend fun authStep1(
        forceUpdateClient: Boolean = false,
    ): Uri {
        val (ti, ri) = TootInstance.get(this)
        // 情報が取れなくても続ける
        log.i("authentication1: instance info version=${ti?.version} misskeyVersion=${ti?.misskeyVersionMajor} responseCode=${ri?.response?.code}")
        return when (val auth = AuthBase.findAuthForAuthStep1(this, ti, ri)) {
            null -> error("can't get server information. ${ri?.error}")
            else -> auth.authStep1(ti, forceUpdateClient)
        }
    }

    suspend fun verifyAccount(
        accessToken: String,
        outTokenInfo: JsonObject?,
        misskeyVersion: Int = 0,
    ) = AuthBase.findAuthForVerifyAccount(this, misskeyVersion)
        .verifyAccount(accessToken, outTokenInfo, misskeyVersion)

    ////////////////////////////////////////////////////////////////////////
    // JSONデータ以外を扱うリクエスト

    suspend fun http(req: Request): TootApiResult {
        val result = TootApiResult.makeWithCaption(req.url.host)
        if (result.error != null) return result
        sendRequest(result, progressPath = null) { req }
        return result
    }

    //	fun requestJson(req : Request) : TootApiResult? {
    //		val result = TootApiResult.makeWithCaption(req.url().host())
    //		if(result.error != null) return result
    //		if(sendRequest(result, progressPath = null) { req }) {
    //			decodeJsonValue(result)
    //		}
    //		return result
    //	}

    // 疑似アカウントでステータスURLからステータスIDを取得するためにHTMLを取得する
    suspend fun getHttp(url: String): TootApiResult? {
        val result = http(Request.Builder().url(url).build())
        return if (result.error != null) result else parseString(result)
    }

    suspend fun getHttpBytes(url: String): Pair<TootApiResult?, ByteArray?> {
        val result = TootApiResult.makeWithCaption(url)
        if (result.error != null) return Pair(result, null)

        if (!sendRequest(result, progressPath = url) {
                Request.Builder().url(url).build()
            }) {
            return Pair(result, null)
        }
        val r2 = parseBytes(result)
        return Pair(r2, r2?.data as? ByteArray)
    }

    suspend fun webSocket(
        urlArg: String,
        wsListener: WebSocketListener,
    ): Pair<TootApiResult?, WebSocket?> {
        var ws: WebSocket? = null
        val result = TootApiResult.makeWithCaption(apiHost)
        if (result.error != null) return Pair(result, null)
        val account = this.account ?: return Pair(TootApiResult("account is null"), null)
        try {
            val requestBuilder = Request.Builder()
            var url = urlArg
            if (account.isMisskey) {
                val accessToken = account.misskeyApiToken
                if (accessToken?.isNotEmpty() == true) {
                    val delm = if (-1 != url.indexOf('?')) '&' else '?'
                    url = "$url${delm}i=${accessToken.encodePercent()}"
                }
            } else {
                account.bearerAccessToken.notEmpty()?.let {
                    val delm = if (url.contains('?')) '&' else '?'
                    url = "$url${delm}access_token=${it.encodePercent()}"
                }
            }

            val request = requestBuilder.url(url).build()
            publishApiProgress(context.getString(R.string.request_api, request.method, urlArg))
            ws = httpClient.getWebSocket(request, wsListener)
            if (isApiCancelled()) {
                ws.cancel()
                return Pair(null, null)
            }
        } catch (ex: Throwable) {
            log.e(ex, "webSocket failed.")
            result.error =
                "${result.caption}: ${ex.withCaption(context.resources, R.string.network_error)}"
        }
        return Pair(result, ws)
    }

    fun copy() = TootApiClient(
        context,
        httpClient,
        callback
    ).also { dst ->
        dst.account = account
        dst.apiHost = apiHost
    }
}

suspend fun TootApiClient.requestMastodonSearch(
    parser: TootParser,
    // 検索文字列
    q: String,
    // リモートサーバの情報を解決するなら真
    resolve: Boolean,
    // ギャップ読み込み時の追加パラメータ
    extra: String = "",
): Pair<TootApiResult?, TootResults?> {

    if (q.all { CharacterGroup.isWhitespace(it.code) }) {
        return Pair(null, null)
    }

    val query = "q=${q.encodePercent()}&resolve=$resolve${
        if (extra.isEmpty()) "" else "&$extra"
    }"

    var searchApiVersion = 2
    var apiResult = request("/api/v2/search?$query")
        ?: return Pair(null, null)

    if ((apiResult.response?.code ?: 0) in 400 until 500) {
        searchApiVersion = 1
        apiResult = request("/api/v1/search?$query")
            ?: return Pair(null, null)
    }

    val searchResult = parser.results(apiResult.jsonObject)
    searchResult?.searchApiVersion = searchApiVersion

    return Pair(apiResult, searchResult)
}

// result.data に TootAccountRefを格納して返す。もしくはエラーかキャンセル
suspend fun TootApiClient.syncAccountByUrl(
    accessInfo: SavedAccount,
    whoUrl: String,
): Pair<TootApiResult?, TootAccountRef?> {

    // misskey由来のアカウントURLは https://host/@user@instance などがある
    val m = TootAccount.reAccountUrl.matcher(whoUrl)
    if (m.find()) {
        // val host = m.group(1)
        val user = m.groupEx(2)!!.decodePercent()
        val instance = m.groupEx(3)?.decodePercent()
        if (instance?.isNotEmpty() == true) {
            return this.syncAccountByUrl(accessInfo, "https://$instance/@$user")
        }
    }

    val parser = TootParser(context, accessInfo)

    return if (accessInfo.isMisskey) {

        val acct = TootAccount.getAcctFromUrl(whoUrl)
            ?: return Pair(
                TootApiResult(context.getString(R.string.user_id_conversion_failed)),
                null
            )

        var ar: TootAccountRef? = null
        val result = request(
            "/api/users/show",
            accessInfo.putMisskeyApiToken().apply {
                put("username", acct.username)
                acct.host?.let { put("host", it.ascii) }
            }.toPostRequestBuilder()
        )
            ?.apply {
                ar = tootAccountRefOrNull(parser, parser.account(jsonObject))
                if (ar == null && error == null) {
                    setError(context.getString(R.string.user_id_conversion_failed))
                }
            }
        Pair(result, ar)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            q = whoUrl,
            resolve = true,
        )
        val ar = searchResult?.accounts?.firstOrNull()
        if (apiResult != null && apiResult.error == null && ar == null) {
            apiResult.setError(context.getString(R.string.user_id_conversion_failed))
        }
        Pair(apiResult, ar)
    }
}

suspend fun TootApiClient.syncAccountByAcct(
    accessInfo: SavedAccount,
    acctArg: String,
): Pair<TootApiResult?, TootAccountRef?> = syncAccountByAcct(accessInfo, Acct.parse(acctArg))

suspend fun TootApiClient.syncAccountByAcct(
    accessInfo: SavedAccount,
    acct: Acct,
): Pair<TootApiResult?, TootAccountRef?> {

    val parser = TootParser(context, accessInfo)
    return if (accessInfo.isMisskey) {
        var ar: TootAccountRef? = null
        val result = request(
            "/api/users/show",
            accessInfo.putMisskeyApiToken()
                .apply {
                    if (acct.isValid) put("username", acct.username)
                    if (acct.host != null) put("host", acct.host.ascii)
                }
                .toPostRequestBuilder()
        )
            ?.apply {
                ar = tootAccountRefOrNull(parser, parser.account(jsonObject))
                if (ar == null && error == null) {
                    setError(context.getString(R.string.user_id_conversion_failed))
                }
            }
        Pair(result, ar)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            q = acct.ascii,
            resolve = true,
        )
        val ar = searchResult?.accounts?.firstOrNull()
        if (apiResult != null && apiResult.error == null && ar == null) {
            apiResult.setError(context.getString(R.string.user_id_conversion_failed))
        }
        Pair(apiResult, ar)
    }
}

suspend fun TootApiClient.syncStatus(
    accessInfo: SavedAccount,
    urlArg: String,
): Pair<TootApiResult?, TootStatus?> {

    var url = urlArg

    // misskey の投稿URLは外部タンスの投稿を複製したものの可能性がある
    // これを投稿元タンスのURLに変換しないと、投稿の同期には使えない
    val m = TootStatus.reStatusPageMisskey.matcher(urlArg)
    if (m.find()) {
        val host = Host.parse(m.groupEx(1)!!)
        val noteId = m.groupEx(2)

        TootApiClient(context, callback = callback)
            .apply { apiHost = host }
            .request(
                "/api/notes/show",
                JsonObject().apply {
                    put("noteId", noteId)
                }
                    .toPostRequestBuilder()
            )
            ?.also { result ->
                TootParser(
                    context,
                    linkHelper = LinkHelper.create(host, misskeyVersion = 10),
                )
                    .status(result.jsonObject)
                    ?.apply {
                        if (accessInfo.matchHost(host)) {
                            return Pair(result, this)
                        }
                        uri.letNotEmpty { url = it }
                    }
            }
            ?: return Pair(null, null) // cancelled.
    }

    // 使いたいタンス上の投稿IDを取得する
    val parser = TootParser(context, accessInfo)
    return if (accessInfo.isMisskey) {
        var targetStatus: TootStatus? = null
        val result = request(
            "/api/ap/show",
            accessInfo.putMisskeyApiToken().apply {
                put("uri", url)
            }
                .toPostRequestBuilder()
        )
            ?.apply {
                targetStatus = parser.parseMisskeyApShow(jsonObject) as? TootStatus
                if (targetStatus == null && error == null) {
                    setError(context.getString(R.string.cant_sync_toot))
                }
            }
        Pair(result, targetStatus)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            q = url,
            resolve = true,
        )
        val targetStatus = searchResult?.statuses?.firstOrNull()
        if (apiResult != null && apiResult.error == null && targetStatus == null) {
            apiResult.setError(context.getString(R.string.cant_sync_toot))
        }
        Pair(apiResult, targetStatus)
    }
}

suspend fun TootApiClient.syncStatus(
    accessInfo: SavedAccount,
    statusRemote: TootStatus,
): Pair<TootApiResult?, TootStatus?> {

    // URL->URIの順に試す

    val uriList = ArrayList<String>(2)

    statusRemote.url.letNotEmpty {
        when {
            it.contains("/notes/") -> {
                // Misskeyタンスから読んだマストドンの投稿はurlがmisskeyタンス上のものになる
                // ActivityPub object id としては不適切なので使わない
            }

            else -> uriList.add(it)
        }
    }

    statusRemote.uri.letNotEmpty {
        // uri の方は↑の問題はない
        uriList.add(it)
    }

    if (accessInfo.isMisskey && uriList.firstOrNull()?.contains("@") == true) {
        // https://github.com/syuilo/misskey/pull/2832
        // @user を含むuri はMisskeyだと少し効率が悪いそうなので順序を入れ替える
        uriList.reverse()
    }

    for (uri in uriList) {
        val pair = syncStatus(accessInfo, uri)
        if (pair.second != null || pair.first == null) {
            return pair
        }
    }

    return Pair(TootApiResult("can't resolve status URL/URI."), null)
}
