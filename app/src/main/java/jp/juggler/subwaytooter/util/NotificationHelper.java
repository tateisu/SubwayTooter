package jp.juggler.subwaytooter.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.support.annotation.NonNull;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.SavedAccount;

public class NotificationHelper {
	
	private static final LogCategory log = new LogCategory( "NotificationHelper" );
	
	@TargetApi(26)
	public static NotificationChannel createNotificationChannel( @NonNull Context context, @NonNull SavedAccount account ){
		
		NotificationManager notification_manager = (NotificationManager) context.getSystemService( Context.NOTIFICATION_SERVICE );
		
		// The id of the channel.
		String channel_id = account.acct;
		
		// The user-visible name of the channel.
		CharSequence name = context.getString( R.string.notification_for, account.acct );
		
		// The user-visible description of the channel.
		String description = context.getString( R.string.notification_channel_description, account.acct );
		//
		int importance = NotificationManager.IMPORTANCE_DEFAULT; // : NotificationManager.IMPORTANCE_LOW;
		//
		NotificationChannel channel = null;
		try{
			channel = notification_manager.getNotificationChannel( channel_id );
		}catch( Throwable ex ){
			log.trace( ex );
		}
		if( channel == null ){
			channel = new NotificationChannel( channel_id, name, importance );
		}
		channel.setName( name );
		channel.setDescription( description );
		notification_manager.createNotificationChannel( channel );
		return channel;
	}
}
