package jp.juggler.subwaytooter.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.SavedAccount;

public class NotificationHelper {
	
	private static final LogCategory log = new LogCategory( "NotificationHelper" );
	
	@TargetApi(26)
	public static NotificationChannel createNotificationChannel( @NonNull Context context, @NonNull SavedAccount account ){
		return createNotificationChannel(context
			,account.acct
			,account.acct
			,context.getString( R.string.notification_channel_description, account.acct )
			,NotificationManager.IMPORTANCE_DEFAULT // : NotificationManager.IMPORTANCE_LOW;
		);
	}
	
	@TargetApi(26)
	public static NotificationChannel createNotificationChannel(
		@NonNull Context context
		, @NonNull String channel_id // id
		, @NonNull String name // The user-visible name of the channel.
	    , @Nullable String description // The user-visible description of the channel.
	    , int importance
	){
		NotificationManager notification_manager = (NotificationManager) context.getSystemService( Context.NOTIFICATION_SERVICE );
		
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
		channel.setImportance( importance );
		if( description != null ) channel.setDescription( description );
		notification_manager.createNotificationChannel( channel );
		return channel;
	}
}
