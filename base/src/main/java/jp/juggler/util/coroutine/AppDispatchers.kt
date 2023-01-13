package jp.juggler.util.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    var unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    var default: CoroutineDispatcher = Dispatchers.Default
    var io: CoroutineDispatcher = Dispatchers.IO
    var main: CoroutineDispatcher = Dispatchers.Main
    var mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
}
