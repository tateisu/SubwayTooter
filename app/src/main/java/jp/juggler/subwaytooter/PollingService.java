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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
public class PollingService extends JobService {
	
	static final LogCategory log = new LogCategory( "PollingService" );
	
	static final int NOTIFICATION_ID = 1;
	
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
	
	//////////////////////////////////////////////////////////////////////
	// ワーカースレッドの管理
	
	PollingService service;
	SharedPreferences pref;
	NotificationManager notification_manager;
	JobScheduler scheduler;
	Handler handler;
	
	@Override public void onCreate(){
		super.onCreate();
		log.d( "service onCreate" );
		
		this.handler = new Handler();
		
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		App1.prepare( getApplicationContext() );
		
		service = PollingService.this;
		
		scheduler = (JobScheduler) getApplicationContext().getSystemService( Context.JOB_SCHEDULER_SERVICE );
		notification_manager = (NotificationManager) getApplicationContext().getSystemService( NOTIFICATION_SERVICE );
		pref = Pref.pref( service );
		
		worker = new Worker();
		worker.start();
	}
	
	@Override public void onDestroy(){
		super.onDestroy();
		log.d( "service onDestroy" );
		
		for( JobItem item : job_list ){
			item.cancel( false );
		}
		
		worker.cancel();
		
		try{
			worker.join();
		}catch( InterruptedException ex ){
			log.e( ex, "onDestroy: can't stop worker thread." );
		}
	}
	
	Worker worker;
	
	class Worker extends WorkerBase {
		
		AtomicBoolean bThreadCancelled = new AtomicBoolean( false );
		
		public void cancel(){
			bThreadCancelled.set( true );
			notifyEx();
		}
		
		public void run(){
			log.e( "worker thread start." );
			while( ! bThreadCancelled.get() ){
				
				JobItem item = null;
				synchronized( job_list ){
					for( JobItem ji : job_list ){
						if( ji.mWorkerAttached.get() || ji.mJobCancelled.get() ) continue;
						item = ji;
						item.mWorkerAttached.set( true );
						break;
					}
				}
				
				if( item == null ){
					waitEx( 86400000L );
					continue;
				}
				
				try{
					item.refWorker.set( Worker.this );
					item.run();
				}catch( Throwable ex ){
					log.trace( ex );
				}finally{
					item.refWorker.set( null );
				}
			}
			log.e( "worker thread end." );
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// ジョブの管理
	
	@Override public boolean onStartJob( JobParameters params ){
		
		int jobId = params.getJobId();
		JobItem item = new JobItem( params );
		
		// 同じジョブ番号がジョブリストにあるか？
		synchronized( job_list ){
			Iterator< JobItem > it = job_list.iterator();
			while( it.hasNext() ){
				JobItem itemOld = it.next();
				if( itemOld.jobId == jobId ){
					log.w( "onStartJob: jobId=%s, old job cancelled." );
					// 同じジョブをすぐに始めるのだからrescheduleはfalse
					itemOld.cancel( false );
					it.remove();
				}
			}
			log.d( "onStartJob: jobId=%s, add to list.", jobId );
			job_list.add( item );
		}
		
		worker.notifyEx();
		return true;
		// return True if your service needs to process the work (on a separate thread).
		// return False if there's no more work to be done for this job.
	}
	
	// ジョブをキャンセルする
	@Override public boolean onStopJob( JobParameters params ){
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
		JobParameters jobParams;
		final AtomicBoolean mJobCancelled = new AtomicBoolean();
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
			mJobCancelled.set( true );
			mReschedule.set( bReschedule );
			if( current_call != null ) current_call.cancel();
			notifyWorkerThread();
		}
		
		public JobItem( JobParameters params ){
			this.jobParams = params;
			this.jobId = params.getJobId();
		}
		
		public void run(){
			
			try{
				log.d( "(JobItem.run jobId=%s", jobId );
				
				if( mJobCancelled.get() ) throw new JobCancelledException();
				
				muted_app = MutedApp.getNameSet();
				muted_word = MutedWord.getNameSet();
				
				// タスクがあれば処理する
				for( ; ; ){
					if( mJobCancelled.get() ) throw new JobCancelledException();
					final JSONObject data = task_list.next( service );
					if( data == null ) break;
					int task_id = data.optInt( EXTRA_TASK_ID, 0 );
					new TaskRunner().runTask( JobItem.this, task_id, data );
				}
				
				if( ! mJobCancelled.get() && ! bPollingComplete && jobId == JOB_POLLING ){
					// タスクがなかった場合でも定期実行ジョブからの実行ならポーリングを行う
					new TaskRunner().runTask( JobItem.this, TASK_POLLING, null );
				}
				
				if( ! mJobCancelled.get() && bPollingComplete ){
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
							scheduleJob( service, JOB_POLLING );
							log.d( "polling job is registered!" );
						}
					}
				}
			}catch( JobCancelledException ex ){
				log.e( "job execution cancelled." );
			}catch( Throwable ex ){
				log.trace( ex );
				log.e( ex, "job execution failed." );
			}
			// ジョブ終了報告
			if( ! mJobCancelled.get() ){
				handler.post( new Runnable() {
					@Override public void run(){
						if( mJobCancelled.get() ) return;

						synchronized( job_list ){
							job_list.remove( JobItem.this );
						}

						try{
							log.d( "sending jobFinished. reschedule=%s",mReschedule.get() );
							jobFinished( jobParams, mReschedule.get() );
						}catch( Throwable ex ){
							log.trace( ex );
							log.e( ex, "jobFinished failed(1)." );
						}
					}
				} );
			}
			log.d( ")JobItem.run jobId=%s, cancel=%s", jobId, mJobCancelled.get() );
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
		
