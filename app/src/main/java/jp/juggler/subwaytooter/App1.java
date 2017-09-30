package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.AcctSet;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.ClientInfo;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.LogData;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.PostDraft;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.TagSet;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.CustomEmojiCache;
import jp.juggler.subwaytooter.util.CustomEmojiLister;
import jp.juggler.subwaytooter.util.LogCategory;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

public class App1 extends Application {
	
	static final LogCategory log = new LogCategory( "App1" );
	
	@Override public void onCreate(){
		log.d( "onCreate" );
		super.onCreate();
		prepare( getApplicationContext() );
	}
	
	@Override public void onTerminate(){
		log.d( "onTerminate" );
		super.onTerminate();
	}
	
	public static final String FILE_PROVIDER_AUTHORITY = "jp.juggler.subwaytooter.FileProvider";
	
	static final String DB_NAME = "app_db";
	static final int DB_VERSION = 18;
	
	// 2017/4/25 v10 1=>2 SavedAccount に通知設定を追加
	// 2017/4/25 v10 1=>2 NotificationTracking テーブルを追加
	// 2017/4/29 v20 2=>5 MediaShown,ContentWarningのインデクスが間違っていたので貼り直す
	// 2017/4/29 v23 5=>6 MutedAppテーブルの追加、UserRelationテーブルの追加
	// 2017/5/01 v26 6=>7 AcctSetテーブルの追加
	// 2017/5/02 v32 7=>8 (この変更は取り消された)
	// 2017/5/02 v32 8=>9 AcctColor テーブルの追加
	// 2017/5/04 v33 9=>10 SavedAccountに項目追加
	// 2017/5/08 v41 10=>11 MutedWord テーブルの追加
	// 2017/5/17 v59 11=>12 PostDraft テーブルの追加
	// 2017/5/23 v68 12=>13 SavedAccountに項目追加
	// 2017/5/25 v69 13=>14 SavedAccountに項目追加
	// 2017/5/27 v73 14=>15 TagSetテーブルの追加
	// 2017/7/22 v99 15=>16 SavedAccountに項目追加
	// 2017/7/22 v106 16=>17 AcctColor に項目追加
	// 2017/9/23 v161 17=>18 SavedAccountに項目追加
	
	private static DBOpenHelper db_open_helper;
	
	public static SQLiteDatabase getDB(){
		return db_open_helper.getWritableDatabase();
	}
	
	private static class DBOpenHelper extends SQLiteOpenHelper {
		
		private DBOpenHelper( Context context ){
			super( context, DB_NAME, null, DB_VERSION );
		}
		
		@Override
		public void onCreate( SQLiteDatabase db ){
			LogData.onDBCreate( db );
			//
			SavedAccount.onDBCreate( db );
			ClientInfo.onDBCreate( db );
			MediaShown.onDBCreate( db );
			ContentWarning.onDBCreate( db );
			NotificationTracking.onDBCreate( db );
			MutedApp.onDBCreate( db );
			UserRelation.onDBCreate( db );
			AcctSet.onDBCreate( db );
			AcctColor.onDBCreate( db );
			MutedWord.onDBCreate( db );
			PostDraft.onDBCreate( db );
			TagSet.onDBCreate( db );
		}
		
