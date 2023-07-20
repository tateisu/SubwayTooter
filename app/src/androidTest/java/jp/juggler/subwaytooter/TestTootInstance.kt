package jp.juggler.subwaytooter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.testutil.TestDispatcherRule
import jp.juggler.subwaytooter.testutil.MockInterceptor
import jp.juggler.subwaytooter.util.SimpleHttpClientImpl
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.test.runTest
import okhttp3.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class TestTootInstance {

    companion object {
        private val log = LogCategory("TestTootInstance")
    }

    // テスト毎に書くと複数テストで衝突するので、MainDispatcherRuleに任せる
    // プロパティは記述順に初期化されることに注意
    @get:Rule
    val mainDispatcherRule = TestDispatcherRule()

    private val client by lazy {
        val mockInterceptor = MockInterceptor(
            // テストアプリのコンテキスト
            context = InstrumentationRegistry.getInstrumentation().context!!,
            // テストアプリ中のリソースID
            rawId = jp.juggler.subwaytooter.test.R.raw.test_toot_instance_mock,
        )

        val okHttp = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()

        val dummyClientCallback = object : TootApiCallback {
            override suspend fun isApiCancelled() = false

            override suspend fun publishApiProgress(s: String) {
                log.d("apiProgress: $s")
            }

            override suspend fun publishApiProgressRatio(value: Int, max: Int) {
                log.d("apiProgressRatio: $value/$max")
            }
        }

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext!!
        TootApiClient(
            context = appContext,
            httpClient = SimpleHttpClientImpl(appContext, okHttp),
            callback = dummyClientCallback
        )
    }

    /*
        TootInstance.get() はアカウントを指定する場合とホストを指定する場合がある
        ホスト指定とアカウント指定、 MastodonとMisskey で試す
     */

    @Test
    fun instanceByHostname() = runTest {
        suspend fun a(host: Host) {
            val (ti, ri) = TootInstance.getEx(client, hostArg = host)
            assertNull("no error", ri?.error)
            assertNotNull("instance information", ti)
            ti?.run { log.d("$instanceType $apDomain $version") }
        }
        a(Host.parse("mastodon.juggler.jp"))
        a(Host.parse("misskey.io"))
    }

    @Test
    fun testWithAccount() = runTest {
        suspend fun a(account: SavedAccount) {
            val (ti, ri) = TootInstance.getEx(client, account = account)
            assertNull(ri?.error)
            assertNotNull(ti)
            ti?.run { log.d("${account.acct} $instanceType $apDomain $version") }
        }
        a(SavedAccount(45, "tateisu@mastodon.juggler.jp"))
        a(SavedAccount(45, "tateisu@misskey.io", misskeyVersion = 12))
    }
}
