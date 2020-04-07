package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.engine.executor.GlideExecutor.newDiskCacheExecutor
import com.bumptech.glide.load.engine.executor.GlideExecutor.newSourceExecutor
import com.bumptech.glide.load.model.GlideUrl
import jp.juggler.subwaytooter.action.cn
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.dialog.DlgAppPicker
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.CustomEmojiCache
import jp.juggler.subwaytooter.util.CustomEmojiLister
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.util.*
import okhttp3.*
import org.conscrypt.Conscrypt
import java.io.File
import java.io.InputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.Security
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class App1 : Application() {
	
	override fun onCreate() {
		log.d("onCreate")
		super.onCreate()
		prepare(applicationContext, "App1.onCreate")
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
		// 2018/5/16 v252 24=>25 SubscriptionServerKey テーブルを追加
		// 2018/5/16 v252 25=>26 SubscriptionServerKey テーブルを丸ごと変更
		// 2018/8/5 v264 26 => 27 SavedAccountテーブルに項目追加
		// 2018/8/17 v267 27 => 28 SavedAccountテーブルに項目追加
		// 2018/8/19 v267 28 => 29 (失敗)ContentWarningMisskey, MediaShownMisskey テーブルを追加
		// 2018/8/19 v268 29 => 30 ContentWarningMisskey, MediaShownMisskey, UserRelationMisskeyテーブルを追加
		// 2018/8/19 v268 30 => 31 (29)で失敗しておかしくなったContentWarningとMediaShownを作り直す
		// 2018/8/28 v279 31 => 32 UserRelation,UserRelationMisskey にendorsedを追加
		// 2018/8/28 v280 32 => 33 NotificationTracking テーブルの作り直し。SavedAccountに通知二種類を追加
		// 2018/10/31 v296 33 => 34 UserRelationMisskey に blocked_by を追加
		// 2018/10/31 v296 34 => 35 UserRelationMisskey に requested_by を追加
		// 2018/12/6 v317 35 => 36 ContentWarningテーブルの作り直し。
		// 2019/6/4 v351 36 => 37 SavedAccount テーブルに項目追加。
		// 2019/6/4 v351 37 => 38 SavedAccount テーブルに項目追加。
		// 2019/8/12 v362 38 => 39 SavedAccount テーブルに項目追加。
		// 2019/10/22 39 => 40 NotificationTracking テーブルに項目追加。
		// 2019/10/22 40 => 41 NotificationCache テーブルに項目追加。
		// 2019/10/23 41=> 42 SavedAccount テーブルに項目追加。
		// 2019/11/15 42=> 43 HighlightWord テーブルに項目追加。
		// 2019/12/17 43=> 44 SavedAccount テーブルに項目追加。
		// 2019/12/18 44=> 45 SavedAccount テーブルに項目追加。
		// 2019/12/18 44=> 46 SavedAccount テーブルに項目追加。
		
		internal const val DB_VERSION = 46
		
		private val tableList = arrayOf(
			LogData,
			SavedAccount,
			ClientInfo,
			MediaShown,
			ContentWarning,
			NotificationTracking,
			NotificationCache,
			MutedApp,
			UserRelation,
			AcctSet,
			AcctColor,
			MutedWord,
			PostDraft,
			TagSet,
			HighlightWord,
			FavMute,
			SubscriptionServerKey
		)
		
		private lateinit var db_open_helper : DBOpenHelper
		
		val database : SQLiteDatabase get() = db_open_helper.writableDatabase
		
		//		private val APPROVED_CIPHER_SUITES = arrayOf(
		//
		//			// 以下は okhttp 3 のデフォルト
		//			// This is nearly equal to the cipher suites supported in Chrome 51, current as of 2016-05-25.
		//			// All of these suites are available on Android 7.0; earlier releases support a subset of these
		//			// suites. https://github.com/square/okhttp/issues/1972
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
		//			CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
		//			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
		//			CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
		//
		//			// Note that the following cipher suites are all on HTTP/2's bad cipher suites list. We'll
		//			// continue to include them until better suites are commonly available. For example, none
		//			// of the better cipher suites listed above shipped with Android 4.4 or Java 7.
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
		//			CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
		//			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
		//			CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
		//			CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
		//			CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
		//			CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
		//			CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
		//
		//			//https://www.ssllabs.com/ssltest/analyze.html?d=mastodon.cloud&latest
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256, // mastodon.cloud用 デフォルトにはない
		//			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384, //mastodon.cloud用 デフォルトにはない
		//
		//			// https://www.ssllabs.com/ssltest/analyze.html?d=m.sighash.info
		//			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384, // m.sighash.info 用 デフォルトにはない
		//			CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384, // m.sighash.info 用 デフォルトにはない
		//			CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256, // m.sighash.info 用 デフォルトにはない
		//			CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA
		//		) // m.sighash.info 用 デフォルトにはない
		
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
		
		val reNotAllowedInUserAgent = "[^\\x21-\\x7e]+".asciiPattern()
		
		val userAgentDefault =
			"SubwayTooter/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.RELEASE}"
		
		private fun getUserAgent() : String {
			val userAgentCustom = Pref.spUserAgent(pref)
			return when {
				userAgentCustom.isNotEmpty() && ! reNotAllowedInUserAgent.matcher(userAgentCustom)
					.find() -> userAgentCustom
				else -> userAgentDefault
			}
		}
		
		private val user_agent_interceptor = object : Interceptor {
			override fun intercept(chain : Interceptor.Chain) : Response {
				val request_with_user_agent = chain.request().newBuilder()
					.header("User-Agent", getUserAgent())
					.build()
				return chain.proceed(request_with_user_agent)
			}
		}
		
		private var cookieManager : CookieManager? = null
		private var cookieJar : CookieJar? = null
		
		private fun prepareOkHttp(
			timeoutSecondsConnect : Int,
			timeoutSecondsRead : Int
		) : OkHttpClient.Builder {
			
			var cookieJar = this.cookieJar
			if(cookieJar == null) {
				val cookieManager = CookieManager().apply {
					setCookiePolicy(CookiePolicy.ACCEPT_ALL)
				}
				CookieHandler.setDefault(cookieManager)
				cookieJar = JavaNetCookieJar(cookieManager)
				
				this.cookieManager = cookieManager
				this.cookieJar = cookieJar
			}
			
			val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
				.allEnabledCipherSuites()
				.allEnabledTlsVersions()
				.build()
			
			return OkHttpClient.Builder()
				.connectTimeout(timeoutSecondsConnect.toLong(), TimeUnit.SECONDS)
				.readTimeout(timeoutSecondsRead.toLong(), TimeUnit.SECONDS)
				.writeTimeout(timeoutSecondsRead.toLong(), TimeUnit.SECONDS)
				.pingInterval(10, TimeUnit.SECONDS)
				.connectionSpecs(Collections.singletonList(spec))
				.sslSocketFactory(MySslSocketFactory, MySslSocketFactory.trustManager)
				.addInterceptor(ProgressResponseBody.makeInterceptor())
				.addInterceptor(user_agent_interceptor)
			
			// クッキーの導入は検討中。とりあえずmstdn.jpではクッキー有効でも改善しなかったので現時点では追加しない
			//	.cookieJar(cookieJar)
			
		}
		
		lateinit var ok_http_client : OkHttpClient
		
		private lateinit var ok_http_client2 : OkHttpClient
		
		lateinit var ok_http_client_media_viewer : OkHttpClient
		
		lateinit var pref : SharedPreferences
		
		// lateinit var task_executor : ThreadPoolExecutor
		
		@SuppressLint("StaticFieldLeak")
		lateinit var custom_emoji_cache : CustomEmojiCache
		
		@SuppressLint("StaticFieldLeak")
		lateinit var custom_emoji_lister : CustomEmojiLister
		
		fun prepare(app_context : Context, caller : String) : AppState {
			var state = appStateX
			if(state != null) return state
			
			log.d("initialize AppState. caller=$caller")
			
			// initialize Conscrypt
			Security.insertProviderAt(
				Conscrypt.newProvider(),
				1 /* 1 means first position */
			)
			
			initializeFont()
			
			pref = Pref.pref(app_context)
			
			run {
				
				// We want at least 2 threads and at most 4 threads in the core pool,
				// preferring to have 1 less than the CPU count to avoid saturating
				// the CPU with background work
				
//				val CPU_COUNT = Runtime.getRuntime().availableProcessors()
//				val CORE_POOL_SIZE = max(2, min(CPU_COUNT - 1, 4))
//				val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
//				val KEEP_ALIVE_SECONDS = 30
				
//				// デフォルトだとキューはmax128で、溢れることがある
//				val sPoolWorkQueue = LinkedBlockingQueue<Runnable>(999)
//
//				val sThreadFactory = object : ThreadFactory {
//					private val mCount = AtomicInteger(1)
//
//					override fun newThread(r : Runnable) : Thread {
//						return Thread(r, "SubwayTooterTask #" + mCount.getAndIncrement())
//					}
//				}
				
				//				task_executor = ThreadPoolExecutor(
				//					CORE_POOL_SIZE  // pool size
				//					, MAXIMUM_POOL_SIZE // max pool size
				//					, KEEP_ALIVE_SECONDS.toLong() // keep-alive-seconds
				//					, TimeUnit.SECONDS // unit of keep-alive-seconds
				//					, sPoolWorkQueue, sThreadFactory
				//				)
				//
				//				task_executor.allowCoreThreadTimeOut(true)
			}
			
			
			log.d("prepareDB 1")
			db_open_helper = DBOpenHelper(app_context)
			
			//			if( BuildConfig.DEBUG){
			//				SQLiteDatabase db = db_open_helper.getWritableDatabase();
			//				db_open_helper.onCreate( db );
			//			}
			
			log.d("prepareDB 2")
			val now = System.currentTimeMillis()
			AcctSet.deleteOld(now)
			UserRelation.deleteOld(now)
			ContentWarning.deleteOld(now)
			MediaShown.deleteOld(now)
			
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
			
			log.d("create okhttp client")
			run {
				// API用のHTTP設定はキャッシュを使わない
				ok_http_client = prepareOkHttp(30, 60)
					.build()
				
				// ディスクキャッシュ
				val cacheDir = File(app_context.cacheDir, "http2")
				val cache = Cache(cacheDir, 30000000L)
				
				// カスタム絵文字用のHTTP設定はキャッシュを使う
				ok_http_client2 = prepareOkHttp(30, 60)
					.cache(cache)
					.build()
				
				// 内蔵メディアビューア用のHTTP設定はタイムアウトを調整可能
				val mediaReadTimeout = max(3, Pref.spMediaReadTimeout.toInt(pref))
				ok_http_client_media_viewer = prepareOkHttp(mediaReadTimeout, mediaReadTimeout)
					.cache(cache)
					.build()
			}
			
			log.d("create custom emoji cache.")
			custom_emoji_cache = CustomEmojiCache(app_context)
			
			custom_emoji_lister = CustomEmojiLister(app_context)
			
			ColumnType.dump()
			
			log.d("create  AppState.")
			
			state = AppState(app_context, pref)
			appStateX = state
			
			// getAppState()を使える状態にしてからカラム一覧をロードする
			log.d("load column list...")
			state.loadColumnList()
			
			log.d("prepare() complete! caller=$caller")
			
			return state
		}
		
		@SuppressLint("StaticFieldLeak")
		private var appStateX : AppState? = null
		
		fun getAppState(context : Context, caller : String = "getAppState") : AppState {
			return prepare(context.applicationContext, caller)
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
			// カスタムされたokhttpを優先的に使うためにprependを指定する
			registry.prepend(
				GlideUrl::class.java,
				InputStream::class.java,
				OkHttpUrlLoader.Factory(ok_http_client)
			)
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
			noActionBar : Boolean = false,
			forceDark : Boolean = false
		) {
			
			prepare(activity.applicationContext, "setActivityTheme")
			
			val theme_idx = Pref.ipUiTheme(pref)
			activity.setTheme(
				if(forceDark || theme_idx == 1) {
					if(noActionBar) R.style.AppTheme_Dark_NoActionBar else R.style.AppTheme_Dark
				} else {
					if(noActionBar) R.style.AppTheme_Light_NoActionBar else R.style.AppTheme_Light
				}
			)
			
			setStatusBarColor(activity, forceDark = forceDark)
		}
		
		internal val CACHE_CONTROL = CacheControl.Builder()
			.maxAge(1, TimeUnit.DAYS) // キャッシュが新鮮であると考えられる時間
			.build()
		
		fun getHttpCached(url : String) : ByteArray? {
			val response : Response
			
			try {
				val request_builder = Request.Builder()
					.cacheControl(CACHE_CONTROL)
					.url(url)
				
				val call = ok_http_client2.newCall(request_builder.build())
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
				response.body?.bytes()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp content error.")
				null
			}
			
		}
		
		fun getHttpCachedString(
			url : String,
			builderBlock : (Request.Builder) -> Unit = {}
		) : String? {
			val response : Response
			
			try {
				val request_builder = Request.Builder()
					.url(url)
					.cacheControl(CACHE_CONTROL)
				
				builderBlock(request_builder)
				
				val call = ok_http_client2.newCall(request_builder.build())
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
				response.body?.string()
			} catch(ex : Throwable) {
				log.e(ex, "getHttp content error.")
				null
			}
			
		}
		
		// returns true if activity is opened.
		// returns false if fallback required
		private fun startActivityExcludeMyApp(
			activity : AppCompatActivity,
			intent : Intent,
			startAnimationBundle : Bundle? = null
		) : Boolean {
			try {
				val pm = activity.packageManager !!
				val flags = PackageManager.MATCH_DEFAULT_ONLY
				val ri = pm.resolveActivity(intent, flags)
				if(ri != null && ri.activityInfo.packageName != activity.packageName) {
					// ST以外が選択された
					activity.startActivity(intent, startAnimationBundle)
					return true
				}
				val rv = DlgAppPicker(
					activity,
					intent,
					autoSelect = true,
					filter = { it.activityInfo.packageName != activity.packageName }
				) {
					try {
						intent.component = it.cn()
						activity.startActivity(intent, startAnimationBundle)
					} catch(ex : Throwable) {
						log.trace(ex)
						showToast(activity, ex, "can't open. ${intent.data}")
					}
				}.show()
				
				return rv
				
			} catch(ex : Throwable) {
				log.trace(ex)
				showToast(activity, ex, "can't open. ${intent.data}")
				return true // fallback not required in this case
			}
		}
		
		fun openBrowser(activity : AppCompatActivity, uri : Uri?) {
			if(uri != null) {
				val rv = startActivityExcludeMyApp(activity, Intent(Intent.ACTION_VIEW, uri))
				if(! rv) showToast(activity, true, "there is no app that can open $uri")
			}
		}
		
		fun openBrowser(activity : AppCompatActivity, url : String?) =
			openBrowser(activity, url.mayUri())
		
		// ubway Tooterの「アプリ設定/挙動/リンクを開く際にCustom Tabsを使わない」をONにして
		// 投稿のコンテキストメニューの「トゥートへのアクション/Webページを開く」「ユーザへのアクション/Webページを開く」を使うと
		// 投げたインテントをST自身が受け取って「次のアカウントから開く」ダイアログが出て
		// 「Webページを開く」をまた押すと無限ループしてダイアログの影が徐々に濃くなりそのうち壊れる
		// これを避けるには、投稿やトゥートを開く際に bpDontUseCustomTabs がオンならST以外のアプリを列挙したアプリ選択ダイアログを出すしかない
		fun openCustomTabOrBrowser(activity : AppCompatActivity, url : String) {
			if(! Pref.bpDontUseCustomTabs(pref)) {
				openCustomTab(activity, url)
			} else {
				openBrowser(activity, url)
			}
		}
		
		// Chrome Custom Tab を開く
		fun openCustomTab(activity : AppCompatActivity, url : String) {
			if(Pref.bpDontUseCustomTabs(pref)) {
				openCustomTabOrBrowser(activity, url)
				return
			}
			
			try {
				if(url.startsWith("http") && Pref.bpPriorChrome(pref)) {
					try {
						// 初回はChrome指定で試す
						val customTabsIntent = CustomTabsIntent.Builder()
							.setToolbarColor(getAttributeColor(activity, R.attr.colorPrimary))
							.setShowTitle(true)
							.build()
						
						val rv = startActivityExcludeMyApp(
							activity,
							customTabsIntent.intent.also {
								it.component = ComponentName(
									"com.android.chrome",
									"com.google.android.apps.chrome.Main"
								)
								it.data = url.toUri()
							},
							customTabsIntent.startAnimationBundle
						)
						if(rv) return
					} catch(ex2 : Throwable) {
						log.e(ex2, "openChromeTab: missing chrome. retry to other application.")
					}
				}
				
				// Chromeがないようなのでcomponent指定なしでリトライ
				val customTabsIntent = CustomTabsIntent.Builder()
					.setToolbarColor(getAttributeColor(activity, R.attr.colorPrimary))
					.setShowTitle(true)
					.build()
				
				val rv = startActivityExcludeMyApp(
					activity,
					customTabsIntent.intent.also {
						it.data = url.toUri()
					},
					customTabsIntent.startAnimationBundle
				)
				if(! rv) {
					showToast(activity, true, "the browser app is not installed.")
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
				val scheme = url.mayUri()?.scheme ?: url
				showToast(activity, true, "can't open browser app for %s", scheme)
			}
		}
		
		fun openCustomTab(activity : AppCompatActivity, ta : TootAttachment) {
			val url = ta.getLargeUrl(pref) ?: return
			openCustomTab(activity, url)
		}
		
		// https://developer.android.com/preview/features/gesturalnav?hl=ja
		fun initEdgeToEdge(@Suppress("UNUSED_PARAMETER") activity : Activity) {
			//			if(Build.VERSION.SDK_INT >= 29){
			//				val viewRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
			//				viewRoot.systemUiVisibility = (viewRoot.systemUiVisibility
			//					or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			//					or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
			//				viewRoot.setOnApplyWindowInsetsListener { v, insets ->
			//					insets.consumeSystemWindowInsets()
			//				}
			//			}
		}
		
		private fun rgbToLab(rgb : Int) : Triple<Float, Float, Float> {
			
			fun Int.revGamma() : Float {
				val v = toFloat() / 255f
				return when {
					v > 0.04045f -> ((v + 0.055f) / 1.055f).pow(2.4f)
					else -> v / 12.92f
				}
			}
			
			val r = Color.red(rgb).revGamma()
			val g = Color.green(rgb).revGamma()
			val b = Color.blue(rgb).revGamma()
			
			//https://en.wikipedia.org/wiki/Lab_color_space#CIELAB-CIEXYZ_conversions
			
			fun f(src : Float, k : Float) : Float {
				val v = src * k
				return when {
					v > 0.008856f -> v.pow(1f / 3f)
					else -> (7.787f * v) + (4f / 29f)
				}
			}
			
			val x = f(r * 0.4124f + g * 0.3576f + b * 0.1805f, 100f / 95.047f)
			val y = f(r * 0.2126f + g * 0.7152f + b * 0.0722f, 100f / 100f)
			val z = f(r * 0.0193f + g * 0.1192f + b * 0.9505f, 100f / 108.883f)
			
			return Triple(
				(116 * y) - 16, // L
				500 * (x - y), // a
				200 * (y - z) //b
			)
		}
		
		fun setStatusBarColor(activity : Activity, forceDark : Boolean = false) {
			
			activity.window?.apply {
				
				// 古い端末ではナビゲーションバーのアイコン色を設定できないため
				// メディアビューア画面ではステータスバーやナビゲーションバーの色を設定しない…
				if(forceDark && Build.VERSION.SDK_INT < 26) return
				
				clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
				clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
				addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
				
				var c = when {
					forceDark -> Color.BLACK
					else -> Pref.ipStatusBarColor(pref).notZero()
						?: getAttributeColor(activity, R.attr.colorPrimaryDark)
				}
				statusBarColor = c or Color.BLACK
				
				if(Build.VERSION.SDK_INT >= 23) {
					decorView.systemUiVisibility =
						if(rgbToLab(c).first >= 50f) {
							//Dark Text to show up on your light status bar
							decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
						} else {
							//Light Text to show up on your dark status bar
							decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
						}
				}
				
				c = when {
					forceDark -> Color.BLACK
					else -> Pref.ipNavigationBarColor(pref)
				}
				
				if(c != 0) {
					navigationBarColor = c or Color.BLACK
					
					if(Build.VERSION.SDK_INT >= 26) {
						decorView.systemUiVisibility =
							if(rgbToLab(c).first >= 50f) {
								//Dark Text to show up on your light status bar
								decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
							} else {
								//Light Text to show up on your dark status bar
								decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
							}
					}
				} // else: need restart app.
			}
		}
		
		fun setSwitchColor1(
			activity : AppCompatActivity,
			pref : SharedPreferences,
			view : Switch?
		) {
			fun mixColor(col1 : Int, col2 : Int) : Int = Color.rgb(
				(Color.red(col1) + Color.red(col2)) ushr 1,
				(Color.green(col1) + Color.green(col2)) ushr 1,
				(Color.blue(col1) + Color.blue(col2)) ushr 1
			)
			
			val colorBg = getAttributeColor(activity, R.attr.colorWindowBackground)
			
			val colorOn = Pref.ipSwitchOnColor(pref)
			
			val colorOff = /* Pref.ipSwitchOffColor(pref).notZero() ?: */
				getAttributeColor(activity, android.R.attr.colorPrimary)
			
			val colorDisabled = mixColor(colorBg, colorOff)
			
			val colorTrackDisabled = mixColor(colorBg, colorDisabled)
			val colorTrackOn = mixColor(colorBg, colorOn)
			val colorTrackOff = mixColor(colorBg, colorOff)
			
			// set Switch Color
			// https://stackoverflow.com/a/25635526/9134243
			val thumbStates = ColorStateList(
				arrayOf(
					intArrayOf(- android.R.attr.state_enabled),
					intArrayOf(android.R.attr.state_checked),
					intArrayOf()
				),
				intArrayOf(
					colorDisabled,
					colorOn,
					colorOff
				)
			)
			
			val trackStates = ColorStateList(
				arrayOf(
					intArrayOf(- android.R.attr.state_enabled),
					intArrayOf(android.R.attr.state_checked),
					intArrayOf()
				),
				intArrayOf(
					colorTrackDisabled,
					colorTrackOn,
					colorTrackOff
				)
			)
			
			view?.apply {
				if(Build.VERSION.SDK_INT < 23) {
					// android 5
					thumbDrawable?.setTintList(thumbStates)
					trackDrawable?.setTintList(thumbStates) // not trackState
				} else {
					// android 6
					thumbTintList = thumbStates
					if(Build.VERSION.SDK_INT >= 24) {
						// android 7
						trackTintList = trackStates
						trackTintMode = PorterDuff.Mode.SRC_OVER
					}
				}
			}
		}
		
		fun setSwitchColor(activity : AppCompatActivity, pref : SharedPreferences, root : View?) {
			
			fun mixColor(col1 : Int, col2 : Int) : Int = Color.rgb(
				(Color.red(col1) + Color.red(col2)) ushr 1,
				(Color.green(col1) + Color.green(col2)) ushr 1,
				(Color.blue(col1) + Color.blue(col2)) ushr 1
			)
			
			val colorBg = getAttributeColor(activity, R.attr.colorWindowBackground)
			
			val colorOn = Pref.ipSwitchOnColor(pref)
			
			val colorOff = /* Pref.ipSwitchOffColor(pref).notZero() ?: */
				getAttributeColor(activity, android.R.attr.colorPrimary)
			
			val colorDisabled = mixColor(colorBg, colorOff)
			
			val colorTrackDisabled = mixColor(colorBg, colorDisabled)
			val colorTrackOn = mixColor(colorBg, colorOn)
			val colorTrackOff = mixColor(colorBg, colorOff)
			
			// set Switch Color
			// https://stackoverflow.com/a/25635526/9134243
			val thumbStates = ColorStateList(
				arrayOf(
					intArrayOf(- android.R.attr.state_enabled),
					intArrayOf(android.R.attr.state_checked),
					intArrayOf()
				),
				intArrayOf(
					colorDisabled,
					colorOn,
					colorOff
				)
			)
			
			val trackStates = ColorStateList(
				arrayOf(
					intArrayOf(- android.R.attr.state_enabled),
					intArrayOf(android.R.attr.state_checked),
					intArrayOf()
				),
				intArrayOf(
					colorTrackDisabled,
					colorTrackOn,
					colorTrackOff
				)
			)
			
			root?.scan {
				(it as? Switch)?.apply {
					if(Build.VERSION.SDK_INT < 23) {
						// android 5
						thumbDrawable?.setTintList(thumbStates)
						trackDrawable?.setTintList(thumbStates) // not trackState
					} else {
						// android 6
						thumbTintList = thumbStates
						if(Build.VERSION.SDK_INT >= 24) {
							// android 7
							trackTintList = trackStates
							trackTintMode = PorterDuff.Mode.SRC_OVER
						}
					}
				}
			}
		}
	}
}
