@file:Suppress("DEPRECATION")

package jp.juggler.subwaytooter.notification

import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R

import jp.juggler.util.LogCategory
import kotlinx.coroutines.runBlocking

/*
	FCMからメッセージを受信した際に起動されるIntentService
	受信したメッセージの通知処理が完了するまでForegroundであり続ける
*/
class PollingForegrounder : IntentService("PollingForegrounder") {

    companion object {

        internal val log = LogCategory("PollingForegrounder")

        internal const val NOTIFICATION_ID_FOREGROUNDER = 2
    }

    private var lastStatus: String? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        log.d("onCreate")
        super.onCreate()

        // メインスレッド上でPollingWorkerを初期化しておく
        PollingWorker.getInstance(applicationContext)

        startForeground(NOTIFICATION_ID_FOREGROUNDER, createNotification(applicationContext, ""))
    }

    override fun onDestroy() {
        log.d("onDestroy")

        stopForeground(true)
        super.onDestroy()
    }

    private fun createNotification(context: Context, text: String): Notification {
        // 通知タップ時のPendingIntent
        val clickIntent = Intent(context, ActMain::class.java)
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val clickPi = PendingIntent.getActivity(
            context,
            2,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
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
            NotificationCompat.Builder(context, channel.id)
        } else {
            NotificationCompat.Builder(context, "not_used")
        }

        builder
            .setContentIntent(clickPi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
            .setColor(ContextCompat.getColor(context, R.color.Light_colorAccent)) // ここは常に白テーマの色を使う
            .setWhen(System.currentTimeMillis())
            .setContentTitle(context.getString(R.string.loading_notification_title))
            .setContentText(text)

        // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
        // 束ねられた通知をタップしても pi_click が実行されないので困るため、
        // アカウント別にグループキーを設定する
        builder.setGroup(context.packageName + ":PollingForegrounder")

        return builder.build()
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        runBlocking {
            val tag = intent.getStringExtra(PollingWorker.EXTRA_TAG)
            val context = applicationContext
            PollingWorker.handleFCMMessage(context, tag) { sv ->
                if (sv.isEmpty() || sv == lastStatus) return@handleFCMMessage
                // 状況が変化したらログと通知領域に出力する
                lastStatus = sv
                log.d("onStatus $sv")
                startForeground(NOTIFICATION_ID_FOREGROUNDER, createNotification(context, sv))
            }
        }
    }
}
