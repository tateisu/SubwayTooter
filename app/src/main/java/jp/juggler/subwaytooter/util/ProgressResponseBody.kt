package jp.juggler.subwaytooter.util

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



class ProgressResponseBody private constructor(private val originalBody : ResponseBody) : ResponseBody() {
	
	
	companion object {
		
		internal val log = LogCategory("ProgressResponseBody")
		
		// please append this for OkHttpClient.Builder#addInterceptor().
		// ex) builder.addInterceptor( ProgressResponseBody.makeInterceptor() );
		fun makeInterceptor() : Interceptor {
			return Interceptor { chain ->
				val originalResponse = chain.proceed(chain.request()) ?: throw RuntimeException("makeInterceptor: chain.proceed() returns null.")
				
				val originalBody = originalResponse.body() ?: throw RuntimeException("makeInterceptor: originalResponse.body() reruens null.")
				
				originalResponse.newBuilder()
					.body(ProgressResponseBody(originalBody))
					.build()
			}
		}
		
		@Throws(IOException::class)
		fun bytes(response : Response, callback : ProgressResponseBodyCallback) : ByteArray {
			val body = response.body() ?: throw RuntimeException("response.body() is null.")
			return bytes(body, callback)
		}
		
		@Suppress("MemberVisibilityCanPrivate")
		@Throws(IOException::class)
		private fun bytes(body : ResponseBody, callback : ProgressResponseBodyCallback) : ByteArray {
			if(body is ProgressResponseBody) {
				body.callback = callback
			}
			return body.bytes()
		}
	}
	
	private var callback : ProgressResponseBodyCallback = {_,_->}
	
	/*
		RequestBody.bytes() is defined as final, We can't override it.
		Make WrappedBufferedSource to capture BufferedSource.readByteArray().
	 */
	
	private var wrappedSource : BufferedSource? = null
	
	/*
		then you can read response body's bytes() with progress callback.
		example:
		byte[] data = ProgressResponseBody.bytes( response, new ProgressResponseBody.Callback() {
			@Override public void progressBytes( long bytesRead, long bytesTotal ){
				publishApiProgressRatio( (int) bytesRead, (int) bytesTotal );
			}
		} );
	 */
	

	
	override fun contentType() : MediaType? {
		return originalBody.contentType()
	}
	
	override fun contentLength() : Long {
		return originalBody.contentLength()
	}
	
	override fun source() : BufferedSource? {
		if(wrappedSource == null) {
			
			val originalSource = originalBody.source() ?: return null
			
			try {
				// if it is RealBufferedSource, I can access to source public field via reflection.
				val field_source = originalSource.javaClass.getField("source")
				
				// If there is the method, create the wrapper.
				wrappedSource = object : ForwardingBufferedSource(originalSource) {
					@Throws(IOException::class)
					override fun readByteArray() : ByteArray {
						/*
							RealBufferedSource.readByteArray() does:
						    - buffer.writeAll(source);
							- return buffer.readByteArray(buffer.size());
							
							We do same things using Reflection, with progress.
						*/
						
						try {
							val contentLength = originalBody.contentLength()
							val buffer = originalSource.buffer()
							val source = field_source.get(originalSource) as Source? ?: throw IllegalArgumentException("source == null")

// same thing of Buffer.writeAll(), with counting.
							var nRead : Long = 0
							callback (0, Math.max(contentLength, 1))
							while(true) {
								val delta = source.read(buffer, 8192)
								if(delta == - 1L) break
								nRead += delta
								if(nRead > 0 ) {
									callback (nRead, Math.max(contentLength, nRead))
								}
							}
							// EOS時の進捗
							callback (nRead, Math.max(contentLength, nRead))
							
							return buffer.readByteArray()
							
						} catch(ex : Throwable) {
							log.trace(ex)
							log.e("readByteArray() failed. ")
							return originalSource.readByteArray()
						}
						
					}
					
				}
			} catch(ex : Throwable) {
				log.e("can't access to RealBufferedSource#source field.")
				wrappedSource = originalSource
			}
			
		}
		return wrappedSource
	}
	
	// To avoid double buffering, We have to make ForwardingBufferedSource.
	internal open class ForwardingBufferedSource(private val originalSource : BufferedSource) : BufferedSource {
		
		override fun buffer() : Buffer {
			return originalSource.buffer()
		}
		
		@Throws(IOException::class)
		override fun exhausted() : Boolean {
			return originalSource.exhausted()
		}
		
		@Throws(IOException::class)
		override fun require(byteCount : Long) {
			originalSource.require(byteCount)
		}
		
		@Throws(IOException::class)
		override fun request(byteCount : Long) : Boolean {
			return originalSource.request(byteCount)
		}
		
		@Throws(IOException::class)
		override fun readByte() : Byte {
			return originalSource.readByte()
		}
		
		@Throws(IOException::class)
		override fun readShort() : Short {
			return originalSource.readShort()
		}
		
		@Throws(IOException::class)
		override fun readShortLe() : Short {
			return originalSource.readShortLe()
		}
		
		@Throws(IOException::class)
		override fun readInt() : Int {
			return originalSource.readInt()
		}
		
		@Throws(IOException::class)
		override fun readIntLe() : Int {
			return originalSource.readIntLe()
		}
		
