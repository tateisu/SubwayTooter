package jp.juggler.subwaytooter.dialog

import android.content.Context
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resumeWithException

class ActionsDialogInitializer(
    val title: CharSequence? = null,
) {
    class Action(val caption: CharSequence, val action: suspend () -> Unit)

    val list = ArrayList<Action>()

    fun action(caption: CharSequence, action: suspend () -> Unit) {
        list.add(Action(caption, action))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun showSuspend(context: Context): Action =
        suspendCancellableCoroutine { cont ->
            val dialog = android.app.AlertDialog.Builder(context).apply {
                title?.notEmpty()?.let { setTitle(it) }
                setNegativeButton(android.R.string.cancel, null)
                setItems(list.map { it.caption }.toTypedArray()) { d, i ->
                    if (cont.isActive) cont.resume(list[i]) {}
                    d.dismissSafe()
                }
                setOnDismissListener {
                    if (cont.isActive) cont.resumeWithException(CancellationException())
                }
            }.create()
            cont.invokeOnCancellation { dialog.dismissSafe() }
            dialog.show()
        }
}

suspend fun Context.actionsDialog(
    title: String? = null,
    init: suspend ActionsDialogInitializer.() -> Unit,
) {
    ActionsDialogInitializer(title)
        .apply { init() }
        .showSuspend(this)
        .action.invoke()
}