		public void runTask( JobItem job, int taskId, JSONObject taskData ){
			try{
				log.e( "(runTask: taskId=%s", taskId );
				this.job = job;
				this.taskId = taskId;
				
				// インストールIDを生成する
				// インストールID生成時にSavedAccountテーブルを操作することがあるので
				// アカウントリストの取得より先に行う
				if( job.install_id == null ){
					job.install_id = getInstallId();
				}
				
				if( taskId == TASK_APP_DATA_IMPORT_BEFORE ){
					scheduler.cancelAll();
					for( SavedAccount a : SavedAccount.loadAccountList( PollingService.this, log ) ){
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
				
				ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( PollingService.this, log );
				
				if( taskId == TASK_FCM_DEVICE_TOKEN ){
					// デバイストークンが更新された
					// アプリサーバへの登録をやり直す
					
				}else if( taskId == TASK_FCM_MESSAGE ){
					boolean bDone = false;
					String tag = taskData.optString( EXTRA_TAG );
					if( tag != null ){
						for( SavedAccount sa : SavedAccount.loadByTag( PollingService.this, log, tag ) ){
							NotificationTracking.resetLastLoad( sa.db_id );
							bDone = true;
						}
					}
					if( ! bDone ){
						// タグにマッチする情報がなかった場合、全部読み直す
						NotificationTracking.resetLastLoad();
					}
					
				}else if( taskId == TASK_NOTIFICATION_CLEAR ){
					long db_id = taskData.optLong( EXTRA_DB_ID, - 1L );
					deleteCacheData( db_id );
					
				}else if( taskId == TASK_DATA_INJECTED ){
					processInjectedData();
					
				}else if( taskId == TASK_BOOT_COMPLETED ){
					NotificationTracking.resetPostAll();
				}else if( taskId == TASK_PACKAGE_REPLACED ){
					NotificationTracking.resetPostAll();
					
				}else if( taskId == TASK_NOTIFICATION_DELETE ){
					long db_id = taskData.optLong( EXTRA_DB_ID, - 1L );
					log.d( "Notification deleted! db_id=%s", db_id );
					NotificationTracking.updateRead( db_id );
					return;
				}else if( taskId == TASK_NOTIFICATION_CLICK ){
					long db_id = taskData.optLong( EXTRA_DB_ID, - 1L );
					log.d( "Notification clicked! db_id=%s", db_id );
					
					// 通知をキャンセル
					notification_manager.cancel( Long.toString( db_id ), NOTIFICATION_ID );
					// DB更新処理
					NotificationTracking.updateRead( db_id );
					return;
				}
				
				loadCustomStreamListenerSetting();
				
				LinkedList< AccountThread > thread_list = new LinkedList<>();
				for( SavedAccount _a : account_list ){
					AccountThread t = new AccountThread( _a );
					thread_list.add( t );
					t.start();
				}
				
				for( ; ; ){
					Iterator< AccountThread > it = thread_list.iterator();
					while( it.hasNext() ){
						AccountThread t = it.next();
						if( ! t.isAlive() ){
							it.remove();
							continue;
						}
						if( job.mJobCancelled.get() ){
							t.cancel();
						}
					}
					if( thread_list.isEmpty() ) break;
					
					job.waitWorkerThread( job.mJobCancelled.get() ? 50L : 1000L );
				}
				
				if( ! job.mJobCancelled.get() ) job.bPollingComplete = true;
				
			}catch( Throwable ex ){
				log.trace( ex );
				log.e( ex, "task execution failed." );
			}finally{
				log.e( ")runTask: taskId=%s", taskId );
			}
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
			SharedPreferences prefDevice = PrefDevice.prefDevice( service );
			
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
			
			final TootApiClient client = new TootApiClient( PollingService.this, new TootApiClient.Callback() {
				@Override public boolean isApiCancelled(){
					return job.mJobCancelled.get();
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
					
					if( job.mJobCancelled.get() ) return;
					
					ArrayList< Data > data_list = new ArrayList<>();
					checkAccount( data_list, job.muted_app, job.muted_word );
					
					if( job.mJobCancelled.get() ) return;
					
					showNotification( data_list );
					
				}catch( Throwable ex ){
					log.trace( ex );
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
						+ "&app_id=" + Uri.encode( getPackageName() )
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
					
					SharedPreferences prefDevice = PrefDevice.prefDevice( service );
					
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
					
					post_data.append( "&app_id=" ).append( Uri.encode( getPackageName() ) );
					
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
							if( job.mJobCancelled.get() ) return;
							JSONObject src = array.optJSONObject( i );
							update_sub( src, data_list, muted_app, muted_word );
						}
					}catch( JSONException ex ){
						log.trace( ex );
					}
				}
				
				if( job.mJobCancelled.get() ) return;
				
				// 前回の更新から一定時刻が経過したら新しいデータを読んでリストに追加する
				long now = System.currentTimeMillis();
				if( now - nr.last_load >= 60000L * 2 ){
					nr.last_load = now;
					
					client.setAccount( account );
					
					for( int nTry = 0 ; nTry < 4 ; ++ nTry ){
						if( job.mJobCancelled.get() ) return;
						
						TootApiResult result = client.request( PATH_NOTIFICATIONS );
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
						}
					}
				}
				
				if( job.mJobCancelled.get() ) return;
				
				Collections.sort( dst_array, new Comparator< JSONObject >() {
					@Override public int compare( JSONObject a, JSONObject b ){
						long la = a.optLong( KEY_TIME, 0 );
						long lb = b.optLong( KEY_TIME, 0 );
						// 新しい順
						if( la < lb ) return + 1;
						if( la > lb ) return - 1;
						return 0;
					}
				} );
				
				if( job.mJobCancelled.get() ) return;
				
				JSONArray d = new JSONArray();
				for( int i = 0 ; i < 10 ; ++ i ){
					if( i >= dst_array.size() ) break;
					d.put( dst_array.get( i ) );
				}
				nr.last_data = d.toString();
				nr.save();
			}
			
			void update_sub(
				@NonNull JSONObject src
				, @NonNull ArrayList< Data > data_list
				, @NonNull HashSet< String > muted_app
				, @NonNull WordTrieTree muted_word
			) throws JSONException{
				
				if( nr.nid_read == 0 || nr.nid_show == 0 ){
					log.d( "update_sub account_db_id=%s, nid_read=%s, nid_show=%s", account.db_id, nr.nid_read, nr.nid_show );
				}
				
				long id = src.optLong( "id" );
				
				if( duplicate_check.contains( id ) ) return;
				duplicate_check.add( id );
				
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
				
				TootNotification notification = TootNotification.parse( PollingService.this, account, src );
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
					return "- " + getString( R.string.display_name_favourited_by, display_name );
				}
				if( TootNotification.TYPE_REBLOG.equals( type ) ){
					return "- " + getString( R.string.display_name_boosted_by, display_name );
				}
				if( TootNotification.TYPE_MENTION.equals( type ) ){
					return "- " + getString( R.string.display_name_replied_by, display_name );
				}
				if( TootNotification.TYPE_FOLLOW.equals( type ) ){
					return "- " + getString( R.string.display_name_followed_by, display_name );
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
				Intent intent_click = new Intent( service, ActCallback.class );
				intent_click.setAction( ActCallback.ACTION_NOTIFICATION_CLICK );
				intent_click.setData( Uri.parse( "subwaytooter://notification_click?db_id=" + account.db_id ) );
				intent_click.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
				PendingIntent pi_click = PendingIntent.getActivity( service, ( 256 + (int) account.db_id ), intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
				
				// 通知を消去した時のPendingIntent
				Intent intent_delete = new Intent( service, EventReceiver.class );
				intent_delete.setAction( EventReceiver.ACTION_NOTIFICATION_DELETE );
				intent_delete.putExtra( EXTRA_DB_ID, account.db_id );
				PendingIntent pi_delete = PendingIntent.getBroadcast( service, ( Integer.MAX_VALUE - (int) account.db_id ), intent_delete, PendingIntent.FLAG_UPDATE_CURRENT );
				
				log.d( "showNotification[%s] creating notification(2)", account.acct );
				
				NotificationCompat.Builder builder;
				if( Build.VERSION.SDK_INT >= 26 ){
					// Android 8 から、通知のスタイルはユーザが管理することになった
					// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
					NotificationChannel channel = NotificationHelper.createNotificationChannel( service, account );
					
					builder = new NotificationCompat.Builder( service, channel.getId() );
				}else{
					builder = new NotificationCompat.Builder( service, "not_used" );
				}
				
				builder
					.setContentIntent( pi_click )
					.setDeleteIntent( pi_delete )
					.setAutoCancel( true )
					.setSmallIcon( R.drawable.ic_notification ) // ここは常に白テーマのアイコンを使う
					.setColor( ContextCompat.getColor( service, R.color.Light_colorAccent ) ) // ここは常に白テーマの色を使う
					.setWhen( item.notification.time_created_at );
				
				// Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
				// 束ねられた通知をタップしても pi_click が実行されないので困るため、
				// アカウント別にグループキーを設定する
				builder.setGroup( getPackageName() + ":" + account.acct );
				
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
					String header = getString( R.string.notification_count, data_list.size() );
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
				
				SavedAccount account = SavedAccount.loadAccount( PollingService.this, log, data.account_db_id );
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
							long id = src.optLong( "id" );
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
						long la = a.optLong( KEY_TIME, 0 );
						long lb = b.optLong( KEY_TIME, 0 );
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
			
			SavedAccount account = SavedAccount.loadAccount( PollingService.this, log, db_id );
			if( account == null ) return;
			
			NotificationTracking nr = NotificationTracking.load( db_id );
			
			nr.last_data = new JSONArray().toString();
			
			nr.save();
		}
		
	}
	
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
	
	public static void queueFCMMessage( @NonNull Context context, @Nullable String tag ){
		try{
			JSONObject data = new JSONObject();
			if( tag != null ) data.putOpt( EXTRA_TAG, tag );
			addTask( context, true, TASK_FCM_MESSAGE, data );
		}catch( JSONException ex ){
			log.trace( ex );
		}
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
}
