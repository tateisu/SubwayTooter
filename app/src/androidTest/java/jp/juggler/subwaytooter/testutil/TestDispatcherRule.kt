package jp.juggler.subwaytooter.testutil

import jp.juggler.util.coroutine.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Dispatchers.Main のテスト中の置き換えを複数テストで衝突しないようにルール化する
 * https://developer.android.com/kotlin/coroutines/test?hl=ja
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherRule(
    /**
     * UnconfinedTestDispatcher か StandardTestDispatcher のどちらかを指定する
     */
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
        AppDispatchers.setTest(testDispatcher)
    }

    override fun finished(description: Description) {
        AppDispatchers.reset()
        Dispatchers.resetMain()
    }
}
