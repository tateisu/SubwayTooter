package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
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
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.LogCategory;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.TypefaceUtils;

public class App1 extends Application {
	
	static final LogCategory log = new LogCategory( "App1" );
	
	static final String DB_NAME = "app_db";
	static final int DB_VERSION = 10;
	// 2017/4/25 v10 1=>2 SavedAccount に通知設定を追加
	// 2017/4/25 v10 1=>2 NotificationTracking テーブルを追加
	// 2017/4/29 v20 2=>5 MediaShown,ContentWarningのインデクスが間違っていたので貼り直す
	// 2017/4/29 v23 5=>6 MutedAppテーブルの追加、UserRelationテーブルの追加
	// 2017/5/01 v26 6=>7 AcctSetテーブルの追加
	// 2017/5/02 v32 7=>8 (この変更は取り消された)
	// 2017/5/02 v32 8=>9 AcctColor テーブルの追加
	// 2017/5/04 v33 9=>10 SavedAccountに項目追加
	
	static DBOpenHelper db_open_helper;
	
	public static SQLiteDatabase getDB(){
		return db_open_helper.getWritableDatabase();
	}
	
	public static void setActivityTheme( Activity activity, boolean bNoActionBar ){
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
	
	static ImageLoader image_loader;
	
	public static ImageLoader getImageLoader(){
		return image_loader;
	}
	
	private static class MyImageLoader extends ImageLoader {
		
		/**
		 * Constructs a new ImageLoader.
		 *
		 * @param queue      The RequestQueue to use for making image requests.
		 * @param imageCache The cache to use as an L1 cache.
		 */
		MyImageLoader( RequestQueue queue, ImageCache imageCache ){
			super( queue, imageCache );
		}
		
		@Override
		protected Request< Bitmap > makeImageRequest( String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, String cacheKey ){
			Request< Bitmap > req = super.makeImageRequest( requestUrl, maxWidth, maxHeight, scaleType, cacheKey );
			req.setRetryPolicy( new DefaultRetryPolicy(
				30000 // SOCKET_TIMEOUT_MS
				, 3 // DefaultRetryPolicy.DEFAULT_MAX_RETRIES
				, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
			) );
			return req;
		}
	}
	
	private static class BitmapCache implements ImageLoader.ImageCache {
		
		private LruCache< String, Bitmap > mCache;
		
		BitmapCache(){
			int maxSize = 10 * 1024 * 1024;
			mCache = new LruCache< String, Bitmap >( maxSize ) {
				@Override
				protected int sizeOf( String key, Bitmap value ){
					return value.getRowBytes() * value.getHeight();
				}
			};
		}
		
		@Override public Bitmap getBitmap( String url ){
			return mCache.get( url );
		}
		
		@Override public void putBitmap( String url, Bitmap bitmap ){
			mCache.put( url, bitmap );
		}
		
	}
	
	public static OkHttpClient ok_http_client;
	
	public static Typeface typeface_emoji;
	
	// public static final RelationshipMap relationship_map = new RelationshipMap();
	
	public static SharedPreferences pref;
	
	
	/**
	 * An {@link Executor} that can be used to execute tasks in parallel.
	 */
	public static ThreadPoolExecutor task_executor;
	
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		CalligraphyConfig.initDefault( new CalligraphyConfig.Builder()
			.setFontAttrId( R.attr.fontPath )
			.build()
		);
		
		if( task_executor == null ){
			
			// We want at least 2 threads and at most 4 threads in the core pool,
			// preferring to have 1 less than the CPU count to avoid saturating
			// the CPU with background work
			
			int CPU_COUNT = Runtime.getRuntime().availableProcessors();
			int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
			int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
			int KEEP_ALIVE_SECONDS = 30;
			
			// デフォルトだとキューはmax128で、溢れることがある
			BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<>(999);

			ThreadFactory sThreadFactory = new ThreadFactory() {
				private final AtomicInteger mCount = new AtomicInteger(1);
				public Thread newThread( @NonNull Runnable r) {
					return new Thread(r, "SubwayTooterTask #" + mCount.getAndIncrement());
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

			task_executor.allowCoreThreadTimeOut(true);
		}
		
		
		if( pref == null ){
			pref = Pref.pref( getApplicationContext() );
		}
		
		if( typeface_emoji == null ){
			typeface_emoji = TypefaceUtils.load( getAssets(), "emojione_android.ttf" );
		}
		
		if( db_open_helper == null ){
			db_open_helper = new DBOpenHelper( getApplicationContext() );
			
			//			if( BuildConfig.DEBUG){
			//				SQLiteDatabase db = db_open_helper.getWritableDatabase();
			//				db_open_helper.onCreate( db );
			//			}
			UserRelation.deleteOld( System.currentTimeMillis() );
			AcctSet.deleteOld( System.currentTimeMillis() );
		}
		
		if( image_loader == null ){
			image_loader = new MyImageLoader(
				Volley.newRequestQueue( getApplicationContext() )
				, new BitmapCache()
			);
		}
		
		if( ok_http_client == null ){
			
			ConnectionSpec spec = new ConnectionSpec.Builder( ConnectionSpec.MODERN_TLS )
				.cipherSuites( APPROVED_CIPHER_SUITES )
				.build();
			
			ArrayList< ConnectionSpec > spec_list = new ArrayList<>();
			spec_list.add( spec );
			spec_list.add( ConnectionSpec.CLEARTEXT );

			OkHttpClient.Builder builder = new OkHttpClient.Builder()
				.connectTimeout( 30, TimeUnit.SECONDS )
				.readTimeout( 30, TimeUnit.SECONDS )
				.writeTimeout( 30, TimeUnit.SECONDS )
				.connectionSpecs( spec_list )
			;
			
			ok_http_client = builder.build();
		}
		
	}
	
	@Override
	public void onTerminate(){
		super.onTerminate();
	}
	
	@SuppressLint("StaticFieldLeak")
	private static AppState app_state;
	
	static AppState getAppState( Context context ){
		// これは最後。loadColumnListでDBが必要になる
		if( app_state == null ){
			app_state = new AppState( context.getApplicationContext(), pref );
		}
		return app_state;
	}
	
}
