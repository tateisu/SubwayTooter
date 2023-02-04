package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R
import jp.juggler.util.log.LogCategory
import jp.juggler.util.systemService

object ServerTimeoutNotification {

    private val log = LogCategory("ServerTimeoutNotification")

    fun createServerTimeoutNotification(
        context: Context,
        accounts: String,
    ) {
        val notificationManager: NotificationManager = systemService(context)!!

        val nc = NotificationChannels.ServerTimeout

        // 通知タップ時のPendingIntent
        val iTap = Intent(context, ActCallback::class.java).apply {
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val clickPi = PendingIntent.getActivity(
            context,
            nc.pircTap,
            iTap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, nc.id)

        val header = context.getString(R.string.error_notification_title)
        val summary = context.getString(R.string.error_notification_summary)

        // ここは常に白テーマのアイコンを使う
        // ここは常に白テーマの色を使う
        builder.apply {
            priority = nc.priority
            setContentIntent(clickPi)
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_notification)
            color = ContextCompat.getColor(context, R.color.colorOsNotificationAccent)
            setWhen(System.currentTimeMillis())
            setGroup(context.packageName + ":" + "Error")
            setContentTitle(header)
            setContentText("$summary: $accounts")
        }
        notificationManager.notify(nc.notificationId, builder.build())
    }
}
