package jp.juggler.subwaytooter.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.util.log.LogCategory

object CheckerNotification {

    private val log = LogCategory("CheckerNotification")

    private val nc = NotificationChannels.Checker

    private var lastMessage: String? = null

    suspend fun showMessage(
        context: Context,
        text: String,
        shower: suspend (Notification, NotificationChannels) -> Unit,
    ) {
        // テキストが変化していないなら更新しない
        if (text.isEmpty() || text == lastMessage) return

        lastMessage = text
        log.i(text)

        // 通知タップ時のPendingIntent
        val iTap = Intent(context, ActMain::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val piTap = PendingIntent.getActivity(
            context,
            nc.pircTap,
            iTap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ここは常に白テーマのアイコンと色を使う
        val builder = NotificationCompat.Builder(context, nc.id).apply {
            priority = nc.priority
            setContentIntent(piTap)
            setAutoCancel(false)
            setOngoing(true)
            setSmallIcon(R.drawable.ic_notification)
            color = ContextCompat.getColor(context, R.color.colorOsNotificationAccent)
            setWhen(System.currentTimeMillis())
            setContentTitle(context.getString(R.string.loading_notification_title))
            setContentText(text)
            // .addAction(android.R.drawable.ic_delete, cancel, cancelIntent)

            // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
            // 束ねられた通知をタップしても pi_click が実行されないので困る。
            // グループキーを設定する
            setGroup(context.packageName + ":PollingForegrounder")
        }

        shower(builder.build(), nc)
    }
}
