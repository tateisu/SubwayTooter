package jp.juggler.subwaytooter;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class AlarmService extends IntentService {
	
	static final LogCategory log = new LogCategory( "AlarmService" );
	
	// PendingIntent の request code
	static final int PENDING_CODE_ALARM = 1;
	static final String ACTION_NOTIFICATION_DELETE = "notification_delete";
	static final String ACTION_NOTIFICATION_CLICK = "notification_click";
	static final int NOTIFICATION_ID = 1;
	static final long INTERVAL_MIN = 60000L * 5;
	
	// Notifiation のJSONObject を日時でソートするためにデータを追加する
	static final String KEY_TIME = "<>time";
	private static final String ACTION_DATA_INJECTED = "data_injected";
	private static final String EXTRA_DB_ID = "db_id";
	
	public AlarmService(){
		// name: Used to name the worker thread, important only for debugging.
		super( "AlarmService" );
	}
	
	AlarmManager alarm_manager;
	PowerManager power_manager;
	NotificationManager notification_manager;
	PowerManager.WakeLock wake_lock;
	PendingIntent pi_next;
	
	@Override public void onCreate(){
		super.onCreate();
		log.d("ctor");
		
		alarm_manager = (AlarmManager) getApplicationContext().getSystemService( ALARM_SERVICE );
		power_manager = (PowerManager) getApplicationContext().getSystemService( POWER_SERVICE );
		notification_manager = (NotificationManager) getApplicationContext().getSystemService( NOTIFICATION_SERVICE );
		
		wake_lock = power_manager.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, AlarmService.class.getName() );
		wake_lock.setReferenceCounted( false );
		wake_lock.acquire();
		
		// 次回レシーバーを起こすためのPendingIntent
		Intent next_intent = new Intent( this, AlarmReceiver.class );
		pi_next = PendingIntent.getBroadcast( this, PENDING_CODE_ALARM, next_intent, PendingIntent.FLAG_UPDATE_CURRENT );
		
	}
	
	@Override public void onDestroy(){
		log.d("dtor");
		wake_lock.release();
		
		super.onDestroy();
	}
	
	// IntentService は onHandleIntent をワーカースレッドから呼び出す
	// 同期処理を行って良い
	@Override protected void onHandleIntent( @Nullable Intent intent ){
		
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( log );
		
		if( intent != null ){
			String action = intent.getAction();
			log.d("onHandleIntent action=%s",action);

			if( ACTION_DATA_INJECTED.equals( action ) ){
				processInjectedData();
			}else if( AlarmReceiver.ACTION_FROM_RECEIVER.equals( action ) ){
				WakefulBroadcastReceiver.completeWakefulIntent( intent );
				//
				Intent received_intent = intent.getParcelableExtra( AlarmReceiver.EXTRA_RECEIVED_INTENT );
				if( received_intent != null ){
					
					action = received_intent.getAction();
					log.d("received_intent.action=%s",action);
					
					if( Intent.ACTION_BOOT_COMPLETED.equals( action ) ){
						NotificationTracking.resetPostAll();
					}else if( ACTION_NOTIFICATION_DELETE.equals( action ) ){
						log.d( "Notification deleted!" );
						long db_id = received_intent.getLongExtra( EXTRA_DB_ID ,0L);
						NotificationTracking.updateRead( db_id );
						return;
					}else if( ACTION_NOTIFICATION_CLICK.equals( action ) ){
						log.d( "Notification clicked!" );
						long db_id = received_intent.getLongExtra( EXTRA_DB_ID ,0L);
						NotificationTracking.updateRead( db_id );
						notification_manager.cancel( Long.toString(db_id),NOTIFICATION_ID );
						//
						intent = new Intent( this, ActOAuthCallback.class );
						intent.setData( Uri.parse( "subwaytooter://notification_click?db_id="+ db_id ) );
						intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
						startActivity( intent );
						return;
						
					}
				}
			}
		}

		TootApiClient client = new TootApiClient( this, new TootApiClient.Callback() {
			@Override public boolean isApiCancelled(){
				return false;
			}
			
			@Override public void publishApiProgress( String s ){
				
			}
		} );
		
		boolean bAlarmRequired = false;
		if( account_list != null ){
			for( SavedAccount account : account_list ){
				try{
					if( account.notification_mention
						|| account.notification_boost
						|| account.notification_favourite
						|| account.notification_follow
						){
						bAlarmRequired = true;
						
						ArrayList< Data > data_list = new ArrayList<>();
						
						checkAccount( client, data_list, account );
						
						showNotification( account.db_id, data_list );

					}
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
			}
		}
		

		
		alarm_manager.cancel( pi_next );
		if( bAlarmRequired ){
			long now = SystemClock.elapsedRealtime();
			alarm_manager.setWindow(
				AlarmManager.ELAPSED_REALTIME_WAKEUP
				, now + INTERVAL_MIN
				, 60000L * 10
				, pi_next
			);
			log.d("alarm set!");
		}else{
			log.d("alarm is no longer required.");
		}
	}
	
	

	private static class Data {
		SavedAccount access_info;
		TootNotification notification;
	}
	
	private static final String PATH_NOTIFICATIONS = "/api/v1/notifications";
	
	private void checkAccount( TootApiClient client, ArrayList< Data > data_list, SavedAccount account ){
		NotificationTracking nr = NotificationTracking.load( account.db_id );
		
		// まずキャッシュされたデータを処理する
		HashSet< Long > duplicate_check = new HashSet<>();
		ArrayList< JSONObject > dst_array = new ArrayList<>();
		if( nr.last_data != null ){
			try{
				JSONArray array = new JSONArray( nr.last_data );
				for( int i = array.length() - 1 ; i >= 0 ; -- i ){
					JSONObject src = array.optJSONObject( i );
					update_sub( src, nr, account, dst_array, data_list, duplicate_check );
				}
			}catch( JSONException ex ){
				ex.printStackTrace();
			}
		}
		
		// 前回の更新から一定時刻が経過したら新しいデータを注ぎ足す
		long now = System.currentTimeMillis();
		if( now - nr.last_load >= INTERVAL_MIN ){
			nr.last_load = now;
			
			client.setAccount( account );
			
			for( int nTry = 0 ; nTry < 4 ; ++ nTry ){
				TootApiResult result = client.request( PATH_NOTIFICATIONS );
				if( result == null ){
					log.d( "cancelled." );
					break;
				}else if( result.array != null ){
					try{
						JSONArray array = result.array;
						for( int i = array.length() - 1 ; i >= 0 ; -- i ){
							JSONObject src = array.optJSONObject( i );
							update_sub( src, nr, account, dst_array, data_list, duplicate_check );
						}
					}catch( JSONException ex ){
						ex.printStackTrace();
					}
					break;
				}else{
					log.d( "error. %s", result.error );
				}
			}
		}
		
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
		
		JSONArray d = new JSONArray();
		for( int i = 0 ; i < 10 ; ++ i ){
			if( i >= dst_array.size() ) break;
			d.put( dst_array.get( i ) );
		}
		nr.last_data = d.toString();
		nr.save();
	}
	
	void update_sub(
		JSONObject src
		, NotificationTracking nr
		, SavedAccount account
		, ArrayList< JSONObject > dst_array
		, ArrayList< Data > data_list
		, HashSet< Long > duplicate_check
	) throws JSONException{
		
		long id = src.optLong( "id" );
		
		if( duplicate_check.contains( id ) ) return;
		duplicate_check.add( id );
		
		String type = Utils.optStringX( src, "type" );
		
		if( id <= nr.nid_read ){
			return;
		}else if( id > nr.nid_show ){
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
		
		//
		Data data = new Data();
		data.access_info = account;
		data.notification = TootNotification.parse( log, account, src );
		if( data.notification != null ){
			data_list.add( data );
			//
			src.put( KEY_TIME, data.notification.time_created_at );
			dst_array.add( src );
		}
	}
	
	public String getNotificationLine( String type, CharSequence display_name ){
		if( TootNotification.TYPE_FAVOURITE.equals( type ) ){
			return "- "+getString( R.string.display_name_favourited_by, display_name );
		}
		if( TootNotification.TYPE_REBLOG.equals( type ) ){
			return "- "+getString( R.string.display_name_boosted_by, display_name );
		}
		if( TootNotification.TYPE_MENTION.equals( type ) ){
			return "- "+getString( R.string.display_name_replied_by, display_name );
		}
		if( TootNotification.TYPE_FOLLOW.equals( type ) ){
			return "- "+getString( R.string.display_name_followed_by, display_name );
		}
		return "- "+"?";
	}
	
	private void showNotification( long account_db_id,ArrayList< Data > data_list ){
		String notification_tag = Long.toString( account_db_id );
		if( data_list.isEmpty() ){
			notification_manager.cancel( notification_tag,NOTIFICATION_ID );
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
		NotificationTracking nt = NotificationTracking.load( account_db_id );
		if( item.notification.time_created_at == nt.post_time
			&& item.notification.id == nt.post_id
			){
			// 先頭にあるデータが同じなら、通知を更新しない
			// このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
			return;
		}
		nt.updatePost(  item.notification.id, item.notification.time_created_at  );
		
		// 通知タップ
		Intent intent_click = new Intent( this, AlarmReceiver.class );
		intent_click.putExtra(EXTRA_DB_ID,account_db_id);
		intent_click.setAction( ACTION_NOTIFICATION_CLICK );
		
		Intent intent_delete = new Intent( this, AlarmReceiver.class );
		intent_click.putExtra(EXTRA_DB_ID,account_db_id);
		intent_delete.setAction( ACTION_NOTIFICATION_DELETE );
		
		PendingIntent pi_click = PendingIntent.getBroadcast( this, (256+(int)account_db_id), intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
		
		// 通知を消去した時のPendingIntent
		PendingIntent pi_delete = PendingIntent.getBroadcast( this, (Integer.MAX_VALUE-(int)account_db_id), intent_delete, PendingIntent.FLAG_UPDATE_CURRENT );
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder( this )
			.setContentIntent( pi_click )
			.setDeleteIntent( pi_delete )
			.setAutoCancel( false )
			.setSmallIcon( R.drawable.ic_notification )
			.setColor( ContextCompat.getColor( this, R.color.colorAccent ) )
			.setDefaults( NotificationCompat.DEFAULT_ALL )
			.setWhen( item.notification.time_created_at );
		
		String a = getNotificationLine( item.notification.type, item.notification.account.display_name );
		String acct = item.access_info.acct +" "+getString( R.string.app_name );
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
				a = getNotificationLine( item.notification.type, item.notification.account.display_name );
				style.addLine( a );
			}
			builder.setStyle( style );
		}
		
		notification_manager.notify( notification_tag,NOTIFICATION_ID, builder.build() );
	}
	
	////////////////////////////////////////////////////////////////////////////
	// Activity との連携
	
	public static void startCheck(Context context){
		Intent intent = new Intent(context,AlarmReceiver.class);
		context.sendBroadcast( intent );
	}
	
	private static class InjectData {
		long account_db_id;
		TootNotification.List list = new TootNotification.List();
	}
	
	static final ConcurrentLinkedQueue< InjectData > inject_queue = new ConcurrentLinkedQueue<>();
	
	public static void injectData( Context context, long account_db_id, TootNotification.List src ){
		InjectData data = new InjectData();
		data.account_db_id = account_db_id;
		data.list.addAll( src );
		inject_queue.add( data );
		
		Intent intent = new Intent( context, AlarmService.class );
		intent.setAction( ACTION_DATA_INJECTED );
		context.startService( intent );
	}
	
	private void processInjectedData(){
		while( inject_queue.size() > 0 ){
			
			InjectData data = inject_queue.poll();
			
			SavedAccount account = SavedAccount.loadAccount( log, data.account_db_id );
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
						dst_array.add( src );
						duplicate_check.add( src.optLong( "id" ) );
					}
				}catch( JSONException ex ){
					ex.printStackTrace();
				}
			}
			for( TootNotification item : data.list ){
				try{
					if( duplicate_check.contains( item.id ) ) continue;
					duplicate_check.add( item.id );
					
					String type = item.type;
					
					if( ( ! account.notification_mention && TootNotification.TYPE_MENTION.equals( type ) )
						|| ( ! account.notification_boost && TootNotification.TYPE_REBLOG.equals( type ) )
						|| ( ! account.notification_favourite && TootNotification.TYPE_FAVOURITE.equals( type ) )
						|| ( ! account.notification_follow && TootNotification.TYPE_FOLLOW.equals( type ) )
						){
						continue;
					}
					
					//
					JSONObject src = item.json;
					src.put( KEY_TIME, item.time_created_at );
					dst_array.add( src );
				}catch( JSONException ex ){
					ex.printStackTrace();
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
				if( i >= dst_array.size() ) break;
				d.put( dst_array.get( i ) );
			}
			nr.last_data = d.toString();
			nr.save();
		}
	}
}
