package jp.juggler.subwaytooter.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import jp.juggler.subwaytooter.push.pushRepo
import jp.juggler.util.coroutine.EmptyScope
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.launch

class NotificationDeleteReceiver : BroadcastReceiver() {
    companion object {
        private val log = LogCategory("NotificationDeleteReceiver")
        fun Context.intentNotificationDelete(dataUri: Uri) =
            Intent(this, NotificationDeleteReceiver::class.java).apply {
                data = dataUri
            }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        EmptyScope.launch {
            try {
                val uri = intent?.data?.toString()
                log.i("onReceive uri=$uri")
                when {
                    uri == null -> Unit
                    uri.startsWith(NotificationChannels.PushMessage.uriPrefixDelete) ->
                        context.pushRepo.onDeleteNotification(uri)
                }
            } catch (ex: Throwable) {
                log.e(ex, "onReceive failed.")
            }
        }
    }
}
