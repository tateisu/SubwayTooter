package jp.juggler.subwaytooter;

import android.app.job.JobParameters;
import android.app.job.JobService;
import jp.juggler.subwaytooter.util.LogCategory;

@SuppressWarnings("WeakerAccess")
public class PollingService extends JobService {
	
	static final LogCategory log = new LogCategory( "PollingService" );
	
	//////////////////////////////////////////////////////////////////////
	// ワーカースレッドの管理
	
	PollingWorker polling_worker;
	
	@Override public void onCreate(){
		log.d( "onCreate" );
		super.onCreate();
		
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		App1.prepare( getApplicationContext() );

		polling_worker = PollingWorker.getInstance( getApplicationContext() );
	}
	
	@Override public void onDestroy(){
		log.d( "onDestroy" );
		super.onDestroy();
		polling_worker.cancelAllJob();
	}
	
	@Override public boolean onStartJob( JobParameters params ){
		return polling_worker.onStartJob( this,params );
	}
	
	@Override public boolean onStopJob( JobParameters params ){
		return polling_worker.onStopJob( params );
	}
}
