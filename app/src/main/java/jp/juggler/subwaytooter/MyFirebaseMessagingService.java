package jp.juggler.subwaytooter;

import android.os.SystemClock;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import jp.juggler.subwaytooter.util.LogCategory;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
	static final LogCategory log = new LogCategory( "MyFirebaseMessagingService" );
	
	@Override public void onCreate(){
		super.onCreate();
		
		// メインスレッド上でPollingWorkerを初期化しておく
		PollingWorker.getInstance( getApplicationContext() );
	}
	
	@Override public void onMessageReceived( RemoteMessage remoteMessage ){
		long time_start = SystemClock.elapsedRealtime();
		
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
		
		PollingWorker.handleFCMMessage( getApplicationContext(), time_start, tag );
		
	}
}
