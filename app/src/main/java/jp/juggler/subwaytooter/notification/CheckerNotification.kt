package jp.juggler.subwaytooter.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory

object CheckerNotification {

    private val log = LogCategory("CheckerNotification")

    private var lastMessage: String? = null

    suspend fun showMessage(
        context: Context,
        text: String,
        shower: suspend (Notification) -> Unit,
    ) {
        // テキストが変化していないなら更新しない
        if (text.isEmpty() || text == lastMessage) return

        lastMessage = text
        log.i(text)

//        // This PendingIntent can be used to cancel the worker
//        val cancel = context.getString(R.string.cancel)
//        val cancelIntent = WorkManager.getInstance(context)
//            .createCancelPendingIntent(id)

        // Android 8 から、通知のスタイルはユーザが管理することになった
        // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
        // The user-visible description of the channel.
        val channel = NotificationHelper.createNotificationChannel(
            context,
            "PollingForegrounder",
            "real-time message notifier",
            null,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
        val builder = NotificationCompat.Builder(context, channel.id)

        // 通知タップ時のPendingIntent
        val clickIntent = Intent(context, ActMain::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val clickPi = PendingIntent.getActivity(
            context,
            2,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ここは常に白テーマのアイコンと色を使う
        builder
            .setContentIntent(clickPi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.Light_colorAccent))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(context.getString(R.string.loading_notification_title))
            .setContentText(text)
        // .addAction(android.R.drawable.ic_delete, cancel, cancelIntent)

        // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
        // 束ねられた通知をタップしても pi_click が実行されないので困るため、
        // アカウント別にグループキーを設定する
        builder.setGroup(context.packageName + ":PollingForegrounder")

        shower(builder.build())
    }
}