		@Throws(IOException::class)
		override fun readLong() : Long {
			return originalSource.readLong()
		}
		
		@Throws(IOException::class)
		override fun readLongLe() : Long {
			return originalSource.readLongLe()
		}
		
		@Throws(IOException::class)
		override fun readDecimalLong() : Long {
			return originalSource.readDecimalLong()
		}
		
		@Throws(IOException::class)
		override fun readHexadecimalUnsignedLong() : Long {
			return originalSource.readHexadecimalUnsignedLong()
		}
		
		@Throws(IOException::class)
		override fun skip(byteCount : Long) {
			originalSource.skip(byteCount)
		}
		
		@Throws(IOException::class)
		override fun readByteString() : ByteString {
			return originalSource.readByteString()
		}
		
		@Throws(IOException::class)
		override fun readByteString(byteCount : Long) : ByteString {
			return originalSource.readByteString(byteCount)
		}
		
		@Throws(IOException::class)
		override fun select(options : Options) : Int {
			return originalSource.select(options)
		}
		
		@Throws(IOException::class)
		override fun readByteArray() : ByteArray {
			return originalSource.readByteArray()
		}
		
		@Throws(IOException::class)
		override fun readByteArray(byteCount : Long) : ByteArray {
			return originalSource.readByteArray(byteCount)
		}
		
		@Throws(IOException::class)
		override fun read(sink : ByteArray) : Int {
			return originalSource.read(sink)
		}
		
		@Throws(IOException::class)
		override fun readFully(sink : ByteArray) {
			originalSource.readFully(sink)
		}
		
		@Throws(IOException::class)
		override fun read(sink : ByteArray, offset : Int, byteCount : Int) : Int {
			return originalSource.read(sink, offset, byteCount)
		}
		
		@Throws(IOException::class)
		override fun readFully(sink : Buffer, byteCount : Long) {
			originalSource.readFully(sink, byteCount)
		}
		
		@Throws(IOException::class)
		override fun readAll(sink : Sink) : Long {
			return originalSource.readAll(sink)
		}
		
		@Throws(IOException::class)
		override fun readUtf8() : String {
			return originalSource.readUtf8()
		}
		
		@Throws(IOException::class)
		override fun readUtf8(byteCount : Long) : String {
			return originalSource.readUtf8(byteCount)
		}
		
		@Throws(IOException::class)
		override fun readUtf8Line() : String? {
			return originalSource.readUtf8Line()
		}
		
		@Throws(IOException::class)
		override fun readUtf8LineStrict() : String {
			return originalSource.readUtf8LineStrict()
		}
		
		@Throws(IOException::class)
		override fun readUtf8LineStrict(limit : Long) : String {
			return originalSource.readUtf8LineStrict(limit)
		}
		
		@Throws(IOException::class)
		override fun readUtf8CodePoint() : Int {
			return originalSource.readUtf8CodePoint()
		}
		
		@Throws(IOException::class)
		override fun readString(charset : Charset) : String {
			return originalSource.readString(charset)
		}
		
		@Throws(IOException::class)
		override fun readString(byteCount : Long, charset : Charset) : String {
			return originalSource.readString(byteCount, charset)
		}
		
		@Throws(IOException::class)
		override fun indexOf(b : Byte) : Long {
			return originalSource.indexOf(b)
		}
		
		@Throws(IOException::class)
		override fun indexOf(b : Byte, fromIndex : Long) : Long {
			return originalSource.indexOf(b, fromIndex)
		}
		
		@Throws(IOException::class)
		override fun indexOf(b : Byte, fromIndex : Long, toIndex : Long) : Long {
			return originalSource.indexOf(b, fromIndex, toIndex)
		}
		
		@Throws(IOException::class)
		override fun indexOf(bytes : ByteString) : Long {
			return originalSource.indexOf(bytes)
		}
		
		@Throws(IOException::class)
		override fun indexOf(bytes : ByteString, fromIndex : Long) : Long {
			return originalSource.indexOf(bytes, fromIndex)
		}
		
		@Throws(IOException::class)
		override fun indexOfElement(targetBytes : ByteString) : Long {
			return originalSource.indexOfElement(targetBytes)
		}
		
		@Throws(IOException::class)
		override fun indexOfElement(targetBytes : ByteString, fromIndex : Long) : Long {
			return originalSource.indexOfElement(targetBytes, fromIndex)
		}
		
		@Throws(IOException::class)
		override fun rangeEquals(offset : Long, bytes : ByteString) : Boolean {
			return originalSource.rangeEquals(offset, bytes)
		}
		
		@Throws(IOException::class)
		override fun rangeEquals(offset : Long, bytes : ByteString, bytesOffset : Int, byteCount : Int) : Boolean {
			return originalSource.rangeEquals(offset, bytes, bytesOffset, byteCount)
		}
		
		override fun inputStream() : InputStream {
			return originalSource.inputStream()
		}
		
		@Throws(IOException::class)
		override fun read(sink : Buffer, byteCount : Long) : Long {
			return originalSource.read(sink, byteCount)
		}
		
		override fun timeout() : Timeout {
			return originalSource.timeout()
		}
		
		@Throws(IOException::class)
		override fun close() {
			originalSource.close()
		}
	}

}
