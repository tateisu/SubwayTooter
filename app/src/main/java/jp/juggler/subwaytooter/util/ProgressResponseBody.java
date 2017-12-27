package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Options;
import okio.Sink;
import okio.Source;
import okio.Timeout;

public class ProgressResponseBody extends ResponseBody {
	
	static final LogCategory log = new LogCategory( "ProgressResponseBody" );
	
	@NonNull private final ResponseBody originalBody;
	
	// please append this for OkHttpClient.Builder#addInterceptor().
	// ex) builder.addInterceptor( ProgressResponseBody.makeInterceptor() );
	public static Interceptor makeInterceptor(){
		return new Interceptor() {
			@Override public Response intercept( @NonNull Chain chain ) throws IOException{
				
				Response originalResponse = chain.proceed( chain.request() );
				if( originalResponse == null )
					throw new RuntimeException( "makeInterceptor: chain.proceed() returns null." );
				
				ResponseBody originalBody = originalResponse.body();
				if( originalBody == null )
					throw new RuntimeException( "makeInterceptor: originalResponse.body() reruens null." );
				
				return originalResponse.newBuilder()
					.body( new ProgressResponseBody( originalBody ) )
					.build();
			}
		};
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
	
	public interface Callback {
		void progressBytes(
			long bytesRead
			, long bytesTotal // initial value is Content-Length, but it may only hint, it may glows greater.
		);
	}
	
	@SuppressWarnings("WeakerAccess")
	public static byte[] bytes( @NonNull Response response, @Nullable Callback callback ) throws IOException{
		ResponseBody body = response.body();
		if( body == null ) throw new RuntimeException( "response.body() is null." );
		return bytes( body, callback );
	}
	
	@SuppressWarnings("WeakerAccess")
	public static byte[] bytes( @NonNull ResponseBody body, @Nullable Callback callback ) throws IOException{
		if( body instanceof ProgressResponseBody ){
			( (ProgressResponseBody) body ).callback = callback;
		}
		return body.bytes();
	}
	
	/////////////////////////////////////////////////////
	// internal
	
	private ProgressResponseBody( @NonNull ResponseBody originalBody ){
		this.originalBody = originalBody;
	}
	
	@Nullable private Callback callback;
	
	@Override public MediaType contentType(){
		return originalBody.contentType();
	}
	
	@Override public long contentLength(){
		return originalBody.contentLength();
	}
	
	/*
		RequestBody.bytes() is defined as final, We can't override it.
		Make WrappedBufferedSource to capture BufferedSource.readByteArray().
	 */
	
	private BufferedSource wrappedSource;
	
	@Override public BufferedSource source(){
		if( wrappedSource == null ){
			
			BufferedSource originalSource = originalBody.source();
			if( originalSource == null ) return null;
			
			try{
				// if it is RealBufferedSource, I can access to source public field via reflection.
				final Field field_source = originalSource.getClass().getField( "source" );
				
				// If there is the method, create the wrapper.
				wrappedSource = new ForwardingBufferedSource( originalSource ) {
					@Override public byte[] readByteArray() throws IOException{
						/*
							RealBufferedSource.readByteArray() does:
						    - buffer.writeAll(source);
							- return buffer.readByteArray(buffer.size());
							
							We do same things using Reflection, with progress.
						*/
						
						try{
							long contentLength = originalBody.contentLength();
							Buffer buffer = originalSource.buffer();
							Source source = (Source) field_source.get( originalSource );
							
							if( source == null )
								throw new IllegalArgumentException( "source == null" );
							
							// same thing of Buffer.writeAll(), with counting.
							long nRead = 0;
							if( callback != null ){
								callback.progressBytes( 0, Math.max( contentLength, 1 ) );
							}
							for( ; ; ){
								long delta = source.read( buffer, 8192 );
								if( delta == - 1L ) break;
								nRead += delta;
								if( nRead > 0 && callback != null ){
									callback.progressBytes( nRead, Math.max( contentLength, nRead ) );
								}
							}
							// EOS時の進捗
							callback.progressBytes( nRead, Math.max( contentLength, nRead ) );
							
							return buffer.readByteArray();
							
						}catch( Throwable ex ){
							log.trace( ex );
							log.e( "readByteArray() failed. " );
							return originalSource.readByteArray();
						}
						
					}
					
				};
			}catch( Throwable ex ){
				log.e( "can't access to RealBufferedSource#source field." );
				wrappedSource = originalSource;
			}
		}
		return wrappedSource;
	}
	
	// To avoid double buffering, We have to make ForwardingBufferedSource.
	static class ForwardingBufferedSource implements BufferedSource {
		@NonNull final BufferedSource originalSource;
		
		ForwardingBufferedSource( @NonNull BufferedSource originalSource ){
			this.originalSource = originalSource;
		}
		
		@Override public Buffer buffer(){
			return originalSource.buffer();
		}
		
		@Override public boolean exhausted() throws IOException{
			return originalSource.exhausted();
		}
		
		@Override public void require( long byteCount ) throws IOException{
			originalSource.require( byteCount );
		}
		
		@Override public boolean request( long byteCount ) throws IOException{
			return originalSource.request( byteCount );
		}
		
		@Override public byte readByte() throws IOException{
			return originalSource.readByte();
		}
		
		@Override public short readShort() throws IOException{
			return originalSource.readShort();
		}
		
		@Override public short readShortLe() throws IOException{
			return originalSource.readShortLe();
		}
		
		@Override public int readInt() throws IOException{
			return originalSource.readInt();
		}
		
		@Override public int readIntLe() throws IOException{
			return originalSource.readIntLe();
		}
		
		@Override public long readLong() throws IOException{
			return originalSource.readLong();
		}
		
		@Override public long readLongLe() throws IOException{
			return originalSource.readLongLe();
		}
		
		@Override public long readDecimalLong() throws IOException{
			return originalSource.readDecimalLong();
		}
		
		@Override public long readHexadecimalUnsignedLong() throws IOException{
			return originalSource.readHexadecimalUnsignedLong();
		}
		
		@Override public void skip( long byteCount ) throws IOException{
			originalSource.skip( byteCount );
		}
		
		@Override public ByteString readByteString() throws IOException{
			return originalSource.readByteString();
		}
		
		@Override public ByteString readByteString( long byteCount ) throws IOException{
			return originalSource.readByteString( byteCount );
		}
		
		@Override public int select( @NonNull Options options ) throws IOException{
			return originalSource.select( options );
		}
		
		@Override public byte[] readByteArray() throws IOException{
			return originalSource.readByteArray();
		}
		
		@Override public byte[] readByteArray( long byteCount ) throws IOException{
			return originalSource.readByteArray( byteCount );
		}
		
		@Override public int read( @NonNull byte[] sink ) throws IOException{
			return originalSource.read( sink );
		}
		
		@Override public void readFully( @NonNull byte[] sink ) throws IOException{
			originalSource.readFully( sink );
		}
		
		@Override
		public int read( @NonNull byte[] sink, int offset, int byteCount ) throws IOException{
			return originalSource.read( sink, offset, byteCount );
		}
		
		@Override public void readFully( @NonNull Buffer sink, long byteCount ) throws IOException{
			originalSource.readFully( sink, byteCount );
		}
		
		@Override public long readAll( @NonNull Sink sink ) throws IOException{
			return originalSource.readAll( sink );
		}
		
		@Override public String readUtf8() throws IOException{
			return originalSource.readUtf8();
		}
		
		@Override public String readUtf8( long byteCount ) throws IOException{
			return originalSource.readUtf8( byteCount );
		}
		
		@Nullable @Override public String readUtf8Line() throws IOException{
			return originalSource.readUtf8Line();
		}
		
		@Override public String readUtf8LineStrict() throws IOException{
			return originalSource.readUtf8LineStrict();
		}
		
		@Override public String readUtf8LineStrict( long limit ) throws IOException{
			return originalSource.readUtf8LineStrict( limit );
		}
		
		@Override public int readUtf8CodePoint() throws IOException{
			return originalSource.readUtf8CodePoint();
		}
		
		@Override public String readString( @NonNull Charset charset ) throws IOException{
			return originalSource.readString( charset );
		}
		
		@Override
		public String readString( long byteCount, @NonNull Charset charset ) throws IOException{
			return originalSource.readString( byteCount, charset );
		}
		
		@Override public long indexOf( byte b ) throws IOException{
			return originalSource.indexOf( b );
		}
		
		@Override public long indexOf( byte b, long fromIndex ) throws IOException{
			return originalSource.indexOf( b, fromIndex );
		}
		
		@Override public long indexOf( byte b, long fromIndex, long toIndex ) throws IOException{
			return originalSource.indexOf( b, fromIndex, toIndex );
		}
		
		@Override public long indexOf( @NonNull ByteString bytes ) throws IOException{
			return originalSource.indexOf( bytes );
		}
		
		@Override
		public long indexOf( @NonNull ByteString bytes, long fromIndex ) throws IOException{
			return originalSource.indexOf( bytes, fromIndex );
		}
		
		@Override public long indexOfElement( @NonNull ByteString targetBytes ) throws IOException{
			return originalSource.indexOfElement( targetBytes );
		}
		
		@Override
		public long indexOfElement( @NonNull ByteString targetBytes, long fromIndex ) throws IOException{
			return originalSource.indexOfElement( targetBytes, fromIndex );
		}
		
		@Override
		public boolean rangeEquals( long offset, @NonNull ByteString bytes ) throws IOException{
			return originalSource.rangeEquals( offset, bytes );
		}
		
		@Override
		public boolean rangeEquals( long offset, @NonNull ByteString bytes, int bytesOffset, int byteCount ) throws IOException{
			return originalSource.rangeEquals( offset, bytes, bytesOffset, byteCount );
		}
		
		@Override public InputStream inputStream(){
			return originalSource.inputStream();
		}
		
		@Override public long read( @NonNull Buffer sink, long byteCount ) throws IOException{
			return originalSource.read( sink, byteCount );
		}
		
		@Override public Timeout timeout(){
			return originalSource.timeout();
		}
		
		@Override public void close() throws IOException{
			originalSource.close();
		}
	}
}
