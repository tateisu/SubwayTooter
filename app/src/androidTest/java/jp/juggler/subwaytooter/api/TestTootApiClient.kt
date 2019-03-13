@file:Suppress("MemberVisibilityCanBePrivate")

package jp.juggler.subwaytooter.api

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.CurrentCallCallback
import jp.juggler.subwaytooter.util.SimpleHttpClient
import jp.juggler.util.*
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("MemberVisibilityCanPrivate")
@RunWith(AndroidJUnit4::class)
class TestTootApiClient {
	
	private val appContext = InstrumentationRegistry.getTargetContext() !!
	
	class SimpleHttpClientMock(
		private val responseGenerator : (request : Request) -> Response,
		val webSocketGenerator : (request : Request, ws_listener : WebSocketListener) -> WebSocket
	) : SimpleHttpClient {
		
		override fun getResponse(
			request : Request,
			tmpOkhttpClient : OkHttpClient?
		) : Response {
			return responseGenerator(request)
		}
		
		override var currentCallCallback : CurrentCallCallback? = null
		
		override fun getWebSocket(
			request : Request,
			webSocketListener : WebSocketListener
		) : WebSocket {
			return webSocketGenerator(request, webSocketListener)
		}
	}
	
	private fun requestBodyString(request : Request?) : String? {
		try {
			val copyBody = request?.newBuilder()?.build()?.body() ?: return null
			val buffer = Buffer()
			copyBody.writeTo(buffer)
			return buffer.readUtf8()
		} catch(ex : Throwable) {
			ex.printStackTrace()
			return null
		}
	}
	
	private fun createHttpClientNormal() : SimpleHttpClient {
		return SimpleHttpClientMock(
			responseGenerator = { request : Request ->
				
				val bodyString = requestBodyString(request)
				
				val path = request.url().encodedPath()
				when(path) {
				
				// クライアント登録
					"/api/v1/apps" ->
						Response.Builder()
							.request(request)
							.protocol(Protocol.HTTP_1_1)
							.code(200)
							.message("status-message")
							.body(
								ResponseBody.create(
									MEDIA_TYPE_JSON,
									"""{"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"DUMMY_ID","client_secret":"DUMMY_SECRET"}"""
								)
							)
							.build()
				
				// client credentialの検証
					"/api/v1/apps/verify_credentials" -> Response.Builder()
						.request(request)
						.protocol(Protocol.HTTP_1_1)
						.code(200)
						.message("status-message")
						.body(
							ResponseBody.create(
								MEDIA_TYPE_JSON,
								"""{"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"DUMMY_ID","client_secret":"DUMMY_SECRET"}"""
							)
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
									ResponseBody.create(
										MEDIA_TYPE_JSON,
										"""{"access_token":"DUMMY_CLIENT_CREDENTIAL"}"""
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
									ResponseBody.create(
										MEDIA_TYPE_JSON,
										"""{"access_token":"DUMMY_ACCESS_TOKEN"}"""
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
						val instance = request.url().host()
						val account1Json = JSONObject()
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
							.body(
								ResponseBody.create(
									MEDIA_TYPE_JSON,
									account1Json.toString()
								)
							)
							.build()
					}
				// インスタンス情報
					"/api/v1/instance" -> {
						val instance = request.url().host()
						val json = JSONObject()
						json.apply {
							put("uri", "http://$instance/")
							put("title", "dummy instance")
							put("description", "dummy description")
							put("version", "0.0.1")
						}
						
						Response.Builder()
							.request(request)
							.protocol(Protocol.HTTP_1_1)
							.code(200)
							.message("status-message")
							.body(
								ResponseBody.create(
									MEDIA_TYPE_JSON,
									json.toString()
								)
							)
							.build()
					}
				// 公開タイムライン
					"/api/v1/timelines/public" -> {
						val instance = request.url().host()
						
						val username = "user1"
						
						val account1Json = JSONObject()
						account1Json.apply {
							put("username", username)
							put("acct", username)
							put("id", 1L)
							put("url", "http://$instance/@$username")
						}
						
						val array = JSONArray()
						for(i in 0 until 10) {
							val json = JSONObject()
							json.apply {
								put("account", account1Json)
								put("id", i.toLong())
								put("uri", "https://$instance/@$username/$i")
								put("url", "https://$instance/@$username/$i")
							}
							array.put(json)
						}
						
						Response.Builder()
							.request(request)
							.protocol(Protocol.HTTP_1_1)
							.code(200)
							.message("status-message")
							.body(
								ResponseBody.create(
									MEDIA_TYPE_JSON,
									array.toString()
								)
							)
							.build()
					}
					
					else ->
						Response.Builder()
							.request(request)
							.protocol(Protocol.HTTP_1_1)
							.code(200)
							.message("status-message")
							.body(
								ResponseBody.create(
									mediaTypeTextPlain,
									request.url().toString()
								)
							)
							.build()
				}
				
			},
			
			webSocketGenerator = { request : Request, _ : WebSocketListener ->
				object : WebSocket {
					override fun queueSize() : Long {
						return 4096L
					}
					
					override fun send(text : String?) : Boolean {
						return true
					}
					
					override fun send(bytes : ByteString?) : Boolean {
						return true
					}
					
					override fun close(code : Int, reason : String?) : Boolean {
						return true
					}
					
					override fun cancel() {
					}
					
					override fun request() : Request {
						return request
					}
					
				}
			}
		)
	}
	
	private fun createHttpClientNotImplemented() : SimpleHttpClient {
		return SimpleHttpClientMock(
			responseGenerator = { _ : Request ->
				throw NotImplementedError()
			},
			webSocketGenerator = { _ : Request, _ : WebSocketListener ->
				throw NotImplementedError()
			}
		)
	}
	
	class ProgressRecordTootApiCallback : TootApiCallback {
		
		var cancelled : Boolean = false
		
		var progressString : String? = null
		
		override val isApiCancelled : Boolean
			get() = cancelled
		
		override fun publishApiProgress(s : String) {
			progressString = s
		}
	}
	
	private val requestSimple : Request = Request.Builder().url("https://dummy-url.net/").build()
	
	private val strJsonOk = """{"a":"A!"}"""
	private val strJsonError = """{"error":"Error!"}"""
	private fun createResponseOk() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonOk))
		.build()
	
