package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefDevice {
	
	private static String file_name = "device";
	
	static SharedPreferences prefDevice( Context context ){
		return context.getSharedPreferences( file_name, Context.MODE_PRIVATE );
	}
	
	static final String KEY_DEVICE_TOKEN = "device_token";
	static final String KEY_INSTALL_ID = "install_id";
}
