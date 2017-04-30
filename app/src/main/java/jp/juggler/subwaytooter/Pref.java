package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

class Pref {
	
	static SharedPreferences pref( Context context ){
		return PreferenceManager.getDefaultSharedPreferences( context );
	}
	
	private static final String KEY_BACK_TO_COLUMN_LIST = "BackToColumnList"; // 使わなくなった
	
	static final String KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN = "DontConfirmBeforeCloseColumn";
	static final String KEY_BACK_BUTTON_ACTION = "back_button_action";
	static final String KEY_PRIOR_LOCAL_URL = "prior_local_url";
	static final String KEY_DISABLE_FAST_SCROLLER = "disable_fast_scroller";
	static final String KEY_UI_THEME = "ui_theme";
	static final String KEY_SIMPLE_LIST = "simple_list";
	static final String KEY_NOTIFICATION_SOUND = "notification_sound";
	static final String KEY_NOTIFICATION_VIBRATION = "notification_vibration";
	static final String KEY_NOTIFICATION_LED = "notification_led";
	
}
