package jp.juggler.subwaytooter.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import jp.juggler.util.log.LogCategory

object NotificationHelper {

    private val log = LogCategory("NotificationHelper")

    fun createNotificationChannel(
        context: Context,
        channelId: String, // id
        name: String, // The user-visible name of the channel.
        description: String?, // The user-visible description of the channel.
        importance: Int,
    ): NotificationChannel {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                ?: throw NotImplementedError("missing NotificationManager system service")

        val channel = try {
            notificationManager.getNotificationChannel(channelId)!!
        } catch (ex: Throwable) {
            log.e(ex, "getNotificationChannel failed.")
            null
        } ?: NotificationChannel(channelId, name, importance)

        channel.name = name
        channel.importance = importance
        description?.let { channel.description = it }
        notificationManager.createNotificationChannel(channel)
        return channel
    }
}
