package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import jp.juggler.util.LogCategory
import java.lang.ref.WeakReference

class ChooseReceiver : BroadcastReceiver() {
	
	companion object {
		private val log = LogCategory("ChooseReceiver")
		var lastComponentName : ComponentName? = null
		var refCallback : WeakReference<() -> Unit>? = null
		
		fun setCallback(cb : () -> Unit) {
			refCallback = WeakReference(cb)
		}
	}
	
	override fun onReceive(context : Context, intent : Intent?) {
		if(Build.VERSION.SDK_INT >= 22) {
			lastComponentName = intent?.extras?.get(Intent.EXTRA_CHOSEN_COMPONENT) as? ComponentName
			refCallback?.get()?.invoke()
		} else {
			log.w("onReceive: Intent.EXTRA_CHOSEN_COMPONENT can't be used in API level 21")
		}
	}
}
