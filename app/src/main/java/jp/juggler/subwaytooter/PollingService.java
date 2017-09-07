package jp.juggler.subwaytooter;

import android.app.job.JobParameters;
import android.app.job.JobService;

@SuppressWarnings("WeakerAccess")
public class PollingService extends JobService {
	
	PollingWorker polling_worker;
	
	@Override public void onCreate(){
		super.onCreate();
		polling_worker = PollingWorker.getInstance( getApplicationContext() );
	}
	
	@Override public void onDestroy(){
		super.onDestroy();
		polling_worker.onJobServiceDestroy();
	}
	
	@Override public boolean onStartJob( JobParameters params ){
		return polling_worker.onStartJob( this, params );
	}
	
	@Override public boolean onStopJob( JobParameters params ){
		return polling_worker.onStopJob( params );
	}
}
