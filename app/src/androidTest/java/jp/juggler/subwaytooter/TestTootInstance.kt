package jp.juggler.subwaytooter

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.SimpleHttpClientImpl
import jp.juggler.util.LogCategory
import jp.juggler.util.MySslSocketFactory
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TestTootInstance {

    companion object {
        private val log = LogCategory("TestTootInstance")

//        val cookieJar = JavaNetCookieJar(CookieManager().apply {
//            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
//            CookieHandler.setDefault(this)
//        })

        private val okHttp = OkHttpClient.Builder()
            .connectTimeout(60.toLong(), TimeUnit.SECONDS)
            .readTimeout(60.toLong(), TimeUnit.SECONDS)
            .writeTimeout(60.toLong(), TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .connectionSpecs(Collections.singletonList(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .allEnabledCipherSuites()
                .allEnabledTlsVersions()
                .build()))
            .sslSocketFactory(MySslSocketFactory, MySslSocketFactory.trustManager)
            .build()

        private val dummyClientCallback = object : TootApiCallback {
            override val isApiCancelled: Boolean = false
            override suspend fun publishApiProgress(s: String) {
                log.d("apiProgress: $s")
            }

            override suspend fun publishApiProgressRatio(value: Int, max: Int) {
                log.d("apiProgressRatio: $value/$max")
            }
        }

        private val appContext = InstrumentationRegistry.getTargetContext()!!

        val client = TootApiClient(
            context = appContext,
            httpClient =SimpleHttpClientImpl(appContext, okHttp),
            callback = dummyClientCallback
        )
    }

    /*
        TootInstance.get() はアカウントを指定する場合とホストを指定する場合がある
        ホスト指定とアカウント指定、 MastodonとMisskey で試す
    */

    @Test
    fun testWithoutAccount() {
        runBlocking {
            withContext(Dispatchers.IO){
                suspend fun a(host:Host){
                    val (ti,ri) =  TootInstance.get(client,host )
                    assertNotNull(ti)
                    assertNull(ri?.error)
                    ti!!.run{ log.d("${instanceType} ${uri} ${version}")}

                }
                a(Host.parse("mastodon.juggler.jp"))
                a(Host.parse("misskey.io"))
            }
        }
    }

    @Test
    fun testWithAccount() {
        runBlocking {
            withContext(Dispatchers.IO){
                suspend fun a(account:SavedAccount){
                    val (ti,ri) =  TootInstance.get(client,account = account )
                    assertNull(ri?.error)
                    assertNotNull(ti)
                    ti!!.run{ log.d("${account.acct} ${instanceType} ${uri} ${version}")}
                }
                a( SavedAccount(45,"tateisu@mastodon.juggler.jp") )
                a( SavedAccount(45,"tateisu@misskey.io",misskeyVersion=12) )
            }
        }
    }
}