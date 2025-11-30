package jp.juggler.subwaytooter.util

import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.*
import java.io.IOException
import kotlin.math.max

class ProgressResponseBody private constructor(
    private val originalBody: ResponseBody,
) : ResponseBody() {

    companion object {
        internal val log = LogCategory("ProgressResponseBody")

        // ProgressResponseBody を間に挟むインタセプタを作成する
        fun makeInterceptor() = Interceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body))
                .build()
        }

        // 進捗コールバックつきでバイト列を読む
        @Throws(IOException::class)
        fun bytes(
            response: Response,
            callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit,
        ) = bytes(response.body, callback)

        // 進捗コールバックつきでバイト列を読む
        @Suppress("MemberVisibilityCanPrivate")
        @Throws(IOException::class)
        private fun bytes(
            body: ResponseBody,
            callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit,
        ): ByteArray {
            (body as? ProgressResponseBody)?.callback = callback
            return body.bytes()
        }
    }

    private var callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit = { _, _ -> }

    private val wrappedSource by lazy {
        object : ForwardingSource(originalBody.source()) {
            var totalBytesRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += max(0, bytesRead)
                runBlocking {
                    callback.invoke(
                        totalBytesRead,
                        originalBody.contentLength(),
                    )
                }
                return bytesRead
            }
        }.buffer()
    }

    override fun contentType() = originalBody.contentType()
    override fun contentLength() = originalBody.contentLength()
    override fun source() = wrappedSource
}
