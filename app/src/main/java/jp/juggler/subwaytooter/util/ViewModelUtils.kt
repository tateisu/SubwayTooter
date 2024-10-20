package jp.juggler.subwaytooter.util

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ViewModelのfactoryを毎回書くのが面倒
// あと使わない場合にはViewModelの引数を生成したくない
fun <VM : ViewModel> viewModelFactory(vmClass: Class<VM>, creator: () -> VM) =
    object : ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(vmClass)) {
                error("unexpected modelClass. ${modelClass.simpleName}")
            }
            return creator() as T
        }
    }

// ViewModelProvider(…).get を毎回書くのが面倒
inline fun <reified T : ViewModel> provideViewModel(
    owner: ViewModelStoreOwner,
    noinline creator: () -> T,
) = ViewModelProvider(owner, viewModelFactory(T::class.java, creator))[T::class.java]

inline fun <reified T : ViewModel> provideViewModel(
    owner: ViewModelStoreOwner,
    key: String,
    noinline creator: () -> T,
) = ViewModelProvider(owner, viewModelFactory(T::class.java, creator))[key, T::class.java]

fun <T : Any?> AppCompatActivity.collectOnLifeCycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit,
) = lifecycleScope.launch {
    lifecycle.repeatOnLifecycle(state = state) {
        flow.collect { block(it) }
    }
}

fun <T : Any?> ComponentActivity.collectOnLifeCycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit,
) = lifecycleScope.launch {
    lifecycle.repeatOnLifecycle(state = state) {
        flow.collect { block(it) }
    }
}
