package jp.juggler.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.juggler.util.coroutine.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * kotlinx.coroutines.test の使い方の説明
 * https://developer.android.com/kotlin/coroutines/test?hl=ja#testdispatchers
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DispatchersTest {

    // 単純なリポジトリ
    private class UserRepository {
        val names = ArrayList<String>()
        fun register(name: String) = names.add(name)
        fun getAllUsers(): List<String> = names
    }

    // Dispatcherを受け取るリポジトリ
    private class Repository(
        private val ioDispatcher: CoroutineDispatcher = AppDispatchers.IO,
    ) {
        private val ioScope = CoroutineScope(ioDispatcher)
        val initialized = AtomicBoolean(false)

        // A function that starts a new coroutine on the IO dispatcher
        fun initializeAsync() = ioScope.async {
            delay(100L)
            initialized.set(true)
        }

        // A suspending function that switches to the IO dispatcher
        suspend fun fetchData(): String = withContext(ioDispatcher) {
            require(initialized.get()) { "Repository should be initialized first" }
            delay(500L)
            "Hello world"
        }
    }

    // プロパティの定義順序に注意
    @get:Rule
    val dispatcheRule = AppTestDispatcherRule()

    // リポジトリのスケジューラを共有する
    private val repository = Repository(dispatcheRule.testDispatcher)

    //====================================================
    // テストでの suspend 関数の呼び出し
    // runTestを使う

    private suspend fun fetchData(): String {
        delay(1000L)
        return "Hello world"
    }

    @Test
    fun useRunTest() = runTest {
        assertEquals("Hello world", fetchData())
    }

    //====================================================
    // launch内部の処理を待つテストコード

    @Test
    fun useAdvanceUntilIdle() = runTest {
        val userRepo = UserRepository()
        launch { userRepo.register("Alice") }
        launch { userRepo.register("Bob") }
        advanceUntilIdle() // Yields to perform the registrations
        assertEquals(listOf("Alice", "Bob"), userRepo.getAllUsers()) // ✅ Passes
    }

    //==============================================
    // UnconfinedTestDispatcher を使うとlaunch内部が先に実行開始する
    // ただしlaunch内部で非同期待機が入ると外側の実行が再開される

    @Test
    fun useUnconfinedTestDispatcher() = runTest(UnconfinedTestDispatcher()) {
        val userRepo = UserRepository()
        launch { userRepo.register("Alice") }
        launch { userRepo.register("Bob") }
        assertEquals(listOf("Alice", "Bob"), userRepo.getAllUsers()) // ✅ Passes
    }

    // =============================================
    // viewModelScopeなどが使うディスパッチャーを差し替える

    class HomeViewModel : ViewModel() {
        private val _message = MutableStateFlow("")
        val message: StateFlow<String> get() = _message

        fun loadMessage() {
            viewModelScope.launch {
                _message.value = "Greetings!"
            }
        }
    }

    @Test
    fun useDispatchersSetMain() = runTest {
        // MainDispatcherRule を指定しているので、viewModelが使う Dispatcher が変わる
        val viewModel = HomeViewModel()
        viewModel.loadMessage()
        assertEquals("Greetings!", viewModel.message.value)
    }

    // =============================================================
    // リポジトリクラスにDispatcherを渡せるようにする

    @Test
    fun useRepoWithTestDispatcher() = runTest {
        val repository = Repository(
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.initializeAsync().await()
        assertEquals(true, repository.initialized.get())
        assertEquals("Hello world", repository.fetchData())
    }

    //=======================================================
    // プロパティ間でスケジューラを共有する

    @Test
    fun someRepositoryTest() = runTest {
        // Takes scheduler from Main

        // Any TestDispatcher created here also takes the scheduler from Main
        // val newTestDispatcher = StandardTestDispatcher()

        // これもStandardTestDispatcher を作成する
        // 注意: 独自の TestScope を作成する場合は、テスト内のそのスコープで runTest を呼び出す必要があります。
        // テストには TestScope インスタンスを 1 つだけ含めることができます。
        // val testScope = TestScope()

        repository.initializeAsync().await()
        assertEquals(true, repository.initialized.get())
        assertEquals("Hello world", repository.fetchData())
    }

    //=======================================================
    // DI
    // クラス内に以下のようなプロパティを定義しておくこともできる。
    // DIする際は参考になるかもしれない。
    // val testScheduler = TestCoroutineScheduler()
    // val testDispatcher = StandardTestDispatcher(testScheduler)
    // val testScope = TestScope(testDispatcher)
    //
    // fun xxx() = testScope.runTest{ ... }
}
