package jp.juggler.subwaytooter

import android.content.Intent
import android.os.Build

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import jp.juggler.subwaytooter.util.LogCategory

class MyFirebaseMessagingService : FirebaseMessagingService() {
	
	companion object {
		internal val log = LogCategory("MyFirebaseMessagingService")
	}
	
	override fun onMessageReceived(remoteMessage : RemoteMessage) {
		
		super.onMessageReceived(remoteMessage)
		
		var tag : String? = null
		val data = remoteMessage.data
		if(data != null) {
			for((key, value) in data) {
				log.d("onMessageReceived: %s=%s", key, value)
				
				if("notification_tag" == key) {
					tag = value
				}
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
}
