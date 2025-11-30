package jp.juggler.subwaytooter.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R

fun createServerTimeoutNotification(
    context: Context,
    accounts: String,
) {
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

    // ここは常に白テーマのアイコンを使う
    // ここは常に白テーマの色を使う
    nc.notify(context) {
        priority = nc.priority
        setContentIntent(clickPi)
        setAutoCancel(true)
        setSmallIcon(R.drawable.ic_notification)
        color = ContextCompat.getColor(context, R.color.colorOsNotificationAccent)
        setWhen(System.currentTimeMillis())
        setGroup(context.packageName + ":" + "Error")
        val header = context.getString(R.string.error_notification_title)
        val summary = context.getString(R.string.error_notification_summary)
        setContentTitle(header)
        setContentText("$summary: $accounts")
    }
}
