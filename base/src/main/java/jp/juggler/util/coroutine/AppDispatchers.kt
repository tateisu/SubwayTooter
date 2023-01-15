package jp.juggler.util.coroutine

import kotlinx.coroutines.*

/**
 * Test時にdispatcherを差し替えられるようにする
 *
 * https://developer.android.com/kotlin/coroutines/test?hl=ja#testdispatchers
 * - TestDispatcher やrunTest を使う
 * - Dispatchers.setMain(testDispatcher) や Dispatchers.resetMain() でMainを切り替えられる
 *   - viewModelScope.launch{} などが使うMainを切り替えられる
 *
 * リポジトリクラスの引数に CoroutineDispatcherを渡すとかもある
 */
object AppDispatchers {

    // Main と Main.immediate は Dispatchers.setMain 差し替えられる
    val mainImmediate get() = Dispatchers.Main.immediate

    var unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    var default: CoroutineDispatcher = Dispatchers.Default
    var io: CoroutineDispatcher = Dispatchers.IO

    fun reset() {
        unconfined = Dispatchers.Unconfined
        default = Dispatchers.Default
        io = Dispatchers.IO
    }

    fun setTest(testDispatcher: CoroutineDispatcher) {
        unconfined = testDispatcher
        default = testDispatcher
        io = testDispatcher
    }

    /**
     * withTimeout はrunTest内部だと即座に例外を出すので、
     * テスト中はタイムアウトをチェックしないようにする
     * https://stackoverflow.com/questions/70658926/how-to-use-kotlinx-coroutines-withtimeout-in-kotlinx-coroutines-test-runtest
     */
    suspend fun <T> withTimeoutSafe(timeMillis: Long, block: suspend CoroutineScope.() -> T) =
        when (io) {
            Dispatchers.IO -> withTimeout(timeMillis, block)
            else -> coroutineScope { block() }
        }
}
