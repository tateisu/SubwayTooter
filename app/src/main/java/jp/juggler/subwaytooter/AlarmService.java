package jp.juggler.subwaytooter;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;

import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.NotificationTracking;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.WordTrieTree;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
	private static final String ACTION_DATA_DELETED = "data_deleted";
	
	public static final String ACTION_APP_DATA_IMPORT_BEFORE = "app_data_import_before";
	public static final AtomicBoolean mBusyAppDataImportBefore = new AtomicBoolean( false );
	public static final AtomicBoolean mBusyAppDataImportAfter = new AtomicBoolean( false );
	public static final String ACTION_APP_DATA_IMPORT_AFTER = "app_data_import_after";
	public static final String ACTION_DEVICE_TOKEN = "device_token";
	private static final String ACTION_RESET_LAST_LOAD = "reset_last_load";
	private static final String EXTRA_TAG = "tag";
	
	public static final String APP_SERVER = "https://mastodon-msg.juggler.jp";
	
	public AlarmService(){
		// name: Used to name the worker thread, important only for debugging.
		super( "AlarmService" );
	}
	
	AlarmManager alarm_manager;
	PowerManager power_manager;
	NotificationManager notification_manager;
	PowerManager.WakeLock wake_lock;
	PendingIntent pi_next;
	SharedPreferences pref;
	
	@Override public void onCreate(){
		super.onCreate();
		log.d( "ctor" );
		
		alarm_manager = (AlarmManager) getApplicationContext().getSystemService( ALARM_SERVICE );
		power_manager = (PowerManager) getApplicationContext().getSystemService( POWER_SERVICE );
		notification_manager = (NotificationManager) getApplicationContext().getSystemService( NOTIFICATION_SERVICE );
		
		wake_lock = power_manager.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, AlarmService.class.getName() );
		wake_lock.setReferenceCounted( false );
		wake_lock.acquire();
		
		pref = Pref.pref( this );
		
		// 次回レシーバーを起こすためのPendingIntent
		Intent next_intent = new Intent( this, AlarmReceiver.class );
		pi_next = PendingIntent.getBroadcast( this, PENDING_CODE_ALARM, next_intent, PendingIntent.FLAG_UPDATE_CURRENT );
		
		
	}
	
	@Override public void onDestroy(){
		log.d( "dtor" );
		wake_lock.release();
		
		super.onDestroy();
	}
	
	String install_id;
	boolean bStreamListenerTest;
	String mCustomStreamListenerSecret;
	String mCustomStreamListenerSettingString;
	JsonObject mCustomStreamListenerSetting;

	void loadCustomStreamListenerSetting(  ){
		mCustomStreamListenerSetting = null;
		mCustomStreamListenerSecret = null;
		mCustomStreamListenerSettingString = pref.getString(Pref.KEY_STREAM_LISTENER_CONFIG_DATA ,null);
		if(! TextUtils.isEmpty( mCustomStreamListenerSettingString ) ){
			try{
				mCustomStreamListenerSetting = JsonValue.readHjson( mCustomStreamListenerSettingString ).asObject();
				mCustomStreamListenerSecret = pref.getString(Pref.KEY_STREAM_LISTENER_SECRET ,null);
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	}
	
	// IntentService は onHandleIntent をワーカースレッドから呼び出す
	// 同期処理を行って良い
	@Override protected void onHandleIntent( @Nullable Intent intent ){
		bStreamListenerTest =false;
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		App1.prepareDB( this.getApplicationContext() );
		
		install_id = getInstallId();
		
		if( intent != null ){
			String action = intent.getAction();
			log.d( "onHandleIntent action=%s", action );
			
			if( ACTION_APP_DATA_IMPORT_BEFORE.equals( action ) ){
				alarm_manager.cancel( pi_next );
				for( SavedAccount a : SavedAccount.loadAccountList( log ) ){
					try{
						String notification_tag = Long.toString( a.db_id );
						notification_manager.cancel( notification_tag, NOTIFICATION_ID );
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
				mBusyAppDataImportBefore.set( false );
				return;
			}else if( ACTION_APP_DATA_IMPORT_AFTER.equals( action ) ){
				mBusyAppDataImportAfter.set( false );
				NotificationTracking.resetPostAll();
			}
		}
		
		if( mBusyAppDataImportAfter.get() ) return;
		
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( log );
		
		if( intent != null ){
			String action = intent.getAction();
			
			if( ACTION_DEVICE_TOKEN.equals( action ) ){
				// デバイストークンが更新された
				// アプリサーバへの登録をやり直す
			}else if( ACTION_RESET_LAST_LOAD.equals( action ) ){
				boolean bDone = false;
				String tag = intent.getStringExtra(EXTRA_TAG);
				if( tag != null ){
					for(SavedAccount sa : SavedAccount.loadByTag(log,tag )){
						bDone = true;
						NotificationTracking.resetLastLoad( sa.db_id);
					}
				}
				if(!bDone){
					// タグにマッチする情報がなかった場合、全部読み直す
					NotificationTracking.resetLastLoad( );
				}
			}else if( ACTION_DATA_DELETED.equals( action ) ){
				deleteCacheData( intent.getLongExtra( EXTRA_DB_ID, - 1L ) );
				
			}else if( ACTION_DATA_INJECTED.equals( action ) ){
				processInjectedData();
				
			}else if( AlarmReceiver.ACTION_FROM_RECEIVER.equals( action ) ){
				WakefulBroadcastReceiver.completeWakefulIntent( intent );
				//
				Intent received_intent = intent.getParcelableExtra( AlarmReceiver.EXTRA_RECEIVED_INTENT );
				if( received_intent != null ){
					
					action = received_intent.getAction();
					log.d( "received_intent.action=%s", action );
					
					if( Intent.ACTION_BOOT_COMPLETED.equals( action ) ){
						NotificationTracking.resetPostAll();
						
					}else if( Intent.ACTION_MY_PACKAGE_REPLACED.equals( action ) ){
						NotificationTracking.resetPostAll();
						
					}else if( ACTION_NOTIFICATION_DELETE.equals( action ) ){
						log.d( "Notification deleted!" );
						long db_id = received_intent.getLongExtra( EXTRA_DB_ID, 0L );
						NotificationTracking.updateRead( db_id );
						return;
					}else if( ACTION_NOTIFICATION_CLICK.equals( action ) ){
						log.d( "Notification clicked!" );
						long db_id = received_intent.getLongExtra( EXTRA_DB_ID, 0L );
						// 画面を開く
						intent = new Intent( this, ActCallback.class );
						intent.setData( Uri.parse( "subwaytooter://notification_click?db_id=" + db_id ) );
						intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
						startActivity( intent );
						// 通知をキャンセル
						notification_manager.cancel( Long.toString( db_id ), NOTIFICATION_ID );
						// DB更新処理
						NotificationTracking.updateRead( db_id );
						return;
						
					}
				}
			}
		}
		
		loadCustomStreamListenerSetting();
		
		final AtomicBoolean bAlarmRequired = new AtomicBoolean( false );
		final HashSet< String > muted_app = MutedApp.getNameSet();
		final WordTrieTree muted_word = MutedWord.getNameSet();
		
		LinkedList< Thread > thread_list = new LinkedList<>();
		for( SavedAccount _a : account_list ){
			final SavedAccount account = _a;
			Thread t = new Thread( new Runnable() {
				@Override public void run(){
					
					try{
						if( account.isPseudo() ) return;
						
						if( ! account.notification_mention
							&& ! account.notification_boost
							&& ! account.notification_favourite
							&& ! account.notification_follow
							){
							unregisterDeviceToken( account );
							return;
						}
						
						if( registerDeviceToken( account ) ){
							return;
						}
						
						bAlarmRequired.set( true );
						
						TootApiClient client = new TootApiClient( AlarmService.this, new TootApiClient.Callback() {
							@Override public boolean isApiCancelled(){
								return false;
							}
							
							@Override public void publishApiProgress( String s ){
							}
						} );
						
						ArrayList< Data > data_list = new ArrayList<>();
						checkAccount( client, data_list, account, muted_app, muted_word );
						showNotification( account.db_id, data_list );
						
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
			} );
			thread_list.add( t );
			t.start();
		}
		
		for( Thread t : thread_list ){
			try{
				t.join();
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		
		alarm_manager.cancel( pi_next );
		if( bAlarmRequired.get() ){
			long now = SystemClock.elapsedRealtime();
			alarm_manager.setWindow(
				AlarmManager.ELAPSED_REALTIME_WAKEUP
				, now + INTERVAL_MIN
				, 60000L * 10
				, pi_next
			);
			log.d( "alarm set!" );
		}else{
			log.d( "alarm is no longer required." );
		}
	}
	
	private void testLog( String s ){
		// TODO アプリ設定画面に進捗表示を追加する
	}
	
	String getInstallId(){
		String sv = pref.getString(Pref.KEY_INSTALL_ID,null);
		if( ! TextUtils.isEmpty( sv ) ) return sv;
		
		try{
			String device_token = pref.getString( Pref.KEY_DEVICE_TOKEN, null );
			if( TextUtils.isEmpty( device_token ) ) return null;
			
			Request request = new Request.Builder()
				.url( APP_SERVER + "/counter" )
				.build();
			
			Call call = App1.ok_http_client.newCall( request );
			
			Response response = call.execute();
			
			if( ! response.isSuccessful() ){
				log.e("getInstallId: get /counter failed. %s",response);
				return null;
			}
			
			//noinspection ConstantConditions
			sv = Utils.digestSHA256( device_token + UUID.randomUUID() + response.body().string() );
			pref.edit().putString(Pref.KEY_INSTALL_ID, sv).apply();
			
			return sv;
			
		}catch( Throwable ex ){
			ex.printStackTrace();
			return null;
		}
	}
	
	static final String REGISTER_KEY_UNREGISTERED = "unregistered";
	
	private void unregisterDeviceToken( @NonNull SavedAccount account ){
		try{
			if( REGISTER_KEY_UNREGISTERED.equals( account.register_key ) ){
				log.d("unregisterDeviceToken: already unregistered.");
				return;
			}

			// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
			if( TextUtils.isEmpty( install_id ) ){
				log.d("unregisterDeviceToken: missing install_id");
				return;
			}
			
			String tag = account.notification_tag;
			if( TextUtils.isEmpty( tag ) ){
				log.d("unregisterDeviceToken: missing notification_tag");
				return;
			}
			
			String post_data = "instance_url=" + Uri.encode( "https://" + account.host )
				+ "&app_id=" + Uri.encode( getPackageName() )
				+ "&tag=" + tag;
			
			Request request = new Request.Builder()
				.url( APP_SERVER + "/unregister" )
				.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data ) )
				.build();
			
			Call call = App1.ok_http_client.newCall( request );
			
			Response response = call.execute();
			
			log.e( "unregisterDeviceToken: %s", response );
			
			if( response.isSuccessful() ){
				account.register_key = REGISTER_KEY_UNREGISTERED;
				account.register_time = 0L;
				account.saveRegisterKey();
			}
		
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	// 定期的な通知更新が不要なら真を返す
	private boolean registerDeviceToken( @NonNull SavedAccount account ){
		try{
			// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
			if( TextUtils.isEmpty( install_id ) ){
				log.d("registerDeviceToken: missing install id");
				return false;
			}
			
			String device_token = pref.getString( Pref.KEY_DEVICE_TOKEN, null );
			if( TextUtils.isEmpty( device_token ) ){
				log.d("registerDeviceToken: missing device_token");
				return false;
			}
			
			String access_token = Utils.optStringX( account.token_info, "access_token" );
			if( TextUtils.isEmpty( access_token ) ){
				log.d("registerDeviceToken: missing access_token");
				return false;
			}

			String tag = account.notification_tag;
			if( TextUtils.isEmpty( tag ) ){
				tag = account.notification_tag = Utils.digestSHA256( install_id + account.db_id + account.acct );
				account.saveNotificationTag();
			}
			
			String reg_key = Utils.digestSHA256(
				tag
				+ access_token
				+ device_token
				+ (mCustomStreamListenerSecret==null? "" :mCustomStreamListenerSecret)
				+ (mCustomStreamListenerSettingString==null? "" :mCustomStreamListenerSettingString)
			);
			long now = System.currentTimeMillis();
			if( reg_key.equals( account.register_key ) && now - account.register_time < 3600000 * 3 ){
				// タグやトークンが同一なら、前回登録に成功してから一定時間は再登録しない
				log.d("registerDeviceToken: already registered.");
				return false;
			}

			// サーバ情報APIを使う
			StringBuilder post_data = new StringBuilder(  );

			post_data.append("instance_url=").append(Uri.encode( "https://" + account.host ));

			post_data.append("&app_id=").append(Uri.encode( getPackageName() ));

			post_data.append("&tag=").append(tag);

			post_data.append("&access_token=").append(Utils.optStringX( account.token_info, "access_token" ));

			post_data.append("&device_token=").append(device_token);

			if( ! TextUtils.isEmpty( mCustomStreamListenerSettingString )
			&& 	! TextUtils.isEmpty( mCustomStreamListenerSecret )
				){
				post_data.append("&user_config=").append(Uri.encode(mCustomStreamListenerSettingString));
				post_data.append("&app_secret=").append(Uri.encode(mCustomStreamListenerSecret));
			}
			
			Request request = new Request.Builder()
				.url( APP_SERVER + "/register" )
				.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data.toString() ) )
				.build();
			
			Call call = App1.ok_http_client.newCall( request  );
			
			Response response = call.execute();
			
			String body=null;
			try{
				body =response.body().string();
				
			}catch(Throwable ignored){
			}
			log.e( "registerDeviceToken: %s (%s)",response,(body==null?"":body) );

			int code = response.code();

			if( response.isSuccessful() || (code >= 400 && code < 500) ){
				// 登録できた時も4xxエラーだった時もDBに記録する
				account.register_key = reg_key;
				account.register_time = now;
				account.saveRegisterKey();
			}
			
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		return false;
	}
	
	private static class Data {
		SavedAccount access_info;
		TootNotification notification;
	}
	
	private static final String PATH_NOTIFICATIONS = "/api/v1/notifications";
	
	private void checkAccount(
		@NonNull TootApiClient client
		, @NonNull ArrayList< Data > data_list
		, @NonNull SavedAccount account
		, @NonNull HashSet< String > muted_app
		, @NonNull WordTrieTree muted_word
	){
		log.d( "checkAccount account_db_id=%s", account.db_id );
		
		NotificationTracking nr = NotificationTracking.load( account.db_id );
		
		// まずキャッシュされたデータを処理する
		HashSet< Long > duplicate_check = new HashSet<>();
		ArrayList< JSONObject > dst_array = new ArrayList<>();
		if( nr.last_data != null ){
			try{
				JSONArray array = new JSONArray( nr.last_data );
				for( int i = array.length() - 1 ; i >= 0 ; -- i ){
					JSONObject src = array.optJSONObject( i );
					update_sub( src, nr, account, dst_array, data_list, duplicate_check, muted_app, muted_word );
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
							update_sub( src, nr, account, dst_array, data_list, duplicate_check, muted_app, muted_word );
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
		@NonNull JSONObject src
		, @NonNull NotificationTracking nr
		, @NonNull SavedAccount account
		, @NonNull ArrayList< JSONObject > dst_array
		, @NonNull ArrayList< Data > data_list
		, @NonNull HashSet< Long > duplicate_check
		, @NonNull HashSet< String > muted_app
		, @NonNull WordTrieTree muted_word
	)
		throws JSONException{
		
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
		
		TootNotification notification = TootNotification.parse( log, account, src );
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
	
	public String getNotificationLine( @NonNull String type, @NonNull CharSequence display_name ){
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
	
	private void showNotification( long account_db_id, @NonNull ArrayList< Data > data_list ){
		String notification_tag = Long.toString( account_db_id );
		if( data_list.isEmpty() ){
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
		NotificationTracking nt = NotificationTracking.load( account_db_id );
		if( item.notification.time_created_at == nt.post_time
			&& item.notification.id == nt.post_id
			){
			// 先頭にあるデータが同じなら、通知を更新しない
			// このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
			return;
		}
		nt.updatePost( item.notification.id, item.notification.time_created_at );
		
		// 通知タップ
		Intent intent_click = new Intent( this, AlarmReceiver.class );
		intent_click.putExtra( EXTRA_DB_ID, account_db_id );
		intent_click.setAction( ACTION_NOTIFICATION_CLICK );
		
		Intent intent_delete = new Intent( this, AlarmReceiver.class );
		intent_click.putExtra( EXTRA_DB_ID, account_db_id );
		intent_delete.setAction( ACTION_NOTIFICATION_DELETE );
		
		PendingIntent pi_click = PendingIntent.getBroadcast( this, ( 256 + (int) account_db_id ), intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
		
		// 通知を消去した時のPendingIntent
		PendingIntent pi_delete = PendingIntent.getBroadcast( this, ( Integer.MAX_VALUE - (int) account_db_id ), intent_delete, PendingIntent.FLAG_UPDATE_CURRENT );
		
		int iv = 0;
		if( pref.getBoolean( Pref.KEY_NOTIFICATION_SOUND, true ) ){
			iv |= NotificationCompat.DEFAULT_SOUND;
		}
		if( pref.getBoolean( Pref.KEY_NOTIFICATION_VIBRATION, true ) ){
			iv |= NotificationCompat.DEFAULT_VIBRATE;
			
		}
		if( pref.getBoolean( Pref.KEY_NOTIFICATION_LED, true ) ){
			iv |= NotificationCompat.DEFAULT_LIGHTS;
		}
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder( this )
			.setContentIntent( pi_click )
			.setDeleteIntent( pi_delete )
			.setAutoCancel( false )
			.setSmallIcon( R.drawable.ic_notification ) // ここは常に白テーマのアイコンを使う
			.setColor( ContextCompat.getColor( this, R.color.Light_colorAccent ) ) // ここは常に白テーマの色を使う
			.setDefaults( iv )
			.setWhen( item.notification.time_created_at );
		
		String a = getNotificationLine( item.notification.type, item.notification.account.display_name );
		String acct = item.access_info.acct + " " + getString( R.string.app_name );
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
		
		notification_manager.notify( notification_tag, NOTIFICATION_ID, builder.build() );
	}
	
	////////////////////////////////////////////////////////////////////////////
	// Activity との連携
	
	public static void startCheck( @NonNull Context context ){
		Intent intent = new Intent( context, AlarmService.class );
		context.startService( intent );
	}
	
	public static void onFirebaseMessage( @NonNull Context context, @Nullable String tag ){
		Intent intent = new Intent( context, AlarmService.class );
		intent.setAction( ACTION_RESET_LAST_LOAD );
		if(tag !=null) intent.putExtra( EXTRA_TAG,tag );
		context.startService( intent );
	}
	
	
	private static class InjectData {
		long account_db_id;
		TootNotification.List list = new TootNotification.List();
	}
	
	static final ConcurrentLinkedQueue< InjectData > inject_queue = new ConcurrentLinkedQueue<>();
	
	public static void injectData( @NonNull Context context, long account_db_id, @NonNull TootNotification.List src ){
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
						long id = src.optLong( "id" );
						dst_array.add( src );
						duplicate_check.add( id );
						log.d( "add old. id=%s", id );
					}
				}catch( JSONException ex ){
					ex.printStackTrace();
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
	
	public static void dataRemoved( @NonNull Context context, long db_id ){
		Intent intent = new Intent( context, AlarmService.class );
		intent.putExtra( EXTRA_DB_ID, db_id );
		intent.setAction( ACTION_DATA_DELETED );
		context.startService( intent );
	}
	
	private void deleteCacheData( long db_id ){
		
		SavedAccount account = SavedAccount.loadAccount( log, db_id );
		if( account == null ) return;
		
		NotificationTracking nr = NotificationTracking.load( db_id );
		
		nr.last_data = new JSONArray().toString();
		
		nr.save();
	}
	
}
