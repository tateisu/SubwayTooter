package jp.juggler.subwaytooter;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import jp.juggler.subwaytooter.util.LogCategory;

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
	static final LogCategory log = new LogCategory( "MyFirebaseInstanceIDService" );
	
	// Called when the system determines that the tokens need to be refreshed.
	@Override public void onTokenRefresh(){
		super.onTokenRefresh();
		
		try{
			String token = FirebaseInstanceId.getInstance().getToken();
			log.d( "onTokenRefresh: instance_token=%s", token );
			
			PrefDevice.prefDevice( this ).edit().putString( PrefDevice.KEY_DEVICE_TOKEN, token ).apply();
			
			PollingWorker.queueFCMTokenUpdated(this);
			
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
}
