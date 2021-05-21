package jp.juggler.util

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// kotlinx.coroutines 1.5.0 で GlobalScopeがdeprecated になったが、
// プロセスが生きてる間ずっと動いててほしいものや特にキャンセルのタイミングがないコルーチンでは使い続けたい
object EndlessScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}