		@Override
		public void onUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
			LogData.onDBUpgrade( db, oldVersion, newVersion );
			//
			SavedAccount.onDBUpgrade( db, oldVersion, newVersion );
			ClientInfo.onDBUpgrade( db, oldVersion, newVersion );
			MediaShown.onDBUpgrade( db, oldVersion, newVersion );
			ContentWarning.onDBUpgrade( db, oldVersion, newVersion );
			NotificationTracking.onDBUpgrade( db, oldVersion, newVersion );
			MutedApp.onDBUpgrade( db, oldVersion, newVersion );
			UserRelation.onDBUpgrade( db, oldVersion, newVersion );
			AcctSet.onDBUpgrade( db, oldVersion, newVersion );
			AcctColor.onDBUpgrade( db, oldVersion, newVersion );
			MutedWord.onDBUpgrade( db, oldVersion, newVersion );
			PostDraft.onDBUpgrade( db, oldVersion, newVersion );
			TagSet.onDBUpgrade( db, oldVersion, newVersion );
		}
	}
	
	@SuppressWarnings("unused")
	private static final CipherSuite[] APPROVED_CIPHER_SUITES = new CipherSuite[]{
		
		// 以下は okhttp 3 のデフォルト
		// This is nearly equal to the cipher suites supported in Chrome 51, current as of 2016-05-25.
		// All of these suites are available on Android 7.0; earlier releases support a subset of these
		// suites. https://github.com/square/okhttp/issues/1972
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
		CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
		CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
		CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
		CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
		
		// Note that the following cipher suites are all on HTTP/2's bad cipher suites list. We'll
		// continue to include them until better suites are commonly available. For example, none
		// of the better cipher suites listed above shipped with Android 4.4 or Java 7.
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
		CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
		CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
		CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
		CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
		CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
		CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
		CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
		
		//https://www.ssllabs.com/ssltest/analyze.html?d=mastodon.cloud&latest
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,// mastodon.cloud用 デフォルトにはない
		CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384, //mastodon.cloud用 デフォルトにはない
		
		// https://www.ssllabs.com/ssltest/analyze.html?d=m.sighash.info
		CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384, // m.sighash.info 用 デフォルトにはない
		CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384, // m.sighash.info 用 デフォルトにはない
		CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256, // m.sighash.info 用 デフォルトにはない
		CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA, // m.sighash.info 用 デフォルトにはない
	};
	
	//	private int getBitmapPoolSize( Context context ){
	//		ActivityManager am = ((ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE));
	//		int memory = am.getMemoryClass();
	//		int largeMemory = am.getLargeMemoryClass();
	//		// どちらも単位はMB
	//		log.d("MemoryClass=%d, LargeMemoryClass = %d",memory,largeMemory);
	//
	//		int maxSize;
	//		if( am.isLowRamDevice() ){
	//			maxSize = 5 * 1024; // 単位はKiB
	//		}else if( largeMemory >= 512 ){
	//			maxSize = 128 * 1024; // 単位はKiB
	//		}else if( largeMemory >= 256 ){
	//			maxSize = 64 * 1024; // 単位はKiB
	//		}else{
	//			maxSize = 10 * 1024; // 単位はKiB
	//		}
	//		return maxSize * 1024;
	//	}
	
	static OkHttpClient.Builder prepareOkHttp(){
		ConnectionSpec spec = new ConnectionSpec.Builder( ConnectionSpec.MODERN_TLS )
			.cipherSuites( APPROVED_CIPHER_SUITES )
			.build();
		
		ArrayList< ConnectionSpec > spec_list = new ArrayList<>();
		spec_list.add( spec );
		spec_list.add( ConnectionSpec.CLEARTEXT );
		
		//noinspection UnnecessaryLocalVariable
		OkHttpClient.Builder builder = new OkHttpClient.Builder()
			.connectTimeout( 30, TimeUnit.SECONDS )
			.readTimeout( 60, TimeUnit.SECONDS )
			.writeTimeout( 60, TimeUnit.SECONDS )
			.pingInterval( 10, TimeUnit.SECONDS )
			.connectionSpecs( spec_list );
		
		return builder;
	}
	
	public static OkHttpClient ok_http_client;
	
	private static OkHttpClient ok_http_client2;
	
