package jp.juggler.subwaytooter;

import android.content.Context;

import jp.juggler.subwaytooter.api.entity.TootStatus;

/**
 * Created by tateisu on 2017/04/22.
 */

public class Styler {
	public static int getVisibilityIcon( String visibility ){
		return
		TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? R.drawable.ic_public
			: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? R.drawable.ic_lock_open
			: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? R.drawable.ic_lock
			: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? R.drawable.ic_mail
			: R.drawable.ic_public;
		
	}

	
	public static String getVisibilityString( Context context ,String visibility){
		return
			TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? context.getString( R.string.visibility_public )
				: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? context.getString( R.string.visibility_unlisted )
				: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? context.getString( R.string.visibility_private )
				: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? context.getString( R.string.visibility_direct )
				: "?";
	}
}
