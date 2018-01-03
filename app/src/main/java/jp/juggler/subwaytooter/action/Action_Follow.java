package jp.juggler.subwaytooter.action;

import android.content.DialogInterface;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

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
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Action_Follow {
	
	public static void follow(
		@NonNull final ActMain activity
		, int pos
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bFollow
		, @Nullable final RelationChangedCallback callback
	){
		follow( activity, pos, access_info, who, bFollow, false, false, callback );
	}
	
	private static void follow(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bFollow
		, final boolean bConfirmMoved
		, final boolean bConfirmed
		, @Nullable final RelationChangedCallback callback
	){
		if( access_info.isMe( who ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmMoved && bFollow && who.moved != null ){
			new AlertDialog.Builder( activity )
				.setMessage( activity.getString( R.string.jump_moved_user
					, access_info.getFullAcct( who )
					, access_info.getFullAcct( who.moved )
				) )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick( DialogInterface dialog, int which ){
						Action_User.profileFromAnotherAccount( activity, pos, access_info, who.moved );
					}
				} )
				.setNeutralButton( R.string.ignore_suggestion, new DialogInterface.OnClickListener() {
					@Override public void onClick( DialogInterface dialog, int which ){
						follow( activity, pos, access_info, who, true, true, false, callback );
					}
				} )
				.setNegativeButton( android.R.string.cancel, null )
				.show();
			return;
		}
		
		if( ! bConfirmed ){
			if( bFollow && who.locked ){
				DlgConfirm.open( activity
					, activity.getString( R.string.confirm_follow_request_who_from, who.decoded_display_name, AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							activity.reloadAccountSetting( access_info );
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							follow( activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback );
						}
					}
				);
				return;
			}else if( bFollow ){
				String msg = activity.getString( R.string.confirm_follow_who_from
					, who.decoded_display_name
					, AcctColor.getNickname( access_info.acct )
				);
				
				DlgConfirm.open( activity
					, msg
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow = bv;
							access_info.saveSetting();
							activity.reloadAccountSetting( access_info );
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							follow( activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback );
						}
					}
				);
				return;
			}else{
				DlgConfirm.open( activity
					, activity.getString( R.string.confirm_unfollow_who_from, who.decoded_display_name, AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_unfollow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_unfollow = bv;
							access_info.saveSetting();
							activity.reloadAccountSetting( access_info );
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							follow( activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback );
						}
					}
				);
				return;
			}
		}
		
		new TootTaskRunner( activity, false ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				TootApiResult result;
				
				if( bFollow & who.acct.contains( "@" ) ){
					
					// リモートフォローする
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "uri=" + Uri.encode( who.acct )
						) );
					
					result = client.request( "/api/v1/follows", request_builder );
					if( result != null ){
						if( result.object != null ){
							TootAccount remote_who = TootAccount.parse( activity, access_info, result.object );
							if( remote_who != null ){
								RelationResult rr = ActionUtils.loadRelation1( client, access_info, remote_who.id );
								result = rr.result;
								relation = rr.relation;
							}
						}
					}
					
				}else{
					
					// ローカルでフォロー/アンフォローする
					
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "" // 空データ
						) );
					result = client.request( "/api/v1/accounts/" + who.id
							+ ( bFollow ? "/follow" : "/unfollow" )
						, request_builder );
					if( result != null && result.object != null ){
						relation = ActionUtils.saveUserRelation( access_info, TootRelationShip.parse( result.object ) );
					}
				}
				
				return result;
			}
			
			UserRelation relation;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( relation != null ){
					
					activity.showColumnMatchAccount( access_info );
					
					if( bFollow && relation.getRequested( who ) ){
						// 鍵付きアカウントにフォローリクエストを申請した状態
						Utils.showToast( activity, false, R.string.follow_requested );
					}else if( ! bFollow && relation.getRequested( who ) ){
						Utils.showToast( activity, false, R.string.follow_request_cant_remove_by_sender );
					}else{
						// ローカル操作成功、もしくはリモートフォロー成功
						if( callback != null ) callback.onRelationChanged();
					}
					
				}else if( bFollow && who.locked && result.response != null && result.response.code() == 422 ){
					Utils.showToast( activity, false, R.string.cant_follow_locked_user );
				}else{
					Utils.showToast( activity, false, result.error );
				}
				
			}
		} );
	}
	
	// acct で指定したユーザをリモートフォローする
	private static void followRemote(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String acct
		, final boolean locked
		, @Nullable final RelationChangedCallback callback
	){
		followRemote( activity, access_info, acct, locked, false, callback );
	}
	
	// acct で指定したユーザをリモートフォローする
	private static void followRemote(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String acct
		, final boolean locked
		, final boolean bConfirmed
		, @Nullable final RelationChangedCallback callback
	){
		if( access_info.isMe( acct ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmed ){
			if( locked ){
				DlgConfirm.open( activity
					, activity.getString( R.string.confirm_follow_request_who_from, AcctColor.getNickname( acct ), AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							activity.reloadAccountSetting( access_info );
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							followRemote( activity, access_info, acct, locked, true, callback );
						}
					}
				);
				return;
			}else{
				DlgConfirm.open( activity
					, activity.getString( R.string.confirm_follow_who_from, AcctColor.getNickname( acct ), AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow = bv;
							access_info.saveSetting();
							activity.reloadAccountSetting();
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							followRemote( activity, access_info, acct, locked, true, callback );
						}
					}
				);
				return;
			}
		}
		
		new TootTaskRunner( activity, false ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "uri=" + Uri.encode( acct )
					) );
				
				TootApiResult result = client.request( "/api/v1/follows", request_builder );
				
				if( result != null ){
					if( result.object != null ){
						remote_who = TootAccount.parse( activity, access_info, result.object );
						if( remote_who != null ){
							RelationResult rr = ActionUtils.loadRelation1( client, access_info, remote_who.id );
							result = rr.result;
							relation = rr.relation;
						}
					}
				}
				
				return result;
			}
			
			TootAccount remote_who;
			UserRelation relation;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( relation != null ){
					
					activity.showColumnMatchAccount( access_info );
					
					if( callback != null ) callback.onRelationChanged();
					
				}else if( locked && result.response != null && result.response.code() == 422 ){
					Utils.showToast( activity, false, R.string.cant_follow_locked_user );
				}else{
					Utils.showToast( activity, false, result.error );
				}
				
			}
		} );
	}
	
	public static void followFromAnotherAccount(
		@NonNull final ActMain activity
		, int pos
		, @NonNull SavedAccount access_info
		, @Nullable final TootAccount account
	){
		followFromAnotherAccount( activity, pos, access_info, account, false );
	}
	
	private static void followFromAnotherAccount(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final SavedAccount access_info
		, @Nullable final TootAccount account
		, final boolean bConfirmMoved
	){
		if( account == null ) return;
		
		if( ! bConfirmMoved && account.moved != null ){
			new AlertDialog.Builder( activity )
				.setMessage( activity.getString( R.string.jump_moved_user
					, access_info.getFullAcct( account )
					, access_info.getFullAcct( account.moved )
				) )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick( DialogInterface dialog, int which ){
						Action_User.profileFromAnotherAccount( activity, pos, access_info, account.moved );
					}
				} )
				.setNeutralButton( R.string.ignore_suggestion, new DialogInterface.OnClickListener() {
					@Override public void onClick( DialogInterface dialog, int which ){
						followFromAnotherAccount( activity, pos, access_info, account, true );
					}
				} )
				.setNegativeButton( android.R.string.cancel, null )
				.show();
			return;
		}
		
		final String who_host = access_info.getAccountHost( account );
		final String who_acct = access_info.getFullAcct( account );
		AccountPicker.pick( activity, false, false
			, activity.getString( R.string.account_picker_follow )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					followRemote( activity, ai, who_acct, account.locked, activity.follow_complete_callback );
				}
			} );
	}
	
	public static void authorizeFollowRequest(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bAllow
	){
		if( access_info.isMe( who ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				
				return client.request(
					"/api/v1/follow_requests/" + who.id + ( bAllow ? "/authorize" : "/reject" )
					, request_builder );
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					for( Column column : App1.app_state.column_list ){
						column.removeFollowRequest( access_info, who.id );
					}
					
					Utils.showToast( activity, false, ( bAllow ? R.string.follow_request_authorized : R.string.follow_request_rejected ), who.decoded_display_name );
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		} );
	}
}
