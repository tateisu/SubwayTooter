package jp.juggler.subwaytooter.action;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.util.ArrayList;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootApiTask;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

@SuppressWarnings("WeakerAccess") @SuppressLint("StaticFieldLeak")
public class Action_Instance {
	
	// インスタンス情報カラムを開く
	public static void information(
		@NonNull final ActMain activity
		, int pos
		, @NonNull String host
	){
		activity.addColumn( pos, SavedAccount.getNA(), Column.TYPE_INSTANCE_INFORMATION, host );
	}
	
	// 指定タンスのローカルタイムラインを開く
	public static void timelineLocal(
		@NonNull final ActMain activity
		, @NonNull String host
	){
		// 指定タンスのアカウントを持ってるか？
		final ArrayList< SavedAccount > account_list = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( activity ) ){
			if( host.equalsIgnoreCase( a.host ) ) account_list.add( a );
		}
		if( account_list.isEmpty() ){
			// 持ってないなら疑似アカウントを追加する
			SavedAccount ai = ActionUtils.addPseudoAccount( activity, host );
			if( ai != null ){
				activity.addColumn( activity.getDefaultInsertPosition(), ai, Column.TYPE_LOCAL );
			}
		}else{
			// 持ってるならアカウントを選んで開く
			SavedAccount.sort( account_list );
			AccountPicker.pick( activity, true, false
				, activity.getString( R.string.account_picker_add_timeline_of, host )
				, account_list
				, new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						activity.addColumn( activity.getDefaultInsertPosition(), ai, Column.TYPE_LOCAL );
					}
				} );
		}
	}
	
	// ドメインブロック
	public static void blockDomain(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String domain
		, final boolean bBlock
	){
		
		if( access_info.host.equalsIgnoreCase( domain ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		new TootApiTask( activity, access_info, true ) {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				RequestBody body = RequestBody.create(
					TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
					, "domain=" + Uri.encode( domain )
				);
				
				Request.Builder request_builder = new Request.Builder();
				request_builder = bBlock ? request_builder.post( body ) : request_builder.delete( body );
				
				return client.request( "/api/v1/domain_blocks", request_builder );
			}
			
			@Override protected void handleResult( TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					
					for( Column column : App1.app_state.column_list ){
						column.onDomainBlockChanged( access_info, domain, bBlock );
					}
					
					Utils.showToast( activity, false, bBlock ? R.string.block_succeeded : R.string.unblock_succeeded );
					
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
	}
	
}
