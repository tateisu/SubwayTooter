package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import jp.juggler.subwaytooter.util.LogCategory;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
	static final LogCategory log = new LogCategory( "MyFirebaseMessagingService" );
	
	@Override public void onCreate(){
		super.onCreate();
	}
	
	@Override public void onMessageReceived( RemoteMessage remoteMessage ){

		super.onMessageReceived( remoteMessage );
		
		String tag = null;
		Map< String, String > data = remoteMessage.getData();
		if( data != null ){
			for( Map.Entry< String, String > entry : data.entrySet() ){
				log.d( "onMessageReceived: %s=%s", entry.getKey(), entry.getValue() );
				
				if( "notification_tag".equals( entry.getKey() ) ){
					tag = entry.getValue();
				}
			}
		}

		Context context = getApplicationContext();
		Intent intent = new Intent( context,PollingForegrounder.class);
		if(tag != null ) intent.putExtra(PollingWorker.EXTRA_TAG,tag);
		if( Build.VERSION.SDK_INT >= 26 ){
			context.startForegroundService( intent );
		}else{
			context.startService(intent);
		}
	}
	
}
