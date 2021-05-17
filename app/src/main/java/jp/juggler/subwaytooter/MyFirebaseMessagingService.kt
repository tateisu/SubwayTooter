package jp.juggler.subwaytooter

import android.content.Intent
import android.os.Build

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.juggler.subwaytooter.notification.PollingForegrounder
import jp.juggler.subwaytooter.notification.PollingWorker

import jp.juggler.util.LogCategory

class MyFirebaseMessagingService : FirebaseMessagingService() {
	
	companion object {
		internal val log = LogCategory("MyFirebaseMessagingService")
	}
	
	override fun onMessageReceived(remoteMessage : RemoteMessage) {
		
		super.onMessageReceived(remoteMessage)
		
		var tag : String? = null
		val data = remoteMessage.data
		for((key, value) in data) {
			log.d("onMessageReceived: %s=%s", key, value)
			when(key){
				"notification_tag" -> tag = value
				"acct" -> tag= "acct<>$value"
			}
		}
		
		val context = applicationContext
		val intent = Intent(context, PollingForegrounder::class.java)
		if(tag != null) intent.putExtra(PollingWorker.EXTRA_TAG, tag)
		if(Build.VERSION.SDK_INT >= 26) {
			context.startForegroundService(intent)
		} else {
			context.startService(intent)
		}
	}

	override fun onNewToken(token : String) {
		try {
			log.d("onTokenRefresh: token=%s", token)
			PrefDevice.prefDevice(this).edit().putString(PrefDevice.KEY_DEVICE_TOKEN, token).apply()

			PollingWorker.queueFCMTokenUpdated(this)
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
}
