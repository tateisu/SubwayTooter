package jp.juggler.base

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
 *
 * junit5対応について
 * https://stackoverflow.com/questions/69423060/viewmodel-ui-testing-with-junit-5
 */
@ExperimentalCoroutinesApi
class TestDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
        AppDispatchers.setTest(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        AppDispatchers.reset()
    }
}
