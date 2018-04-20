package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.util.Log

import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.engine.executor.GlideExecutor.newDiskCacheExecutor
import com.bumptech.glide.load.engine.executor.GlideExecutor.newSourceExecutor
import com.bumptech.glide.load.model.GlideUrl
import jp.juggler.subwaytooter.api.TootApiClient

import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Response
import uk.co.chrisjenx.calligraphy.CalligraphyConfig

class App1 : Application() {
	
	override fun onCreate() {
		log.d("onCreate")
		super.onCreate()
		prepare(applicationContext)
	}
	
	override fun onTerminate() {
		log.d("onTerminate")
		super.onTerminate()
	}
	
	class DBOpenHelper(context : Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

		override fun onCreate(db : SQLiteDatabase) {
			for(ti in tableList) {
				ti.onDBCreate(db)
			}
		}
		
		override fun onUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			for(ti in tableList) {
				ti.onDBUpgrade(db, oldVersion, newVersion)
			}
		}
	}
	
	companion object {
		
		internal val log = LogCategory("App1")
		
		const val FILE_PROVIDER_AUTHORITY = "jp.juggler.subwaytooter.FileProvider"
		
		internal const val DB_NAME = "app_db"
		
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
		// 2017/9/23 v161 18=>19 ClientInfoテーブルを置き換える
		// 2017/12/01 v175 19=>20 UserRelation に項目追加
		// 2018/1/03 v197 20=>21 HighlightWord テーブルを追加
		// 2018/3/16 v226 21=>22 FavMuteテーブルを追加
		// 2018/4/17 v236 22=>23 SavedAccountテーブルに項目追加
		// 2018/4/20 v240 23=>24 SavedAccountテーブルに項目追加
		internal const val DB_VERSION = 24
		
		private val tableList = arrayOf(
			LogData,
			SavedAccount,
			ClientInfo,
			MediaShown,
			ContentWarning,
			NotificationTracking,
			MutedApp,
			UserRelation,
			AcctSet,
			AcctColor,
			MutedWord,
			PostDraft,
			TagSet,
			HighlightWord,
			FavMute
		)
		
		private lateinit var db_open_helper : DBOpenHelper
		
		val database : SQLiteDatabase get() = db_open_helper.writableDatabase
		
		private val APPROVED_CIPHER_SUITES = arrayOf(
			
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
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256, // mastodon.cloud用 デフォルトにはない
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384, //mastodon.cloud用 デフォルトにはない
			
			// https://www.ssllabs.com/ssltest/analyze.html?d=m.sighash.info
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384, // m.sighash.info 用 デフォルトにはない
			CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384, // m.sighash.info 用 デフォルトにはない
			CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256, // m.sighash.info 用 デフォルトにはない
			CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA
		) // m.sighash.info 用 デフォルトにはない
		
		//	private int getBitmapPoolSize( Context context ){
		//		ActivityManager am = ((ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE));
		//		int memory = am.getMemoryClass();
		//		int largeMemory = am.getLargeMemoryClass();
		//		// どちらも単位はMB
		//		warning.d("MemoryClass=%d, LargeMemoryClass = %d",memory,largeMemory);
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
		
		private fun prepareOkHttp() : OkHttpClient.Builder {
			val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
				.cipherSuites(*APPROVED_CIPHER_SUITES)
				.build()
			
			val spec_list = ArrayList<ConnectionSpec>()
			spec_list.add(spec)
			spec_list.add(ConnectionSpec.CLEARTEXT)
			
			
			return OkHttpClient.Builder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.writeTimeout(60, TimeUnit.SECONDS)
				.pingInterval(10, TimeUnit.SECONDS)
				.connectionSpecs(spec_list)
				.addInterceptor(ProgressResponseBody.makeInterceptor())
		}
		
		lateinit var ok_http_client : OkHttpClient
		
		lateinit var ok_http_client2 : OkHttpClient
		
		lateinit var pref : SharedPreferences
		
		lateinit var task_executor : ThreadPoolExecutor
		
		private lateinit var glide_okhttp3_factory : OkHttpUrlLoader.Factory
		
		@SuppressLint("StaticFieldLeak")
		lateinit var custom_emoji_cache : CustomEmojiCache
		
		@SuppressLint("StaticFieldLeak")
		lateinit var custom_emoji_lister : CustomEmojiLister
		
		fun prepare(app_context : Context) : AppState {
			var state = appStateX
			if(state != null) return state
			
			CalligraphyConfig.initDefault(
				CalligraphyConfig.Builder()
					.setFontAttrId(R.attr.fontPath)
					.build()
			)
			
			pref = Pref.pref(app_context)
			
			run {
				
				// We want at least 2 threads and at most 4 threads in the core pool,
				// preferring to have 1 less than the CPU count to avoid saturating
				// the CPU with background work
				
				val CPU_COUNT = Runtime.getRuntime().availableProcessors()
				val CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4))
				val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
				val KEEP_ALIVE_SECONDS = 30
				
				// デフォルトだとキューはmax128で、溢れることがある
				val sPoolWorkQueue = LinkedBlockingQueue<Runnable>(999)
				
				val sThreadFactory = object : ThreadFactory {
					private val mCount = AtomicInteger(1)
					
					override fun newThread(r : Runnable) : Thread {
						return Thread(r, "SubwayTooterTask #" + mCount.getAndIncrement())
					}
				}
				
				task_executor = ThreadPoolExecutor(
					CORE_POOL_SIZE  // pool size
					, MAXIMUM_POOL_SIZE // max pool size
					, KEEP_ALIVE_SECONDS.toLong() // keep-alive-seconds
					, TimeUnit.SECONDS // unit of keep-alive-seconds
					, sPoolWorkQueue, sThreadFactory
				)
				
				task_executor.allowCoreThreadTimeOut(true)
			}
			
			
			log.d("prepareDB")
			db_open_helper = DBOpenHelper(app_context)
			
			//			if( BuildConfig.DEBUG){
			//				SQLiteDatabase db = db_open_helper.getWritableDatabase();
			//				db_open_helper.onCreate( db );
			//			}
			
			UserRelation.deleteOld(System.currentTimeMillis())
			AcctSet.deleteOld(System.currentTimeMillis())
			
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
			
			run {
				val builder = prepareOkHttp()
				ok_http_client = builder.build()
			}
			
			run {
				val builder = prepareOkHttp()
				
				val cacheDir = File(app_context.cacheDir, "http2")
				val cache = Cache(cacheDir, 30000000L)
				builder.cache(cache)
				
				ok_http_client2 = builder.build()
			}
			
			
			
			custom_emoji_cache = CustomEmojiCache(app_context)
			
			custom_emoji_lister = CustomEmojiLister(app_context)
			
			state = AppState(app_context, pref)
			appStateX = state
			return state
		}
		
		@SuppressLint("StaticFieldLeak")
		private var appStateX : AppState? = null
		
		fun getAppState(context : Context) : AppState {
			return prepare(context.applicationContext)
		}
		
		fun sound(item : HighlightWord) {
			try {
				appStateX?.sound(item)
			} catch(ex : Throwable) {
				log.trace(ex)
				// java.lang.NoSuchFieldError:
				// at jp.juggler.subwaytooter.App1$Companion.sound (App1.kt:544)
				// at jp.juggler.subwaytooter.Column$startRefresh$task$1.onPostExecute (Column.kt:2432)
			}
		}
		
		@Suppress("UNUSED_PARAMETER")
		fun registerGlideComponents(context : Context, glide : Glide, registry : Registry) {
			
			glide_okhttp3_factory = OkHttpUrlLoader.Factory(ok_http_client)
			registry.append(GlideUrl::class.java, InputStream::class.java, glide_okhttp3_factory)
			
			// registry.append(Photo.class, InputStream.class, new FlickrModelLoader.Factory());
			// 3.xの書き方 Glide.get(app_context).register(GlideUrl::class.java, InputStream::class.java, glide_okhttp3_factory)
		}
		
		fun applyGlideOptions(context : Context, builder : GlideBuilder) {
			
			// ログレベル
			builder.setLogLevel(Log.ERROR)
			
			// エラー処理
			val catcher = GlideExecutor.UncaughtThrowableStrategy { ex ->
				log.trace(ex)
			}
			builder.setDiskCacheExecutor(newDiskCacheExecutor(catcher))
			builder.setSourceExecutor(newSourceExecutor(catcher))
			
			builder.setDiskCache(InternalCacheDiskCacheFactory(context, 10 * 1024 * 1024))
			
			// DEBUG 画像のディスクキャッシュの消去
			//			new Thread(new Runnable() {
			//				@Override
			//				public void run() {
			//					Glide.get(context).clearDiskCache();
			//				}
			//			}).start();
			
			//
			//			////////////
			//			// サンプル1：キャッシュサイズを自動で計算する
			//			val calculator = MemorySizeCalculator.Builder(context)
			//				.setMemoryCacheScreens(2f)
			//				.setBitmapPoolScreens(3f)
			//				.build()
			//
			//			builder.setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
			
			//////
			// サンプル2：キャッシュサイズをアプリが決める
			// int memoryCacheSizeBytes = 1024 * 1024 * 20; // 20mb
			// builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));
			
			//////
			// サンプル3 ： 自前のメモリキャッシュ
			// builder.setMemoryCache(new YourAppMemoryCacheImpl());
			
			//			builder.setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong()))
			
			// ディスクキャッシュを保存する場所を変えたい場合
			// builder.setDiskCache(new ExternalDiskCacheFactory(context));
			
			// ディスクキャッシュのサイズを変えたい場合
			//			val diskCacheSizeBytes = 1024 * 1024 * 100 // 100 MB
			//			builder.setDiskCache(InternalDiskCacheFactory(context, diskCacheSizeBytes))
			
			// Although RequestOptions are typically specified per request,
			// you can also apply a default set of RequestOptions that will be applied to every load
			// you start in your application by using an AppGlideModule:
			//			val ro = RequestOptions()
			//				.format(DecodeFormat.PREFER_ARGB_8888)
			//				.disallowHardwareConfig()
			//			builder.setDefaultRequestOptions(ro)
			
		}
		
		fun setActivityTheme(
			activity : Activity,
			bNoActionBar : Boolean,
			forceDark : Boolean = false
		) {
			
			prepare(activity.applicationContext)
			
			var theme_idx = Pref.ipUiTheme(pref)
			
			if(forceDark) theme_idx = 1
			
			when(theme_idx) {
				0 -> activity.setTheme(if(bNoActionBar) R.style.AppTheme_Light_NoActionBar else R.style.AppTheme_Light)
				
				1 -> activity.setTheme(if(bNoActionBar) R.style.AppTheme_Dark_NoActionBar else R.style.AppTheme_Dark)
				
				else -> activity.setTheme(if(bNoActionBar) R.style.AppTheme_Light_NoActionBar else R.style.AppTheme_Light)
			}
			
		}
		
		internal val CACHE_5MIN = CacheControl.Builder()
			.maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS) // キャッシュをいつまで保持するか
			//s	.minFresh( 1, TimeUnit.HOURS ) // キャッシュが新鮮であると考えられる時間
			.maxAge(1, TimeUnit.HOURS) // キャッシュが新鮮であると考えられる時間
			.build()
		
		fun getHttpCached(url : String) : ByteArray? {
			val response : Response
			
			try {
				val request_builder = okhttp3.Request.Builder()
				request_builder.url(url)
				request_builder.cacheControl(CACHE_5MIN)
				
				val call = App1.ok_http_client2.newCall(request_builder.build())
				response = call.execute()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp network error.")
				return null
			}
			
			if(! response.isSuccessful) {
				log.e(TootApiClient.formatResponse(response, "getHttp response error."))
				return null
			}
			
			return try {
				response.body()?.bytes()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp content error.")
				null
			}
			
		}
		
		fun getHttpCachedString(url : String) : String? {
			val response : Response
			
			try {
				val request_builder = okhttp3.Request.Builder()
				request_builder.url(url)
				request_builder.cacheControl(CACHE_5MIN)
				
				val call = App1.ok_http_client2.newCall(request_builder.build())
				response = call.execute()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp network error.")
				return null
			}
			
			if(! response.isSuccessful) {
				log.e(TootApiClient.formatResponse(response, "getHttp response error."))
				return null
			}
			
			return try {
				response.body()?.string()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp content error.")
				null
			}
			
		}
		
		// Chrome Custom Tab を開く
		fun openCustomTab(activity : Activity, url : String) {
			try {
				if(Pref.bpPriorChrome(pref)) {
					try {
						// 初回はChrome指定で試す
						val builder = CustomTabsIntent.Builder()
						builder.setToolbarColor(
							Styler.getAttributeColor(
								activity,
								R.attr.colorPrimary
							)
						).setShowTitle(true)
						val customTabsIntent = builder.build()
						customTabsIntent.intent.component = ComponentName(
							"com.android.chrome",
							"com.google.android.apps.chrome.Main"
						)
						customTabsIntent.launchUrl(activity, Uri.parse(url))
						return
					} catch(ex2 : Throwable) {
						log.e(ex2, "openChromeTab: missing chrome. retry to other application.")
					}
					
				}
				
				// chromeがないなら ResolverActivity でアプリを選択させる
				val builder = CustomTabsIntent.Builder()
				builder.setToolbarColor(Styler.getAttributeColor(activity, R.attr.colorPrimary))
					.setShowTitle(true)
				val customTabsIntent = builder.build()
				customTabsIntent.launchUrl(activity, Uri.parse(url))
			} catch(ex : Throwable) {
				log.trace(ex)
				showToast(activity, false, "can't open browser app")
			}
			
		}
		
		fun openCustomTab(activity : Activity, ta : TootAttachment) {
			val url = ta.getLargeUrl(pref)
			if(url != null) {
				openCustomTab(activity, url)
			}
		}
		
	}
	
}
