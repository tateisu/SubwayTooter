package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences

object PrefDevice {
	
	private const val file_name = "device"
	
	fun prefDevice(context : Context) : SharedPreferences {
		return context.getSharedPreferences(file_name, Context.MODE_PRIVATE)
	}

	internal const val KEY_DEVICE_TOKEN = "device_token"
	internal const val KEY_INSTALL_ID = "install_id"
	
}
