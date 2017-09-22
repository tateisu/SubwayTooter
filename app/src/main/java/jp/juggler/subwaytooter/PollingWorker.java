package jp.juggler.subwaytooter;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.google.firebase.iid.FirebaseInstanceId;

import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.NotificationHelper;
import jp.juggler.subwaytooter.util.TaskList;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.WordTrieTree;
import jp.juggler.subwaytooter.util.WorkerBase;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("WeakerAccess")
public class PollingWorker {
	static final LogCategory log = new LogCategory( "PollingWorker" );
	
	static final int NOTIFICATION_ID = 1;
	static final int NOTIFICATION_ID_ERROR = 3;
	
	// Notification のJSONObject を日時でソートするためにデータを追加する
	static final String KEY_TIME = "<>time";
	
	public static final AtomicBoolean mBusyAppDataImportBefore = new AtomicBoolean( false );
	public static final AtomicBoolean mBusyAppDataImportAfter = new AtomicBoolean( false );
	
	public static final String EXTRA_DB_ID = "db_id";
	public static final String EXTRA_TAG = "tag";
	public static final String EXTRA_TASK_ID = "task_id";
	
	public static final String APP_SERVER = "https://mastodon-msg.juggler.jp";
	
	private static class Data {
		SavedAccount access_info;
		TootNotification notification;
	}
	
	private static final String PATH_NOTIFICATIONS = "/api/v1/notifications";
	
	static class InjectData {
		long account_db_id;
		TootNotification.List list = new TootNotification.List();
	}
	
	static final ConcurrentLinkedQueue< InjectData > inject_queue = new ConcurrentLinkedQueue<>();
	
	// ジョブID
	static final int JOB_POLLING = 1;
	static final int JOB_TASK = 2;
	static final int JOB_FCM = 3;
	
	// タスクID
	static final int TASK_POLLING = 1;
	static final int TASK_DATA_INJECTED = 2;
	static final int TASK_NOTIFICATION_CLEAR = 3;
	static final int TASK_APP_DATA_IMPORT_BEFORE = 4;
	static final int TASK_APP_DATA_IMPORT_AFTER = 5;
	static final int TASK_FCM_DEVICE_TOKEN = 6;
	static final int TASK_FCM_MESSAGE = 7;
	static final int TASK_BOOT_COMPLETED = 8;
	static final int TASK_PACKAGE_REPLACED = 9;
	static final int TASK_NOTIFICATION_DELETE = 10;
	static final int TASK_NOTIFICATION_CLICK = 11;
	static final int TASK_UPDATE_NOTIFICATION = 12;
	static final int TASK_UPDATE_LISTENER = 13;
	
	private static PollingWorker sInstance;
	
	public static PollingWorker getInstance( Context applicationContext ){
		if( sInstance == null ) sInstance = new PollingWorker( applicationContext );
		return sInstance;
	}
	
	final Context context;
	final Handler handler;
	final SharedPreferences pref;
	final ConnectivityManager connectivityManager;
	final NotificationManager notification_manager;
	final JobScheduler scheduler;
	final PowerManager power_manager;
	final PowerManager.WakeLock power_lock;
	final WifiManager wifi_manager;
	final WifiManager.WifiLock wifi_lock;
	
	private PollingWorker( Context c ){
		log.d( "ctor" );
		
		this.context = c.getApplicationContext();
		this.connectivityManager = (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );
		this.notification_manager = (NotificationManager) context.getSystemService( Context.NOTIFICATION_SERVICE );
		this.scheduler = (JobScheduler) context.getSystemService( Context.JOB_SCHEDULER_SERVICE );
		
		//
		this.power_manager = (PowerManager) context.getSystemService( Context.POWER_SERVICE );
		power_lock = power_manager.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, PollingWorker.class.getName() );
		power_lock.setReferenceCounted( false );
		
		this.wifi_manager = (WifiManager) context.getApplicationContext().getSystemService( Context.WIFI_SERVICE );
		wifi_lock = wifi_manager.createWifiLock( PollingWorker.class.getName() );
		wifi_lock.setReferenceCounted( false );
		
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		App1.prepare( context );
		
		this.pref = App1.pref;
		this.handler = new Handler( context.getMainLooper() );
		
