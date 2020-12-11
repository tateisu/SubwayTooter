package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.subwaytooter.notification.PollingWorker

import jp.juggler.util.LogCategory

class EventReceiver : BroadcastReceiver() {
	
	companion object {
		internal val log = LogCategory("EventReceiver")
		const val ACTION_NOTIFICATION_DELETE = "notification_delete"
	}
	
	override fun onReceive(context : Context, intent : Intent?) {
		when(val action = intent?.action) {

			Intent.ACTION_BOOT_COMPLETED ->
				PollingWorker.queueBootCompleted(context)

			Intent.ACTION_MY_PACKAGE_REPLACED ->
				PollingWorker.queuePackageReplaced(context)
			
			ACTION_NOTIFICATION_DELETE ->
				PollingWorker.queueNotificationDeleted( context,intent.data)
			
			else -> log.e("onReceive: unsupported action %s", action)
		}
	}
}
