package jp.juggler.subwaytooter.action;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Pattern;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Action_ListMember {
	
	public interface Callback {
		void onListMemberUpdated( boolean willRegistered, boolean bSuccess );
	}
	
	private static final Pattern reFollowError = Pattern.compile( "follow", Pattern.CASE_INSENSITIVE );
	
	public static void add(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, final long list_id
		, @NonNull final TootAccount local_who
		, final boolean bFollow
		, @Nullable final Callback callback
	){
		new TootTaskRunner( activity,  true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
				if( access_info.isMe( local_who ) ){
					return new TootApiResult( activity.getString( R.string.it_is_you ) );
				}
				
				TootApiResult result;
				
				if( bFollow ){
					TootRelationShip relation;
					if( access_info.isLocalUser( local_who ) ){
						Request.Builder request_builder = new Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
								, "" // 空データ
							) );
						
						result = client.request( "/api/v1/accounts/" + local_who.id + "/follow", request_builder );
					}else{
						// リモートフォローする
						Request.Builder request_builder = new Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
								, "uri=" + Uri.encode( local_who.acct )
							) );
						
						result = client.request( "/api/v1/follows", request_builder );
						if( result == null || result.object == null ) return result;
						
						TootAccount a = TootAccount.parse( activity, access_info, result.object );
						if( a == null ){
							return new TootApiResult( "parse error." );
						}
						
						// リモートフォローの後にリレーションシップを取得しなおす
						result = client.request( "/api/v1/accounts/relationships?id[]=" + a.id );
					}
					
					if( result == null || result.array == null ){
						return result;
					}
					
					TootRelationShip.List relation_list = TootRelationShip.parseList( result.array );
					relation = relation_list.isEmpty() ? null : relation_list.get( 0 );
					
					if( relation == null ){
						return new TootApiResult( "parse error." );
					}
					ActionUtils.saveUserRelation( access_info, relation );
					
					if( ! relation.following ){
						if( relation.requested ){
							return new TootApiResult( activity.getString( R.string.cant_add_list_follow_requesting ) );
						}else{
							// リモートフォローの場合、正常ケースでもここを通る場合がある
							// 何もしてはいけない…
						}
					}
				}
				
				// リストメンバー追加
				
				JSONObject content = new JSONObject();
				try{
					JSONArray account_ids = new JSONArray();
					account_ids.put( Long.toString( local_who.id ) );
					content.put( "account_ids", account_ids );
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "can't encoding json parameter." ) );
				}
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON
						, content.toString()
					) );
				
				return client.request( "/api/v1/lists/" + list_id + "/accounts", request_builder );
				
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				boolean bSuccess = false;
				
				try{
					//noinspection StatementWithEmptyBody
					if( result == null ) return; // cancelled.
					
					if( result.object != null ){
						for( Column column : App1.app_state.column_list ){
							// リストメンバー追加イベントをカラムに伝達
							column.onListMemberUpdated( access_info, list_id, local_who, true );
						}
						// フォロー状態の更新を表示に反映させる
						if( bFollow ) activity.showColumnMatchAccount( access_info );
						
						Utils.showToast( activity, false, R.string.list_member_added );
						
						bSuccess = true;
						
					}else{
						
						if( result.response != null
							&& result.response.code() == 422
							&& result.error != null && reFollowError.matcher( result.error ).find()
							){
							
							if( ! bFollow ){
								DlgConfirm.openSimple(
									activity
									, activity.getString( R.string.list_retry_with_follow, access_info.getFullAcct( local_who ) )
									, new Runnable() {
										@Override public void run(){
											Action_ListMember.add( activity, access_info, list_id, local_who, true, callback );
										}
									}
								);
							}else{
								new android.app.AlertDialog.Builder( activity )
									.setCancelable( true )
									.setMessage( R.string.cant_add_list_follow_requesting )
									.setNeutralButton( R.string.close, null )
									.show();
							}
							return;
						}
						
						Utils.showToast( activity, true, result.error );
						
					}
				}finally{
					if( callback != null ) callback.onListMemberUpdated( true, bSuccess );
				}
				
			}
		} );
	}
	
	public static void delete(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, final long list_id
		, @NonNull final TootAccount local_who
		, @Nullable final Callback callback
	){
		new TootTaskRunner( activity,  true ) .run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				return client.request(
					"/api/v1/lists/" + list_id + "/accounts?account_ids[]=" + local_who.id
					, new Request.Builder().delete()
				);
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				boolean bSuccess = false;
				
				try{
					
					if( result == null ) return; // cancelled.
					
					if( result.object != null ){
						
						for( Column column : App1.app_state.column_list ){
							column.onListMemberUpdated( access_info, list_id, local_who, false );
						}
						
						Utils.showToast( activity, false, R.string.delete_succeeded );
						
						bSuccess = true;
						
					}else{
						Utils.showToast( activity, false, result.error );
					}
				}finally{
					if( callback != null ) callback.onListMemberUpdated( false, bSuccess );
				}
				
			}
		} );
	}
}
