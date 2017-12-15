package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.App1;

@SuppressWarnings("WeakerAccess")
public class CustomEmojiCache {
	
	private static final LogCategory log = new LogCategory( "CustomEmojiCache" );
	
	static final boolean DEBUG = false;
	
	static final int CACHE_MAX = 512; // 使用中のビットマップは掃除しないので、頻度によってはこれより多くなることもある
	static final long ERROR_EXPIRE = ( 60000L * 10 );
	
	private static long getNow(){
		return SystemClock.elapsedRealtime();
	}
	
	////////////////////////////////
	// エラーキャッシュ
	
	final ConcurrentHashMap< String, Long > cache_error = new ConcurrentHashMap<>();
	
	// カラムのリロードボタンを押したタイミングでエラーキャッシュをクリアする
	public void clearErrorCache(){
		cache_error.clear();
	}
	
	////////////////////////////////
	// 成功キャッシュ
	
	static class CacheItem {
		
		@NonNull final String url;
		
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
		void onAPNGLoadComplete();
	}
	
	static class Request {
		@NonNull final WeakReference< Object > refTarget;
		@NonNull final String url;
		@NonNull final Callback callback;
		
		public Request( @NonNull Object target_tag, @NonNull String url, @NonNull Callback callback ){
			this.refTarget = new WeakReference<>( target_tag );
			this.url = url;
			this.callback = callback;
		}
	}
	
	final LinkedList< Request > queue = new LinkedList<>();
	
	////////////////////////////////
	
	public void cancelRequest( @NonNull Object target_tag ){
		synchronized( queue ){
			Iterator< Request > it = queue.iterator();
			while( it.hasNext() ){
				Request request = it.next();
				Object tag = request.refTarget.get();
				if( tag == null || tag == target_tag ){
					it.remove();
				}
			}
		}
	}
	
	@Nullable
	public APNGFrames get( @NonNull Object target_tag, @NonNull String url, @NonNull Callback callback ){
		
		cancelRequest( target_tag );
		
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
		synchronized( queue ){
			queue.addLast( new Request( target_tag, url, callback ) );
		}
		worker.notifyEx();
		return null;
	}
	
	////////////////////////////////
	
	final Context context;
	final Handler handler;
	
	public CustomEmojiCache( Context context ){
		this.context = context;
		this.handler = new Handler( context.getMainLooper() );
		this.worker = new Worker();
		worker.start();
	}
	
	final Worker worker;
	
	class Worker extends WorkerBase {
		final AtomicBoolean bCancelled = new AtomicBoolean( false );
		
		@Override public void cancel(){
		}
		
		@Override public void run(){
			while( ! bCancelled.get() ){
				Request request;
				int req_size;
				synchronized( queue ){
					request = queue.isEmpty() ? null : queue.removeFirst();
					//noinspection UnusedAssignment
					req_size = queue.size();
				}
				
				if( request == null ){
					if(DEBUG) log.d( "wait. req_size=%d", req_size );
					waitEx( 86400000L );
					continue;
				}
				
				if( request.refTarget.get() == null ){
					continue;
				}
				
				long now = getNow();
				int cache_size;
				synchronized( cache ){
					
					// 成功キャッシュ
					CacheItem item = cache.get( request.url );
					if( item != null ){
						fireCallback( request.callback );
						continue;
					}
					
					// エラーキャッシュ
					Long time_error = cache_error.get( request.url );
					if( time_error != null && now < time_error + ERROR_EXPIRE ){
						continue;
					}
					
					sweep_cache();
					
					//noinspection UnusedAssignment
					cache_size = cache.size();
				}
				if(DEBUG) log.d( "start get image. req_size=%d, cache_size=%d url=%s", req_size, cache_size, request.url );
				
				APNGFrames frames = null;
				try{
					byte[] data = App1.getHttpCached( request.url );
					if( data == null ){
						log.e("get failed. url=%s",request.url );
					}else{
						frames = decodeAPNG( data, request.url );
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
						fireCallback( request.callback );
					}else{
						cache_error.put( request.url, getNow() );
					}
				}
			}
		}
		
		private void fireCallback( final Callback callback ){
			handler.post( new Runnable() {
				@Override public void run(){
					callback.onAPNGLoadComplete();
				}
			} );
		}
		
		private void sweep_cache(){
			
			// キャッシュの掃除
			if( cache.size() >= CACHE_MAX + 64 ){
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
					cache.remove( item.url );
					item.frames.dispose();
				}
			}
			
		}
		
		@Nullable private APNGFrames decodeAPNG( byte[] data, String url ){
			try{
				APNGFrames frames = APNGFrames.parseAPNG( new ByteArrayInputStream( data ), 64 );
				if( frames == null ){
					if(DEBUG) log.d("parseAPNG returns null.");
					// fall thru
				}else if( frames.isSingleFrame() ){
					if(DEBUG) log.d( "parseAPNG returns single frame." );
					// mastodonのstatic_urlが返すPNG画像はAPNGだと透明になってる場合がある。BitmapFactoryでデコードしなおすべき
					frames.dispose();
					// fall thru
				}else{
					return frames;
				}
			}catch( Throwable ex ){
				log.e( ex, "PNG decode failed. %s ", url );
				// PngFeatureException Interlaced images are not yet supported
			}
			
			// 通常のビットマップでのロードを試みる
			try{
				Bitmap b = decodeBitmap( data, 128 );
				if( b != null ){
					if(DEBUG) log.d("bitmap decoded.");
					return new APNGFrames( b );
				}else{
					log.e( "Bitmap decode returns null. %s", url );
				}
			}catch( Throwable ex ){
				log.e( ex, "Bitmap decode failed. %s", url );
			}
			return null;
		}
		
		private final BitmapFactory.Options options = new BitmapFactory.Options();
		
		private Bitmap decodeBitmap( byte[] data, @SuppressWarnings("SameParameterValue") int pixel_max ){
			options.inJustDecodeBounds = true;
			options.inScaled = false;
			options.outWidth = 0;
			options.outHeight = 0;
			BitmapFactory.decodeByteArray( data, 0, data.length, options );
			int w = options.outWidth;
			int h = options.outHeight;
			if( w <= 0 || h <= 0 ){
				log.e( "can't decode bounds." );
				return null;
			}
			int bits = 0;
			while( w > pixel_max || h > pixel_max ){
				++ bits;
				w >>= 1;
				h >>= 1;
			}
			options.inJustDecodeBounds = false;
			options.inSampleSize = 1 << bits;
			return BitmapFactory.decodeByteArray( data, 0, data.length, options );
		}
	}
}
