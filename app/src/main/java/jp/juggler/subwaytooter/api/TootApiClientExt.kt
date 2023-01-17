package jp.juggler.subwaytooter.api

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.isAndroid7TlsBug
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException

private val log = LogCategory("TootApiClientExt")

class SendException(
    val request: Request,
    message: String,
    cause: Throwable? = null,
    val response: Response? = null,
) : IOException(message, cause)

abstract class ResponseWithBase {
    abstract val client: TootApiClient
    abstract val response: Response
    abstract val progressPath: String?
    abstract val errorSuffix: String?
    abstract val jsonErrorParser: (json: JsonObject) -> String?

    /**
     * 応答ボディのHTMLやテキストを整形する
     */
    private fun simplifyErrorHtml(body: String): String {
        // JsonObjectとして解釈できるならエラーメッセージを検出する
        try {
            val json = body.decodeJsonObject()
            jsonErrorParser(json)?.notEmpty()?.let { return it }
        } catch (_: Throwable) {
        }

        // HTMLならタグの除去を試みる
        try {
            val ct = response.body?.contentType()
            if (ct?.subtype == "html") {
                val decoded = DecodeOptions().decodeHTML(body).toString()
                return TootApiResult.reWhiteSpace.matcher(decoded).replaceAll(" ").trim()
            }
        } catch (_: Throwable) {
        }

        // XXX: Amazon S3 が403を返した場合にcontent-typeが?/xmlでserverがAmazonならXMLをパースしてエラーを整形することもできるが、多分必要ない

        // 通常テキストの空白や改行を整理した文字列を返す
        try {
            return TootApiResult.reWhiteSpace.matcher(body).replaceAll(" ").trim()
        } catch (_: Throwable) {
        }

        // 全部失敗したら入力そのまま
        return body
    }

    /**
     * エラー応答のステータス部分や本文を文字列にする
     */
    fun parseErrorResponse(body: String? = null): String =
        try {
            StringBuilder().apply {
                // 応答ボディのテキストがあれば追加
                if (body.isNullOrBlank()) {
                    append("(missing response body)")
                } else {
                    append(simplifyErrorHtml(body))
                }
                if (isNotEmpty()) append(' ')
                append("(HTTP ").append(response.code.toString())
                response.message.notBlank()?.let { message ->
                    append(' ')
                    append(message)
                }
                append(")")
                errorSuffix?.notBlank()?.let {
                    append(' ')
                    append(errorSuffix)
                }
            }.toString().replace("""[\x0d\x0a]+""".toRegex(), "\n")
        } catch (ex: Throwable) {
            log.e(ex, "parseErrorResponse failed.")
            "(can't parse response body)"
        }

    fun <T : Any?> newContent(newContent: T) = ResponseWith(
        client = client,
        response = response,
        progressPath = progressPath,
        errorSuffix = errorSuffix,
        jsonErrorParser = jsonErrorParser,
        content = newContent
    )
}

class ResponseWith<T : Any?>(
    override val client: TootApiClient,
    override val response: Response,
    override val progressPath: String? = null,
    override val errorSuffix: String? = null,
    override val jsonErrorParser: (json: JsonObject) -> String? = TootApiClient.DEFAULT_JSON_ERROR_PARSER,
    val content: T,
) : ResponseWithBase()

class ResponseBeforeRead(
    override val client: TootApiClient,
    override val response: Response,
    override val progressPath: String? = null,
    override val errorSuffix: String? = null,
    override val jsonErrorParser: (json: JsonObject) -> String? = TootApiClient.DEFAULT_JSON_ERROR_PARSER,
) : ResponseWithBase() {
    /**
     * レスポンスボディを文字列として読む
     * ボディがない場合はnullを返す
     * その他はSendExceptionを返す
     */
    private suspend fun readString(): ResponseWith<String?> {
        val response = response
        val request = response.request
        return try {
            client.publishApiProgress(
                client.context.getString(
                    R.string.reading_api,
                    request.method,
                    progressPath ?: request.url.host
                )
            )
            withContext(AppDispatchers.IO) {
                val bodyString = response.body?.string()
                if (bodyString.isNullOrEmpty()) {
                    if (response.code in 200 until 300) {
                        // Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
                        return@withContext newContent("")
                    } else if (!response.isSuccessful) {
                        throw SendException(
                            response = response,
                            request = request,
                            message = parseErrorResponse(body = bodyString),
                        )
                    }
                }
                newContent(bodyString)
            }
        } catch (ex: Throwable) {
            when (ex) {
                is CancellationException, is SendException -> throw ex
                else -> {
                    log.e(ex, "readString failed.")
                    throw SendException(
                        response = response,
                        request = request,
                        message = parseErrorResponse(ex.withCaption("readString failed."))
                    )
                }
            }
        } finally {
            response.body?.closeQuietly()
        }
    }

    /**
     * レスポンスボディを文字列として読む
     * ボディがない場合はnullを返す
     * その他はSendExceptionを返す
     */
    suspend fun readBytes(
        callback: (suspend (bytesRead: Long, bytesTotal: Long) -> Unit)? = null,
    ): ByteArray {
        val response = response
        val request = response.request
        return try {
            client.publishApiProgress(
                client.context.getString(
                    R.string.reading_api,
                    request.method,
                    request.url
                )
            )
            withContext(AppDispatchers.IO) {
                when {
                    !response.isSuccessful -> {
                        val errorBody = try {
                            response.body?.string()
                        } catch (ignored: Throwable) {
                            null
                        }
                        throw SendException(
                            response = response,
                            request = request,
                            message = parseErrorResponse(body = errorBody),
                        )
                    }
                    callback != null ->
                        ProgressResponseBody.bytes(response, callback)

                    else ->
                        response.body?.bytes() ?: error("missing response body.")
                }
            }
        } catch (ex: Throwable) {
            when (ex) {
                is CancellationException, is SendException -> throw ex
                else -> {
                    log.e(ex, "readString failed.")
                    throw SendException(
                        response = response,
                        request = request,
                        message = parseErrorResponse(ex.withCaption("readString failed."))
                    )
                }
            }
        } finally {
            response.body?.closeQuietly()
        }
    }

    suspend fun readJsonObject() = readString().stringToJsonObject()
}

