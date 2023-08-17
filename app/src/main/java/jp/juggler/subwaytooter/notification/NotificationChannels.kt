package jp.juggler.subwaytooter.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.startup.Initializer
import androidx.work.ForegroundInfo
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.LazyContextInitializer
import jp.juggler.subwaytooter.push.FcmFlavor
import jp.juggler.util.*
import jp.juggler.util.log.LogCategory

private val log = LogCategory("NotificationChannels")

@SuppressLint("InlinedApi")
enum class NotificationChannels(
    val id: String,
    @StringRes val titleId: Int,
    @StringRes val descId: Int,
    val importance: Int,
    val priority: Int,
    // foreground service type,
    val foregroundServiceType: Int,
    // 通知ID。(ID+tagでユニーク)
    val notificationId: Int,
    // PendingIntentのrequestCode。(ID+intentのdata Uriでユニーク)
    // pending intent request code for tap
    val pircTap: Int,
    // pending intent request code for delete
    val pircDelete: Int,
    // 通知削除のUri prefix
    val uriPrefixDelete: String,
    // 通知タップのUri prefix
    val uriPrefixTap: String,
) {
    PullNotification(
        id = "SnsNotification",
        titleId = R.string.pull_notification,
        descId = R.string.pull_notification_desc,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 1,
        pircTap = 1,
        pircDelete = 1, // uriでtapとdeleteを区別している
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://notification_delete/",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://notification_click/",
    ),
    PullWorker(
        id = "PollingForegrounder",
        titleId = R.string.loading_notification_title,
        descId = R.string.polling_foregrounder_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_MIN,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 2,
        pircTap = 2,
        pircDelete = -1,
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://checker",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://checker-tap",
    ),
    ServerTimeout(
        id = "ErrorNotification",
        titleId = R.string.server_timeout,
        descId = R.string.server_timeout_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_LOW,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 3,
        pircTap = 3,
        pircDelete = 4,
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://server-timeout",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://server-timeout-tap",
    ),
    PushMessage(
        id = "PushMessage",
        titleId = R.string.push_message,
        descId = R.string.push_message_desc,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 5,
        pircTap = 5,
        pircDelete = 6,
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://pushMessage",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://notification_click/",
    ),
    Alert(
        id = "Alert",
        titleId = R.string.alert,
        descId = R.string.alert_notification_desc,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 7,
        pircTap = 7,
        pircDelete = 8,
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://alert",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://alert-tap",
    ),
    PushWorker(
        id = "PushMessageWorker",
        titleId = R.string.push_worker,
        descId = R.string.push_worker_desc,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        priority = NotificationCompat.PRIORITY_LOW,
        foregroundServiceType = FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        notificationId = 9,
        pircTap = 9,
        pircDelete = 10,
        uriPrefixDelete = "${FcmFlavor.CUSTOM_SCHEME}://pushWorker",
        uriPrefixTap = "${FcmFlavor.CUSTOM_SCHEME}://pushWorker-tag",
    ),

    ;

    fun isDisabled(context: Context) = !isEnabled(context)

    private fun isEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                log.w("[$name] missing POST_NOTIFICATIONS.")
                return false
            }
        }
        return NotificationManagerCompat.from(context).isChannelEnabled(id)
    }

    fun notify(
        context: Context,
        tag: String? = null,
        iniitalizer: NotificationCompat.Builder.() -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                log.w("[$name] missing POST_NOTIFICATIONS.")
                return
            }
        }
        val nc = this
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.isChannelEnabled(nc.id)) {
            log.w("[$name] notification channel is disabled.")
            return
        }
        val builder = NotificationCompat.Builder(context, nc.id).apply {
            priority = nc.priority
            iniitalizer()
        }
        notificationManager.notify(tag, notificationId, builder.build())
    }

    fun createForegroundInfo(
        context: Context,
        @DrawableRes iconId: Int =
            R.drawable.ic_refresh,
        @ColorInt color: Int =
            ContextCompat.getColor(context, R.color.colorOsNotificationAccent),
        title: String? = context.getString(titleId),
        text: String? = context.getString(descId),
        piTap: PendingIntent? = null,
        piDelete: PendingIntent? = null,
        force: Boolean = false,
    ): ForegroundInfo? {
        val notificationManager = NotificationManagerCompat.from(context)

        if (!force) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    log.w("[$id] missing POST_NOTIFICATIONS.")
                    return null
                }
            }
            if (!notificationManager.isChannelEnabled(id)) {
                log.w("[$id] notification channel is disabled.")
                return null
            }
        }
        val nc = this
        val builder = NotificationCompat.Builder(context, nc.id).apply {
            priority = nc.priority
            setSmallIcon(iconId)
            setColor(color)
            title?.let { setContentTitle(it) }
            text?.let { setContentText(it) }
            piTap?.let { setContentIntent(piTap) }
            piDelete?.let { setDeleteIntent(piDelete) }
            setWhen(System.currentTimeMillis())
            setOngoing(true)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                nc.notificationId,
                builder.build(),
                nc.foregroundServiceType,
            )
        } else {
            // WorkManagerのサービスにforegroundServiceTypeが定義されてないので、
            // foregroundServiceTypeを渡すと怒られる
            ForegroundInfo(
                nc.notificationId,
                builder.build(),
            )
        }
    }

    fun cancel(context: Context, tag: String? = null) {
        NotificationManagerCompat.from(context).cancel(tag, notificationId)
    }

    companion object {
        fun NotificationManagerCompat.isChannelEnabled(channelId: String): Boolean {
            val importance = getNotificationChannel(channelId)?.importance
            log.i("isChannelEnabled: importance=$importance")
            return when (importance) {
                null, NotificationManagerCompat.IMPORTANCE_NONE -> false
                else -> true
            }
        }
    }
}

/**
 * 通知チャネルの初期化を
 * androidx app startupのイニシャライザとして実装したもの
 */
@Suppress("unused")
class NotificationChannelsInitializer : Initializer<Boolean> {
    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(LazyContextInitializer::class.java)

    override fun create(context: Context): Boolean {
        context.run {
            val list = NotificationChannels.values()
            log.i("createNotificationChannel(s) size=${list.size}")
            val notificationManager = NotificationManagerCompat.from(this)
            for (nc in list) {
                val channel = NotificationChannel(
                    nc.id,
                    getString(nc.titleId),
                    nc.importance,
                ).apply {
                    description = getString(nc.descId)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
        return true
    }
}
