package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Pref {
	
	
	public static SharedPreferences pref( Context context){
		return PreferenceManager.getDefaultSharedPreferences( context );
	}
	
	private static final String KEY_BACK_TO_COLUMN_LIST ="BackToColumnList"; // 使わなくなった
	public static final String KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN ="DontConfirmBeforeCloseColumn";
	public static final String KEY_BACK_BUTTON_ACTION ="back_button_action";
	public static final String KEY_PRIOR_LOCAL_URL = "prior_local_url";
	public static final String KEY_DISABLE_FAST_SCROLLER = "disable_fast_scroller";
	
}
