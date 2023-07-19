package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.model.GlideUrl
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.pref.LazyContextHolder
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.CustomEmojiCache
import jp.juggler.subwaytooter.util.CustomEmojiLister
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.subwaytooter.util.getUserAgent
import jp.juggler.util.*
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.initializeToastUtils
import jp.juggler.util.network.MySslSocketFactory
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.os.applicationContextSafe
import jp.juggler.util.ui.*
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
        LazyContextHolder.init(applicationContextSafe)
        super.onCreate()
        initializeToastUtils(this)
        prepare(applicationContext, "App1.onCreate")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LazyContextHolder.init(applicationContextSafe)
    }

    override fun onTerminate() {
        log.d("onTerminate")
        super.onTerminate()
    }

    companion object {

        internal val log = LogCategory("App1")

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

        private var cookieManager: CookieManager? = null
        private var cookieJar: CookieJar? = null

        private fun Context.userAgentInterceptor() =
            Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", getUserAgent())
                        .build()
                )
            }

        private fun Context.prepareOkHttp(
            timeoutSecondsConnect: Int,
            timeoutSecondsRead: Int,
        ): OkHttpClient.Builder {

            Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE

            var cookieJar = this@Companion.cookieJar
            if (cookieJar == null) {
                val cookieManager = CookieManager().apply {
                    setCookiePolicy(CookiePolicy.ACCEPT_ALL)
                }
                CookieHandler.setDefault(cookieManager)
                cookieJar = JavaNetCookieJar(cookieManager)

                this@Companion.cookieManager = cookieManager
                this@Companion.cookieJar = cookieJar
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
                .addInterceptor(userAgentInterceptor())

            // クッキーの導入は検討中。とりあえずmstdn.jpではクッキー有効でも改善しなかったので現時点では追加しない
            //	.cookieJar(cookieJar)
        }

        lateinit var ok_http_client: OkHttpClient

        private lateinit var ok_http_client2: OkHttpClient

        lateinit var ok_http_client_media_viewer: OkHttpClient

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

            // emoji2 はデフォルトで自動初期化を行うのだが、新し目のPlayサービスに依存してるため
            // Playサービスが古い端末ではEmojiCompatの初期化がまだ行われていない状態になる
            // ワークアラウンドとして、アプリ内にバンドルしたデータを使うBundledEmojiCompatConfigで初期化する
            // (初期化が既に行われている場合は無害である)
            EmojiCompat.init(BundledEmojiCompatConfig(appContext))

            // initialize Conscrypt
            Security.insertProviderAt(
                Conscrypt.newProvider(),
                1 /* 1 means first position */
            )

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

                val apiReadTimeout = max(3, PrefS.spApiReadTimeout.toInt())

                // API用のHTTP設定はキャッシュを使わない
                ok_http_client = appContext.prepareOkHttp(apiReadTimeout, apiReadTimeout)
                    .build()

                // ディスクキャッシュ
                val cacheDir = File(appContext.cacheDir, "http2")
                val cache = Cache(cacheDir, 30000000L)

                // カスタム絵文字用のHTTP設定はキャッシュを使う
                ok_http_client2 = appContext.prepareOkHttp(apiReadTimeout, apiReadTimeout)
                    .cache(cache)
                    .build()

                // 内蔵メディアビューア用のHTTP設定はタイムアウトを調整可能
                val mediaReadTimeout = max(3, PrefS.spMediaReadTimeout.toInt())
                ok_http_client_media_viewer =
                    appContext.prepareOkHttp(mediaReadTimeout, mediaReadTimeout)
                        .cache(cache)
                        .build()
            }

            val handler = Handler(appContext.mainLooper)

            log.d("create custom emoji cache.")
            custom_emoji_cache = CustomEmojiCache(appContext, handler)
            custom_emoji_lister = CustomEmojiLister(appContext, handler)

            ColumnType.dump()

            log.d("create  AppState.")

            state = AppState(appContext, handler)
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
                log.e(ex, "sound failed.")
                // java.lang.NoSuchFieldError:
                // at jp.juggler.subwaytooter.App1$Companion.sound (App1.kt:544)
                // at jp.juggler.subwaytooter.column.Column$startRefresh$task$1.onPostExecute (Column.kt:2432)
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
                log.e(ex, "glide uncaught error.")
            }
            builder.setDiskCacheExecutor(
                GlideExecutor.newDiskCacheBuilder()
                    .setUncaughtThrowableStrategy(catcher).build()
            )
            builder.setSourceExecutor(
                GlideExecutor.newSourceBuilder()
                    .setUncaughtThrowableStrategy(catcher).build()
            )

            builder.setDiskCache(InternalCacheDiskCacheFactory(context, 10L * 1024L * 1024L))

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
            forceDark: Boolean = false,
        ) {
            prepare(activity.applicationContext, "setActivityTheme")

            var nTheme = PrefI.ipUiTheme.value
            if (forceDark && nTheme == 0) nTheme = 1
            activity.setTheme(
                when (nTheme) {
                    2 -> R.style.AppTheme_Mastodon
                    1 -> R.style.AppTheme_Dark
                    else -> R.style.AppTheme_Light
                }
            )
            activity.setStatusBarColor(forceDark = forceDark)
        }

        internal val CACHE_CONTROL = CacheControl.Builder()
            .maxAge(1, TimeUnit.DAYS) // キャッシュが新鮮であると考えられる時間
            .build()

        suspend fun getHttpCached(url: String): ByteArray? {
            val caller = RuntimeException("caller's stackTrace.")
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
                log.e(
                    caller,
                    TootApiClient.formatResponse(response, "getHttp response error. $url")
                )
                return null
            }

            return try {
                response.body.bytes()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp content error. $url")
                null
            }
        }

        suspend fun getHttpCachedString(
            url: String,
            accessInfo: SavedAccount? = null,
            misskeyPost: Boolean = false,
            builderBlock: (Request.Builder) -> Unit = {},
        ): String? {
            val response: Response

            try {
                val request_builder = when {
                    misskeyPost && accessInfo?.isMisskey == true ->
                        accessInfo.putMisskeyApiToken().toPostRequestBuilder()
                            .url(url)
                            .cacheControl(CACHE_CONTROL)

                    else ->
                        Request.Builder()
                            .url(url)
                            .cacheControl(CACHE_CONTROL)
                            .also {
                                accessInfo?.bearerAccessToken?.notEmpty()?.let { a ->
                                    it.header("Authorization", "Bearer $a")
                                }
                            }
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
                response.body.string()
            } catch (ex: Throwable) {
                log.e(ex, "getHttp content error. $url")
                null
            }
        }
    }
}

val kJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
