package jp.juggler.subwaytooter.action;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootApiTask;
import jp.juggler.subwaytooter.api.entity.TootList;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

@SuppressWarnings("WeakerAccess") @SuppressLint("StaticFieldLeak")
public class Action_List {
	
	public interface CreateCallback {
		void onCreated(@NonNull TootList list);
	}
	
	// リストを作成する
	public static void create(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String title
	    ,@Nullable final CreateCallback callback
	){
		new TootApiTask( activity, access_info, true ) {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				JSONObject content = new JSONObject();
				try{
					content.put( "title", title );
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "can't encoding json parameter." ) );
				}
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON
						, content.toString()
					) );
				
				TootApiResult result = client.request( "/api/v1/lists", request_builder );
				
				publishApiProgress( activity.getString( R.string.parsing_response ) );
				
				if( result != null ){
					if( result.object != null ){
						list = TootList.parse( result.object );
						
					}
				}
				
				return result;
			}
			
			TootList list;
			
			@Override protected void handleResult( TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( list != null ){
					
					for( Column column : activity.app_state.column_list ){
						column.onListListUpdated( access_info );
					}
					
					Utils.showToast( activity, false, R.string.list_created );
					
					if( callback != null ) callback.onCreated(list);
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
	}
	
	// リストを削除する
	public static void delete(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, final long list_id
	){
		new TootApiTask( activity, access_info, true ) {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				return client.request( "/api/v1/lists/" + list_id, new Request.Builder().delete() );
			}
			
			@Override protected void handleResult( TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					
					for( Column column : activity.app_state.column_list ){
						column.onListListUpdated( access_info );
					}
					
					Utils.showToast( activity, false, R.string.delete_succeeded );
					
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
	}
}
