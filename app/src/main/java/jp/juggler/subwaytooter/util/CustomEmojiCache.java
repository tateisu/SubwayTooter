package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.apng.APNGFrames;
import okhttp3.Call;
import okhttp3.Response;

@SuppressWarnings("WeakerAccess")
public class CustomEmojiCache {
	
	private static final LogCategory log = new LogCategory( "CustomEmojiCache" );


	static final int CACHE_MAX = 512; // 使用中のビットマップは掃除しないので、頻度によってはこれより多くなることもある
	static final long ERROR_EXPIRE = ( 60000L * 10 );
	
	
	private static long getNow(){
		return SystemClock.elapsedRealtime();
	}
	
	////////////////////////////////
	// エラーキャッシュ
	
	final ConcurrentHashMap< String, Long > cache_error = new ConcurrentHashMap<>();
	
	////////////////////////////////
	// 成功キャッシュ
	
	static class CacheItem {
		
		@NonNull String url;
		
		@NonNull APNGFrames frames;
		
		// 参照された時刻
		long time_used;
		
		CacheItem( @NonNull String url, @NonNull APNGFrames frames ){
			this.url = url;
			this.frames = frames;
			time_used = getNow();
		}
	}
	
	final ConcurrentHashMap< String, CacheItem > cache = new ConcurrentHashMap<>();
	
	////////////////////////////////
	// リクエスト
	
	public interface Callback {
		void onComplete( APNGFrames b );
	}
	
	static class Request {
		@NonNull String url;
		@NonNull Callback callback;
		
		public Request( @NonNull String url, @NonNull Callback callback ){
			this.url = url;
			this.callback = callback;
		}
	}
	
	final ConcurrentLinkedQueue< Request > queue = new ConcurrentLinkedQueue<>();
	
	////////////////////////////////
	
	@Nullable public APNGFrames get( @NonNull String url, @NonNull Callback callback ){
		
		synchronized( cache ){
			long now = getNow();
			
			// 成功キャッシュ
			CacheItem item = cache.get( url );
			if( item != null ){
				item.time_used = now;
				return item.frames;
			}
			
			// エラーキャッシュ
			Long time_error = cache_error.get( url );
			if( time_error != null && now < time_error + ERROR_EXPIRE ){
				return null;
			}
		}
		queue.add( new Request( url, callback ) );
		worker.notifyEx();
		return null;
	}
	
	////////////////////////////////
	
	Context context;
	Handler handler;
	
	public CustomEmojiCache( Context context ){
		this.context = context;
		this.handler = new Handler( context.getMainLooper() );
		this.worker = new Worker();
		worker.start();
	}
	
	Worker worker;
	
	class Worker extends WorkerBase {
		final AtomicBoolean bCancelled = new AtomicBoolean( false );
		
		@Override public void cancel(){
		}
		
		@Override public void run(){
			while( ! bCancelled.get() ){
				Request request = queue.poll();
				if( request == null ){
					waitEx(86400000L);
					continue;
				}

				long now = getNow();
				synchronized( cache ){
					
					// 成功キャッシュ
					CacheItem item = cache.get( request.url );
					if( item != null ){
						fireCallback( request.callback, item.frames );
						continue;
					}
					
					// エラーキャッシュ
					Long time_error = cache_error.get( request.url );
					if( time_error != null && now < time_error + ERROR_EXPIRE ){
						continue;
					}
					
					sweep_cache();
				}
				
				APNGFrames frames = null;
				try{
					byte[] data = getHttp( request.url );
					if( data != null ){
						frames = decode( data, request.url );
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
				
				synchronized( cache ){
					if( frames != null ){
						CacheItem item = cache.get( request.url );
						if( item == null ){
							item = new CacheItem( request.url, frames );
							cache.put( request.url, item );
						}else{
							item.frames.dispose();
							item.frames = frames;
						}
						fireCallback( request.callback, frames );
					}else{
						cache_error.put( request.url, getNow() );
					}
				}
			}
		}
		
		private void fireCallback( final Callback callback, final APNGFrames frames ){
			handler.post( new Runnable() {
				@Override public void run(){
					callback.onComplete( frames );
				}
			} );
		}
		
		private byte[] getHttp( String url ){
			Response response;
			try{
				okhttp3.Request.Builder request_builder = new okhttp3.Request.Builder();
				request_builder.url( url );
				Call call = App1.ok_http_client2.newCall( request_builder.build() );
				response = call.execute();
			}catch( Throwable ex ){
				log.e( ex, "getHttp network error." );
				return null;
			}
			
			if( ! response.isSuccessful() ){
				log.e( "getHttp response error. %s", response );
				return null;
			}
			
			try{
				//noinspection ConstantConditions
				return response.body().bytes();
			}catch( Throwable ex ){
				log.e( ex, "getHttp content error." );
				return null;
			}
		}
		
		private void sweep_cache(){
			
			// キャッシュの掃除
			if( cache.size() >= CACHE_MAX ){
				ArrayList< CacheItem > list = new ArrayList<>();
				list.addAll( cache.values() );
				
				// 降順ソート
				Collections.sort( list, new Comparator< CacheItem >() {
					@Override
					public int compare( CacheItem a, CacheItem b ){
						long delta = b.time_used - a.time_used;
						return delta < 0L ? - 1 : delta > 0L ? 1 : 0;
					}
				} );
				// 古い物から順にチェック
				long now = getNow();
				for( int i = list.size()-1; i>= CACHE_MAX-1; --i){
					CacheItem item = list.get( i );
					// あまり古くないなら無理に掃除しない
					if( now - item.time_used < 1000L ) break;
					cache.remove( item.url );
					item.frames.dispose();
				}
			}
			
		}

		@Nullable private APNGFrames decode( byte[] data, String url ){
			try{
				return APNGFrames.parseAPNG( context, new ByteArrayInputStream( data ) ,64 );
			}catch(Throwable ex){
				log.e(ex,"PNG decode failed. %s",url);
			}
			return null;
		}
	}
}
