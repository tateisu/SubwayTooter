package jp.juggler.subwaytooter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.lang.ref.WeakReference

class ChooseReceiver :BroadcastReceiver(){
	
	companion object{
		var lastComponentName: ComponentName? = null
		var refCallback : WeakReference<()->Unit>? = null
		
		fun setCallback(cb:()->Unit){
			refCallback = WeakReference(cb)
		}
	}
	
	override fun onReceive(context: Context,intent: Intent?) {
		lastComponentName = intent?.extras?.get(Intent.EXTRA_CHOSEN_COMPONENT) as? ComponentName
		refCallback?.get()?.invoke()
	}
}
