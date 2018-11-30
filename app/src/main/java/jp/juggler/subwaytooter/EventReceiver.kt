package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import jp.juggler.util.LogCategory

class EventReceiver : BroadcastReceiver() {
	
	companion object {
		internal val log = LogCategory("EventReceiver")
		const val ACTION_NOTIFICATION_DELETE = "notification_delete"
	}
	
	override fun onReceive(context : Context, intent : Intent?) {
		if(intent != null) {
			val action = intent.action
			
			when {
				Intent.ACTION_BOOT_COMPLETED == action -> PollingWorker.queueBootCompleted(context)
				Intent.ACTION_MY_PACKAGE_REPLACED == action -> PollingWorker.queuePackageReplaced(
					context
				)
				
				ACTION_NOTIFICATION_DELETE == action -> {
					val db_id = intent.getLongExtra(PollingWorker.EXTRA_DB_ID, - 1L)
					PollingWorker.queueNotificationDeleted(context, db_id)
					
				}
				
				else -> log.e("onReceive: unsupported action %s", action)
			}
		}
	}
	
}
