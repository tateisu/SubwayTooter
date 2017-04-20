package jp.juggler.subwaytooter.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import android.os.SystemClock;

//! リトライつきHTTPクライアント
public class HTTPClient {
	
	static final boolean debug_http = false;
	
	public String[] extra_header;
	public int rcode;
	public boolean allow_error = false;
	public Map< String, List< String > > response_header;
	public HashMap< String, String > cookie_pot;
	public int max_try;
	@SuppressWarnings("unused")
	public int timeout_dns = 1000 * 3;
	public int timeout_connect;
	public int timeout_read;
	public String caption;
	public boolean silent_error = false;
	public long time_expect_connect = 3000;
	public boolean bDisableKeepAlive = false;
	
	@SuppressWarnings("unused")
	public HTTPClient( int timeout, int max_try, String caption, CancelChecker cancel_checker ){
		this.cancel_checker = cancel_checker;
		this.timeout_connect = this.timeout_read = timeout;
		this.max_try = max_try;
		this.caption = caption;
	}
	
	@SuppressWarnings("unused")
	public HTTPClient( int timeout, int max_try, String caption, final AtomicBoolean _cancel_checker ){
		this.cancel_checker = new CancelChecker() {
			@Override
			public boolean isCancelled(){
				return _cancel_checker.get();
			}
		};
		this.timeout_connect = this.timeout_read = timeout;
		this.max_try = max_try;
		this.caption = caption;
	}
	
	@SuppressWarnings("unused")
	public void setCookiePot( boolean enabled ){
		if( enabled == ( cookie_pot != null ) ) return;
		cookie_pot = ( enabled ? new HashMap< String, String >() : null );
	}
	
	///////////////////////////////
	// デフォルトの入力ストリームハンドラ
	
	HTTPClientReceiver default_receiver = new HTTPClientReceiver() {
		byte[] buf = new byte[ 2048 ];
		ByteArrayOutputStream bao = new ByteArrayOutputStream( 0 );
		
		public byte[] onHTTPClientStream( LogCategory log, CancelChecker cancel_checker, InputStream in, int content_length ){
			try{
				bao.reset();
				for( ; ; ){
					if( cancel_checker.isCancelled() ){
						if( debug_http ) log.w(
							"[%s,read]cancelled!"
							, caption
						);
						return null;
					}
					int delta = in.read( buf );
					if( delta <= 0 ) break;
					bao.write( buf, 0, delta );
				}
				return bao.toByteArray();
			}catch( Throwable ex ){
				log.e(
					"[%s,read] %s:%s"
					, caption
					, ex.getClass().getSimpleName()
					, ex.getMessage()
				);
			}
			return null;
		}
	};
	
	///////////////////////////////
	// 別スレッドからのキャンセル処理
	
	public CancelChecker cancel_checker;
	volatile Thread io_thread;
	
	@SuppressWarnings("unused")
	public boolean isCancelled(){
		return cancel_checker.isCancelled();
	}
	
