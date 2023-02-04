package jp.juggler.subwaytooter.notification

import android.app.NotificationChannel
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.startup.Initializer
import jp.juggler.subwaytooter.R
import jp.juggler.util.*
import jp.juggler.util.log.LogCategory

private val log = LogCategory("NotificationChannels")

enum class NotificationChannels(
    val id: String,
    @StringRes val titleId: Int,
    @StringRes val descId: Int,
    val importance: Int,
    val priority: Int,
    // 通知ID。(ID+tagでユニーク)
    val notificationId: Int,
    // PendingIntentのrequestCode。(ID+intentのdata Uriでユニーク)
    // pending intent request code for tap
    val pircTap: Int,
    // pending intent request code for delete
    val pircDelete: Int,
    // 通知削除のUri prefix
    val uriPrefixDelete: String,
) {
    PullNotification(
        id = "SnsNotification",
        titleId = R.string.pull_notification,
        descId = R.string.pull_notification_desc,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        notificationId = 1,
        pircTap = 1,
        pircDelete = 1, // uriでtapとdeleteを区別している
        uriPrefixDelete = "subwaytooter://sns-notification",
    ),
    Checker(
        id = "PollingForegrounder",
        titleId = R.string.polling_foregrounder,
        descId = R.string.polling_foregrounder_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_MIN,
        notificationId = 2,
        pircTap = 2,
        pircDelete = -1,
        uriPrefixDelete = "subwaytooter://checker",
    ),
    ServerTimeout(
        id = "ErrorNotification",
        titleId = R.string.server_timeout,
        descId = R.string.server_timeout_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_LOW,
        notificationId = 3,
        pircTap = 3,
        pircDelete = -1,
        uriPrefixDelete = "subwaytooter://server-timeout",
    ),
    PushMessage(
        id = "PushMessage",
        titleId = R.string.push_message,
        descId = R.string.push_message_desc,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        notificationId = 4,
        pircTap = 4,
        pircDelete = 5,
        uriPrefixDelete = "pushreceiverapp://pushMessage",
    ),
    Alert(
        id = "Alert",
        titleId = R.string.alert,
        descId = R.string.alert_notification_desc,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        notificationId = 6,
        pircTap = 6,
        pircDelete = -1,
        uriPrefixDelete = "pushreceiverapp://alert",
    ),
    PushMessageWorker(
        id = "PushMessageWorker",
        titleId = R.string.push_worker,
        descId = R.string.push_worker_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_LOW,
        notificationId = 7,
        pircTap = 7,
        pircDelete = 8,
        uriPrefixDelete = "pushreceiverapp://PushMessageWorker",
    ),
    /////////////////////////////
    // 以下、通知IDやpirc を吟味していない

    //    SubscriptionUpdate(
//        id = "SubscriptionUpdate",
//        titleId = R.string.push_subscription_update,
//        descId = R.string.push_subscription_update_desc,
//        importance = NotificationManagerCompat.IMPORTANCE_LOW,
//        priority = NotificationCompat.PRIORITY_LOW,
//        notificationId = 3,
//        pircTap = 4,
//        pircDelete = 5,
//        uriPrefixDelete = "pushreceiverapp://subscriptionUpdate",
//    ),
}

/**
 * 通知チャネルの初期化を
 * androidx app startupのイニシャライザとして実装したもの
 */
@Suppress("unused")
class NotificationChannelsInitializer : Initializer<Boolean> {
    override fun dependencies(): List<Class<out Initializer<*>>> =
        emptyList()

    override fun create(context: Context): Boolean {
        context.run {
            val list = NotificationChannels.values()
            log.i("createNotificationChannel(s) size=${list.size}")
            val notificationManager = NotificationManagerCompat.from(this)
            list.map {
                NotificationChannel(
                    it.id,
                    getString(it.titleId),
                    it.importance,
                ).apply {
                    description = getString(it.descId)
                }
            }.forEach {
                notificationManager.createNotificationChannel(it)
            }
        }
        return true
    }
}
