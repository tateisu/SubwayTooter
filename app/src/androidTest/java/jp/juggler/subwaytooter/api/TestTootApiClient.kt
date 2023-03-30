@file:Suppress("MemberVisibilityCanBePrivate")

package jp.juggler.subwaytooter.api

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.auth.AuthMastodon
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoClientInfo
import jp.juggler.subwaytooter.testutil.TestDispatcherRule
import jp.juggler.subwaytooter.testutil.assertThrowsSuspend
import jp.juggler.subwaytooter.util.SimpleHttpClient
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.MEDIA_TYPE_JSON
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("MemberVisibilityCanPrivate")
@RunWith(AndroidJUnit4::class)
class TestTootApiClient {

    // テスト毎に書くと複数テストで衝突するので、MainDispatcherRuleに任せる
    // プロパティは記述順に初期化されることに注意
    @get:Rule
    val mainDispatcherRule = TestDispatcherRule()

    companion object {
        private val log = LogCategory("TestTootApiClient")
        private val mediaTypeTextPlain = "text/plain".toMediaType()
        private val mediaTypeHtml = "text/html".toMediaType()
    }

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext!!

    class SimpleHttpClientMock(
        private val responseGenerator: (request: Request) -> Response,
        val webSocketGenerator: (request: Request, ws_listener: WebSocketListener) -> WebSocket,
    ) : SimpleHttpClient {

        override var onCallCreated: (Call) -> Unit = {}

        // override var currentCallCallback : CurrentCallCallback? = null

        override suspend fun getResponse(
            request: Request,
            overrideClient: OkHttpClient?,
        ): Response {
            return responseGenerator(request)
        }

        override fun getWebSocket(
            request: Request,
            webSocketListener: WebSocketListener,
        ): WebSocket {
            return webSocketGenerator(request, webSocketListener)
        }
    }

    private fun <T> assertOneOf(actual: T?, vararg expect: T?) {
        if (!expect.any { it == actual }) {
            fail("actual=$actual, expected = one of [${expect.joinToString(", ")}]")
        }
    }

    private fun assertParsingResponse(callback: ProgressRecordTootApiCallback) {
        assertOneOf(
            callback.progressString,
            "Parsing response…",
            "応答の解析中…"
        )
    }

    private fun assertReading(
        callback: ProgressRecordTootApiCallback,
        @Suppress("SameParameterValue")
        path: String,
    ) {
        assertOneOf(
            callback.progressString,
            "Reading: GET $path",
            "読込中: GET $path",
        )
    }

    private fun requestBodyString(request: Request?): String? {
        try {
            val copyBody = request?.newBuilder()?.build()?.body ?: return null
            val buffer = Buffer()
            copyBody.writeTo(buffer)
            return buffer.readUtf8()
        } catch (ex: Throwable) {
            log.e(ex, "requestBodyString failed.")
            return null
        }
    }

