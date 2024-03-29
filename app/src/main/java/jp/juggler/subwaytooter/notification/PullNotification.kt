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

object PullNotification {
    private val log = LogCategory("PullNotification")

    const val TRACKING_NAME_DEFAULT = ""
    const val TRACKING_NAME_REPLY = "reply"

    private val ncPullNotification = NotificationChannels.PullNotification

    /**
     * メッセージ通知を消す
     */
    fun NotificationManager.removeMessageNotification(id: String?, tag: String) {
        when (id) {
            null -> cancel(tag, ncPullNotification.notificationId)
            else -> cancel("$tag/$id", ncPullNotification.notificationId)
        }
    }

    /**
     * メッセージ通知をたくさん消す
     */
    fun NotificationManager.removeMessageNotification(account: SavedAccount, tag: String) {
        if (PrefB.bpDivideNotification.value) {
            activeNotifications?.filterNotNull()?.filter {
                it.id == ncPullNotification.notificationId && it.tag.startsWith("$tag/")
            }?.forEach {
                log.d("cancel: ${it.tag} context=${account.acct.pretty} $tag")
                cancel(it.tag, ncPullNotification.notificationId)
            }
        } else {
            cancel(tag, ncPullNotification.notificationId)
        }
    }

    /**
     * 表示中のメッセージ通知の一覧
     */
    fun NotificationManager.getMessageNotifications(tag: String) =
        activeNotifications?.filterNotNull()?.filter {
            it.id == ncPullNotification.notificationId && it.tag.startsWith("$tag/")
        }?.map { Pair(it.tag, it) }?.toMutableMap() ?: mutableMapOf()

    fun showMessageNotification(
        context: Context,
        account: SavedAccount,
        trackingType: TrackingType,
        notificationTag: String,
        notificationId: String? = null,
        setContent: (builder: NotificationCompat.Builder) -> Unit,
    ) {
        log.d("showNotification[${account.acct.pretty}] creating notification(1)")

        val params = listOf(
            "db_id" to account.db_id.toString(),
            "type" to trackingType.str, // URIをユニークにするため。参照されない
            "notificationId" to notificationId, // URIをユニークにするため。参照されない
        ).mapNotNull {
            when (val second = it.second) {
                null -> null
                else -> "${it.first.encodePercent()}=${second.encodePercent()}"
            }
        }.joinToString("&")

        val iTap = Intent(context, ActCallback::class.java).apply {
            data = "${ncPullNotification.uriPrefixTap}?$params".toUri()
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val piTap = PendingIntent.getActivity(
            context,
            ncPullNotification.pircTap,
            iTap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iDelete = Intent(context, EventReceiver::class.java).apply {
            action = EventReceiver.ACTION_NOTIFICATION_DELETE
            data = "${ncPullNotification.uriPrefixDelete}?$params".toUri()
        }
        val piDelete = PendingIntent.getBroadcast(
            context,
            ncPullNotification.pircDelete,
            iDelete,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ncPullNotification.notify(context, notificationTag) {
            setContentIntent(piTap)
            setDeleteIntent(piDelete)
            setAutoCancel(true)

            // 常に白テーマのアイコンを使う
            setSmallIcon(R.drawable.ic_notification)

            // 常に白テーマの色を使う
            color = ContextCompat.getColor(context, R.color.colorOsNotificationAccent)

            // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
            // 束ねられた通知をタップしても pi_click が実行されないので困るため、
            // アカウント別にグループキーを設定する
            setGroup(context.packageName + ":" + account.acct.ascii)

            log.d("showNotification[${account.acct.pretty}] creating notification(3)")
            setContent(this)
        }
    }

    fun openNotificationChannelSetting(context: Context) {
        val intent = Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS")
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, ncPullNotification.id)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
    }
}