		//
		worker = new Worker();
		worker.start();
	}
	
	Worker worker;
	
	class Worker extends WorkerBase {
		
		AtomicBoolean bThreadCancelled = new AtomicBoolean( false );
		
		public void cancel(){
			bThreadCancelled.set( true );
			notifyEx();
		}
		
		void acquirePowerLock(){
			log.d( "acquire power lock..." );
			try{
				if( ! power_lock.isHeld() ){
					power_lock.acquire();
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				if( ! wifi_lock.isHeld() ){
					wifi_lock.acquire();
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		void releasePowerLock(){
			log.d( "release power lock..." );
			try{
				if( power_lock.isHeld() ){
					power_lock.release();
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				if( wifi_lock.isHeld() ){
					wifi_lock.release();
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		public void run(){
			log.e( "worker thread start." );
			job_status.set( "worker thread start." );
			while( ! bThreadCancelled.get() ){
				JobItem item = null;
				try{
					synchronized( job_list ){
						for( JobItem ji : job_list ){
							if( bThreadCancelled.get() ) break;
							if( ji.mWorkerAttached.get() || ji.mJobCancelled_.get() ) continue;
							item = ji;
							item.mWorkerAttached.set( true );
							break;
						}
					}
					
					if( item == null ){
						job_status.set( "no job to run." );
						waitEx( 86400000L );
						continue;
					}
					job_status.set( "start job " + item.jobId );
					acquirePowerLock();
					try{
						item.refWorker.set( Worker.this );
						item.run();
					}finally{
						job_status.set( "end job " + item.jobId );
						item.refWorker.set( null );
						releasePowerLock();
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
			job_status.set( "worker thread end." );
			log.e( "worker thread end." );
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// ジョブの管理
	
	// JobService#onDestroy から呼ばれる
	public void onJobServiceDestroy(){
		log.d( "onJobServiceDestroy" );
		
		synchronized( job_list ){
			Iterator< JobItem > it = job_list.iterator();
			while( it.hasNext() ){
				JobItem item = it.next();
				if( item.jobId != JOB_FCM ){
					it.remove();
					item.cancel( false );
				}
			}
		}
	}
	
	// JobService#onStartJob から呼ばれる
	public boolean onStartJob( @NonNull JobService jobService, @NonNull JobParameters params ){
		JobItem item = new JobItem( jobService, params );
		addJob( item, true );
		return true;
		// return True if your context needs to process the work (on a separate thread).
		// return False if there's no more work to be done for this job.
	}
	
	// FCMメッセージイベントから呼ばれる
	private boolean hasJob( int jobId ){
		synchronized( job_list ){
			for( JobItem itemOld : job_list ){
				if( itemOld.jobId == jobId ) return true;
			}
		}
		return false;
	}
	
	// FCMメッセージイベントから呼ばれる
	private void addJob( int jobId, boolean bRemoveOld ){
		addJob( new JobItem( jobId ), bRemoveOld );
	}
	
	private void addJob( @NonNull JobItem item, boolean bRemoveOld ){
		int jobId = item.jobId;
		
		// 同じジョブ番号がジョブリストにあるか？
		synchronized( job_list ){
			if( bRemoveOld ){
				Iterator< JobItem > it = job_list.iterator();
				while( it.hasNext() ){
					JobItem itemOld = it.next();
					if( itemOld.jobId == jobId ){
						log.w( "addJob: jobId=%s, old job cancelled.", jobId );
						// 同じジョブをすぐに始めるのだからrescheduleはfalse
						itemOld.cancel( false );
						it.remove();
					}
				}
			}
			log.d( "addJob: jobId=%s, add to list.", jobId );
			job_list.add( item );
		}
		
		worker.notifyEx();
	}
	
	// JobService#onStopJob から呼ばれる
	public boolean onStopJob( @NonNull JobParameters params ){
		int jobId = params.getJobId();
		
		// 同じジョブ番号がジョブリストにあるか？
		synchronized( job_list ){
			Iterator< JobItem > it = job_list.iterator();
			while( it.hasNext() ){
				JobItem item = it.next();
				if( item.jobId == jobId ){
					log.w( "onStopJob: jobId=%s, set cancel flag." );
					// リソースがなくてStopされるのだからrescheduleはtrue
					item.cancel( true );
					it.remove();
					return item.mReschedule.get();
				}
			}
		}
		
		// 該当するジョブを依頼されていない
		log.w( "onStopJob: jobId=%s, not started.." );
		return false;
		// return True to indicate to the JobManager whether you'd like to reschedule this job based on the retry criteria provided at job creation-time.
		// return False to drop the job. Regardless of the value returned, your job must stop executing.
	}
	
	final LinkedList< JobItem > job_list = new LinkedList<>();
	
	static class JobCancelledException extends RuntimeException {
		public JobCancelledException(){
			super( "job is cancelled." );
		}
	}
	
	class JobItem {
		int jobId;
		WeakReference< JobService > refJobService;
		JobParameters jobParams;
		final AtomicBoolean mJobCancelled_ = new AtomicBoolean();
		final AtomicBoolean mReschedule = new AtomicBoolean();
		final AtomicBoolean mWorkerAttached = new AtomicBoolean();
		
		final AtomicBoolean bPollingRequired = new AtomicBoolean( false );
		HashSet< String > muted_app;
		WordTrieTree muted_word;
		boolean bPollingComplete = false;
		String install_id;
		
		Call current_call;
		
		final AtomicReference< Worker > refWorker = new AtomicReference<>( null );
		
		public void notifyWorkerThread(){
			Worker worker = refWorker.get();
			if( worker != null ) worker.notifyEx();
		}
		
		public void waitWorkerThread( long ms ){
			Worker worker = refWorker.get();
			if( worker != null ) worker.waitEx( ms );
		}
		
		public void cancel( boolean bReschedule ){
			mJobCancelled_.set( true );
			mReschedule.set( bReschedule );
			if( current_call != null ) current_call.cancel();
			notifyWorkerThread();
		}
		
		public JobItem( @NonNull JobService jobService, @NonNull JobParameters params ){
			this.jobParams = params;
			this.jobId = params.getJobId();
			this.refJobService = new WeakReference<>( jobService );
		}
		
		public JobItem( int jobId ){
			this.jobId = jobId;
			this.jobParams = null;
			this.refJobService = null;
		}
		
		public boolean isJobCancelled(){
			if( mJobCancelled_.get() ) return true;
			Worker worker = refWorker.get();
			return worker != null && worker.bThreadCancelled.get();
		}
		
		public void run(){
			
			job_status.set( "job start." );
			try{
				log.d( "(JobItem.run jobId=%s", jobId );
				if( isJobCancelled() ) throw new JobCancelledException();
				
				job_status.set( "check network status.." );
				
				long net_wait_start = SystemClock.elapsedRealtime();
				while( ! checkNetwork() ){
					if( isJobCancelled() ) throw new JobCancelledException();
					long now = SystemClock.elapsedRealtime();
					long delta = now - net_wait_start;
					if( delta >= 10000L ){
						log.d( "network state timeout." );
						break;
					}
					waitWorkerThread( 333L );
				}
				
				muted_app = MutedApp.getNameSet();
				muted_word = MutedWord.getNameSet();
				
				// タスクがあれば処理する
				for( ; ; ){
					if( isJobCancelled() ) throw new JobCancelledException();
					final JSONObject data = task_list.next( context );
					if( data == null ) break;
					int task_id = data.optInt( EXTRA_TASK_ID, 0 );
					new TaskRunner().runTask( JobItem.this, task_id, data );
				}
				
				if( ! isJobCancelled() && ! bPollingComplete && jobId == JOB_POLLING ){
					// タスクがなかった場合でも定期実行ジョブからの実行ならポーリングを行う
					new TaskRunner().runTask( JobItem.this, TASK_POLLING, null );
				}
				job_status.set( "make next schedule." );
				
				if( ! isJobCancelled() && bPollingComplete ){
					// ポーリングが完了したのならポーリングが必要かどうかに合わせてジョブのスケジュールを変更する
					if( ! bPollingRequired.get() ){
						log.d( "polling job is no longer required." );
						try{
							scheduler.cancel( JOB_POLLING );
						}catch( Throwable ex ){
							log.trace( ex );
						}
					}else{
						boolean bRegistered = false;
						for( JobInfo info : scheduler.getAllPendingJobs() ){
							if( info.getId() == JOB_POLLING ){
								bRegistered = true;
								break;
							}
						}
						if( ! bRegistered ){
							scheduleJob( context, JOB_POLLING );
							log.d( "polling job is registered!" );
						}
					}
				}
			}catch( JobCancelledException ex ){
				log.e( "job execution cancelled." );
			}catch( Throwable ex ){
				log.trace( ex );
				log.e( ex, "job execution failed." );
			}finally{
				job_status.set( "job finished." );
			}
			// ジョブ終了報告
			if( ! isJobCancelled() ){
				handler.post( new Runnable() {
					@Override public void run(){
						if( isJobCancelled() ) return;
						
						synchronized( job_list ){
							job_list.remove( JobItem.this );
						}
						
						try{
							JobService jobService = refJobService == null ? null : refJobService.get();
							if( jobService != null ){
								log.d( "sending jobFinished. reschedule=%s", mReschedule.get() );
								jobService.jobFinished( jobParams, mReschedule.get() );
							}
						}catch( Throwable ex ){
							log.trace( ex );
							log.e( ex, "jobFinished failed(1)." );
						}
					}
				} );
			}
			log.d( ")JobItem.run jobId=%s, cancel=%s", jobId, isJobCancelled() );
		}
		
		private boolean checkNetwork(){
			NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
			if( ni == null ){
				log.d( "checkNetwork: getActiveNetworkInfo() returns null." );
				return false;
			}else{
				NetworkInfo.State state = ni.getState();
				NetworkInfo.DetailedState detail = ni.getDetailedState();
				log.d( "checkNetwork: state=%s,detail=%s", state, detail );
				if( state != NetworkInfo.State.CONNECTED ){
					log.d( "checkNetwork: not connected." );
					return false;
				}else{
					return true;
				}
			}
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// タスクの管理
	
	static final TaskList task_list = new TaskList();
	
	class TaskRunner {
		
		String mCustomStreamListenerSecret;
		String mCustomStreamListenerSettingString;
		JsonObject mCustomStreamListenerSetting;
		
		JobItem job;
		int taskId;
		
		final ArrayList< String > error_instance = new ArrayList<>();
		
		public void runTask( JobItem job, int taskId, JSONObject taskData ){
			try{
				log.e( "(runTask: taskId=%s", taskId );
				job_status.set( "start task " + taskId );
				
				this.job = job;
				this.taskId = taskId;
				
				long process_db_id = - 1L;
				
				if( taskId == TASK_APP_DATA_IMPORT_BEFORE ){
					scheduler.cancelAll();
					for( SavedAccount a : SavedAccount.loadAccountList( context, log ) ){
						try{
							String notification_tag = Long.toString( a.db_id );
							notification_manager.cancel( notification_tag, NOTIFICATION_ID );
						}catch( Throwable ex ){
							log.trace( ex );
						}
					}
					mBusyAppDataImportBefore.set( false );
					return;
				}else if( taskId == TASK_APP_DATA_IMPORT_AFTER ){
					NotificationTracking.resetPostAll();
					mBusyAppDataImportAfter.set( false );
					// fall
				}
				
				// アプリデータのインポート処理がビジーな間、他のジョブは実行されない
				if( mBusyAppDataImportBefore.get() ) return;
				if( mBusyAppDataImportAfter.get() ) return;
				
				if( taskId == TASK_FCM_DEVICE_TOKEN ){
					// デバイストークンが更新された
					// アプリサーバへの登録をやり直す
					
				}else if( taskId == TASK_FCM_MESSAGE ){
					boolean bDone = false;
					String tag = taskData.optString( EXTRA_TAG );
					if( tag != null ){
						for( SavedAccount sa : SavedAccount.loadByTag( context, log, tag ) ){
							NotificationTracking.resetLastLoad( sa.db_id );
							process_db_id = sa.db_id;
							bDone = true;
						}
					}
					if( ! bDone ){
						// タグにマッチする情報がなかった場合、全部読み直す
						NotificationTracking.resetLastLoad();
					}
					
				}else if( taskId == TASK_NOTIFICATION_CLEAR ){
					long db_id = Utils.optLongX( taskData, EXTRA_DB_ID, - 1L );
					deleteCacheData( db_id );
					
				}else if( taskId == TASK_DATA_INJECTED ){
					processInjectedData();
					
				}else if( taskId == TASK_BOOT_COMPLETED ){
					NotificationTracking.resetPostAll();
				}else if( taskId == TASK_PACKAGE_REPLACED ){
					NotificationTracking.resetPostAll();
					
				}else if( taskId == TASK_NOTIFICATION_DELETE ){
					long db_id = Utils.optLongX( taskData, EXTRA_DB_ID, - 1L );
					log.d( "Notification deleted! db_id=%s", db_id );
					NotificationTracking.updateRead( db_id );
					return;
				}else if( taskId == TASK_NOTIFICATION_CLICK ){
					long db_id = Utils.optLongX( taskData, EXTRA_DB_ID, - 1L );
					log.d( "Notification clicked! db_id=%s", db_id );
					
					// 通知をキャンセル
					notification_manager.cancel( Long.toString( db_id ), NOTIFICATION_ID );
					// DB更新処理
					NotificationTracking.updateRead( db_id );
					return;
				}
				
				loadCustomStreamListenerSetting();
				
				job_status.set( "make install id" );
				
				// インストールIDを生成する
				// インストールID生成時にSavedAccountテーブルを操作することがあるので
				// アカウントリストの取得より先に行う
				if( job.install_id == null ){
					job.install_id = getInstallId();
				}
				
				job_status.set( "create account thread" );
				
				LinkedList< AccountThread > thread_list = new LinkedList<>();
				for( SavedAccount _a : SavedAccount.loadAccountList( context, log ) ){
					if( _a.isPseudo() ) continue;
					if( process_db_id != - 1L && _a.db_id != process_db_id ) continue;
					AccountThread t = new AccountThread( _a );
					thread_list.add( t );
					t.start();
				}
				
				for( ; ; ){
					TreeSet< String > set = new TreeSet<>();
					Iterator< AccountThread > it = thread_list.iterator();
					while( it.hasNext() ){
						AccountThread t = it.next();
						if( ! t.isAlive() ){
							it.remove();
							continue;
						}
						set.add( t.account.host );
						if( job.isJobCancelled() ){
							t.cancel();
						}
					}
					int remain = thread_list.size();
					if( remain <= 0 ) break;
					//
					StringBuilder sb = new StringBuilder();
					for( String s : set ){
						if( sb.length() > 0 ) sb.append( ", " );
						sb.append( s );
					}
					job_status.set( "waiting " + sb.toString() );
					//
					job.waitWorkerThread( job.isJobCancelled() ? 50L : 1000L );
				}
				
				synchronized( error_instance ){
					createErrorNotification( error_instance );
				}
				
				if( ! job.isJobCancelled() ) job.bPollingComplete = true;
				
			}catch( Throwable ex ){
				log.trace( ex );
				log.e( ex, "task execution failed." );
			}finally{
				log.e( ")runTask: taskId=%s", taskId );
				job_status.set( "end task " + taskId );
			}
		}
		
		private void createErrorNotification( ArrayList< String > error_instance ){
			if( error_instance.isEmpty() ){
				return;
			}
			
			// 通知タップ時のPendingIntent
			Intent intent_click = new Intent( context, ActCallback.class );
			intent_click.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
			PendingIntent pi_click = PendingIntent.getActivity( context, 3, intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
			
			NotificationCompat.Builder builder;
			if( Build.VERSION.SDK_INT >= 26 ){
				// Android 8 から、通知のスタイルはユーザが管理することになった
				// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
				NotificationChannel channel = NotificationHelper.createNotificationChannel(
					context
					, "ErrorNotification"
					, "Error"
					, null
					, NotificationManager.IMPORTANCE_LOW
				);
				
				builder = new NotificationCompat.Builder( context, channel.getId() );
			}else{
				builder = new NotificationCompat.Builder( context, "not_used" );
			}
			
			builder
				.setContentIntent( pi_click )
				.setAutoCancel( true )
				.setSmallIcon( R.drawable.ic_notification ) // ここは常に白テーマのアイコンを使う
				.setColor( ContextCompat.getColor( context, R.color.Light_colorAccent ) ) // ここは常に白テーマの色を使う
				.setWhen( System.currentTimeMillis() )
				.setGroup( context.getPackageName() + ":" + "Error" )
			;
			
			{
				String header = context.getString( R.string.error_notification_title );
				String summary = context.getString( R.string.error_notification_summary );
				
				builder
					.setContentTitle( header )
					.setContentText( summary + ": " + error_instance.get( 0 ) )
				;
				
				NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
					.setBigContentTitle( header )
					.setSummaryText( summary );
				for( int i = 0 ; i < 5 ; ++ i ){
					if( i >= error_instance.size() ) break;
					style.addLine( error_instance.get( i ) );
				}
				builder.setStyle( style );
			}
			notification_manager.notify( NOTIFICATION_ID_ERROR, builder.build() );
		}
		
		void loadCustomStreamListenerSetting(){
			mCustomStreamListenerSetting = null;
			mCustomStreamListenerSecret = null;
			mCustomStreamListenerSettingString = pref.getString( Pref.KEY_STREAM_LISTENER_CONFIG_DATA, null );
			if( ! TextUtils.isEmpty( mCustomStreamListenerSettingString ) ){
				try{
					mCustomStreamListenerSetting = JsonValue.readHjson( mCustomStreamListenerSettingString ).asObject();
					mCustomStreamListenerSecret = pref.getString( Pref.KEY_STREAM_LISTENER_SECRET, null );
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
		}
		
		String getInstallId(){
			SharedPreferences prefDevice = PrefDevice.prefDevice( context );
			
			String sv = prefDevice.getString( PrefDevice.KEY_INSTALL_ID, null );
			if( ! TextUtils.isEmpty( sv ) ) return sv;
			
			// インストールIDを生成する前に、各データの通知登録キャッシュをクリアする
			SavedAccount.clearRegistrationCache();
			
			try{
				String device_token = prefDevice.getString( PrefDevice.KEY_DEVICE_TOKEN, null );
				if( TextUtils.isEmpty( device_token ) ){
					try{
						// トークンがまだ生成されていない場合、このメソッドは null を返します。
						device_token = FirebaseInstanceId.getInstance().getToken();
						if( TextUtils.isEmpty( device_token ) ){
							log.e( "getInstallId: missing device token." );
							return null;
						}else{
							prefDevice.edit().putString( PrefDevice.KEY_DEVICE_TOKEN, device_token ).apply();
						}
					}catch( Throwable ex ){
						log.e( "getInstallId: could not get device token." );
						log.trace( ex );
						return null;
					}
				}
				
				Request request = new Request.Builder()
					.url( APP_SERVER + "/counter" )
					.build();
				
				Call call = job.current_call = App1.ok_http_client.newCall( request );
				
				Response response = call.execute();
				
				if( ! response.isSuccessful() ){
					log.e( "getInstallId: get /counter failed. %s", response );
					return null;
				}
				
				//noinspection ConstantConditions
				sv = Utils.digestSHA256( device_token + UUID.randomUUID() + response.body().string() );
				prefDevice.edit().putString( PrefDevice.KEY_INSTALL_ID, sv ).apply();
				
				return sv;
				
			}catch( Throwable ex ){
				log.trace( ex );
				return null;
			}
		}
		
		class AccountThread extends Thread implements TootApiClient.CurrentCallCallback {
			
			@NonNull final SavedAccount account;
			
			public AccountThread( @NonNull SavedAccount a ){
				this.account = a;
				client.setCurrentCallCallback( this );
			}
			
			Call current_call;
			
			@Override public void onCallCreated( Call call ){
				this.current_call = call;
			}
			
			void cancel(){
				try{
					if( current_call != null ) current_call.cancel();
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
			
			final TootApiClient client = new TootApiClient( context, new TootApiClient.Callback() {
				@Override public boolean isApiCancelled(){
					return job.isJobCancelled();
				}
				
				@Override public void publishApiProgress( String s ){
				}
			} );
			
			@Override public void run(){
				try{
					if( account.isPseudo() ) return;
					
					if( ! account.notification_mention
						&& ! account.notification_boost
						&& ! account.notification_favourite
						&& ! account.notification_follow
						){
						unregisterDeviceToken();
						return;
					}
					
					if( registerDeviceToken() ){
						return;
					}
					
					job.bPollingRequired.set( true );
					
					if( job.isJobCancelled() ) return;
					
					ArrayList< Data > data_list = new ArrayList<>();
					checkAccount( data_list, job.muted_app, job.muted_word );
					
					if( job.isJobCancelled() ) return;
					
					showNotification( data_list );
					
				}catch( Throwable ex ){
					log.trace( ex );
				}finally{
					job.notifyWorkerThread();
				}
			}
			
			void unregisterDeviceToken(){
				try{
					if( SavedAccount.REGISTER_KEY_UNREGISTERED.equals( account.register_key ) ){
						log.d( "unregisterDeviceToken: already unregistered." );
						return;
					}
					
					// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
					if( TextUtils.isEmpty( job.install_id ) ){
						log.d( "unregisterDeviceToken: missing install_id" );
						return;
					}
					
					String tag = account.notification_tag;
					if( TextUtils.isEmpty( tag ) ){
						log.d( "unregisterDeviceToken: missing notification_tag" );
						return;
					}
					
					String post_data = "instance_url=" + Uri.encode( "https://" + account.host )
						+ "&app_id=" + Uri.encode( context.getPackageName() )
						+ "&tag=" + tag;
					
					Request request = new Request.Builder()
						.url( APP_SERVER + "/unregister" )
						.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data ) )
						.build();
					
					Call call = current_call = App1.ok_http_client.newCall( request );
					
					Response response = call.execute();
					
					log.e( "unregisterDeviceToken: %s", response );
					
					if( response.isSuccessful() ){
						account.register_key = SavedAccount.REGISTER_KEY_UNREGISTERED;
						account.register_time = 0L;
						account.saveRegisterKey();
					}
					
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
			
			// 定期的な通知更新が不要なら真を返す
			private boolean registerDeviceToken(){
				try{
					// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
					if( TextUtils.isEmpty( job.install_id ) ){
						log.d( "registerDeviceToken: missing install id" );
						return false;
					}
					
					SharedPreferences prefDevice = PrefDevice.prefDevice( context );
					
					String device_token = prefDevice.getString( PrefDevice.KEY_DEVICE_TOKEN, null );
					if( TextUtils.isEmpty( device_token ) ){
						log.d( "registerDeviceToken: missing device_token" );
						return false;
					}
					
					String access_token = Utils.optStringX( account.token_info, "access_token" );
					if( TextUtils.isEmpty( access_token ) ){
						log.d( "registerDeviceToken: missing access_token" );
						return false;
					}
					
					String tag = account.notification_tag;
					
					if( SavedAccount.REGISTER_KEY_UNREGISTERED.equals( account.register_key ) ){
						tag = null;
					}
					
					if( TextUtils.isEmpty( tag ) ){
						tag = account.notification_tag = Utils.digestSHA256( job.install_id + account.db_id + account.acct );
						account.saveNotificationTag();
					}
					
					String reg_key = Utils.digestSHA256(
						tag
							+ access_token
							+ device_token
							+ ( mCustomStreamListenerSecret == null ? "" : mCustomStreamListenerSecret )
							+ ( mCustomStreamListenerSettingString == null ? "" : mCustomStreamListenerSettingString )
					);
					long now = System.currentTimeMillis();
					if( reg_key.equals( account.register_key ) && now - account.register_time < 3600000 * 3 ){
						// タグやトークンが同一なら、前回登録に成功してから一定時間は再登録しない
						log.d( "registerDeviceToken: already registered." );
						return false;
					}
					
					// サーバ情報APIを使う
					StringBuilder post_data = new StringBuilder();
					
					post_data.append( "instance_url=" ).append( Uri.encode( "https://" + account.host ) );
					
					post_data.append( "&app_id=" ).append( Uri.encode( context.getPackageName() ) );
					
					post_data.append( "&tag=" ).append( tag );
					
					post_data.append( "&access_token=" ).append( Utils.optStringX( account.token_info, "access_token" ) );
					
					post_data.append( "&device_token=" ).append( device_token );
					
					if( ! TextUtils.isEmpty( mCustomStreamListenerSettingString )
						&& ! TextUtils.isEmpty( mCustomStreamListenerSecret )
						){
						post_data.append( "&user_config=" ).append( Uri.encode( mCustomStreamListenerSettingString ) );
						post_data.append( "&app_secret=" ).append( Uri.encode( mCustomStreamListenerSecret ) );
					}
					
					Request request = new Request.Builder()
						.url( APP_SERVER + "/register" )
						.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data.toString() ) )
						.build();
					
					Call call = current_call = App1.ok_http_client.newCall( request );
					
					Response response = call.execute();
					
					String body = null;
					try{
						//noinspection ConstantConditions
						body = response.body().string();
					}catch( Throwable ignored ){
					}
					log.e( "registerDeviceToken: %s (%s)", response, ( body == null ? "" : body ) );
					
					int code = response.code();
					
					if( response.isSuccessful() || ( code >= 400 && code < 500 ) ){
						// 登録できた時も4xxエラーだった時もDBに記録する
						account.register_key = reg_key;
						account.register_time = now;
						account.saveRegisterKey();
					}
					
				}catch( Throwable ex ){
					log.trace( ex );
				}
				return false;
			}
			
			NotificationTracking nr;
			final HashSet< Long > duplicate_check = new HashSet<>();
			final ArrayList< JSONObject > dst_array = new ArrayList<>();
			
			private void checkAccount(
				@NonNull ArrayList< Data > data_list
				, @NonNull HashSet< String > muted_app
				, @NonNull WordTrieTree muted_word
			){
				nr = NotificationTracking.load( account.db_id );
				
				// まずキャッシュされたデータを処理する
				if( nr.last_data != null ){
					try{
						JSONArray array = new JSONArray( nr.last_data );
						for( int i = array.length() - 1 ; i >= 0 ; -- i ){
							if( job.isJobCancelled() ) return;
							JSONObject src = array.optJSONObject( i );
							update_sub( src, data_list, muted_app, muted_word );
						}
					}catch( JSONException ex ){
						log.trace( ex );
					}
				}
				
				if( job.isJobCancelled() ) return;
				
				// 前回の更新から一定時刻が経過したら新しいデータを読んでリストに追加する
				long now = System.currentTimeMillis();
				if( now - nr.last_load >= 60000L * 2 ){
					nr.last_load = now;
					
					client.setAccount( account );
					
					for( int nTry = 0 ; nTry < 4 ; ++ nTry ){
						if( job.isJobCancelled() ) return;
						
						String path = PATH_NOTIFICATIONS;
						if( nid_last_show != - 1L ){
							path = path + "?since_id=" + nid_last_show;
						}
						
						TootApiResult result = client.request( path );
						if( result == null ){
							log.d( "cancelled." );
							break;
						}else if( result.array != null ){
							try{
								JSONArray array = result.array;
								for( int i = array.length() - 1 ; i >= 0 ; -- i ){
									JSONObject src = array.optJSONObject( i );
									update_sub( src, data_list, muted_app, muted_word );
								}
							}catch( JSONException ex ){
								log.trace( ex );
							}
							break;
						}else{
							log.d( "error. %s", result.error );
							
							String sv = result.error;
							if( sv.contains( "Timeout" ) && ! account.dont_show_timeout ){
								synchronized( error_instance ){
									boolean bFound = false;
									for( String x : error_instance ){
										if( x.equals( sv ) ){
											bFound = true;
											break;
										}
									}
									if( ! bFound ){
										error_instance.add( sv );
									}
								}
							}
						}
					}
				}
				
				if( job.isJobCancelled() ) return;
				
				Collections.sort( dst_array, new Comparator< JSONObject >() {
					@Override public int compare( JSONObject a, JSONObject b ){
						long la = Utils.optLongX( a, KEY_TIME, 0 );
						long lb = Utils.optLongX( b, KEY_TIME, 0 );
						// 新しい順
						if( la < lb ) return + 1;
						if( la > lb ) return - 1;
						return 0;
					}
				} );
				
				if( job.isJobCancelled() ) return;
				
				JSONArray d = new JSONArray();
				for( int i = 0 ; i < 10 ; ++ i ){
					if( i >= dst_array.size() ) break;
					d.put( dst_array.get( i ) );
				}
				nr.last_data = d.toString();
				nr.save();
			}
			
			long nid_last_show = - 1L;
			
			void update_sub(
				@NonNull JSONObject src
				, @NonNull ArrayList< Data > data_list
				, @NonNull HashSet< String > muted_app
				, @NonNull WordTrieTree muted_word
			) throws JSONException{
				
				if( nr.nid_read == 0 || nr.nid_show == 0 ){
					log.d( "update_sub account_db_id=%s, nid_read=%s, nid_show=%s", account.db_id, nr.nid_read, nr.nid_show );
				}
				
				long id = Utils.optLongX( src, "id" );
				
				if( duplicate_check.contains( id ) ) return;
				duplicate_check.add( id );
				
				if( id > nid_last_show ){
					nid_last_show = id;
				}
				
				String type = Utils.optStringX( src, "type" );
				
				if( id <= nr.nid_read ){
					// log.d("update_sub: ignore data that id=%s, <= read id %s ",id,nr.nid_read);
					return;
				}else{
					log.d( "update_sub: found data that id=%s, > read id %s ", id, nr.nid_read );
				}
				
				if( id > nr.nid_show ){
					log.d( "update_sub: found new data that id=%s, greater than shown id %s ", id, nr.nid_show );
					// 種別チェックより先に「表示済み」idの更新を行う
					nr.nid_show = id;
				}
				
				if( ( ! account.notification_mention && TootNotification.TYPE_MENTION.equals( type ) )
					|| ( ! account.notification_boost && TootNotification.TYPE_REBLOG.equals( type ) )
					|| ( ! account.notification_favourite && TootNotification.TYPE_FAVOURITE.equals( type ) )
					|| ( ! account.notification_follow && TootNotification.TYPE_FOLLOW.equals( type ) )
					){
					return;
				}
				
				TootNotification notification = TootNotification.parse( context, account, src );
				if( notification == null ){
					return;
				}
				
				{
					TootStatus status = notification.status;
					if( status != null ){
						if( status.checkMuted( muted_app, muted_word ) ){
							return;
						}
					}
				}
				
				//
				Data data = new Data();
				data.access_info = account;
				data.notification = notification;
				data_list.add( data );
				//
				src.put( KEY_TIME, data.notification.time_created_at );
				dst_array.add( src );
			}
			
			String getNotificationLine( @NonNull String type, @NonNull CharSequence display_name ){
				if( TootNotification.TYPE_FAVOURITE.equals( type ) ){
					return "- " + context.getString( R.string.display_name_favourited_by, display_name );
				}
				if( TootNotification.TYPE_REBLOG.equals( type ) ){
					return "- " + context.getString( R.string.display_name_boosted_by, display_name );
				}
				if( TootNotification.TYPE_MENTION.equals( type ) ){
					return "- " + context.getString( R.string.display_name_replied_by, display_name );
				}
				if( TootNotification.TYPE_FOLLOW.equals( type ) ){
					return "- " + context.getString( R.string.display_name_followed_by, display_name );
				}
				return "- " + "?";
			}
			
			private void showNotification( @NonNull ArrayList< Data > data_list ){
				
				String notification_tag = Long.toString( account.db_id );
				if( data_list.isEmpty() ){
					log.d( "showNotification[%s] cancel notification.", account.acct );
					notification_manager.cancel( notification_tag, NOTIFICATION_ID );
					return;
				}
				
				Collections.sort( data_list, new Comparator< Data >() {
					@Override public int compare( Data a, Data b ){
						long la = a.notification.time_created_at;
						long lb = b.notification.time_created_at;
						// 新しい順
						if( la < lb ) return + 1;
						if( la > lb ) return - 1;
						return 0;
					}
				} );
				Data item = data_list.get( 0 );
				
				NotificationTracking nt = NotificationTracking.load( account.db_id );
				if( item.notification.time_created_at == nt.post_time
					&& item.notification.id == nt.post_id
					){
					// 先頭にあるデータが同じなら、通知を更新しない
					// このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
					
					log.d( "showNotification[%s] id=%s is already shown.", account.acct, item.notification.id );
					
					return;
				}
				
				nt.updatePost( item.notification.id, item.notification.time_created_at );
				
				log.d( "showNotification[%s] creating notification(1)", account.acct );
				
				// 通知タップ時のPendingIntent
				Intent intent_click = new Intent( context, ActCallback.class );
				intent_click.setAction( ActCallback.ACTION_NOTIFICATION_CLICK );
				intent_click.setData( Uri.parse( "subwaytooter://notification_click?db_id=" + account.db_id ) );
				intent_click.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
				PendingIntent pi_click = PendingIntent.getActivity( context, ( 256 + (int) account.db_id ), intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
				
				// 通知を消去した時のPendingIntent
				Intent intent_delete = new Intent( context, EventReceiver.class );
				intent_delete.setAction( EventReceiver.ACTION_NOTIFICATION_DELETE );
				intent_delete.putExtra( EXTRA_DB_ID, account.db_id );
				PendingIntent pi_delete = PendingIntent.getBroadcast( context, ( Integer.MAX_VALUE - (int) account.db_id ), intent_delete, PendingIntent.FLAG_UPDATE_CURRENT );
				
				log.d( "showNotification[%s] creating notification(2)", account.acct );
				
				NotificationCompat.Builder builder;
				if( Build.VERSION.SDK_INT >= 26 ){
					// Android 8 から、通知のスタイルはユーザが管理することになった
					// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
					NotificationChannel channel = NotificationHelper.createNotificationChannel( context, account );
					
					builder = new NotificationCompat.Builder( context, channel.getId() );
				}else{
					builder = new NotificationCompat.Builder( context, "not_used" );
				}
				
				builder
					.setContentIntent( pi_click )
					.setDeleteIntent( pi_delete )
					.setAutoCancel( true )
					.setSmallIcon( R.drawable.ic_notification ) // ここは常に白テーマのアイコンを使う
					.setColor( ContextCompat.getColor( context, R.color.Light_colorAccent ) ) // ここは常に白テーマの色を使う
					.setWhen( item.notification.time_created_at );
				
				// Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
				// 束ねられた通知をタップしても pi_click が実行されないので困るため、
				// アカウント別にグループキーを設定する
				builder.setGroup( context.getPackageName() + ":" + account.acct );
				
				log.d( "showNotification[%s] creating notification(3)", account.acct );
				
				if( Build.VERSION.SDK_INT < 26 ){
					
					int iv = 0;
					
					if( pref.getBoolean( Pref.KEY_NOTIFICATION_SOUND, true ) ){
						
						Uri sound_uri = null;
						
						try{
							String acct = item.access_info.getFullAcct( item.notification.account );
							if( acct != null ){
								String sv = AcctColor.getNotificationSound( acct );
								sound_uri = TextUtils.isEmpty( sv ) ? null : Uri.parse( sv );
							}
						}catch( Throwable ex ){
							log.trace( ex );
						}
						
						if( sound_uri == null ){
							try{
								String sv = account.sound_uri;
								sound_uri = TextUtils.isEmpty( sv ) ? null : Uri.parse( sv );
							}catch( Throwable ex ){
								log.trace( ex );
							}
						}
						
						boolean bSoundSet = false;
						if( sound_uri != null ){
							try{
								builder.setSound( sound_uri );
								bSoundSet = true;
							}catch( Throwable ex ){
								log.trace( ex );
							}
						}
						if( ! bSoundSet ){
							iv |= NotificationCompat.DEFAULT_SOUND;
						}
					}
					
					log.d( "showNotification[%s] creating notification(4)", account.acct );
					
					if( pref.getBoolean( Pref.KEY_NOTIFICATION_VIBRATION, true ) ){
						iv |= NotificationCompat.DEFAULT_VIBRATE;
					}
					
					log.d( "showNotification[%s] creating notification(5)", account.acct );
					
					if( pref.getBoolean( Pref.KEY_NOTIFICATION_LED, true ) ){
						iv |= NotificationCompat.DEFAULT_LIGHTS;
					}
					
					log.d( "showNotification[%s] creating notification(6)", account.acct );
					
					builder.setDefaults( iv );
				}
				
				log.d( "showNotification[%s] creating notification(7)", account.acct );
				
				String a = getNotificationLine( item.notification.type, item.notification.account.decoded_display_name );
				String acct = item.access_info.acct;
				if( data_list.size() == 1 ){
					builder.setContentTitle( a );
					builder.setContentText( acct );
				}else{
					String header = context.getString( R.string.notification_count, data_list.size() );
					builder.setContentTitle( header )
						.setContentText( a );
					
					NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
						.setBigContentTitle( header )
						.setSummaryText( acct );
					for( int i = 0 ; i < 5 ; ++ i ){
						if( i >= data_list.size() ) break;
						item = data_list.get( i );
						a = getNotificationLine( item.notification.type, item.notification.account.decoded_display_name );
						style.addLine( a );
					}
					builder.setStyle( style );
				}
				
				log.d( "showNotification[%s] set notification...", account.acct );
				
				notification_manager.notify( notification_tag, NOTIFICATION_ID, builder.build() );
			}
		}
		
		private void processInjectedData(){
			while( inject_queue.size() > 0 ){
				
				InjectData data = inject_queue.poll();
				
				SavedAccount account = SavedAccount.loadAccount( context, log, data.account_db_id );
				if( account == null ) continue;
				
				NotificationTracking nr = NotificationTracking.load( data.account_db_id );
				
				HashSet< Long > duplicate_check = new HashSet<>();
				
				ArrayList< JSONObject > dst_array = new ArrayList<>();
				if( nr.last_data != null ){
					// まずキャッシュされたデータを処理する
					try{
						JSONArray array = new JSONArray( nr.last_data );
						for( int i = array.length() - 1 ; i >= 0 ; -- i ){
							JSONObject src = array.optJSONObject( i );
							long id = Utils.optLongX( src, "id" );
							dst_array.add( src );
							duplicate_check.add( id );
							log.d( "add old. id=%s", id );
						}
					}catch( JSONException ex ){
						log.trace( ex );
					}
				}
				for( TootNotification item : data.list ){
					try{
						if( duplicate_check.contains( item.id ) ){
							log.d( "skip duplicate. id=%s", item.id );
							continue;
						}
						duplicate_check.add( item.id );
						
						String type = item.type;
						
						if( ( ! account.notification_mention && TootNotification.TYPE_MENTION.equals( type ) )
							|| ( ! account.notification_boost && TootNotification.TYPE_REBLOG.equals( type ) )
							|| ( ! account.notification_favourite && TootNotification.TYPE_FAVOURITE.equals( type ) )
							|| ( ! account.notification_follow && TootNotification.TYPE_FOLLOW.equals( type ) )
							){
							log.d( "skip by setting. id=%s", item.id );
							continue;
						}
						
						//
						JSONObject src = item.json;
						src.put( KEY_TIME, item.time_created_at );
						dst_array.add( src );
					}catch( JSONException ex ){
						log.trace( ex );
					}
				}
				
				// 新しい順にソート
				Collections.sort( dst_array, new Comparator< JSONObject >() {
					@Override public int compare( JSONObject a, JSONObject b ){
						long la = Utils.optLongX( a, KEY_TIME, 0 );
						long lb = Utils.optLongX( b, KEY_TIME, 0 );
						// 新しい順
						if( la < lb ) return + 1;
						if( la > lb ) return - 1;
						return 0;
					}
				} );
				
				// 最新10件を保存
				JSONArray d = new JSONArray();
				for( int i = 0 ; i < 10 ; ++ i ){
					if( i >= dst_array.size() ){
						log.d( "inject %s data", i );
						break;
					}
					d.put( dst_array.get( i ) );
				}
				nr.last_data = d.toString();
				
				nr.save();
			}
		}
		
		private void deleteCacheData( long db_id ){
			
			SavedAccount account = SavedAccount.loadAccount( context, log, db_id );
			if( account == null ) return;
			
			NotificationTracking nr = NotificationTracking.load( db_id );
			
			nr.last_data = new JSONArray().toString();
			
			nr.save();
		}
		
	}
	
	////////////////////////////////////////////////////////////////////////////////////
	
	public static void scheduleJob( Context context, int job_id ){
		
		JobScheduler scheduler = (JobScheduler) context.getSystemService( Context.JOB_SCHEDULER_SERVICE );
		ComponentName component = new ComponentName( context, PollingService.class );
		
		JobInfo.Builder builder = new JobInfo.Builder( job_id, component )
			.setRequiredNetworkType( JobInfo.NETWORK_TYPE_ANY );
		
		if( job_id == JOB_POLLING ){
			if( Build.VERSION.SDK_INT >= 24 ){
				builder.setPeriodic( 60000L * 5, 60000L * 10 );
			}else{
				builder.setPeriodic( 60000L * 5 );
			}
			builder.setPersisted( true );
		}else{
			builder
				.setMinimumLatency( 0 )
				.setOverrideDeadline( 60000L );
		}
		
		scheduler.schedule( builder.build() );
	}
	
	////////////////////////////////////////////////////////////////////////////
	// FCMメッセージの処理
	//
	
	////////////////////////////////////////////////////////////////////////////
	// タスクの追加
	
	private static void addTask( @NonNull Context context, boolean removeOld, int task_id, @Nullable JSONObject taskData ){
		try{
			if( taskData == null ) taskData = new JSONObject();
			taskData.put( EXTRA_TASK_ID, task_id );
			task_list.addLast( context, removeOld, taskData );
			scheduleJob( context, JOB_TASK );
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	public static void queueUpdateListener( @NonNull Context context ){
		addTask( context, true, TASK_UPDATE_LISTENER, null );
	}
	
	public static void queueUpdateNotification( @NonNull Context context ){
		addTask( context, true, TASK_UPDATE_NOTIFICATION, null );
	}
	
	public static void injectData( @NonNull Context context, long account_db_id, @NonNull TootNotification.List src ){
		
		if( src.isEmpty() ) return;
		
		InjectData id = new InjectData();
		id.account_db_id = account_db_id;
		id.list.addAll( src );
		inject_queue.add( id );
		
		addTask( context, true, TASK_DATA_INJECTED, null );
	}
	
	public static void queueNotificationCleared( @NonNull Context context, long db_id ){
		try{
			JSONObject data = new JSONObject();
			data.putOpt( EXTRA_DB_ID, db_id );
			addTask( context, true, TASK_NOTIFICATION_CLEAR, data );
		}catch( JSONException ex ){
			log.trace( ex );
		}
	}
	
	public static void queueNotificationDeleted( Context context, long db_id ){
		try{
			JSONObject data = new JSONObject();
			data.putOpt( EXTRA_DB_ID, db_id );
			addTask( context, true, TASK_NOTIFICATION_DELETE, data );
		}catch( JSONException ex ){
			log.trace( ex );
		}
	}
	
	public static void queueNotificationClicked( Context context, long db_id ){
		try{
			JSONObject data = new JSONObject();
			data.putOpt( EXTRA_DB_ID, db_id );
			addTask( context, true, TASK_NOTIFICATION_CLICK, data );
		}catch( JSONException ex ){
			log.trace( ex );
		}
	}
	
	public static void queueAppDataImportBefore( Context context ){
		mBusyAppDataImportBefore.set( true );
		mBusyAppDataImportAfter.set( true );
		addTask( context, false, TASK_APP_DATA_IMPORT_BEFORE, null );
	}
	
	public static void queueAppDataImportAfter( Context context ){
		addTask( context, false, TASK_APP_DATA_IMPORT_AFTER, null );
	}
	
	public static void queueFCMTokenUpdated( Context context ){
		addTask( context, true, TASK_FCM_DEVICE_TOKEN, null );
	}
	
	public static void queueBootCompleted( Context context ){
		addTask( context, true, TASK_BOOT_COMPLETED, null );
	}
	
	public static void queuePackageReplaced( Context context ){
		addTask( context, true, TASK_PACKAGE_REPLACED, null );
	}
	
	public interface JobStatusCallback {
		void onStatus( String sv );
	}
	
	static final AtomicReference< String > job_status = new AtomicReference<>( null );
	
	public static void handleFCMMessage( Context context, String tag, JobStatusCallback callback ){
		log.d( "handleFCMMessage: start. tag=%s", tag );
		long time_start = SystemClock.elapsedRealtime();
		
		callback.onStatus( "=>" );
		
		// タスクを追加
		JSONObject data = new JSONObject();
		try{
			if( tag != null ) data.putOpt( EXTRA_TAG, tag );
			data.put( EXTRA_TASK_ID, TASK_FCM_MESSAGE );
		}catch( JSONException ignored ){
		}
		task_list.addLast( context, true, data );
		
		callback.onStatus( "==>" );
		
		// 疑似ジョブを開始
		PollingWorker pw = getInstance( context );
		pw.addJob( JOB_FCM, false );
		
		// 疑似ジョブが終了するまで待機する
		for( ; ; ){
			// ジョブが完了した？
			long now = SystemClock.elapsedRealtime();
			if( ! pw.hasJob( JOB_FCM ) ){
				log.d( "handleFCMMessage: JOB_FCM completed. time=%.2f", ( now - time_start ) / 1000f );
				break;
			}
			// ジョブの状況を通知する
			String sv = job_status.get();
			if( sv == null ) sv = "(null)";
			callback.onStatus( sv );
			
			// 少し待機
			try{
				Thread.sleep( 50L );
			}catch( InterruptedException ex ){
				log.e( ex, "handleFCMMessage: blocking is interrupted." );
				break;
			}
		}
	}
	
}
