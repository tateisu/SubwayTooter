package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.juggler.subwaytooter.databinding.DlgSuspendProgressBinding
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.vg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val log = LogCategory("SuspendProgress")

class SuspendProgress(val activity: AppCompatActivity) {

    private val views = DlgSuspendProgressBinding.inflate(activity.layoutInflater)
    private val dialog = Dialog(activity)

    suspend fun <T : Any?> run(
        message: String,
        title: String,
        cancellable: Boolean,
        block: suspend (Reporter) -> T?,
    ): T? = Reporter().use { reporter ->
        try {
            dialog.setContentView(views.root)
            reporter.setMessage(message)
            reporter.setTitle(title)
            dialog.setCancelable(cancellable)
            dialog.setCanceledOnTouchOutside(cancellable)
            dialog.show()
            block(reporter)
        } finally {
            dialog.dismissSafe()
        }
    }

    inner class Reporter : AutoCloseable {
        private val flowMessage = MutableStateFlow<CharSequence>("")
        private val flowTitle = MutableStateFlow<CharSequence>("")

        private val jobMessage = activity.lifecycleScope.launch(AppDispatchers.MainImmediate) {
            try {
                flowMessage.collect {
                    views.tvMessage.vg(it.isNotEmpty())?.text = it
                }
            } catch (ex: Throwable) {
                when (ex) {
                    is CancellationException, is ClosedReceiveChannelException -> Unit
                    else -> log.w(ex, "error.")
                }
            }
        }
        private val jobTitle = activity.lifecycleScope.launch(AppDispatchers.MainImmediate) {
            try {
                flowTitle.collect {
                    views.tvTitle.vg(it.isNotEmpty())?.text = it
                }
            } catch (ex: Throwable) {
                when (ex) {
                    is CancellationException, is ClosedReceiveChannelException -> Unit
                    else -> log.w(ex, "error.")
                }
            }
        }

        override fun close() {
            jobMessage.cancel()
            jobTitle.cancel()
        }

        fun setMessage(msg: CharSequence) {
            flowMessage.value = msg
        }

        fun setTitle(title: CharSequence) {
            flowTitle.value = title
        }
    }
}

suspend fun <T : Any?> AppCompatActivity.runInProgress(
    message: String = "please wait…",
    title: String = "",
    cancellable: Boolean = true,
    block: suspend (SuspendProgress.Reporter) -> T?,
): T? = SuspendProgress(this).run(
    message = message,
    title = title,
    cancellable = cancellable,
    block = block
)
