package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.api.entity.CustomEmoji;

@SuppressWarnings("WeakerAccess")
public class CustomEmojiLister {
	
	private static final LogCategory log = new LogCategory( "CustomEmojiLister" );
	
	static final int CACHE_MAX = 50;
	
	static final long ERROR_EXPIRE = ( 60000L * 5 );
	
	private static long getNow(){
		return SystemClock.elapsedRealtime();
	}
	
	////////////////////////////////
	// エラーキャッシュ
	
	final ConcurrentHashMap< String, Long > cache_error = new ConcurrentHashMap<>();
	
	////////////////////////////////
	// 成功キャッシュ
	
	static class CacheItem {
		
		@NonNull String instance;
		
		@NonNull CustomEmoji.List list;
		
		// 参照された時刻
		long time_used;

		// ロードした時刻
		long time_update;
		
		CacheItem( @NonNull String instance, @NonNull CustomEmoji.List list ){
			this.instance = instance;
			this.list = list;
			time_used = time_update = getNow();
		}
	}
	
	final ConcurrentHashMap< String, CacheItem > cache = new ConcurrentHashMap<>();
	
	////////////////////////////////
	// リクエスト
	
	public interface Callback {
		void onListLoadComplete( CustomEmoji.List list );
	}
	
	static class Request {
		@NonNull String instance;
		@NonNull Callback callback;
		
		public Request( @NonNull String instance, @NonNull Callback callback ){
			this.instance = instance;
			this.callback = callback;
		}
	}
	
	final ConcurrentLinkedQueue< Request > queue = new ConcurrentLinkedQueue<>();
	
	////////////////////////////////
	
	@Nullable
	public CustomEmoji.List get( @NonNull String instance, @NonNull Callback callback ){

		if( TextUtils.isEmpty( instance )) return null;

		instance = instance.toLowerCase();
		
		synchronized( cache ){
			long now = getNow();
			
			// 成功キャッシュ
			CacheItem item = cache.get( instance );
			if( item == null ){
				// エラーキャッシュ
				Long time_error = cache_error.get( instance );
				if( time_error != null && now < time_error + ERROR_EXPIRE ){
					// エラーキャッシュ期間の中にいる
				}else{
					addRequest( instance,callback );
				}
				return null;
			}else{
				if( now - item.time_update >= ERROR_EXPIRE ){
					addRequest( instance, callback );
				}
				item.time_used = now;
				return item.list;
			}
		}
	}
	
	private void addRequest(@NonNull String instance, @NonNull Callback callback){
		queue.add( new Request( instance, callback ) );
		worker.notifyEx();
	}
	
	////////////////////////////////
	
	Context context;
	Handler handler;
	
	public CustomEmojiLister( Context context ){
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
					waitEx( 86400000L );
					continue;
				}
				
				long now = getNow();
				synchronized( cache ){
					
					// 成功キャッシュ
					CacheItem item = cache.get( request.instance );
					if( item != null ){
						fireCallback( request.callback, item.list );

						if( now - item.time_update <= ERROR_EXPIRE ){
							continue;
						}
					}else{
						// エラーキャッシュ
						Long time_error = cache_error.get( request.instance );
						if( time_error != null && now < time_error + ERROR_EXPIRE ){
							continue;
						}
					}
					
					sweep_cache();
				}
				
				CustomEmoji.List list = null;
				try{
					String data = App1.getHttpCachedString("https://"+ request.instance + "/api/v1/custom_emojis");
					if( data != null ){
						list = decodeEmojiList( data, request.instance );
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
				
				synchronized( cache ){
					if( list != null ){
						CacheItem item = cache.get( request.instance );
						if( item == null ){
							item = new CacheItem( request.instance, list );
							cache.put( request.instance, item );
						}else{
							item.list = list;
							item.time_update = getNow();
						}
						fireCallback( request.callback, list );
					}else{
						cache_error.put( request.instance, getNow() );
					}
				}
			}
		}
		
		private void fireCallback( final Callback callback, final CustomEmoji.List list ){
			handler.post( new Runnable() {
				@Override public void run(){
					callback.onListLoadComplete( list );
				}
			} );
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
				for( int i = list.size() - 1 ; i >= CACHE_MAX - 1 ; -- i ){
					CacheItem item = list.get( i );
					// あまり古くないなら無理に掃除しない
					if( now - item.time_used < 1000L ) break;
					cache.remove( item.instance );
				}
			}
			
		}
		
		@Nullable private CustomEmoji.List decodeEmojiList( String data, String instance ){
			try{
				JSONArray array = new JSONArray( data );
				return CustomEmoji.parseList( array );
			}catch( Throwable ex ){
				log.e( ex, "decodeEmojiList failed. instance=%s", instance );
				return null;
			}
		}
		
	}
}
