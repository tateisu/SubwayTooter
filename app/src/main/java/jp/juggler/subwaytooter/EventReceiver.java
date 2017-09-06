package jp.juggler.subwaytooter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import jp.juggler.subwaytooter.util.LogCategory;

public class EventReceiver extends BroadcastReceiver {
	
	static final LogCategory log = new LogCategory( "EventReceiver" );
	public static final String ACTION_NOTIFICATION_DELETE = "notification_delete";
	
	@Override public void onReceive( Context context, Intent intent ){
		if( intent != null ){
			String action = intent.getAction();
			
			if( Intent.ACTION_BOOT_COMPLETED.equals( action ) ){
				PollingWorker.queueBootCompleted( context );
				
			}else if( Intent.ACTION_MY_PACKAGE_REPLACED.equals( action ) ){
				PollingWorker.queuePackageReplaced( context );
				
			}else if( ACTION_NOTIFICATION_DELETE.equals( action ) ){
				long db_id = intent.getLongExtra( PollingWorker.EXTRA_DB_ID, - 1L );
				PollingWorker.queueNotificationDeleted( context, db_id );
				
			}else{
				log.e( "onReceive: unsupported action %s", action );
			}
		}
	}
}
