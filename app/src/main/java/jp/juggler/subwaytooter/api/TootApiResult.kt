package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import okhttp3.Response
import okhttp3.WebSocket

open class TootApiResult(
    @Suppress("unused") val dummy: Int = 0,
    var error: String? = null,
    var response: Response? = null,
    var caption: String = "?",
    var bodyString: String? = null,
) {

    companion object {

        private val log = LogCategory("TootApiResult")

        val reWhiteSpace = """\s+""".asciiPattern()

        private val reLinkURL = """<([^>]+)>;\s*rel="([^"]+)"""".asciiPattern()

        fun makeWithCaption(apiHost: Host?) = makeWithCaption(apiHost?.pretty)

        fun makeWithCaption(caption: String?) = TootApiResult().apply {
            when (caption) {
                null, "" -> {
                    log.e("makeWithCaption: missing caption!")
                    error = "missing instance name"
                }
                else -> this.caption = caption
            }
        }
    }

    var requestInfo = ""

    var tokenInfo: JsonObject? = null

    var data: Any? = null
        set(value) {
            if (value is JsonArray) {
                parseLinkHeader(response, value)
            }
            field = value
        }

    val jsonObject: JsonObject?
        get() = data as? JsonObject

    val jsonArray: JsonArray?
        get() = data as? JsonArray

    val string: String?
        get() = data as? String

    var linkOlder: String? = null // より古いデータへのリンク
    var linkNewer: String? = null // より新しいデータへの

    constructor() : this(0)

    constructor(error: String) : this(0, error = error)

    constructor(socket: WebSocket) : this(0) {
        this.data = socket
    }

    constructor(response: Response, error: String)
            : this(0, error, response)

    constructor(response: Response, bodyString: String, data: Any?) : this(
        0,
        response = response,
        bodyString = bodyString
    ) {
        this.data = data
    }

    // return result.setError(...) と書きたい
    fun setError(error: String) = also { it.error = error }

    private fun parseLinkHeader(response: Response?, array: JsonArray) {
        response ?: return

        log.d("array size=${array.size}")

        // https://handon.club/@highemerly/109755355021758238
        // https://mastodon.juggler.jp/@tateisu/109756228563804507
        // リンクヘッダが複数ある場合がある
        val linkHeaders = response.headers("Link")
        if (linkHeaders.isEmpty()) {
            log.d("missing Link header")
        } else {
            for (sv in linkHeaders) {
                // Link:  <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&max_id=405228>; rel="next",
                //        <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&since_id=436946>; rel="prev"
                val m = reLinkURL.matcher(sv)
                while (m.find()) {
                    val url = m.groupEx(1)
                    val rel = m.groupEx(2)
                    log.d("Link: $rel $url")
                    when (rel) {
                        "next" -> linkOlder = url
                        "prev" -> linkNewer = url
                    }
                }
            }
        }
    }

    // アカウント作成APIのdetailsを読むため、エラー応答のjsonオブジェクトを保持する
    private var errorJson: JsonObject? = null

    internal fun simplifyErrorHtml(
        sv: String,
        jsonErrorParser: (json: JsonObject) -> String? = TootApiClient.DEFAULT_JSON_ERROR_PARSER,
    ): String {
        val response = this.response!!

        // JsonObjectとして解釈できるならエラーメッセージを検出する
        try {
            val json = sv.decodeJsonObject()
            this.errorJson = json
            jsonErrorParser(json)?.notEmpty()?.let { return it }
        } catch (_: Throwable) {
        }

        // HTMLならタグの除去を試みる
        val ct = response.body.contentType()
        if (ct?.subtype == "html") {
            val decoded = DecodeOptions().decodeHTML(sv).toString()
            return reWhiteSpace.matcher(decoded).replaceAll(" ").trim()
        }

        // XXX: Amazon S3 が403を返した場合にcontent-typeが?/xmlでserverがAmazonならXMLをパースしてエラーを整形することもできるが、多分必要ない

        return reWhiteSpace.matcher(sv).replaceAll(" ").trim()
    }

    fun parseErrorResponse(
        bodyString: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = TootApiClient.DEFAULT_JSON_ERROR_PARSER,
    ) {
        val response = this.response!!

        val sb = StringBuilder()
        try {
            // body は既に読み終わっているか、そうでなければこれから読む
            if (bodyString != null) {
                sb.append(simplifyErrorHtml(bodyString, jsonErrorParser))
            } else {
                try {
                    val string = response.body.string()
                    sb.append(simplifyErrorHtml(string, jsonErrorParser))
                } catch (ex: Throwable) {
                    log.e(ex, "missing response body.")
                    sb.append("(missing response body)")
                }
            }

            if (sb.isNotEmpty()) sb.append(' ')
            sb.append("(HTTP ").append(response.code.toString())

            val message = response.message
            if (message.isNotEmpty()) sb.append(' ').append(message)
            sb.append(")")

            if (caption.isNotEmpty()) {
                sb.append(' ').append(caption)
            }
        } catch (ex: Throwable) {
            log.e(ex, "parseErrorResponse failed.")
        }

        this.error = sb.toString().replace("\n+".toRegex(), "\n")
    }
}