    private fun createHttpClientMock(): SimpleHttpClient {
        return SimpleHttpClientMock(
            responseGenerator = { request ->

                val bodyString = requestBodyString(request)

                when (request.url.encodedPath) {

                    // クライアント登録
                    "/api/v1/apps" -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("status-message")
                        .body(
                            """{"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"DUMMY_ID","client_secret":"DUMMY_SECRET"}"""
                                .toResponseBody(MEDIA_TYPE_JSON)
                        )
                        .build()

                    // client credentialの検証
                    "/api/v1/apps/verify_credentials" -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("status-message")
                        .body(
                            """{"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"DUMMY_ID","client_secret":"DUMMY_SECRET"}"""
                                .toResponseBody(MEDIA_TYPE_JSON)
                        )
                        .build()

                    "/oauth/token" -> when {
                        // client credential の作成
                        bodyString?.contains("grant_type=client_credentials") == true -> {
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("status-message")
                                .body(
                                    """{"access_token":"DUMMY_CLIENT_CREDENTIAL"}""".toResponseBody(
                                        MEDIA_TYPE_JSON
                                    )
                                )
                                .build()
                        }
                        // アクセストークンの作成
                        bodyString?.contains("grant_type=authorization_code") == true -> {
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("status-message")
                                .body(
                                    """{"access_token":"DUMMY_ACCESS_TOKEN"}""".toResponseBody(
                                        MEDIA_TYPE_JSON
                                    )
                                )
                                .build()
                        }

                        else -> {
                            createResponseErrorCode()
                        }
                    }
                    // ログインユーザの情報
                    "/api/v1/accounts/verify_credentials" -> {
                        val instance = request.url.host
                        val account1Json = JsonObject()
                        account1Json.apply {
                            put("username", "user1")
                            put("acct", "user1")
                            put("id", 1L)
                            put("url", "http://$instance/@user1")
                        }

                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("status-message")
                            .body(account1Json.toString().toResponseBody(MEDIA_TYPE_JSON))
                            .build()
                    }
                    // インスタンス情報
                    "/api/v1/instance" -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("status-message")
                        .body(JsonObject().apply {
                            put("uri", "http://${request.url.host}/")
                            put("title", "dummy instance")
                            put("description", "dummy description")
                            put("version", "0.0.1")
                        }.toString().toResponseBody(MEDIA_TYPE_JSON))
                        .build()

                    // 公開タイムライン
                    "/api/v1/timelines/public" -> {
                        val instance = request.url.host

                        val username = "user1"

                        val account1Json = JsonObject()
                        account1Json.apply {
                            put("username", username)
                            put("acct", username)
                            put("id", 1L)
                            put("url", "http://$instance/@$username")
                        }

                        val array = buildJsonArray {
                            for (i in 0 until 10) {
                                add(buildJsonObject {
                                    put("account", account1Json)
                                    put("id", i.toLong())
                                    put("uri", "https://$instance/@$username/$i")
                                    put("url", "https://$instance/@$username/$i")
                                })
                            }
                        }

                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("status-message")
                            .body(array.toString().toResponseBody(MEDIA_TYPE_JSON))
                            .build()
                    }

                    "/api/meta" -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("not found")
                        .body("""{"error":"404 not found"}""".toResponseBody(MEDIA_TYPE_JSON))
                        .build()

                    else -> Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("status-message")
                        .body(request.url.toString().toResponseBody(mediaTypeTextPlain))
                        .build()
                }
            },

            webSocketGenerator = { request: Request, _: WebSocketListener ->
                object : WebSocket {
                    override fun queueSize(): Long = 4096L
                    override fun send(text: String): Boolean = true
                    override fun send(bytes: ByteString): Boolean = true
                    override fun close(code: Int, reason: String?): Boolean = true
                    override fun cancel() = Unit
                    override fun request(): Request = request
                }
            }
        )
    }

    private fun createHttpClientNotImplemented(): SimpleHttpClient {
        return SimpleHttpClientMock(
            responseGenerator = { throw NotImplementedError() },
            webSocketGenerator = { _, _ -> throw NotImplementedError() }
        )
    }

    class ProgressRecordTootApiCallback : TootApiCallback {

        var cancelled: Boolean = false

        var progressString: String? = null

        override suspend fun isApiCancelled(): Boolean {
            return cancelled
        }

        override suspend fun publishApiProgress(s: String) {
            progressString = s
        }
    }

    private val requestSimple: Request = Request.Builder().url("https://dummy-url.net/").build()

