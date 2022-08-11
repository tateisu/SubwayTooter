package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import jp.juggler.util.systemService

object ServerTimeoutNotification {

    private val log = LogCategory("ServerTimeoutNotification")

    private const val NOTIFICATION_ID_TIMEOUT = 3

    fun createServerTimeoutNotification(
        context: Context,
        accounts: String,
    ) {
        val notificationManager: NotificationManager = systemService(context)!!

        // 通知タップ時のPendingIntent
        val clickIntent = Intent(context, ActCallback::class.java)
        // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val clickPi = PendingIntent.getActivity(
            context,
            3,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Android 8 から、通知のスタイルはユーザが管理することになった
        // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
        val channel = NotificationHelper.createNotificationChannel(
            context,
            "ErrorNotification",
            "Error",
            null,
            2 /* NotificationManager.IMPORTANCE_LOW */
        )
        val builder = NotificationCompat.Builder(context, channel.id)

        val header = context.getString(R.string.error_notification_title)
        val summary = context.getString(R.string.error_notification_summary)

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
            .setContentTitle(header)
            .setContentText("$summary: $accounts")

        notificationManager.notify(NOTIFICATION_ID_TIMEOUT, builder.build())
    }
}
