package jp.juggler.subwaytooter.util

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory

object NotificationHelper {
	
	private val log = LogCategory("NotificationHelper")
	
	internal const val TRACKING_NAME_DEFAULT = ""
	internal const val TRACKING_NAME_REPLY = "reply"
	
	@TargetApi(26)
	fun createNotificationChannel(context : Context, account : SavedAccount,trackingName:String) =when(trackingName) {
		"" -> createNotificationChannel(
			context,
			account.acct, // id
			account.acct, // name
			context.getString(R.string.notification_channel_description, account.acct),
			NotificationManager.IMPORTANCE_DEFAULT // : NotificationManager.IMPORTANCE_LOW;
		)
		
		else-> createNotificationChannel(
			context,
			"${account.acct}/$trackingName", // id
			"${account.acct}/$trackingName", // name
			context.getString(R.string.notification_channel_description, account.acct),
			NotificationManager.IMPORTANCE_DEFAULT // : NotificationManager.IMPORTANCE_LOW;
		)
	}
	
	@TargetApi(26)
	fun createNotificationChannel(
		context : Context, channel_id : String // id
		, name : String // The user-visible name of the channel.
		, description : String? // The user-visible description of the channel.
		, importance : Int
	) : NotificationChannel {
		val notification_manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
			?: throw NotImplementedError("missing NotificationManager system service")

		var channel : NotificationChannel? = null
		try {
			channel = notification_manager.getNotificationChannel(channel_id)
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		if(channel == null) {
			channel = NotificationChannel(channel_id, name, importance)
		}
		channel.name = name
		channel.importance = importance
		if(description != null) channel.description = description
		notification_manager.createNotificationChannel(channel)
		return channel
		
	}
}
