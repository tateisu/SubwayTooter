package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.subwaytooter.notification.TrackingType
import jp.juggler.subwaytooter.notification.onNotificationDeleted
import jp.juggler.subwaytooter.table.daoNotificationTracking
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.os.applicationContextSafe

class EventReceiver : BroadcastReceiver() {

    companion object {
        internal val log = LogCategory("EventReceiver")
        const val ACTION_NOTIFICATION_DELETE = "notification_delete"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        launchMain {
            try {
                log.i("onReceive action=${intent?.action}")

                when (val action = intent?.action) {

                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    -> {
                        App1.prepare(context.applicationContextSafe, action)
                        daoNotificationTracking.resetPostAll()
                    }

                    ACTION_NOTIFICATION_DELETE,
                    -> intent.data?.let { uri ->
                        val dbId = uri.getQueryParameter("db_id")?.toLongOrNull()
                        val type = TrackingType.parseStr(uri.getQueryParameter("type"))
                        val typeName = type.typeName
                        val id = uri.getQueryParameter("notificationId")?.notEmpty()
                        log.d("Notification deleted! db_id=$dbId,type=$type,id=$id")
                        if (dbId != null) {
                            onNotificationDeleted(dbId, typeName)
                        }
                    }

                    else -> log.e("onReceive: unsupported action $action")
                }
            } catch (ex: Throwable) {
                log.e(ex, "resetPostAll failed.")
            }
        }
    }
}
