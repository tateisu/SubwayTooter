package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.model.GlideUrl
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.CustomEmojiCache
import jp.juggler.subwaytooter.util.CustomEmojiLister
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.util.*
import okhttp3.*
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import ru.gildor.coroutines.okhttp.await
import java.io.File
import java.io.InputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.Security
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max

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

    class DBOpenHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            for (ti in tableList) {
                ti.onDBCreate(db)
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            for (ti in tableList) {
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
        // 2020/6/8 46 => 54 別ブランチで色々してた。このブランチには影響ないが onDowngrade()を実装してないので上げてしまう
        // 2020/7/19 54=>55 UserRelation テーブルに項目追加。
        // 2020/9/7 55=>56 SavedAccountテーブルにCOL_DOMAINを追加。
        // 2020/9/20 56=>57 SavedAccountテーブルに項目追加
        // 2020/9/20 57=>58 UserRelationテーブルに項目追加
        // 2021/2/10 58=>59 SavedAccountテーブルに項目追加
        // 2021/5/11 59=>60 SavedAccountテーブルに項目追加
        // 2021/5/23 60=>61 SavedAccountテーブルに項目追加

        internal const val DB_VERSION = 61

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

        private lateinit var db_open_helper: DBOpenHelper

        val database: SQLiteDatabase get() = db_open_helper.writableDatabase

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

        private fun getUserAgent(): String {
            val userAgentCustom = PrefS.spUserAgent(pref)
            return when {
                userAgentCustom.isNotEmpty() && !reNotAllowedInUserAgent.matcher(userAgentCustom)
                    .find() -> userAgentCustom
                else -> userAgentDefault
            }
        }

        private val user_agent_interceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request_with_user_agent = chain.request().newBuilder()
                    .header("User-Agent", getUserAgent())
                    .build()
                return chain.proceed(request_with_user_agent)
            }
        }

        private var cookieManager: CookieManager? = null
        private var cookieJar: CookieJar? = null

        private fun prepareOkHttp(
            timeoutSecondsConnect: Int,
            timeoutSecondsRead: Int,
        ): OkHttpClient.Builder {

            var cookieJar = this.cookieJar
            if (cookieJar == null) {
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

        lateinit var ok_http_client: OkHttpClient

        private lateinit var ok_http_client2: OkHttpClient

        lateinit var ok_http_client_media_viewer: OkHttpClient

        lateinit var pref: SharedPreferences

        // lateinit var task_executor : ThreadPoolExecutor

        @SuppressLint("StaticFieldLeak")
        lateinit var custom_emoji_cache: CustomEmojiCache

        @SuppressLint("StaticFieldLeak")
        lateinit var custom_emoji_lister: CustomEmojiLister

        fun prepare(appContext: Context, caller: String): AppState {
            var state = appStateX
            if (state != null) return state

            log.d("initialize AppState. caller=$caller")

            // initialize EmojiMap
            EmojiMap.load(appContext)

            // initialize Conscrypt
            Security.insertProviderAt(
                Conscrypt.newProvider(),
                1 /* 1 means first position */
            )

            initializeFont()

            pref = appContext.pref()

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
            db_open_helper = DBOpenHelper(appContext)

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

                Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE

                // API用のHTTP設定はキャッシュを使わない
                ok_http_client = prepareOkHttp(60, 60)
                    .build()

                // ディスクキャッシュ
                val cacheDir = File(appContext.cacheDir, "http2")
                val cache = Cache(cacheDir, 30000000L)

                // カスタム絵文字用のHTTP設定はキャッシュを使う
                ok_http_client2 = prepareOkHttp(60, 60)
                    .cache(cache)
                    .build()

                // 内蔵メディアビューア用のHTTP設定はタイムアウトを調整可能
                val mediaReadTimeout = max(3, PrefS.spMediaReadTimeout.toInt(pref))
                ok_http_client_media_viewer = prepareOkHttp(mediaReadTimeout, mediaReadTimeout)
                    .cache(cache)
                    .build()
            }

            val handler = Handler(appContext.mainLooper)

            log.d("create custom emoji cache.")
            custom_emoji_cache = CustomEmojiCache(appContext, handler)
            custom_emoji_lister = CustomEmojiLister(appContext, handler)

            ColumnType.dump()

            log.d("create  AppState.")

            state = AppState(appContext, handler, pref)
            appStateX = state

            // getAppState()を使える状態にしてからカラム一覧をロードする
            log.d("load column list...")
            state.loadColumnList()

            log.d("prepare() complete! caller=$caller")

            return state
        }

        @SuppressLint("StaticFieldLeak")
        private var appStateX: AppState? = null

        fun getAppState(context: Context, caller: String = "getAppState"): AppState {
            return prepare(context.applicationContext, caller)
        }

        fun sound(item: HighlightWord) {
            try {
                appStateX?.sound(item)
            } catch (ex: Throwable) {
                log.trace(ex)
                // java.lang.NoSuchFieldError:
                // at jp.juggler.subwaytooter.App1$Companion.sound (App1.kt:544)
                // at jp.juggler.subwaytooter.Column$startRefresh$task$1.onPostExecute (Column.kt:2432)
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun registerGlideComponents(context: Context, glide: Glide, registry: Registry) {
            // カスタムされたokhttpを優先的に使うためにprependを指定する
            registry.prepend(
                GlideUrl::class.java,
                InputStream::class.java,
                OkHttpUrlLoader.Factory(ok_http_client)
            )
        }

        fun applyGlideOptions(context: Context, builder: GlideBuilder) {

            // ログレベル
            builder.setLogLevel(Log.ERROR)

            // エラー処理
            val catcher = GlideExecutor.UncaughtThrowableStrategy { ex ->
                log.trace(ex)
            }
            builder.setDiskCacheExecutor(
                GlideExecutor.newDiskCacheBuilder()
                    .setUncaughtThrowableStrategy(catcher).build()
            )
            builder.setSourceExecutor(
                GlideExecutor.newSourceBuilder()
                    .setUncaughtThrowableStrategy(catcher).build()
            )

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
            activity: AppCompatActivity,
            noActionBar: Boolean = false,
            forceDark: Boolean = false,
        ) {

            prepare(activity.applicationContext, "setActivityTheme")

            val theme_idx = PrefI.ipUiTheme(pref)
            activity.setTheme(
                if (forceDark || theme_idx == 1) {
                    if (noActionBar) R.style.AppTheme_Dark_NoActionBar else R.style.AppTheme_Dark
                } else {
                    if (noActionBar) R.style.AppTheme_Light_NoActionBar else R.style.AppTheme_Light
                }
            )

            activity.setStatusBarColor(forceDark = forceDark)
        }

        internal val CACHE_CONTROL = CacheControl.Builder()
            .maxAge(1, TimeUnit.DAYS) // キャッシュが新鮮であると考えられる時間
            .build()

        suspend fun getHttpCached(url: String): ByteArray? {
            val response: Response

            try {
                val request_builder = Request.Builder()
                    .cacheControl(CACHE_CONTROL)
                    .url(url)

                val call = ok_http_client2.newCall(request_builder.build())
                response = call.await()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp network error. $url")
                return null
            }

            if (!response.isSuccessful) {
                log.e(TootApiClient.formatResponse(response, "getHttp response error. $url"))
                return null
            }

            return try {
                response.body?.bytes()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp content error. $url")
                null
            }
        }

        suspend fun getHttpCachedString(
            url: String,
            accessInfo: SavedAccount? = null,
            builderBlock: (Request.Builder) -> Unit = {},
        ): String? {
            val response: Response

            try {
                val request_builder = Request.Builder()
                    .url(url)
                    .cacheControl(CACHE_CONTROL)

                val access_token = accessInfo?.getAccessToken()
                if (access_token?.isNotEmpty() == true) {
                    request_builder.header("Authorization", "Bearer $access_token")
                }

                builderBlock(request_builder)

                val call = ok_http_client2.newCall(request_builder.build())
                response = call.await()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp network error. $url")
                return null
            }

            if (!response.isSuccessful) {
                log.e(TootApiClient.formatResponse(response, "getHttp response error. $url"))
                return null
            }

            return try {
                response.body?.string()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp content error. $url")
                null
            }
        }

        // https://developer.android.com/preview/features/gesturalnav?hl=ja
        fun initEdgeToEdge(@Suppress("UNUSED_PARAMETER") activity: Activity) {
            //if(Build.VERSION.SDK_INT >= 29){
            //	val viewRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            //	viewRoot.systemUiVisibility = (viewRoot.systemUiVisibility
            //		or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            //		or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            //	viewRoot.setOnApplyWindowInsetsListener { v, insets ->
            //		insets.consumeSystemWindowInsets()
            //	}
            //}
        }
    }
}

val kJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
