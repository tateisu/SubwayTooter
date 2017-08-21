package jp.juggler.subwaytooter;

import android.content.Intent;

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
			
			Intent intent = new Intent( this, AlarmService.class );
			intent.setAction( AlarmService.ACTION_DEVICE_TOKEN );
			startService( intent );
			
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
}
