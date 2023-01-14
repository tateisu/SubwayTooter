package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.EventReceiver
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.encodePercent
import jp.juggler.util.data.toMutableMap
import jp.juggler.util.log.LogCategory

object MessageNotification {
    private val log = LogCategory("MessageNotification")

    private const val NOTIFICATION_ID_MESSAGE = 1

    const val TRACKING_NAME_DEFAULT = ""
    const val TRACKING_NAME_REPLY = "reply"

    /**
     * メッセージ通知を消す
     */
    fun NotificationManager.removeMessageNotification(id: String?, tag: String) {
        when (id) {
            null -> cancel(tag, NOTIFICATION_ID_MESSAGE)
            else -> cancel("$tag/$id", NOTIFICATION_ID_MESSAGE)
        }
    }

    /** メッセージ通知をたくさん消す
     *
     */
    fun NotificationManager.removeMessageNotification(account: SavedAccount, tag: String) {
        if (PrefB.bpDivideNotification()) {
            activeNotifications?.filterNotNull()?.filter {
                it.id == NOTIFICATION_ID_MESSAGE && it.tag.startsWith("$tag/")
            }?.forEach {
                log.d("cancel: ${it.tag} context=${account.acct.pretty} $tag")
                cancel(it.tag, NOTIFICATION_ID_MESSAGE)
            }
        } else {
            cancel(tag, NOTIFICATION_ID_MESSAGE)
        }
    }

    /**
     * 表示中のメッセージ通知の一覧
     */
    fun NotificationManager.getMessageNotifications(tag: String) =
        activeNotifications?.filterNotNull()?.filter {
            it.id == NOTIFICATION_ID_MESSAGE && it.tag.startsWith("$tag/")
        }?.map { Pair(it.tag, it) }?.toMutableMap() ?: mutableMapOf()

    fun NotificationManager.showMessageNotification(
        context: Context,
        account: SavedAccount,
        trackingName: String,
        trackingType: TrackingType,
        notificationTag: String,
        notificationId: String? = null,
        setContent: (builder: NotificationCompat.Builder) -> Unit,
    ) {
        log.d("showNotification[${account.acct.pretty}] creating notification(1)")

        // Android 8 から、通知のスタイルはユーザが管理することになった
        // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
        val channel = createMessageNotificationChannel(
            context,
            account,
            trackingName
        )
        val builder = NotificationCompat.Builder(context, channel.id)

        builder.apply {

            val params = listOf(
                "db_id" to account.db_id.toString(),
                "type" to trackingType.str,
                "notificationId" to notificationId
            ).mapNotNull {
                when (val second = it.second) {
                    null -> null
                    else -> "${it.first.encodePercent()}=${second.encodePercent()}"
                }
            }.joinToString("&")

            val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            PendingIntent.getActivity(
                context,
                257,
                Intent(context, ActCallback::class.java).apply {
                    data = "subwaytooter://notification_click/?$params".toUri()
                    // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                flag
            )?.let { setContentIntent(it) }

            PendingIntent.getBroadcast(
                context,
                257,
                Intent(context, EventReceiver::class.java).apply {
                    action = EventReceiver.ACTION_NOTIFICATION_DELETE
                    data = "subwaytooter://notification_delete/?$params".toUri()
                },
                flag
            )?.let { setDeleteIntent(it) }

            setAutoCancel(true)

            // 常に白テーマのアイコンを使う
            setSmallIcon(R.drawable.ic_notification)

            // 常に白テーマの色を使う
            color = ContextCompat.getColor(context, R.color.colorOsNotificationAccent)

            // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
            // 束ねられた通知をタップしても pi_click が実行されないので困るため、
            // アカウント別にグループキーを設定する
            setGroup(context.packageName + ":" + account.acct.ascii)
        }

        log.d("showNotification[${account.acct.pretty}] creating notification(3)")
        setContent(builder)

        log.d("showNotification[${account.acct.pretty}] set notification...")
        notify(
            notificationTag,
            NOTIFICATION_ID_MESSAGE,
            builder.build()
        )
    }

    private fun createMessageNotificationChannel(
        context: Context,
        account: SavedAccount,
        trackingName: String,
    ) = when (trackingName) {
        "" -> NotificationHelper.createNotificationChannel(
            context,
            account.acct.ascii, // id
            account.acct.pretty, // name
            context.getString(R.string.notification_channel_description, account.acct.pretty),
            NotificationManager.IMPORTANCE_DEFAULT // : NotificationManager.IMPORTANCE_LOW;
        )

        else -> NotificationHelper.createNotificationChannel(
            context,
            "${account.acct.ascii}/$trackingName", // id
            "${account.acct.pretty}/$trackingName", // name
            context.getString(R.string.notification_channel_description, account.acct.pretty),
            NotificationManager.IMPORTANCE_DEFAULT // : NotificationManager.IMPORTANCE_LOW;
        )
    }

    fun openNotificationChannelSetting(
        context: Context,
        account: SavedAccount,
        trackingName: String,
    ) {
        val channel = createMessageNotificationChannel(context, account, trackingName)
        val intent = Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS")
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
    }
}
