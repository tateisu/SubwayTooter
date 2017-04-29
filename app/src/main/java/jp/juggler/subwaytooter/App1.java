package jp.juggler.subwaytooter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.ClientInfo;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.LogData;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import okhttp3.OkHttpClient;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.TypefaceUtils;

public class App1 extends Application {
	
	
	static final String DB_NAME = "app_db";
	static final int DB_VERSION = 5;
	// 2017/4/25 v10 1=>2 SavedAccount に通知設定を追加
	// 2017/4/25 v10 1=>2 NotificationTracking テーブルを追加
	// 2017/4/29 v20 2=>5 MediaShown,ContentWarningのインデクスが間違っていたので貼り直す
	static DBOpenHelper db_open_helper;
	
	public static SQLiteDatabase getDB(){
		return db_open_helper.getWritableDatabase();
	}
	
	public static void setActivityTheme( Activity activity ,boolean bNoActionBar ){
		int theme_idx = pref.getInt(Pref.KEY_UI_THEME,0);
		switch(theme_idx){

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
		
		private DBOpenHelper(Context context) {
			super( context, DB_NAME, null, DB_VERSION );
		}
		
		@Override
		public void onCreate( SQLiteDatabase db ){
			LogData.onDBCreate( db );
			//
			SavedAccount.onDBCreate( db );
			ClientInfo.onDBCreate( db );
			MediaShown.onDBCreate(db);
			ContentWarning.onDBCreate(db);
			NotificationTracking.onDBCreate(db);
			MutedApp.onDBCreate(db);
			UserRelation.onDBCreate(db);
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
		}
	}
	
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
		public MyImageLoader( RequestQueue queue, ImageCache imageCache ){
			super( queue, imageCache );
		}
		
		@Override
		protected Request< Bitmap > makeImageRequest( String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, String cacheKey ){
			Request< Bitmap > req =  super.makeImageRequest( requestUrl, maxWidth, maxHeight, scaleType, cacheKey );
			req.setRetryPolicy(new DefaultRetryPolicy(
				30000 // SOCKET_TIMEOUT_MS
				,3 // DefaultRetryPolicy.DEFAULT_MAX_RETRIES
				,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
			));
			return req;
		}
	}
	
	private static class BitmapCache implements ImageLoader.ImageCache {
		
		private LruCache<String, Bitmap> mCache;
		
		public BitmapCache() {
			int maxSize = 10 * 1024 * 1024;
			mCache = new LruCache<String, Bitmap>(maxSize) {
				@Override
				protected int sizeOf(String key, Bitmap value) {
					return value.getRowBytes() * value.getHeight();
				}
			};
		}
		
		@Override
		public Bitmap getBitmap(String url) {
			return mCache.get(url);
		}
		
		@Override
		public void putBitmap(String url, Bitmap bitmap) {
			mCache.put(url, bitmap);
		}
		
	}
	
	public static final OkHttpClient ok_http_client = new OkHttpClient();
	
	public static Typeface typeface_emoji ;
	
	// public static final RelationshipMap relationship_map = new RelationshipMap();
	
	public static SharedPreferences pref;
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		
		CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
			.setFontAttrId(R.attr.fontPath)
			.build()
		);
		
		if( pref == null ){
			pref = Pref.pref(getApplicationContext());
		}
		
		if( typeface_emoji == null ){
			typeface_emoji = TypefaceUtils.load(getAssets(), "emojione_android.ttf");
		}
		
		if( db_open_helper == null ){
			db_open_helper = new DBOpenHelper( getApplicationContext() );

			if( BuildConfig.DEBUG){
				SQLiteDatabase db = db_open_helper.getWritableDatabase();
				db_open_helper.onCreate( db );
			}
		}

		if( image_loader == null ){
			image_loader = new MyImageLoader(
				Volley.newRequestQueue( getApplicationContext() )
				, new BitmapCache()
			);
		}
	}
	
	@Override
	public void onTerminate(){
		super.onTerminate();
	}
}
