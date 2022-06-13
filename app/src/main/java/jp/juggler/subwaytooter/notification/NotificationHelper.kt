package jp.juggler.subwaytooter.notification

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import jp.juggler.util.LogCategory

object NotificationHelper {

    private val log = LogCategory("NotificationHelper")

    @TargetApi(26)
    fun createNotificationChannel(
        context: Context,
        channelId: String, // id
        name: String, // The user-visible name of the channel.
        description: String?, // The user-visible description of the channel.
        importance: Int,
    ): NotificationChannel {
        val notification_manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                ?: throw NotImplementedError("missing NotificationManager system service")

        var channel: NotificationChannel? = null
        try {
            channel = notification_manager.getNotificationChannel(channelId)
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        if (channel == null) {
            channel = NotificationChannel(channelId, name, importance)
        }
        channel.name = name
        channel.importance = importance
        if (description != null) channel.description = description
        notification_manager.createNotificationChannel(channel)
        return channel
    }
}
