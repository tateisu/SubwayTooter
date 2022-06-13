package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.subwaytooter.notification.TrackingType
import jp.juggler.subwaytooter.notification.onNotificationDeleted
import jp.juggler.subwaytooter.table.NotificationTracking
import jp.juggler.util.LogCategory
import jp.juggler.util.launchMain
import jp.juggler.util.notEmpty

class EventReceiver : BroadcastReceiver() {

    companion object {
        internal val log = LogCategory("EventReceiver")
        const val ACTION_NOTIFICATION_DELETE = "notification_delete"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (val action = intent?.action) {

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                App1.prepare(context.applicationContext, action)
                NotificationTracking.resetPostAll()
            }

            ACTION_NOTIFICATION_DELETE -> intent.data?.let { uri ->
                val dbId = uri.getQueryParameter("db_id")?.toLongOrNull()
                val type = TrackingType.parseStr(uri.getQueryParameter("type"))
                val typeName = type.typeName
                val id = uri.getQueryParameter("notificationId")?.notEmpty()
                log.d("Notification deleted! db_id=$dbId,type=$type,id=$id")
                if (dbId != null) {
                    launchMain {
                        onNotificationDeleted(dbId, typeName)
                    }
                }
            }

            else -> log.e("onReceive: unsupported action $action")
        }
    }
}
