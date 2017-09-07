package jp.juggler.subwaytooter;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.NotificationHelper;

public class PollingForegrounder extends IntentService {
	
	static final LogCategory log = new LogCategory( "PollingForegrounder" );
	
	static final int NOTIFICATION_ID_FOREGROUNDER = 2;
	
	public PollingForegrounder(){
		super( "PollingForegrounder" );
	}
	
	@Nullable @Override public IBinder onBind( Intent intent ){
		return null;
	}
	
	@Override public void onCreate(){
		log.d( "onCreate" );
		super.onCreate();
		
		// メインスレッド上でPollingWorkerを初期化しておく
		PollingWorker.getInstance( getApplicationContext() );
		
		startForeground( NOTIFICATION_ID_FOREGROUNDER, createNotification( getApplicationContext(), "" ) );
	}
	
	@Override public void onDestroy(){
		log.d( "onDestroy" );
		
		stopForeground( true );
		super.onDestroy();
	}
	
	private Notification createNotification( Context context, String text ){
		// 通知タップ時のPendingIntent
		Intent intent_click = new Intent( context, ActMain.class );
		intent_click.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
		PendingIntent pi_click = PendingIntent.getActivity( context, 2, intent_click, PendingIntent.FLAG_UPDATE_CURRENT );
		
		NotificationCompat.Builder builder;
		if( Build.VERSION.SDK_INT >= 26 ){
			// Android 8 から、通知のスタイルはユーザが管理することになった
			// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
			NotificationChannel channel = NotificationHelper.createNotificationChannel(
				context
				, "PollingForegrounder" // id
				, "real-time message notifier" // The user-visible name of the channel.
				, null // The user-visible description of the channel.
				, NotificationManager.IMPORTANCE_LOW
			);
			builder = new NotificationCompat.Builder( context, channel.getId() );
		}else{
			builder = new NotificationCompat.Builder( context, "not_used" );
		}
		
		builder
			.setContentIntent( pi_click )
			.setAutoCancel( false )
			.setOngoing( true )
			.setSmallIcon( R.drawable.ic_notification ) // ここは常に白テーマのアイコンを使う
			.setColor( ContextCompat.getColor( context, R.color.Light_colorAccent ) ) // ここは常に白テーマの色を使う
			.setWhen( System.currentTimeMillis() )
			.setContentTitle( context.getString( R.string.loading_notification_title ) )
			.setContentText( text )
		;
		
		// Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
		// 束ねられた通知をタップしても pi_click が実行されないので困るため、
		// アカウント別にグループキーを設定する
		builder.setGroup( context.getPackageName() + ":PollingForegrounder" );
		
		return builder.build();
	}
	
	String last_status = null;
	
	@Override protected void onHandleIntent( @Nullable Intent intent ){
		if( intent == null ) return;
		String tag = intent.getStringExtra( PollingWorker.EXTRA_TAG );
		final Context context = getApplicationContext();
		PollingWorker.handleFCMMessage( this, tag, new PollingWorker.JobStatusCallback() {
			@Override public void onStatus( final String sv ){
				if( TextUtils.isEmpty( sv ) || sv.equals( last_status ) ) return;
				last_status = sv;
				log.d( "onStatus %s",sv );
				startForeground( NOTIFICATION_ID_FOREGROUNDER, createNotification( context, sv ) );
			}
		} );
	}
}