	private fun createResponseOkButJsonError() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonError))
		.build()
	
	private fun createResponseErrorCode() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(500)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonError))
		.build()
	
	private fun createResponseEmptyBody() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, ""))
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
				
				override fun contentType() : MediaType? {
					return MEDIA_TYPE_JSON
				}
				
				override fun source() : BufferedSource? {
					return null
				}
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
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonArray1))
		.build()
	
	private fun createResponseJsonArray2() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonArray2))
		.build()
	
	private fun createResponseJsonObject2() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(MEDIA_TYPE_JSON, strJsonObject2))
		.build()
	
	private val mediaTypeTextPlain = MediaType.parse("text/plain")
	private val mediaTypeHtml = MediaType.parse("text/html")
	
	private fun createResponsePlainText() = Response.Builder()
		.request(requestSimple)
		.protocol(Protocol.HTTP_1_1)
		.code(200)
		.message("status-message")
		.body(ResponseBody.create(mediaTypeTextPlain, strPlainText))
		.build()
	
	@Test
	fun testSimplifyErrorHtml() {
		var request : Request
		var response : Response
		var message : String
		
		// json error
		response = createResponseErrorCode()
		message = TootApiClient.simplifyErrorHtml(response, response.body()?.string() ?: "")
		assertEquals("Error!", message)
		
		// HTML error
		
		response = Response.Builder()
			.request(requestSimple)
			.protocol(Protocol.HTTP_1_1)
			.code(500)
			.message("This is test")
			.body(
				ResponseBody.create(
					mediaTypeHtml,
					"""<html><body>Error!</body></html>"""
				)
			)
			.build()
		
		message = TootApiClient.simplifyErrorHtml(response, response.body()?.string() ?: "")
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
			.body(
				ResponseBody.create(
					MediaType.parse("text/plain"),
					"""Error!"""
				)
			)
			.build()
		
		message = TootApiClient.simplifyErrorHtml(response, response.body()?.string() ?: "")
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
			.body(
				ResponseBody.create(
					MediaType.parse("text/plain"),
					""
				)
			)
			.build()
		
		message = TootApiClient.simplifyErrorHtml(response, response.body()?.string() ?: "")
		assertEquals("", message)
	}
	
	@Test
	fun testFormatResponse() {
		
		var request : Request
		var response : Response
		var bodyString : String?
		var message : String
		
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
		
		message = TootApiClient.formatResponse(response, "caption", null)
		
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
			.body(
				ResponseBody.create(
					MEDIA_TYPE_JSON,
					"""{"error":"Error!"}"""
				)
			)
			.build()
		
		message = TootApiClient.formatResponse(response, "caption", null)
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
			.body(
				ResponseBody.create(
					MEDIA_TYPE_JSON,
					"""{"error":"Error!"}"""
				)
			)
			.build()
		
		bodyString = response.body()?.string()
		
		message = TootApiClient.formatResponse(response, "caption", bodyString)
		assertEquals("Error! (HTTP 500 status-message) caption", message)
		
		// without status message
		request = Request.Builder()
			.url("https://dummy-url.net/")
			.build()
		
		response = Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(500)
			.message("")
			.body(
				ResponseBody.create(
					MEDIA_TYPE_JSON,
					"""{"error":"Error!"}"""
				)
			)
			.build()
		
		bodyString = response.body()?.string()
		
		message = TootApiClient.formatResponse(response, "caption", bodyString)
		assertEquals("Error! (HTTP 500) caption", message)
		
	}
	
	@Test
	fun testIsApiCancelled() {
		
		var flag = 0
		var progressString : String? = null
		var progressValue : Int? = null
		var progressMax : Int? = null
		
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNotImplemented(),
			callback = object : TootApiCallback {
				override val isApiCancelled : Boolean
					get() {
						++ flag
						return true
					}
				
				override fun publishApiProgress(s : String) {
					++ flag
					progressString = s
				}
				
				override fun publishApiProgressRatio(value : Int, max : Int) {
					++ flag
					progressValue = value
					progressMax = max
				}
			}
		)
		val isApiCancelled = client.isApiCancelled
		client.publishApiProgress("testing")
		client.publishApiProgressRatio(50, 100)
		assertEquals(3, flag)
		assertEquals(true, isApiCancelled)
		assertEquals("testing", progressString)
		assertEquals(50, progressValue)
		assertEquals(100, progressMax)
		
	}
	
	@Test
	fun testSendRequest() {
		
		val callback = ProgressRecordTootApiCallback()
		
		// 正常ケースではResponseが返ってくること
		run {
			val client = TootApiClient(
				appContext,
				httpClient = createHttpClientNormal(),
				callback = callback
			)
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			
			callback.progressString = null
			val bOk = client.sendRequest(result) { requestSimple }
			assertEquals(true, bOk)
			assertEquals("取得中: GET /", callback.progressString)
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
			assertEquals("取得中: GET /", callback.progressString)
			assertEquals(
				"instance: 通信エラー :NotImplementedError An operation is not implemented.",
				result.error
			)
			assertNull(result.response)
			
		}
		
		// progressPath を指定したらpublishApiProgressに渡されること
		run {
			val client = TootApiClient(
				appContext,
				httpClient = createHttpClientNormal(),
				callback = callback
			)
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			
			callback.progressString = null
			val bOk = client.sendRequest(result, progressPath = "XXX") { requestSimple }
			assertEquals(true, bOk)
			assertEquals("取得中: GET XXX", callback.progressString)
			assertEquals(null, result.error)
			assertNotNull(result.response)
		}
		
	}
	
	@Test
	fun testReadBodyString() {
		
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("読込中: GET instance", callback.progressString)
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
			assertEquals(null, bodyString)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
		}
		// ボディがnullなら
		run {
			
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			result.response = createResponseWithoutBody()
			
			callback.progressString = null
			val bodyString = client.readBodyString(result)
			assertEquals(null, bodyString)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
		}
		
		// string() が例外
		run {
			
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			result.response = createResponseExceptionBody()
			
			var catched : Throwable? = null
			val bodyString = try {
				client.readBodyString(result)
			} catch(ex : Throwable) {
				ex.printStackTrace()
				catched = ex
				null
			}
			assertEquals(null, bodyString)
			assertNotNull(catched)
		}
	}
	
	@Test
	fun testParseString() {
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("Error! (HTTP 500 status-message) instance", result.error)
		}
		
		// ボディが空なら
		run {
			
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			result.response = createResponseEmptyBody()
			callback.progressString = null
			val r2 = client.parseString(result)
			assertNotNull(r2)
			assertEquals(null, result.string)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
		}
		// ボディがnullなら
		run {
			
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			result.response = createResponseWithoutBody()
			
			callback.progressString = null
			val r2 = client.parseString(result)
			assertNotNull(r2)
			assertEquals(null, result.string)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
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
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
		}
		
	}
	
	@Test
	fun testParseJson() {
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("読込中: GET instance", callback.progressString)
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
			assertEquals(null, result.data)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
		}
		
		// ボディがnullなら
		run {
			val result = TootApiResult.makeWithCaption("instance")
			assertEquals(null, result.error)
			result.response = createResponseWithoutBody()
			callback.progressString = null
			val r2 = client.parseJson(result)
			assertNotNull(r2)
			assertEquals(null, result.data)
			assertEquals(null, result.bodyString)
			assertEquals("読込中: GET instance", callback.progressString)
			assertEquals("(no information) (HTTP 200 status-message) instance", result.error)
			assertNull(result.data)
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
			assertEquals("読込中: GET instance", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
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
			assertEquals("応答の解析中…", callback.progressString)
			assertEquals("APIの応答がJSONではありません\nHello!", result.error)
		}
	}
	
	@Test
	fun testRegisterClient() {
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
			callback = callback
		)
		val instance = "unit-test"
		client.instance = instance
		val clientName = "SubwayTooterUnitTest"
		val scope_string = "read+write+follow+push"
		
		// まずクライアント情報を作らないとcredentialのテストができない
		var result = client.registerClient(scope_string , clientName)
		assertNotNull(result)
		assertEquals(null, result?.error)
		var jsonObject = result?.jsonObject
		assertNotNull(jsonObject)
		if(jsonObject == null) return@testRegisterClient
		val clientInfo = jsonObject
		
		// clientCredential の作成
		result = client.getClientCredential(clientInfo)
		assertNotNull(result)
		assertEquals(null, result?.error)
		val clientCredential = result?.string
		assertNotNull(clientCredential)
		if(clientCredential == null) return@testRegisterClient
		clientInfo.put(TootApiClient.KEY_CLIENT_CREDENTIAL, clientCredential)
		
		// clientCredential の検証
		result = client.verifyClientCredential(clientCredential)
		assertNotNull(result)
		assertEquals(null, result?.error)
		jsonObject = result?.jsonObject
		assertNotNull(jsonObject) // 中味は別に見てない。jsonObjectなら良いらしい
		if(jsonObject == null) return
		
		var url : String?
		
		// ブラウザURLの作成
		url = client.prepareBrowserUrl(scope_string,clientInfo)
		assertNotNull(url)
		println(url)
		
		// ここまでと同じことをauthorize1でまとめて行う
		result = client.authentication1(clientName)
		url = result?.string
		assertNotNull(url)
		if(url == null) return
		println(url)
		
		// ブラウザからコールバックで受け取ったcodeを処理する
		result = client.authentication2(clientName, "DUMMY_CODE")
		jsonObject = result?.jsonObject
		assertNotNull(jsonObject)
		if(jsonObject == null) return
		println(jsonObject.toString())
		
		// 認証できたならアクセストークンがある
		val tokenInfo = result?.tokenInfo
		assertNotNull(tokenInfo)
		if(tokenInfo == null) return
		val accessToken = tokenInfo.optString("access_token")
		assertNotNull(accessToken)
		if(accessToken == null) return
		
		// アカウント手動入力でログインする場合はこの関数を直接呼び出す
		result = client.getUserCredential(accessToken, tokenInfo)
		jsonObject = result?.jsonObject
		assertNotNull(jsonObject)
		if(jsonObject == null) return
		println(jsonObject.toString())
		
	}
	
	@Test
	fun testGetInstanceInformation() {
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
			callback = callback
		)
		val instance = "unit-test"
		client.instance = instance
		val result = client.getInstanceInformation()
		val jsonObject = result?.jsonObject
		assertNotNull(jsonObject)
		if(jsonObject == null) return
		println(jsonObject.toString())
		
	}
	
	@Test
	fun testGetHttp() {
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
			callback = callback
		)
		val result = client.getHttp("http://juggler.jp/")
		val content = result?.string
		assertNotNull(content)
		println(content.toString())
	}
	
	@Test
	fun testRequest() {
		val tokenInfo = JSONObject()
		tokenInfo.put("access_token", "DUMMY_ACCESS_TOKEN")
		
		val accessInfo = SavedAccount(
			db_id = 1,
			acct = "user1@host1",
			hostArg = null,
			token_info = tokenInfo
		)
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
			callback = callback
		)
		client.account = accessInfo
		val result = client.request("/api/v1/timelines/public")
		println(result?.bodyString)
		
		val content = result?.jsonArray
		assertNotNull(content)
		println(content?.optJSONObject(0).toString())
	}
	
	@Test
	fun testWebSocket() {
		val tokenInfo = JSONObject()
		tokenInfo.put("access_token", "DUMMY_ACCESS_TOKEN")
		
		val accessInfo = SavedAccount(
			db_id = 1,
			acct = "user1@host1",
			hostArg = null,
			token_info = tokenInfo
		)
		val callback = ProgressRecordTootApiCallback()
		val client = TootApiClient(
			appContext,
			httpClient = createHttpClientNormal(),
			callback = callback
		)
		client.account = accessInfo
		val(_,ws) = client.webSocket("/api/v1/streaming/?stream=public:local",
			object : WebSocketListener() {
			})
		assertNotNull(ws)
		ws?.cancel()
	}
}