	@SuppressWarnings("unused")
	public synchronized void cancel( LogCategory log ){
		Thread t = io_thread;
		if( t == null ) return;
		log.i(
			"[%s,cancel] %s"
			, caption
			, t
		);
		try{
			t.interrupt();
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	public byte[] post_content = null;
	public String post_content_type = null;
	public boolean quit_network_error = false;
	
	public String last_error = null;
	public long mtime;
	
	public static String user_agent = null;
	
	///////////////////////////////
	// HTTPリクエスト処理
	
	@SuppressWarnings("unused")
	public byte[] getHTTP( LogCategory log,  String url ){
		return getHTTP( log,  url, default_receiver );
	}
	
	@SuppressWarnings("ConstantConditions")
	public byte[] getHTTP( LogCategory log, String url, HTTPClientReceiver receiver ){

//		// http://android-developers.blogspot.jp/2011/09/androids-http-clients.html
//		// HTTP connection reuse which was buggy pre-froyo
//		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO ){
//			System.setProperty( "http.keepAlive", "false" );
//		}
		
		try{
			synchronized( this ){
				this.io_thread = Thread.currentThread();
			}
			URL urlObject;
			try{
				urlObject = new URL( url );
			}catch( MalformedURLException ex ){
				log.d( "[%s,init] bad url %s %s", caption, url, ex.getMessage() );
				return null;
			}
/*
			// desire だと、どうもリソースリークしているようなので行わないことにした。
			// DNSを引けるか確認する
			if(debug_http) Log.d(logcat,"check hostname "+url);
			if( !checkDNSResolver(urlObject) ){
				Log.w(logcat,"broken name resolver");
				return null;
			}
*/
			long timeStart = SystemClock.elapsedRealtime();
			for( int nTry = 0 ; nTry < max_try ; ++ nTry ){
				long t1, t2, lap;
				try{
					this.rcode = 0;
					// キャンセルされたか確認
					if( cancel_checker.isCancelled() ) return null;
					
					// http connection
					HttpURLConnection conn = (HttpURLConnection) urlObject.openConnection();
					
					if( user_agent != null ) conn.setRequestProperty( "User-Agent", user_agent );
					
					// 追加ヘッダがあれば記録する
					if( extra_header != null ){
						for( int i = 0 ; i < extra_header.length ; i += 2 ){
							conn.addRequestProperty( extra_header[ i ], extra_header[ i + 1 ] );
							if( debug_http )
								log.d( "%s: %s", extra_header[ i ], extra_header[ i + 1 ] );
						}
					}
					if( bDisableKeepAlive ){
						conn.setRequestProperty( "Connection", "close" );
					}
					// クッキーがあれば指定する
					if( cookie_pot != null ){
						StringBuilder sb = new StringBuilder();
						for( Map.Entry< String, String > pair : cookie_pot.entrySet() ){
							if( sb.length() > 0 ) sb.append( "; " );
							sb.append( pair.getKey() );
							sb.append( '=' );
							sb.append( pair.getValue() );
						}
						conn.addRequestProperty( "Cookie", sb.toString() );
					}
					
					// リクエストを送ってレスポンスの頭を読む
					try{
						t1 = SystemClock.elapsedRealtime();
						if( debug_http )
							log.d( "[%s,connect] start %s", caption, toHostName( url ) );
						conn.setDoInput( true );
						conn.setConnectTimeout( this.timeout_connect );
						conn.setReadTimeout( this.timeout_read );
						if( post_content == null ){
							conn.setDoOutput( false );
							conn.connect();
						}else{
							conn.setDoOutput( true );
//							if( Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ){
//								conn.setRequestProperty( "Content-Length", Integer.toString( post_content.length ) );
//							}
							if( post_content_type != null ){
								conn.setRequestProperty( "Content-Type", post_content_type );
							}
							OutputStream out = conn.getOutputStream();
							out.write( post_content );
							out.flush();
							out.close();
						}
						// http://stackoverflow.com/questions/12931791/java-io-ioexception-received-authentication-challenge-is-null-in-ics-4-0-3
						int rcode;
						try{
							// Will throw IOException if server responds with 401.
							rcode = this.rcode = conn.getResponseCode();
						}catch( IOException ex ){
							String sv = ex.getMessage();
							if( sv != null && sv.contains( "authentication challenge" ) ){
								log.d( "retry getResponseCode!" );
								// Will return 401, because now connection has the correct internal state.
								rcode = this.rcode = conn.getResponseCode();
							}else{
								throw ex;
							}
						}
						mtime = conn.getLastModified();
						t2 = SystemClock.elapsedRealtime();
						lap = t2 - t1;
						if( lap > time_expect_connect )
							log.d( "[%s,connect] time=%sms %s", caption, lap, toHostName( url ) );
						
						// ヘッダを覚えておく
						response_header = conn.getHeaderFields();
						
						// クッキーが来ていたら覚える
						if( cookie_pot != null ){
							String v = conn.getHeaderField( "set-cookie" );
							if( v != null ){
								int pos = v.indexOf( '=' );
								cookie_pot.put( v.substring( 0, pos ), v.substring( pos + 1 ) );
							}
						}
						
						if( rcode >= 500 ){
							if( ! silent_error )
								log.e( "[%s,connect] temporary error %d", caption, rcode );
							last_error = String.format( "(HTTP error %d)", rcode );
							continue;
						}else if( ! allow_error && rcode >= 300 ){
							if( ! silent_error )
								log.e( "[%s,connect] permanent error %d", caption, rcode );
							last_error = String.format( "(HTTP error %d)", rcode );
							return null;
						}
						
					}catch( UnknownHostException ex ){
						rcode = 0;
						last_error = ex.getClass().getSimpleName();
						// このエラーはリトライしてもムリ
						conn.disconnect();
						return null;
					}catch( SSLHandshakeException ex ){
						last_error = String.format( "SSL handshake error. Please check device's date and time. (%s %s)", ex.getClass().getSimpleName(), ex.getMessage() );
						
						if( ! silent_error ){
							log.e( "[%s,connect] %s"
								, caption
								, last_error
							);
							if( ex.getMessage() == null ){
								ex.printStackTrace();
							}
						}
						this.rcode = - 1;
						return null;
					}catch( Throwable ex ){
						last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
						
						if( ! silent_error ){
							log.e( "[%s,connect] %s"
								, caption
								, last_error
							);
							if( ex.getMessage() == null ){
								ex.printStackTrace();
							}
						}
						
						// 時計が合ってない場合は Received authentication challenge is null なエラーが出るらしい
						// getting a 401 Unauthorized error, due to a malformed Authorization header.
						if( ex instanceof IOException
							&& ex.getMessage() != null
							&& ex.getMessage().contains( "authentication challenge" )
							){
							ex.printStackTrace();
							log.d( "Please check device's date and time." );
							this.rcode = 401;
							return null;
						}else if( ex instanceof ConnectException
							&& ex.getMessage() != null
							&& ex.getMessage().contains( "ENETUNREACH" )
							){
							// このアプリの場合は network unreachable はリトライしない
							return null;
						}
						if( quit_network_error ) return null;
						
						// 他のエラーはリトライしてみよう。キャンセルされたなら次のループの頭で抜けるはず
						conn.disconnect();
						continue;
					}
					InputStream in = null;
					try{
						if( debug_http ) if( rcode != 200 )
							log.d( "[%s,read] start status=%d", caption, this.rcode );
						try{
							in = conn.getInputStream();
						}catch( FileNotFoundException ex ){
							in = conn.getErrorStream();
						}
						if( in == null ){
							log.d( "[%s,read] missing input stream. rcode=%d", caption, rcode );
							return null;
						}
						int content_length = conn.getContentLength();
						byte[] data = receiver.onHTTPClientStream( log, cancel_checker, in, content_length );
						if( data == null ) continue;
						if( data.length > 0 ){
							if( nTry > 0 ) log.w( "[%s] OK. retry=%d,time=%dms"
								, caption
								, nTry
								, SystemClock.elapsedRealtime() - timeStart
							);
							return data;
						}
						if( ! cancel_checker.isCancelled()
							&& ! silent_error
							){
							log.w(
								"[%s,read] empty data."
								, caption
							);
						}
					}finally{
						try{
							if( in != null ) in.close();
						}catch( Throwable ignored ){
						}
						conn.disconnect();
					}
				}catch( Throwable ex ){
					last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
					ex.printStackTrace();
				}
			}
			if( ! silent_error ) log.e( "[%s] fail. try=%d. rcode=%d", caption, max_try, rcode );
		}catch( Throwable ex ){
			ex.printStackTrace();
			last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
		}finally{
			synchronized( this ){
				io_thread = null;
			}
		}
		return null;
	}
	
	//! HTTPレスポンスのヘッダを読む
	@SuppressWarnings("unused")
	public void dump_res_header( LogCategory log ){
		log.d( "HTTP code %d", rcode );
		if( response_header != null ){
			for( Map.Entry< String, List< String > > entry : response_header.entrySet() ){
				String k = entry.getKey();
				for( String v : entry.getValue() ){
					log.d( "%s: %s", k, v );
				}
			}
		}
	}
	
	@SuppressWarnings({ "unused", "ConstantConditions" })
	public String get_cache( LogCategory log, File file, String url ){
		String last_error = null;
		for( int nTry = 0 ; nTry < 10 ; ++ nTry ){
			if( cancel_checker.isCancelled() ) return "cancelled";
			
			long now = System.currentTimeMillis();
			try{
				HttpURLConnection conn = (HttpURLConnection) new URL( url ).openConnection();
				try{
					conn.setConnectTimeout( 1000 * 10 );
					conn.setReadTimeout( 1000 * 10 );
					if( file.exists() ) conn.setIfModifiedSince( file.lastModified() );
					conn.connect();
					this.rcode = conn.getResponseCode();
					if( rcode == 304 ){
						if( file.exists() ){
							//noinspection ResultOfMethodCallIgnored
							file.setLastModified( now );
						}
						return null;
					}
					if( rcode == 200 ){
						InputStream in = conn.getInputStream();
						try{
							ByteArrayOutputStream bao = new ByteArrayOutputStream();
							try{
								byte[] tmp = new byte[ 4096 ];
								for( ; ; ){
									if( cancel_checker.isCancelled() ) return "cancelled";
									int delta = in.read( tmp, 0, tmp.length );
									if( delta <= 0 ) break;
									bao.write( tmp, 0, delta );
								}
								byte[] data = bao.toByteArray();
								if( data != null ){
									FileOutputStream out = new FileOutputStream( file );
									try{
										out.write( data );
										return null;
									}finally{
										try{
											out.close();
										}catch( Throwable ignored ){
										}
									}
								}
							}finally{
								try{
									bao.close();
								}catch( Throwable ignored ){
								}
							}
						}catch( Throwable ex ){
							ex.printStackTrace();
							if( file.exists() ){
								//noinspection ResultOfMethodCallIgnored
								file.delete();
							}
							last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
						}finally{
							try{
								in.close();
							}catch( Throwable ignored ){
							}
						}
						break;
					}
					log.e( "http error: %d %s", rcode, url );
					if( rcode >= 400 && rcode < 500 ){
						last_error = String.format( "HTTP error %d", rcode );
						break;
					}
				}finally{
					conn.disconnect();
				}
				// retry ?
			}catch( MalformedURLException ex ){
				ex.printStackTrace();
				last_error = String.format( "bad URL:%s", ex.getMessage() );
				break;
			}catch( IOException ex ){
				ex.printStackTrace();
				last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
			}
		}
		return last_error;
	}
	/////////////////////////////////////////////////////////
	// 複数URLに対応したリクエスト処理
	
	public boolean no_cache = false;
	
	@SuppressWarnings({ "unused", "ConstantConditions" })
	public File getFile( LogCategory log, File cache_dir, String[] url_list, File _file ){
		//
		if( url_list == null || url_list.length < 1 ){
			setError( 0, "missing url argument." );
			return null;
		}
		// make cache_dir
		if( cache_dir != null ){
			if( ! cache_dir.mkdirs() && ! cache_dir.isDirectory() ){
				setError( 0, "can not create cache_dir" );
				return null;
			}
		}
		for( int nTry = 0 ; nTry < 10 ; ++ nTry ){
			if( cancel_checker.isCancelled() ){
				setError( 0, "cancelled." );
				return null;
			}
			//
			String url = url_list[ nTry % url_list.length ];
			File file = ( _file != null ? _file : new File( cache_dir, Utils.url2name( url ) ) );
			
			//
			//noinspection TryWithIdenticalCatches
			try{
				HttpURLConnection conn = (HttpURLConnection) new URL( url ).openConnection();
				if( user_agent != null ) conn.setRequestProperty( "User-Agent", user_agent );
				try{
					conn.setConnectTimeout( 1000 * 10 );
					conn.setReadTimeout( 1000 * 10 );
					if( ! no_cache && file.exists() )
						conn.setIfModifiedSince( file.lastModified() );
					conn.connect();
					this.rcode = conn.getResponseCode();
					
					if( debug_http ) if( rcode != 200 ) log.d( "getFile %s %s", rcode, url );
					
					// 変更なしの場合
					if( rcode == 304 ){
						/// log.d("304: %s",file);
						return file;
					}
					
					// 変更があった場合
					if( rcode == 200 ){
						// メッセージボディをファイルに保存する
						InputStream in = null;
						FileOutputStream out = null;
						try{
							byte[] tmp = new byte[ 4096 ];
							in = conn.getInputStream();
							out = new FileOutputStream( file );
							for( ; ; ){
								if( cancel_checker.isCancelled() ){
									setError( 0, "cancelled" );
									if( file.exists() ){
										//noinspection ResultOfMethodCallIgnored
										file.delete();
									}
									return null;
								}
								int delta = in.read( tmp, 0, tmp.length );
								if( delta <= 0 ) break;
								out.write( tmp, 0, delta );
							}
							out.close();
							out = null;
							//
							long mtime = conn.getLastModified();
							if( mtime >= 1000 ){
								
								//noinspection ResultOfMethodCallIgnored
								file.setLastModified( mtime );
							}
							//
							/// log.d("200: %s",file);
							return file;
						}catch( Throwable ex ){
							setError( ex );
						}finally{
							try{
								if( in != null ) in.close();
							}catch( Throwable ignored ){
							}
							try{
								if( out != null ) out.close();
							}catch( Throwable ignored ){
							}
						}
						// エラーがあったらリトライ
						if( file.exists() ){
							//noinspection ResultOfMethodCallIgnored
							file.delete();
						}
						
						continue;
					}
					
					// その他、よく分からないケース
					log.e( "http error: %d %s", rcode, url );
					
					// URLが複数提供されている場合、404エラーはリトライ対象
					if( rcode == 404 && url_list.length > 1 ){
						last_error = String.format( "(HTTP error %d)", rcode );
						continue;
					}
					
					// それ以外の永続エラーはリトライしない
					if( rcode >= 400 && rcode < 500 ){
						last_error = String.format( "(HTTP error %d)", rcode );
						break;
					}
				}finally{
					conn.disconnect();
				}
				// retry ?
			}catch( UnknownHostException ex ){
				rcode = 0;
				last_error = ex.getClass().getSimpleName();
				// このエラーはリトライしてもムリ
				break;
			}catch( MalformedURLException ex ){
				setError( ex );
				break;
			}catch( SocketTimeoutException ex ){
				setError_silent( log, ex );
			}catch( ConnectException ex ){
				setError_silent( log, ex );
			}catch( IOException ex ){
				setError( ex );
			}
		}
		return null;
	}
	
	///////////////////////////////////////////////////////////////////
	
	public boolean setError( int i, String string ){
		rcode = i;
		last_error = string;
		return false;
	}
	
	public boolean setError( Throwable ex ){
		ex.printStackTrace();
		rcode = 0;
		last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
		return false;
	}
	
	public boolean setError_silent( LogCategory log, Throwable ex ){
		log.d( "ERROR: %s %s", ex.getClass().getName(), ex.getMessage() );
		rcode = 0;
		last_error = String.format( "%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
		return false;
	}
	
	//! HTTPレスポンスのヘッダを読む
	public String getHeaderString( String key, String defval ){
		List< String > list = response_header.get( key );
		if( list != null && list.size() > 0 ){
			String v = list.get( 0 );
			if( v != null ) return v;
		}
		return defval;
	}
	
	//! HTTPレスポンスのヘッダを読む
	@SuppressWarnings("unused")
	public int getHeaderInt( String key, int defval ){
		String v = getHeaderString( key, null );
		try{
			return Integer.parseInt( v, 10 );
		}catch( Throwable ex ){
			return defval;
		}
	}
	
	static Pattern reHostName = Pattern.compile( "//([^/]+)/" );
	
	static String toHostName( String url ){
		Matcher m = reHostName.matcher( url );
		if( m.find() ) return m.group( 1 );
		return url;
	}
}
