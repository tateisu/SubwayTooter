package jp.juggler.subwaytooter.api

import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import ru.gildor.coroutines.okhttp.await
import java.io.IOException

private val log = LogCategory("ApiUtils2")

const val JSON_SERVER_TYPE = "<>serverType"
const val SERVER_MISSKEY = "misskey"
const val SERVER_MASTODON = "mastodon"

val DEFAULT_JSON_ERROR_PARSER =
    { json: JsonObject -> json["error"]?.toString() }

private val reWhiteSpace = """\s+""".toRegex()
private val reStartJsonArray = """\A\s*\[""".toRegex()
private val reStartJsonObject = """\A\s*\{""".toRegex()

fun Request.Builder.authorizationBearer(token: String?) =
    apply { token.notEmpty()?.let { header("Authorization", "Bearer $it") } }

class ApiError(
    message: String,
    cause: Throwable? = null,
    val response: Response? = null,
) : IOException(message, cause)

private fun Response.formatError(caption: String? = null) = when {
    caption.isNullOrBlank() -> "HTTP $code $message ${request.method} ${request.url}"
    else -> "$caption: HTTP $code $message ${request.method} ${request.url}"
}

private fun Request.formatError(ex: Throwable, caption: String? = null) = when {
    caption.isNullOrBlank() -> "${ex.withCaption()} $method $url"
    else -> "$caption: ${ex.withCaption()} $method $url"
}

/**
 * 応答ボディのHTMLやテキストを整形する
 */
private fun simplifyErrorHtml(body: String): String {
//    // JsonObjectとして解釈できるならエラーメッセージを検出する
//    try {
//        val json = body.decodeJsonObject()
//        jsonErrorParser(json)?.notEmpty()?.let { return it }
//    } catch (_: Throwable) {
//    }

//    // HTMLならタグの除去を試みる
//    try {
//        val ct = response.body?.contentType()
//        if (ct?.subtype == "html") {
//            // XXX HTMLデコードを省略
//            return reWhiteSpace.replace(body," ").trim()
//        }
//    } catch (_: Throwable) {
//    }

    // XXX: Amazon S3 が403を返した場合にcontent-typeが?/xmlでserverがAmazonならXMLをパースしてエラーを整形することもできるが、多分必要ない

    // 通常テキストの空白や改行を整理した文字列を返す
    try {
        return reWhiteSpace.replace(body, " ").trim()
    } catch (_: Throwable) {
    }

    // 全部失敗したら入力そのまま
    return body
}

/**
 * エラー応答のステータス部分や本文を文字列にする
 */
fun parseErrorResponse(response: Response, body: String? = null): String =
    try {
        val request = response.request
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
            append(") ${request.method} ${request.url}")
        }.toString().replace("""[\x0d\x0a]+""".toRegex(), "\n")
    } catch (ex: Throwable) {
        log.e(ex, "parseErrorResponse failed.")
        "(can't parse response body)"
    }

suspend fun Request.await(okHttp: OkHttpClient) =
    try {
        okHttp.newCall(this).await()
    } catch (ex: Throwable) {
        throw ApiError(cause = ex, message = this.formatError(ex))
    }

/**
 * レスポンスボディを文字列として読む
 * ボディがない場合はnullを返す
 * その他はSendExceptionを返す
 */
private suspend fun Response.readString(): String? {
    val response = this
    return try {
        // XXX: 進捗表示
        withContext(AppDispatchers.IO) {
            val bodyString = response.body?.string()
            if (bodyString.isNullOrEmpty()) {
                if (response.code in 200 until 300) {
                    // Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
                    return@withContext ""
                } else if (!response.isSuccessful) {
                    throw ApiError(
                        response = response,
                        message = parseErrorResponse(response = response, body = ""),
                    )
                }
            }
            bodyString
        }
    } catch (ex: Throwable) {
        log.e(ex, "readString failed.")
        when (ex) {
            is CancellationException, is ApiError -> throw ex
            else -> {
                throw ApiError(
                    response = response,
                    message = parseErrorResponse(
                        response = response,
                        ex.withCaption("readString failed.")
                    )
                )
            }
        }
    } finally {
        response.body?.closeQuietly()
    }
}

/**
 * ResponseWith<String?> をResponseWith<JsonObject?>に変換する
 */
suspend fun String?.stringToJsonObject(response: Response): JsonObject =
    try {
        val content = this
        withContext(AppDispatchers.IO) {
            when {
                content == null -> throw ApiError(
                    response = response,
                    message = response.formatError("response body is null.")
                )

                // 204 no content は 空オブジェクトと解釈する
                content == "" -> JsonObject()

                reStartJsonArray.containsMatchIn(content) ->
                    jsonObjectOf("root" to content.decodeJsonArray())

                reStartJsonObject.containsMatchIn(content) -> {
                    val json = content.decodeJsonObject()
                    DEFAULT_JSON_ERROR_PARSER(json)?.let { error ->
                        throw ApiError(
                            response = response,
                            message = response.formatError(error)
                        )
                    }
                    json
                }

                else -> throw ApiError(
                    response = response,
                    message = response.formatError("not a JSON object.")
                )
            }
        }
    } catch (ex: Throwable) {
        when (ex) {
            is CancellationException, is ApiError -> throw ex
            else -> {
                throw ApiError(
                    response = response,
                    message = response.formatError("readJsonObject failed."),
                    cause = ex,
                )
            }
        }
    }

suspend fun Response.readJsonObject() = readString().stringToJsonObject(this)
