package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory

object ServerTimeoutNotification {
    private val log = LogCategory("ServerTimeoutNotification")
    private const val NOTIFICATION_ID_ERROR = 3
    fun NotificationManager.createServerTimeoutNotification(
        context: Context,
        account: SavedAccount,
    ) {
        val instance = account.apiHost.pretty
        val accountDbId = account.db_id

        // 通知タップ時のPendingIntent
        val clickIntent = Intent(context, ActCallback::class.java)
        // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val clickPi = PendingIntent.getActivity(
            context,
            3,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            // Android 8 から、通知のスタイルはユーザが管理することになった
            // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
            val channel = NotificationHelper.createNotificationChannel(
                context,
                "ErrorNotification",
                "Error",
                null,
                2 /* NotificationManager.IMPORTANCE_LOW */
            )
            NotificationCompat.Builder(context, channel.id)
        } else {
            NotificationCompat.Builder(context, "not_used")
        }

        builder
            .setContentIntent(clickPi)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
            .setColor(
                ContextCompat.getColor(
                    context,
                    R.color.Light_colorAccent
                )
            ) // ここは常に白テーマの色を使う
            .setWhen(System.currentTimeMillis())
            .setGroup(context.packageName + ":" + "Error")

        val header = context.getString(R.string.error_notification_title)
        val summary = context.getString(R.string.error_notification_summary)

        builder
            .setContentTitle(header)
            .setContentText("$summary: $instance")

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(header)
            .setSummaryText(summary)
        style.addLine(instance)
        builder.setStyle(style)

        val tag = accountDbId.toString()

        notify(tag, NOTIFICATION_ID_ERROR, builder.build())
    }
}