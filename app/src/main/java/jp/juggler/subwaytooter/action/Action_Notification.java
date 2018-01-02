package jp.juggler.subwaytooter.action;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootApiTask;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

@SuppressWarnings("WeakerAccess") @SuppressLint("StaticFieldLeak")
public class Action_Notification {
	
	public static void deleteAll(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount target_account
		, boolean bConfirmed
	){
		if( ! bConfirmed ){
			new AlertDialog.Builder( activity )
				.setMessage( R.string.confirm_delete_notification )
				.setNegativeButton( R.string.cancel, null )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick( DialogInterface dialog, int which ){
						deleteAll( activity, target_account, true );
					}
				} )
				.show();
			return;
		}
		new TootApiTask( activity, target_account, true ) {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				return client.request( "/api/v1/notifications/clear", request_builder );
			}
			
			@Override protected void handleResult( TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					// ok. api have return empty object.
					for( Column column : App1.app_state.column_list ){
						if( column.column_type == Column.TYPE_NOTIFICATIONS
							&& column.access_info.acct.equals( target_account.acct )
							){
							column.removeNotifications();
						}
					}
					Utils.showToast( activity, false, R.string.delete_succeeded );
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
			
		}.executeOnExecutor( App1.task_executor );
	}
	
	public static void deleteOne(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootNotification notification
	){
		new TootApiTask( activity, access_info, true ) {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "id=" + Long.toString( notification.id )
						)
					);
				
				return client.request(
					"/api/v1/notifications/dismiss"
					, request_builder );
			}
			
			@Override protected void handleResult( TootApiResult result ){
				
				if( result == null ){
					// cancelled.
				}else if( result.object != null ){
					// 成功したら空オブジェクトが返される
					for( Column column : App1.app_state.column_list ){
						column.removeNotificationOne( access_info, notification );
					}
					Utils.showToast( activity, true, R.string.delete_succeeded );
				}else{
					Utils.showToast( activity, true, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
	}
	
}
