package jp.juggler.subwaytooter.notification

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import jp.juggler.subwaytooter.ActAlert.Companion.intentActAlert
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.notification.NotificationDeleteReceiver.Companion.intentNotificationDelete
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = LogCategory("AlertNotification")

/**
 * トーストの代わりに使えるような、単純なメッセージを表示する通知
 */

fun Context.showAlertNotification(
    message: String,
    title: String = getString(R.string.alert),
) {
    if (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        log.w("missing POST_NOTIFICATIONS. alert=$message")
        return
    }

    val nc = NotificationChannels.Alert

    val now = System.currentTimeMillis()
    val tag = "${System.currentTimeMillis()}/${message.hashCode()}"
    val uri = "${nc.uriPrefixDelete}/$tag"

    // Create an explicit intent for an Activity in your app
    val iTap = intentActAlert(tag = tag, message = message, title = title)
    val iDelete = intentNotificationDelete(uri.toUri())
    val piTap = PendingIntent.getActivity(this, nc.pircTap, iTap, PendingIntent.FLAG_IMMUTABLE)
    val piDelete =
        PendingIntent.getBroadcast(this, nc.pircDelete, iDelete, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(this, nc.id).apply {
        priority = nc.priority
        setSmallIcon(R.drawable.ic_error)
        setContentTitle(title)
        setContentText(message)
        setWhen(now)
        setContentIntent(piTap)
        setDeleteIntent(piDelete)
        setAutoCancel(true)
    }

    NotificationManagerCompat.from(this).notify(tag, nc.notificationId, builder.build())
}

fun Context.dialogOrAlert(message: String) {
    try {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    } catch (_: Throwable) {
        showAlertNotification(message)
    }
}

fun Context.showError(ex: Throwable, message: String) {
    when (ex) {
        is CancellationException -> Unit
        is IllegalStateException -> {
            log.e(ex, message)
            dialogOrAlert(ex.message ?: ex.cause?.message ?: "?")
        }
        else -> {
            log.e(ex, message)
            dialogOrAlert(ex.withCaption(message))
        }
    }
}

//fun AppCompatActivity.launchAndShowError(
//    context: CoroutineContext = EmptyCoroutineContext,
//    block: suspend () -> Unit,
//) = lifecycleScope.launch(context) {
//    try {
//        block()
//    } catch (ex: Throwable) {
//        showError(ex, "")
//    }
//}
