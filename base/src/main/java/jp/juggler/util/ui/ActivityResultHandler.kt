package jp.juggler.util.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast

class ActivityResultHandler(
    private val log: LogCategory,
    private val callback: (ActivityResult) -> Unit,
) {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var getContext: (() -> Context?)? = null

    val context
        get() = getContext?.invoke()

    // startForActivityResultの代わりに呼び出す
    fun launch(intent: Intent, options: ActivityOptionsCompat? = null) = try {
        (launcher ?: error("ActivityResultHandler not registered."))
            .launch(intent, options)
    } catch (ex: Throwable) {
        log.e(ex, "launch failed")
        context?.showToast(ex, "activity launch failed.")
    }

    // onCreate時に呼び出す
    fun register(a: FragmentActivity) {
        getContext = { a.applicationContext }
        this.launcher = a.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { callback(it) }
    }
    // onCreate時に呼び出す
    fun register(a: ComponentActivity) {
        getContext = { a.applicationContext }
        this.launcher = a.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { callback(it) }
    }
}

fun Intent.launch(ar: ActivityResultHandler) = ar.launch(this)

val ActivityResult.isNotOk
    get() = Activity.RESULT_OK != resultCode

val ActivityResult.isOk
    get() = Activity.RESULT_OK == resultCode