/**
 * okHttpのリクエストを TootApiClient で処理して Response を得る
 * 失敗すると SendException を投げる
 */
suspend fun Request.send(
    client: TootApiClient,
    progressPath: String? = null,
    errorSuffix: String = "",
    overrideClient: OkHttpClient? = null,
    jsonErrorParser: (json: JsonObject) -> String? = TootApiClient.DEFAULT_JSON_ERROR_PARSER,
): ResponseBeforeRead {
    val request = this
    val requestInfo = "$method ${progressPath ?: url.encodedPath}"
    client.context.getString(R.string.request_api, method, progressPath ?: url.encodedPath)
        .let { client.callback.publishApiProgress(it) }
    return try {
        ResponseBeforeRead(
            client = client,
            response = client.httpClient.getResponse(request, overrideClient = overrideClient),
            jsonErrorParser = jsonErrorParser,
            progressPath = progressPath,
            errorSuffix = errorSuffix,
        )
    } catch (ex: Throwable) {
        // キャンセルはそのまま投げる
        if (ex is CancellationException) throw ex
        // 他は SendException に加工する
        val error = ex.withCaption(client.context.resources, R.string.network_error)
        throw SendException(
            cause = ex,
            message = when (errorSuffix) {
                "" -> "$error $requestInfo$method ${url.host} ${progressPath ?: url.encodedPath}"
                else -> "$error $requestInfo$method ${url.host} ${progressPath ?: url.encodedPath} ($errorSuffix)"
            },
            request = request,
        )
    }
}

/**
 * ResponseWith<String?> をResponseWith<JsonObject?>に変換する
 */
suspend fun ResponseWith<String?>.stringToJsonObject(): JsonObject =
    try {
        withContext(AppDispatchers.IO) {
            when {
                content == null -> throw SendException(
                    request = response.request,
                    response = response,
                    message = "response body is null. ($errorSuffix)",
                )

                // 204 no content は 空オブジェクトと解釈する
                content == "" -> JsonObject()

                TootApiClient.reStartJsonArray.find(content) != null ->
                    jsonObjectOf("root" to content.decodeJsonArray())

                TootApiClient.reStartJsonObject.find(content) != null -> {
                    val json = content.decodeJsonObject()
                    jsonErrorParser(json)?.let {
                        throw SendException(
                            request = response.request,
                            response = response,
                            message = "$it ($errorSuffix)",
                        )
                    }
                    json
                }

                else -> throw SendException(
                    response = response,
                    request = response.request,
                    message = parseErrorResponse("not a JSON object.")
                )
            }
        }
    } catch (ex: Throwable) {
        when (ex) {
            is CancellationException, is SendException -> throw ex
            else -> {
                log.e(ex, "readJsonObject failed. ($errorSuffix)")
                throw SendException(
                    response = response,
                    request = response.request,
                    message = ex.withCaption("readJsonObject failed. ($errorSuffix)"),
                )
            }
        }
    }

fun AppCompatActivity.dialogOrToast(message: String?) {
    if (message.isNullOrBlank()) return
    try {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    } catch (_: Throwable) {
        showToast(true, message)
    }
}

fun AppCompatActivity.showApiError(ex: Throwable) {
    try {
        log.e(ex, "showApiError")
        val errorText = ex.message
        if (isAndroid7TlsBug(errorText ?: "")) {
            dialogOrToast(errorText + "\n\n" + getString(R.string.ssl_bug_7_0))
            return
        }
        when (ex) {
            is CancellationException -> return
            is SendException -> dialogOrToast("${ex.message} ${ex.request.method} ${ex.request.url}")
            is IllegalStateException -> when (ex.cause) {
                null -> dialogOrToast(ex.message ?: "(??)")
                else -> dialogOrToast(ex.withCaption())
            }
            else -> dialogOrToast(ex.withCaption())
        }
    } catch (ignored: Throwable) {
    }
}
