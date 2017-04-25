package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Pref {
	
	
	public static SharedPreferences pref( Context context){
		return PreferenceManager.getDefaultSharedPreferences( context );
	}
	
	public static final String KEY_BACK_TO_COLUMN_LIST ="BackToColumnList";
	public static final String KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN ="DontConfirmBeforeCloseColumn";
	
}