//	public static final boolean USE_OLD_EMOJIONE = false;
//	public static Typeface typeface_emoji;
	
	public static SharedPreferences pref;
	
	public static ThreadPoolExecutor task_executor;
	
	static OkHttpUrlLoader.Factory glide_okhttp3_factory;
	
	@SuppressLint("StaticFieldLeak")
	public static CustomEmojiCache custom_emoji_cache;
	public static CustomEmojiLister custom_emoji_lister;
	
	private static boolean bPrepared = false;
	
	public static void prepare( final Context app_context ){
		if( bPrepared ) return;
		bPrepared = true;
		
		CalligraphyConfig.initDefault( new CalligraphyConfig.Builder()
			.setFontAttrId( R.attr.fontPath )
			.build()
		);
		
		if( task_executor == null ){
			
			// We want at least 2 threads and at most 4 threads in the core pool,
			// preferring to have 1 less than the CPU count to avoid saturating
			// the CPU with background work
			
			int CPU_COUNT = Runtime.getRuntime().availableProcessors();
			int CORE_POOL_SIZE = Math.max( 2, Math.min( CPU_COUNT - 1, 4 ) );
			int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
			int KEEP_ALIVE_SECONDS = 30;
			
			// デフォルトだとキューはmax128で、溢れることがある
			BlockingQueue< Runnable > sPoolWorkQueue = new LinkedBlockingQueue<>( 999 );
			
			ThreadFactory sThreadFactory = new ThreadFactory() {
				private final AtomicInteger mCount = new AtomicInteger( 1 );
				
				public Thread newThread( @NonNull Runnable r ){
					return new Thread( r, "SubwayTooterTask #" + mCount.getAndIncrement() );
				}
			};
			
			task_executor = new ThreadPoolExecutor(
				CORE_POOL_SIZE  // pool size
				, MAXIMUM_POOL_SIZE // max pool size
				, KEEP_ALIVE_SECONDS // keep-alive-seconds
				, TimeUnit.SECONDS // unit of keep-alive-seconds
				, sPoolWorkQueue
				, sThreadFactory
			);
			
			task_executor.allowCoreThreadTimeOut( true );
		}
		
		if( pref == null ){
			pref = Pref.pref( app_context );
		}
		
		if( db_open_helper == null ){
			log.d( "prepareDB" );
			db_open_helper = new DBOpenHelper( app_context );
			
			//			if( BuildConfig.DEBUG){
			//				SQLiteDatabase db = db_open_helper.getWritableDatabase();
			//				db_open_helper.onCreate( db );
			//			}
			
			UserRelation.deleteOld( System.currentTimeMillis() );
			AcctSet.deleteOld( System.currentTimeMillis() );
		}
		
//		if( USE_OLD_EMOJIONE ){
//			if( typeface_emoji == null ){
//				typeface_emoji = TypefaceUtils.load( app_context.getAssets(), "emojione_android.ttf" );
//			}
//		}
		
		//		if( image_loader == null ){
		//			image_loader = new MyImageLoader(
		//				Volley.newRequestQueue( getApplicationContext() )
		//				, new BitmapCache( getApplicationContext() )
		//			);
		//		}
		
		if( ok_http_client == null ){
			OkHttpClient.Builder builder = prepareOkHttp();
			ok_http_client = builder.build();
		}
		
		if( ok_http_client2 == null ){
			OkHttpClient.Builder builder = prepareOkHttp();
			
			File cacheDir = new File( app_context.getCacheDir(), "http2" );
			Cache cache = new Cache( cacheDir, 30000000L );
			builder.cache( cache );
			
			ok_http_client2 = builder.build();
		}
		
		// Glide.isSetup は Glide 4.0 で廃止になるらしいが、俺が使ってるのは3.xだ
		//noinspection deprecation
		if( ! Glide.isSetup() ){
			
			GlideBuilder builder = new GlideBuilder( app_context );
			builder.setDiskCache( new InternalCacheDiskCacheFactory( app_context, 10 * 1024 * 1024 ) );
			
			// 割とGlide任せで十分いけるっぽい
			//			MemorySizeCalculator calculator = new MemorySizeCalculator(context);
			//			int defaultMemoryCacheSize = calculator.getMemoryCacheSize();
			//			int defaultBitmapPoolSize = calculator.getBitmapPoolSize();
			//
			//			ActivityManager am = ((ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE));
			//			int class_memory = am.getMemoryClass(); // 単位はMB
			//			int class_large = am.getLargeMemoryClass(); // 単位はMB
			//
			//				int maxSize;
			//				if( am.isLowRamDevice() ){
			//					maxSize = 5 * 1024; // 単位はKiB
			//				}else if( largeMemory >= 512 ){
			//					maxSize = 128 * 1024; // 単位はKiB
			//				}else if( largeMemory >= 256 ){
			//					maxSize = 64 * 1024; // 単位はKiB
			//				}else{
			//					maxSize = 10 * 1024; // 単位はKiB
			//				}
			//				return maxSize * 1024;
			//			}
			//			builder.setMemoryCache(new LruResourceCache(getMemoryCacheSize(getApplicationContext())));
			//			builder.setBitmapPool(new LruBitmapPool(getBitmapPoolSize(getApplicationContext())));
			
			// Glide.setupはGLide 4.0 で廃止になるらしいが、俺が使ってるのは3.xだ
			//noinspection deprecation
			Glide.setup( builder );
			
			// DEBUG 画像のディスクキャッシュの消去
			//			new Thread(new Runnable() {
			//				@Override
			//				public void run() {
			//					Glide.get(context).clearDiskCache();
			//				}
			//			}).start();
			
			glide_okhttp3_factory = new OkHttpUrlLoader.Factory( ok_http_client );
			Glide.get( app_context ).register( GlideUrl.class, InputStream.class, glide_okhttp3_factory );
		}
		
		if( custom_emoji_cache == null ){
			custom_emoji_cache = new CustomEmojiCache( app_context );
		}
		if( custom_emoji_lister == null ){
			custom_emoji_lister = new CustomEmojiLister( app_context );
		}
	}
	
	@SuppressLint("StaticFieldLeak")
	private static AppState app_state;
	
	static AppState getAppState( @NonNull Context context ){
		
		if( app_state == null ){
			context = context.getApplicationContext();
			prepare( context );
			app_state = new AppState( context, pref );
		}
		return app_state;
	}
	
	public static void setActivityTheme( @NonNull Activity activity, boolean bNoActionBar ){
		
		prepare( activity.getApplicationContext() );
		
		int theme_idx = pref.getInt( Pref.KEY_UI_THEME, 0 );
		switch( theme_idx ){
		
		default:
		case 0:
			activity.setTheme( bNoActionBar ? R.style.AppTheme_Light_NoActionBar : R.style.AppTheme_Light );
			break;
		
		case 1:
			activity.setTheme( bNoActionBar ? R.style.AppTheme_Dark_NoActionBar : R.style.AppTheme_Dark );
			break;
			
		}
	}
	
	static final CacheControl CACHE_5MIN = new CacheControl.Builder()
		.maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS) // キャッシュをいつまで保持するか
	//s	.minFresh( 1, TimeUnit.HOURS ) // キャッシュが新鮮であると考えられる時間
		.maxAge( 1, TimeUnit.HOURS ) // キャッシュが新鮮であると考えられる時間
		.build();
	
	@Nullable public static byte[] getHttpCached( @NonNull String url ){
		Response response;

		try{
			okhttp3.Request.Builder request_builder = new okhttp3.Request.Builder();
			request_builder.url( url );
			request_builder.cacheControl( CACHE_5MIN );
			
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
	
	@Nullable public static String getHttpCachedString( @NonNull String url ){
		Response response;

		try{
			okhttp3.Request.Builder request_builder = new okhttp3.Request.Builder();
			request_builder.url( url );
			request_builder.cacheControl( CACHE_5MIN );
			
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
			return response.body().string();
		}catch( Throwable ex ){
			log.e( ex, "getHttp content error." );
			return null;
		}
	}
}
