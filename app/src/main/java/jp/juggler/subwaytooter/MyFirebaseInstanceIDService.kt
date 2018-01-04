package jp.juggler.subwaytooter

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService

import jp.juggler.subwaytooter.util.LogCategory

class MyFirebaseInstanceIDService : FirebaseInstanceIdService() {
	
	companion object {
		internal val log = LogCategory("MyFirebaseInstanceIDService")
	}
	
	// Called when the system determines that the tokens need to be refreshed.
	override fun onTokenRefresh() {
		super.onTokenRefresh()
		
		try {
			val token = FirebaseInstanceId.getInstance().token
			log.d("onTokenRefresh: instance_token=%s", token)
			
			PrefDevice.prefDevice(this).edit().putString(PrefDevice.KEY_DEVICE_TOKEN, token).apply()
			
			PollingWorker.queueFCMTokenUpdated(this)
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	

}