    private val strJsonOk = """{"a":"A!"}"""
    private val strJsonError = """{"error":"Error!"}"""
    private fun createResponseOk() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strJsonOk.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseOkButJsonError() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strJsonError.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseErrorCode() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(500)
        .message("status-message")
        .body(strJsonError.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseEmptyBody(code: Int = 200) = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status-message")
        .body("".toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseWithoutBody() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        // without body
        .build()

    private fun createResponseExceptionBody() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(
            object : ResponseBody() {
                override fun contentLength() = 10L
                override fun contentType(): MediaType = MEDIA_TYPE_JSON
                override fun source(): BufferedSource = error("ExceptionBody")
            }
        )
        .build()

    private val strJsonArray1 = """["A!"]"""
    private val strJsonArray2 = """   [  "A!"  ]  """
    private val strJsonObject2 = """  {  "a"  :  "A!"  }  """
    private val strPlainText = "Hello!"

    private fun createResponseJsonArray1() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strJsonArray1.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseJsonArray2() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strJsonArray2.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponseJsonObject2() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strJsonObject2.toResponseBody(MEDIA_TYPE_JSON))
        .build()

    private fun createResponsePlainText() = Response.Builder()
        .request(requestSimple)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("status-message")
        .body(strPlainText.toResponseBody(mediaTypeTextPlain))
        .build()

    @Test
    fun testSimplifyErrorHtml() {
        var request: Request
        var response: Response
        var message: String

        // json error
        response = createResponseErrorCode()
        message = TootApiClient.simplifyErrorHtml(response)
        assertEquals("Error!", message)

        // HTML error

        response = Response.Builder()
            .request(requestSimple)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("This is test")
            .body("""<html><body>Error!</body></html>""".toResponseBody(mediaTypeHtml))
            .build()

        message = TootApiClient.simplifyErrorHtml(response)
        assertEquals("Error!", message)

        // other error
        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("This is test")
            .body("Error!".toResponseBody("text/plain".toMediaType()))
            .build()

        message = TootApiClient.simplifyErrorHtml(response)
        assertEquals("Error!", message)

        // empty body
        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("This is test")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()

        message = TootApiClient.simplifyErrorHtml(response = response, caption = "caption")
        assertEquals("", message)
    }

    @Test
    fun testFormatResponse() {

        var request: Request
        var response: Response
        var bodyString: String?
        var message: String

        // without response body
        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("This is test")
            .build()

        message = TootApiClient.formatResponse(response, "caption")

        assertEquals("(HTTP 500 This is test) caption", message)

        // json error
        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("status-message")
            .body("""{"error":"Error!"}""".toResponseBody(MEDIA_TYPE_JSON))
            .build()

        message = TootApiClient.formatResponse(response, "caption")
        assertEquals("Error! (HTTP 500 status-message) caption", message)

        // json error (after reading body)

        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("status-message")
            .body("""{"error":"Error!"}""".toResponseBody(MEDIA_TYPE_JSON))
            .build()

        bodyString = response.body?.string()

        message = TootApiClient.formatResponse(response, "caption", bodyString)
        assertEquals("(HTTP 500 status-message) caption", message)

        // without status message
        request = Request.Builder()
            .url("https://dummy-url.net/")
            .build()

        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("")
            .body("""{"error":"Error!"}""".toResponseBody(MEDIA_TYPE_JSON))
            .build()

        bodyString = response.body?.string()

        message = TootApiClient.formatResponse(
            response = response,
            caption = "caption",
            bodyString = bodyString
        )
        assertEquals("(HTTP 500) caption", message)
    }

    @Test
    fun testIsApiCancelled() {
        runTest {
            var flag = 0
            var progressString: String? = null
            var progressValue: Int? = null
            var progressMax: Int? = null

            val client = TootApiClient(
                appContext,
                httpClient = createHttpClientNotImplemented(),
                callback = object : TootApiCallback {
                    override suspend fun isApiCancelled(): Boolean {
                        ++flag
                        return true
                    }

                    override suspend fun publishApiProgress(s: String) {
                        ++flag
                        progressString = s
                    }

                    override suspend fun publishApiProgressRatio(value: Int, max: Int) {
                        ++flag
                        progressValue = value
                        progressMax = max
                    }
                }
            )
            val isApiCancelled = client.isApiCancelled()
            client.publishApiProgress("testing")
            client.publishApiProgressRatio(50, 100)
            assertEquals(3, flag)
            assertEquals(true, isApiCancelled)
            assertEquals("testing", progressString)
            assertEquals(50, progressValue)
            assertEquals(100, progressMax)
        }
    }

    @Test
    fun testSendRequest() {
        runTest {

            val callback = ProgressRecordTootApiCallback()

            // 正常ケースではResponseが返ってくること
            run {
                val client = TootApiClient(
                    appContext,
                    httpClient = createHttpClientMock(),
                    callback = callback
                )
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)

                callback.progressString = null
                val bOk = client.sendRequest(result) { requestSimple }
                assertEquals(true, bOk)
                assertOneOf(
                    callback.progressString,
                    "Acquiring: GET /",
                    "取得中: GET /",
                )
                assertEquals(null, result.error)
                assertNotNull(result.response)
            }

            // httpClient.getResponseが例外を出す場合に対応できること
            run {
                val client = TootApiClient(
                    appContext,
                    httpClient = createHttpClientNotImplemented(),
                    callback = callback
                )
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)

                callback.progressString = null
                val bOk = client.sendRequest(result) { requestSimple }
                assertEquals(false, bOk)
                assertOneOf(
                    callback.progressString,
                    "Acquiring: GET /",
                    "取得中: GET /",
                )
                assertOneOf(
                    result.error,
                    "instance: Network error.: NotImplementedError An operation is not implemented.",
                    "instance: 通信エラー。: NotImplementedError An operation is not implemented.",
                )
                assertNull(result.response)
            }

            // progressPath を指定したらpublishApiProgressに渡されること
            run {
                val client = TootApiClient(
                    appContext,
                    httpClient = createHttpClientMock(),
                    callback = callback
                )
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)

                callback.progressString = null
                val bOk = client.sendRequest(result, progressPath = "XXX") { requestSimple }
                assertEquals(true, bOk)
                assertOneOf(
                    callback.progressString,
                    "Acquiring: GET XXX",
                    "取得中: GET XXX",
                )
                assertEquals(null, result.error)
                assertNotNull(result.response)
            }
        }
    }

    @Test
    fun testReadBodyString() {
        runTest {
            val callback = ProgressRecordTootApiCallback()
            val client = TootApiClient(
                appContext,
                httpClient = createHttpClientMock(),
                callback = callback
            )

            // キャンセルされてたらnullを返すこと
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()
                callback.progressString = null
                callback.cancelled = true
                val bodyString = client.readBodyString(result)
                callback.cancelled = false
                assertNull(bodyString)
                assertNull(result.bodyString)
                assertNull(result.data)
                assertNull(result.error)
                assertNull(callback.progressString)
            }

            // 正常ケースなら progressを更新してbodyStringを返す
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()

                callback.progressString = null
                val bodyString = client.readBodyString(result)
                assertEquals(strJsonOk, bodyString)
                assertEquals(strJsonOk, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
                assertNull(result.data)
            }

            // レスポンスコードがエラーなら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseErrorCode()

                callback.progressString = null
                val bodyString = client.readBodyString(result)
                assertEquals(null, bodyString)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("Error! (HTTP 500 status-message) instance", result.error)
                assertNull(result.data)
            }

            // ボディが空なら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseEmptyBody()
                callback.progressString = null
                val bodyString = client.readBodyString(result)
                assertEquals("", bodyString)
                assertEquals("", result.bodyString)
                assertReading(callback, "instance")
                assertEquals(null, result.error)
                assertNull(result.data)
            }
            // ボディがnullなら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseWithoutBody()

                callback.progressString = null
                val bodyString = client.readBodyString(result)
                assertEquals("", bodyString)
                assertEquals("", result.bodyString)
                assertReading(callback, "instance")
                assertEquals(null, result.error)
                assertNull(result.data)
            }

            // string() が例外
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseExceptionBody()

                var catched: Throwable? = null
                val bodyString = try {
                    client.readBodyString(result)
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    catched = ex
                    null
                }
                assertEquals(null, bodyString)
                assertNotNull(catched)
            }
        }
    }

    @Test
    fun testParseString() {
        runTest {

            val callback = ProgressRecordTootApiCallback()
            val client = TootApiClient(
                appContext,
                httpClient = createHttpClientMock(),
                callback = callback
            )

            // キャンセルされてたらnullを返すこと
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()
                callback.progressString = null
                callback.cancelled = true
                val r2 = client.parseString(result)
                callback.cancelled = false
                assertNull(r2)
                assertNull(result.bodyString)
                assertNull(result.data)
                assertNull(result.error)
                assertNull(callback.progressString)
            }

            // 正常ケースなら progressを更新してbodyStringを返す
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()

                callback.progressString = null
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals(strJsonOk, result.string)
                assertEquals(strJsonOk, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }
            // 正常レスポンスならJSONにエラーがあってもreadStringは関知しない
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOkButJsonError()

                callback.progressString = null
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals(strJsonError, result.string)
                assertEquals(strJsonError, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }

            // レスポンスコードがエラーなら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseErrorCode()
                callback.progressString = null
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals(null, result.string)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("Error! (HTTP 500 status-message) instance", result.error)
            }

            // ボディが空で応答コードがエラーなら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseEmptyBody(code = 404)
                callback.progressString = null
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals(null, result.string)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("(no information) (HTTP 404 status-message) instance", result.error)
                assertNull(result.data)
            }
            // ボディがnullでも応答コードが成功ならエラーではない
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseWithoutBody()

                callback.progressString = null
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals("", result.string)
                assertEquals("", result.bodyString)
                assertReading(callback, "instance")
                assertEquals(null, result.error)
                assertEquals("", result.data)
            }

            // string() が例外
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseExceptionBody()
                val r2 = client.parseString(result)
                assertNotNull(r2)
                assertEquals(null, result.string)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
                assertNull(result.data)
            }
        }
    }

    @Test
    fun testParseJson() {
        runTest {
            val callback = ProgressRecordTootApiCallback()
            val client = TootApiClient(
                appContext,
                httpClient = createHttpClientMock(),
                callback = callback
            )

            // キャンセルされてたらnullを返すこと
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()
                callback.progressString = null
                callback.cancelled = true
                val r2 = client.parseJson(result)
                callback.cancelled = false
                assertNull(r2)
                assertNull(result.bodyString)
                assertNull(result.data)
                assertNull(result.error)
                assertNull(callback.progressString)
            }

            // 正常ケースなら progressを更新してbodyStringを返す
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOk()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals("A!", result.jsonObject?.optString("a"))
                assertEquals(strJsonOk, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }
            // 正常ケースでもjsonデータにerror項目があれば
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseOkButJsonError()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(null, result.data)
                assertEquals(strJsonError, result.bodyString)
                assertParsingResponse(callback)
                assertEquals("Error!", result.error)
            }

            // レスポンスコードがエラーなら
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseErrorCode()
                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(null, result.data)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("Error! (HTTP 500 status-message) instance", result.error)
            }

            // ボディが空なら
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseEmptyBody()
                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(0, result.jsonObject?.size)
                assertEquals("", result.bodyString)
                assertReading(callback, "instance")
                assertEquals(null, result.error)
            }

            // ボディがnullなら
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseWithoutBody()
                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(0, result.jsonObject?.size)
                assertEquals("", result.bodyString)
                assertReading(callback, "instance")
                assertEquals(null, result.error)
            }

            // string() が例外
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseExceptionBody()
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(null, result.data)
                assertEquals(null, result.bodyString)
                assertReading(callback, "instance")
                assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
                assertNull(result.data)
            }

            // JSON Arrayを処理する
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseJsonArray1()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals("A!", result.jsonArray?.optString(0))
                assertEquals(strJsonArray1, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }

            // 空白が余計に入ってるJSON Arrayを処理する
            run {

                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseJsonArray2()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals("A!", result.jsonArray?.optString(0))
                assertEquals(strJsonArray2, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }

            // 空白が余計に入ってるJSON Objectを処理する
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponseJsonObject2()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals("A!", result.jsonObject?.optString("a"))
                assertEquals(strJsonObject2, result.bodyString)
                assertParsingResponse(callback)
                assertNull(result.error)
            }
            // JSONじゃない
            run {
                val result = TootApiResult.makeWithCaption("instance")
                assertEquals(null, result.error)
                result.response = createResponsePlainText()

                callback.progressString = null
                val r2 = client.parseJson(result)
                assertNotNull(r2)
                assertEquals(null, result.data)
                assertEquals(strPlainText, result.bodyString)
                assertParsingResponse(callback)
                assertOneOf(
                    result.error,
                    "API response is not JSON. Hello! (HTTP 200 status-message) https://dummy-url.net/",
                    "APIの応答がJSONではありません Hello! (HTTP 200 status-message) https://dummy-url.net/",
                )
            }
        }
    }

    @Test
    fun testRegisterClient() = runTest {
        AuthBase.testClientName = "SubwayTooterUnitTest"
        val client = TootApiClient(
            appContext,
            httpClient = createHttpClientMock(),
            callback = ProgressRecordTootApiCallback()
        )
        val testHost = Host.parse("unit-test")
        client.apiHost = testHost

        val (ti, ri) = TootInstance.get(client)
        ti ?: error("can't get server information. ${ri?.error}")

        val auth = AuthBase.findAuthForAuthStep1(client, ti, ri) as AuthMastodon
        val authUri = auth.authStep1(ti, forceUpdateClient = false)
        println("authUri=$authUri")

        // ブラウザからコールバックで受け取ったcodeを処理する

        val clientInfo = jsonObjectOf(
            // ...
            "client_id" to "abc",
            "client_secret" to "def",
            AuthBase.KEY_CLIENT_SCOPE to "scope",
        )

        daoClientInfo.save(testHost, AuthBase.clientName, clientInfo.toString())

        // コールバックのエラーケース
        arrayOf(
            // handle error message
            Triple("?error=e1", IllegalStateException::class.java, "e1"),
            Triple("?error_description=e1", IllegalStateException::class.java, "e1"),
            Triple("?error=e1&error_description=e2", IllegalStateException::class.java, "e2\ne1"),
            // missing 'code'
            Triple("", IllegalStateException::class.java, "missing code in callback url."),
            Triple("?", IllegalStateException::class.java, "missing code in callback url."),
            Triple("?code=", IllegalStateException::class.java, "missing code in callback url."),
            // missing 'state'
            Triple("?code=a", IllegalStateException::class.java, "missing state in callback url."),
            Triple(
                "?code=a&state=",
                IllegalStateException::class.java,
                "missing state in callback url."
            ),
            // bad db id
            Triple(
                "?code=a&state=db:",
                IllegalStateException::class.java,
                "invalide state.db in callback parameter."
            ),
            Triple(
                "?code=a&state=db:a",
                IllegalStateException::class.java,
                "invalide state.db in callback parameter."
            ),
            Triple(
                "?code=a&state=db:-1",
                IllegalStateException::class.java,
                "invalide state.db in callback parameter."
            ),
            // bad host
            Triple(
                "?code=a&state=host:",
                IllegalStateException::class.java,
                "can't find client info for apiHost=, clientName=SubwayTooterUnitTest"
            ),
            Triple(
                "?code=a&state=host:a",
                IllegalStateException::class.java,
                "can't find client info for apiHost=a, clientName=SubwayTooterUnitTest"
            ),
            Triple(
                "?code=a&state=host:-1",
                IllegalStateException::class.java,
                "can't find client info for apiHost=-1, clientName=SubwayTooterUnitTest"
            ),
            // other params in state ignored
            Triple("?code=a&state=host:${testHost.ascii},other:a", null, "ignored"),
        ).forEach {
            val (suffix, exClass, exMessage) = it
            val callbackUrl = "${AuthMastodon.callbackUrl}$suffix".toUri()
            if (exClass == null) {
                // expect not throw
                auth.authStep2(callbackUrl)
            } else {
                val ex = assertThrowsSuspend("exClass callbackUrl=$callbackUrl", exClass) {
                    auth.authStep2(callbackUrl)
                }
                assertEquals("exMessage callbackUrl=$callbackUrl", exMessage, ex.message)
            }
        }

        // 正常ケース
        val auth2Result =
            auth.authStep2("${AuthMastodon.callbackUrl}?code=a&state=host:${testHost.ascii}".toUri())

        assertEquals(
            "auth2Result.tokenJson",
            """{"SubwayTooterAuthVersion":5,"access_token":"DUMMY_ACCESS_TOKEN"}""",
            auth2Result.tokenJson.toString(0, sort = true)
        )
        assertEquals(
            "auth2Result.accountJson",
            """{"_fromStream":false,"acct":"user1","id":1,"url":"http://unit-test/@user1","username":"user1"}""",
            auth2Result.accountJson.toString(0, sort = true)
        )

        // 認証できたならアクセストークンがある
        val accessToken = auth2Result.tokenJson.string("access_token")
        assertEquals(
            "accessToken",
            "DUMMY_ACCESS_TOKEN",
            accessToken
        )
        accessToken!!

        // アクセストークン手動入力
        val outTokenJson = JsonObject()
        val accountJson = auth.verifyAccount(accessToken, outTokenJson, misskeyVersion = 0)
        assertEquals(
            "outTokenJson",
            """{"SubwayTooterAuthVersion":5,"access_token":"DUMMY_ACCESS_TOKEN"}""",
            outTokenJson.toString(0, sort = true)
        )
        assertEquals(
            "accountJson",
            """{"acct":"user1","id":1,"url":"http://unit-test/@user1","username":"user1"}""",
            accountJson.toString(0, sort = true)
        )
    }

    @Test
    fun testGetInstanceInformation() = runTest {
        val callback = ProgressRecordTootApiCallback()
        val client = TootApiClient(
            appContext,
            httpClient = createHttpClientMock(),
            callback = callback
        )
        val instance = Host.parse("unit-test")
        client.apiHost = instance
        val (instanceInfo, instanceResult) = TootInstance.get(client)
        assertNull("no error", instanceResult?.error)
        assertNotNull("instance info", instanceInfo)
        val json = instanceResult?.jsonObject
        if (json != null) println(json.toString())
    }

    @Test
    fun testGetHttp() = runTest {
        val callback = ProgressRecordTootApiCallback()
        val client = TootApiClient(
            appContext,
            httpClient = createHttpClientMock(),
            callback = callback
        )
        val result = client.getHttp("http://juggler.jp/")
        val content = result?.string
        assertNotNull(content)
        println(content.toString())
    }

    @Test
    fun testRequest() = runTest {
        val tokenInfo = JsonObject()
        tokenInfo["access_token"] = "DUMMY_ACCESS_TOKEN"

        val accessInfo = SavedAccount(
            db_id = 1,
            acctArg = "user1@host1",
            apiHostArg = null,
            tokenJson = tokenInfo
        )
        val callback = ProgressRecordTootApiCallback()
        val client = TootApiClient(
            appContext,
            httpClient = createHttpClientMock(),
            callback = callback
        )
        client.account = accessInfo
        val result = client.request("/api/v1/timelines/public")
        println(result?.bodyString)

        val content = result?.jsonArray
        assertNotNull(content)
        println(content?.jsonObject(0).toString())
    }

    @Test
    fun testWebSocket() = runTest {
        val tokenInfo = buildJsonObject {
            put("access_token", "DUMMY_ACCESS_TOKEN")
        }

        val accessInfo = SavedAccount(
            db_id = 1,
            acctArg = "user1@host1",
            apiHostArg = null,
            tokenJson = tokenInfo
        )
        val callback = ProgressRecordTootApiCallback()
        val client = TootApiClient(
            appContext,
            httpClient = createHttpClientMock(),
            callback = callback
        )
        client.account = accessInfo
        val (_, ws) = client.webSocket(
            "wss://${accessInfo.apiHost.ascii}/api/v1/streaming/?stream=public:local",
            object : WebSocketListener() {}
        )
        assertNotNull(ws)
        ws?.cancel()
    }
}
