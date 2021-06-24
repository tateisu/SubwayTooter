package jp.juggler.subwaytooter.util

import jp.juggler.util.LogCategory
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.Options
import okio.Sink
import okio.Source
import okio.Timeout
import java.nio.ByteBuffer
import kotlin.jvm.Throws
import kotlin.math.max

class ProgressResponseBody private constructor(
    private val originalBody: ResponseBody,
) : ResponseBody() {

    companion object {

        internal val log = LogCategory("ProgressResponseBody")

        // please append this for OkHttpClient.Builder#addInterceptor().
        // ex) builder.addInterceptor( ProgressResponseBody.makeInterceptor() );
        fun makeInterceptor(): Interceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val originalResponse = chain.proceed(chain.request())

                val originalBody = originalResponse.body
                    ?: error("makeInterceptor: originalResponse.body() returns null.")

                return originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalBody))
                    .build()
            }
        }

        @Throws(IOException::class)
        fun bytes(response: Response, callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit): ByteArray {
            val body = response.body ?: error("response.body() is null.")
            return bytes(body, callback)
        }

        @Suppress("MemberVisibilityCanPrivate")
        @Throws(IOException::class)
        private fun bytes(
            body: ResponseBody,
            callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit,
        ): ByteArray {
            if (body is ProgressResponseBody) {
                body.callback = callback
            }
            return body.bytes()
        }
    }

    private var callback: suspend (bytesRead: Long, bytesTotal: Long) -> Unit = { _, _ -> }

    /*
        RequestBody.bytes() is defined as final, We can't override it.
        Make WrappedBufferedSource to capture BufferedSource.readByteArray().
     */

    private val wrappedSource: BufferedSource by lazy {
        val originalSource = originalBody.source()

        try {
            // if it is RealBufferedSource, I can access to source public field via reflection.
            val fieldSource = originalSource.javaClass.getField("source")

            // If there is the method, create the wrapper.
            object : ForwardingBufferedSource(originalSource) {

                @Throws(IOException::class)
                override fun readByteArray(): ByteArray {
                    /*
                        RealBufferedSource.readByteArray() does:
                        - buffer.writeAll(source);
                        - return buffer.readByteArray(buffer.size());

                        We do same things using Reflection, with progress.
                    */

                    try {
                        val contentLength = originalBody.contentLength()
                        val buffer = originalSource.buffer
                        val source = fieldSource.get(originalSource) as Source?
                            ?: throw IllegalArgumentException("source == null")

                        // same thing of Buffer.writeAll(), with counting.
                        var nRead: Long = 0
                        runBlocking { callback(0, max(contentLength, 1)) }
                        while (true) {
                            val delta = source.read(buffer, 8192)
                            if (delta == -1L) break
                            nRead += delta
                            if (nRead > 0) {
                                runBlocking { callback(nRead, max(contentLength, nRead)) }
                            }
                        }
                        // EOS時の進捗
                        runBlocking { callback(nRead, max(contentLength, nRead)) }

                        return buffer.readByteArray()
                    } catch (ex: Throwable) {
                        log.trace(ex)
                        log.e("readByteArray() failed. ")
                        return originalSource.readByteArray()
                    }
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't access to RealBufferedSource#source field.")
            originalSource
        }
    }

    /*
        then you can read response body's bytes() with progress callback.
        example:
        byte[] data = ProgressResponseBody.bytes( response, new ProgressResponseBody.Callback() {
            @Override public void progressBytes( long bytesRead, long bytesTotal ){
                publishApiProgressRatio( (int) bytesRead, (int) bytesTotal );
            }
        } );
     */

    override fun contentType(): MediaType? {
        return originalBody.contentType()
    }

    override fun contentLength(): Long {
        return originalBody.contentLength()
    }

    override fun source(): BufferedSource = wrappedSource

    // To avoid double buffering, We have to make ForwardingBufferedSource.
    @Suppress("TooManyFunctions")
    internal open class ForwardingBufferedSource(
        private val originalSource: BufferedSource,
    ) : BufferedSource {

        override val buffer: Buffer
            get() = originalSource.buffer

        @Suppress("DEPRECATION", "OverridingDeprecatedMember")
        override fun buffer(): Buffer = originalSource.buffer()

        override fun peek(): BufferedSource = originalSource.peek()

        override fun read(dst: ByteBuffer?) = originalSource.read(dst)

        override fun isOpen() = originalSource.isOpen

        override fun exhausted() = originalSource.exhausted()

        override fun require(byteCount: Long) = originalSource.require(byteCount)

        override fun request(byteCount: Long) = originalSource.request(byteCount)

        override fun readByte() = originalSource.readByte()

        override fun readShort() = originalSource.readShort()

        override fun readShortLe() = originalSource.readShortLe()

        override fun readInt() = originalSource.readInt()

        override fun readIntLe() = originalSource.readIntLe()

        override fun readLong() = originalSource.readLong()

        override fun readLongLe() = originalSource.readLongLe()

        override fun readDecimalLong() = originalSource.readDecimalLong()

        override fun readHexadecimalUnsignedLong() = originalSource.readHexadecimalUnsignedLong()

        override fun skip(byteCount: Long) = originalSource.skip(byteCount)

        override fun readByteString(): ByteString = originalSource.readByteString()

        override fun readByteString(byteCount: Long): ByteString =
            originalSource.readByteString(byteCount)

        override fun select(options: Options) = originalSource.select(options)

        override fun readByteArray(): ByteArray = originalSource.readByteArray()

        override fun readByteArray(byteCount: Long): ByteArray =
            originalSource.readByteArray(byteCount)

        override fun read(sink: ByteArray) = originalSource.read(sink)

        override fun readFully(sink: ByteArray) = originalSource.readFully(sink)

        override fun read(sink: ByteArray, offset: Int, byteCount: Int) =
            originalSource.read(sink, offset, byteCount)

        override fun readFully(sink: Buffer, byteCount: Long) =
            originalSource.readFully(sink, byteCount)

        override fun readAll(sink: Sink) = originalSource.readAll(sink)

        override fun readUtf8(): String = originalSource.readUtf8()

        override fun readUtf8(byteCount: Long): String = originalSource.readUtf8(byteCount)

        override fun readUtf8Line(): String? = originalSource.readUtf8Line()

        override fun readUtf8LineStrict(): String = originalSource.readUtf8LineStrict()

        override fun readUtf8LineStrict(limit: Long): String =
            originalSource.readUtf8LineStrict(limit)

        override fun readUtf8CodePoint() = originalSource.readUtf8CodePoint()

        override fun readString(charset: Charset): String = originalSource.readString(charset)

        override fun readString(byteCount: Long, charset: Charset): String =
            originalSource.readString(byteCount, charset)

        override fun indexOf(b: Byte) = originalSource.indexOf(b)

        override fun indexOf(b: Byte, fromIndex: Long) = originalSource.indexOf(b, fromIndex)

        override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long) =
            originalSource.indexOf(b, fromIndex, toIndex)

        override fun indexOf(bytes: ByteString) = originalSource.indexOf(bytes)

        override fun indexOf(bytes: ByteString, fromIndex: Long) =
            originalSource.indexOf(bytes, fromIndex)

        override fun indexOfElement(targetBytes: ByteString) =
            originalSource.indexOfElement(targetBytes)

        override fun indexOfElement(targetBytes: ByteString, fromIndex: Long) =
            originalSource.indexOfElement(targetBytes, fromIndex)

        override fun rangeEquals(offset: Long, bytes: ByteString) =
            originalSource.rangeEquals(offset, bytes)

        override fun rangeEquals(
            offset: Long,
            bytes: ByteString,
            bytesOffset: Int,
            byteCount: Int,
        ) = originalSource.rangeEquals(offset, bytes, bytesOffset, byteCount)

        override fun inputStream(): InputStream = originalSource.inputStream()

        override fun read(sink: Buffer, byteCount: Long) = originalSource.read(sink, byteCount)

        override fun timeout(): Timeout = originalSource.timeout()

        override fun close() = originalSource.close()
    }
}
