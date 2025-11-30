package jp.juggler.subwaytooter.testutil

import android.content.Context
import androidx.annotation.RawRes
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.encodeUTF8
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class MockInterceptor(private val mockJsonMap: JsonObject) : Interceptor {
    constructor(context: Context, @RawRes rawId: Int) : this(
        context.resources.openRawResource(rawId)
            .use { it.readBytes() }
            .decodeUTF8()
            .decodeJsonObject()
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        return when (val resultValue = mockJsonMap[url]) {
            null -> throw IOException("missing mockJson for $url")
            is JsonObject -> Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .body(
                    resultValue.toString().encodeUTF8()
                        .toResponseBody("application/json".toMediaType())
                ).build()
            is Number -> Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(resultValue.toInt())
                .message("error $resultValue")
                .body(
                    """{"error":$resultValue}""".encodeUTF8()
                        .toResponseBody("application/json".toMediaType())
                ).build()
            else -> Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(500)
                .message("unknonw result type: $resultValue")
                .body(
                    """{"error":$resultValue}""".encodeUTF8()
                        .toResponseBody("application/json".toMediaType())
                ).build()
        }
    }
}
