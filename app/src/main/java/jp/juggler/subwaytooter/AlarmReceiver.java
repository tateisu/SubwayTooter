package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;


public class AlarmReceiver extends WakefulBroadcastReceiver {

	static final String EXTRA_RECEIVED_INTENT = "received_intent";
	static final String ACTION_FROM_RECEIVER = "from_receiver";
	
	@Override public void onReceive( Context context, Intent intent ){
		Intent serviceIntent = new Intent(context,AlarmService.class);
		serviceIntent.setAction( ACTION_FROM_RECEIVER );
		serviceIntent.putExtra(EXTRA_RECEIVED_INTENT,intent);
		startWakefulService(context,serviceIntent);
	}
}
