package jp.juggler.subwaytooter.action;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.ActPost;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.api.TootParser;
import jp.juggler.subwaytooter.api_msp.entity.MSPToot;
import jp.juggler.subwaytooter.api_tootsearch.entity.TSToot;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Action_Toot {
	
	private static final LogCategory log = new LogCategory( "Action_Favourite" );
	
	// アカウントを選んでお気に入り
	public static void favouriteFromAnotherAccount(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount timeline_account
		, @Nullable final TootStatusLike status
	){
		if( status == null ) return;
		String who_host = status.account == null ? null : timeline_account.getAccountHost( status.account );
		
		AccountPicker.pick( activity, false, false
			, activity.getString( R.string.account_picker_favourite )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount action_account ){
					favourite(
						activity
						, action_account
						, status
						, ActionUtils.calcCrossAccountMode( timeline_account, action_account )
						, true
						, activity.favourite_complete_callback
					);
				}
			} );
	}
	
	// お気に入りの非同期処理
	public static void favourite(
		@NonNull final ActMain activity
		, final SavedAccount access_info
		, final TootStatusLike arg_status
		, final int nCrossAccountMode
		, final boolean bSet
		, final RelationChangedCallback callback
	){
		if( App1.app_state.isBusyFav( access_info, arg_status ) ){
			Utils.showToast( activity, false, R.string.wait_previous_operation );
			return;
		}
		//
		App1.app_state.setBusyFav( access_info, arg_status );
		
		//
		new TootTaskRunner( activity, false ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				TootApiResult result;
				
				TootStatusLike target_status;
				if( nCrossAccountMode == ActionUtils.CROSS_ACCOUNT_REMOTE_INSTANCE ){
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( arg_status.url ) );
					path = path + "&resolve=1";
					
					result = client.request( path );
					if( result == null || result.object == null ){
						return result;
					}
					target_status = null;
					TootResults tmp = new TootParser( activity, access_info).results( result.object );
					if( tmp != null ){
						if( tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							target_status = tmp.statuses.get( 0 );
							
							log.d( "status id conversion %s => %s", arg_status.id, target_status.id );
						}
					}
					if( target_status == null ){
						return new TootApiResult( activity.getString( R.string.status_id_conversion_failed ) );
					}else if( target_status.favourited ){
						return new TootApiResult( activity.getString( R.string.already_favourited ) );
					}
				}else{
					target_status = arg_status;
				}
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, ""
					) );
				
				result = client.request(
					( bSet
						? "/api/v1/statuses/" + target_status.id + "/favourite"
						: "/api/v1/statuses/" + target_status.id + "/unfavourite"
					)
					, request_builder );
				if( result != null && result.object != null ){
					new_status = new TootParser( activity, access_info).status( result.object );
				}
				
				return result;
				
			}
			
			TootStatus new_status;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				App1.app_state.resetBusyFav( access_info, arg_status );
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
					// カウント数は遅延があるみたいなので、恣意的に表示を変更する
					if( bSet && new_status.favourited && new_status.favourites_count <= arg_status.favourites_count ){
						// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = arg_status.favourites_count + 1;
					}else if( ! bSet && ! new_status.favourited && new_status.favourites_count >= arg_status.favourites_count ){
						// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = arg_status.favourites_count - 1;
						// 0未満にはならない
						if( new_status.favourites_count < 0 ){
							new_status.favourites_count = 0;
						}
					}
					
					for( Column column : App1.app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								status.favourites_count = new_status.favourites_count;
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.favourited = new_status.favourited;
								}
								return true;
							}
						} );
					}
					if( callback != null ) callback.onRelationChanged();
					
				}else{
					Utils.showToast( activity, true, result.error );
				}
				// 結果に関わらず、更新中状態から復帰させる
				activity.showColumnMatchAccount( access_info );
				
			}
		} );
		
		// ファボ表示を更新中にする
		activity.showColumnMatchAccount( access_info );
	}
	
	public static void boostFromAnotherAccount(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount timeline_account
		, @Nullable final TootStatusLike status
	){
		if( status == null ) return;
		String who_host = status.account == null ? null : timeline_account.getAccountHost( status.account );
		
		AccountPicker.pick( activity, false, false
			, activity.getString( R.string.account_picker_boost )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount action_account ){
					boost(
						activity
						, action_account
						, status
						, ActionUtils.calcCrossAccountMode( timeline_account, action_account )
						, true
						, false
						, activity.boost_complete_callback
					);
				}
			} );
	}
	
	public static void boost(
		@NonNull final ActMain activity
		, final SavedAccount access_info
		, final TootStatusLike arg_status
		, final int nCrossAccountMode
		, final boolean bSet
		, final boolean bConfirmed
		, final RelationChangedCallback callback
	){
		
		// アカウントからステータスにブースト操作を行っているなら、何もしない
		if( App1.app_state.isBusyBoost( access_info, arg_status ) ){
			Utils.showToast( activity, false, R.string.wait_previous_operation );
			return;
		}
		
		// クロスアカウント操作ではないならステータス内容を使ったチェックを行える
		if( nCrossAccountMode == ActionUtils.NOT_CROSS_ACCOUNT ){
			if( arg_status.reblogged ){
				if( App1.app_state.isBusyFav( access_info, arg_status ) || arg_status.favourited ){
					// FAVがついているか、FAV操作中はBoostを外せない
					Utils.showToast( activity, false, R.string.cant_remove_boost_while_favourited );
					return;
				}
			}
		}
		
		// 必要なら確認を出す
		if( bSet && ! bConfirmed ){
			DlgConfirm.open(
				activity
				, activity.getString( R.string.confirm_boost_from, AcctColor.getNickname( access_info.acct ) )
				, new DlgConfirm.Callback() {
					@Override public boolean isConfirmEnabled(){
						return access_info.confirm_boost;
					}
					
					@Override public void setConfirmEnabled( boolean bv ){
						access_info.confirm_boost = bv;
						access_info.saveSetting();
						activity.reloadAccountSetting( access_info );
					}
					
					@Override public void onOK(){
						boost( activity, access_info, arg_status, nCrossAccountMode, true, true, callback );
					}
				}
			);
			return;
		}
		
		App1.app_state.setBusyBoost( access_info, arg_status );
		
		new TootTaskRunner( activity, false ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
				TootParser parser = new TootParser( activity, access_info);
				
				TootApiResult result;
				
				TootStatusLike target_status;
				if( nCrossAccountMode == ActionUtils.CROSS_ACCOUNT_REMOTE_INSTANCE ){
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( arg_status.url ) );
					path = path + "&resolve=1";
					
					result = client.request( path );
					if( result == null || result.object == null ){
						return result;
					}
					target_status = null;
					TootResults tmp = parser.results( result.object );
					if( tmp != null ){
						if( tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							target_status = tmp.statuses.get( 0 );
						}
					}
					if( target_status == null ){
						return new TootApiResult( activity.getString( R.string.status_id_conversion_failed ) );
					}else if( target_status.reblogged ){
						return new TootApiResult( activity.getString( R.string.already_boosted ) );
					}
				}else{
					// 既に自タンスのステータスがある
					target_status = arg_status;
				}
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, ""
					) );
				
				result = client.request(
					"/api/v1/statuses/" + target_status.id + ( bSet ? "/reblog" : "/unreblog" )
					, request_builder );
				
				if( result != null && result.object != null ){
					
					new_status = parser .status( result.object );
					
					// reblogはreblogを表すStatusを返す
					// unreblogはreblogしたStatusを返す
					if( new_status != null && new_status.reblog != null )
						new_status = new_status.reblog;
					
					//					// reblog,unreblog のレスポンスは信用ならんのでステータスを再取得する
					//					result = client.request( "/api/v1/statuses/" + target_status.id );
					//					if( result != null && result.object != null ){
					//					}
				}
				
				return result;
			}
			
			TootStatus new_status;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				App1.app_state.resetBusyBoost( access_info, arg_status );
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
					// カウント数は遅延があるみたいなので、恣意的に表示を変更する
					// ブーストカウント数を加工する
					if( bSet && new_status.reblogged && new_status.reblogs_count <= arg_status.reblogs_count ){
						// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = arg_status.reblogs_count + 1;
					}else if( ! bSet && ! new_status.reblogged && new_status.reblogs_count >= arg_status.reblogs_count ){
						// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = arg_status.reblogs_count - 1;
						// 0未満にはならない
						if( new_status.reblogs_count < 0 ){
							new_status.reblogs_count = 0;
						}
					}
					
					for( Column column : App1.app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								status.reblogs_count = new_status.reblogs_count;
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.reblogged = new_status.reblogged;
								}
								return true;
							}
						} );
					}
					if( callback != null ) callback.onRelationChanged();
				}else{
					Utils.showToast( activity, true, result.error );
				}
				
				// 結果に関わらず、更新中状態から復帰させる
				activity.showColumnMatchAccount( access_info );
				
			}
		} );
		
		// ブースト表示を更新中にする
		activity.showColumnMatchAccount( access_info );
	}
	
	public static void delete(
		@NonNull final ActMain activity
		, final SavedAccount access_info
		, final long status_id
	){
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				Request.Builder request_builder = new Request.Builder().delete();
				
				return client.request( "/api/v1/statuses/" + status_id, request_builder );
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					Utils.showToast( activity, false, R.string.delete_succeeded );
					for( Column column : App1.app_state.column_list ){
						column.removeStatus( access_info, status_id );
					}
				}else{
					Utils.showToast( activity, false, result.error );
				}
				
			}
		} );
	}
	
	/////////////////////////////////////////////////////////////////////////////////////
	// open conversation
	
	// ローカルかリモートか判断する
	public static void conversation(
		@NonNull final ActMain activity
		, int pos
		, @NonNull SavedAccount access_info
		, @NonNull TootStatusLike status
	){
		if( access_info.isNA() || ! access_info.host.equalsIgnoreCase( status.host_access ) ){
			conversationOtherInstance( activity, pos, status );
		}else{
			conversationLocal( activity, pos, access_info, status.id );
		}
	}
	
	// ローカルから見える会話の流れを表示する
	public static void conversationLocal(
		@NonNull final ActMain activity
		, int pos
		, @NonNull SavedAccount access_info
		, long status_id
	){
		activity.addColumn( pos, access_info, Column.TYPE_CONVERSATION, status_id );
	}
	
	// リモートかもしれない会話の流れを表示する
	public static void conversationOtherInstance(
		@NonNull final ActMain activity
		, int pos
		, @Nullable TootStatusLike status
	){
		// アカウント情報がないと出来ないことがある
		if( status == null || status.account == null ) return;
		
		if( status instanceof MSPToot ){
			conversationOtherInstance( activity, pos, status.url
				, status.id
				, null, - 1L
			);
		}else if( status instanceof TSToot ){
			// Tootsearch ではステータスのアクセス元ホストは分からない
			
			// uri から投稿元タンスでのステータスIDを調べる
			long status_id_original = TootStatusLike.parseStatusId( status );
			
			conversationOtherInstance( activity, pos, status.url
				, status_id_original
				, null, - 1L
			);
			
		}else if( status instanceof TootStatus ){
			if( status.host_original.equals( status.host_access ) ){
				// TLアカウントのホストとトゥートのアカウントのホストが同じ場合
				conversationOtherInstance( activity, pos, status.url
					, status.id
					, null, - 1L
				);
			}else{
				// TLアカウントのホストとトゥートのアカウントのホストが異なる場合
				// uri から投稿元タンスでのステータスIDを調べる
				long status_id_original = TootStatusLike.parseStatusId( status );
				
				conversationOtherInstance( activity, pos, status.url
					, status_id_original
					, status.host_access, status.id
				);
			}
		}
	}
	
	public static void conversationOtherInstance(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final String url
		, final long status_id_original
		, final String host_access, final long status_id_access
	){
		ActionsDialog dialog = new ActionsDialog();
		
		final String host_original = Uri.parse( url ).getAuthority();
		
		// 選択肢：ブラウザで表示する
		dialog.addAction( activity.getString( R.string.open_web_on_host, host_original ), new Runnable() {
			@Override public void run(){
				App1.openCustomTab( activity, url );
			}
		} );
		
		// トゥートの投稿元タンスにあるアカウント
		ArrayList< SavedAccount > local_account_list = new ArrayList<>();
		
		// TLを読んだタンスにあるアカウント
		ArrayList< SavedAccount > access_account_list = new ArrayList<>();
		
		// その他のタンスにあるアカウント
		ArrayList< SavedAccount > other_account_list = new ArrayList<>();
		
		for( SavedAccount a : SavedAccount.loadAccountList( activity ) ){
			
			// 疑似アカウントは後でまとめて処理する
			if( a.isPseudo() ) continue;
			
			if( status_id_original >= 0L && host_original.equalsIgnoreCase( a.host ) ){
				// アクセス情報＋ステータスID でアクセスできるなら
				// 同タンスのアカウントならステータスIDの変換なしに表示できる
				local_account_list.add( a );
			}else if( status_id_access >= 0L && host_access.equalsIgnoreCase( a.host ) ){
				// 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
				access_account_list.add( a );
			}else{
				// 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
				other_account_list.add( a );
			}
		}
		
		// 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
		if( local_account_list.isEmpty() ){
			if( status_id_original >= 0L ){
				dialog.addAction( activity.getString( R.string.open_in_pseudo_account, "?@" + host_original ), new Runnable() {
					@Override public void run(){
						SavedAccount sa = ActionUtils.addPseudoAccount( activity, host_original );
						if( sa != null ){
							conversationLocal( activity, pos, sa, status_id_original );
						}
					}
				} );
			}else{
				dialog.addAction( activity.getString( R.string.open_in_pseudo_account, "?@" + host_original ), new Runnable() {
					@Override public void run(){
						SavedAccount sa = ActionUtils.addPseudoAccount( activity, host_original );
						if( sa != null ){
							conversationRemote( activity, pos, sa, url );
						}
					}
				} );
			}
		}
		
		// ローカルアカウント
		SavedAccount.sort( local_account_list );
		for( SavedAccount a : local_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					conversationLocal( activity, pos, _a, status_id_original );
				}
			} );
		}
		
		// アクセスしたアカウント
		SavedAccount.sort( access_account_list );
		for( SavedAccount a : access_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					conversationLocal( activity, pos, _a, status_id_access );
				}
			} );
		}
		
		// その他の実アカウント
		SavedAccount.sort( other_account_list );
		for( SavedAccount a : other_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					conversationRemote( activity, pos, _a, url );
				}
			} );
		}
		
		dialog.show( activity, activity.getString( R.string.open_status_from ) );
	}
	
	private static final Pattern reDetailedStatusTime = Pattern.compile( "<a\\b[^>]*?\\bdetailed-status__datetime\\b[^>]*href=\"https://[^/]+/@[^/]+/(\\d+)\"" );
	
	private static void conversationRemote(
		@NonNull final ActMain activity
		, final int pos
		, final SavedAccount access_info
		, final String remote_status_url
	){
		new TootTaskRunner( activity, true )
			.progressPrefix( activity.getString( R.string.progress_synchronize_toot ) )
			.run( access_info, new TootTask() {
				@Override public TootApiResult background( @NonNull TootApiClient client ){
					TootApiResult result;
					if( access_info.isPseudo() ){
						result = client.getHttp( remote_status_url );
						if( result != null && result.json != null ){
							try{
								Matcher m = reDetailedStatusTime.matcher( result.json );
								if( m.find() ){
									local_status_id = Long.parseLong( m.group( 1 ), 10 );
								}
							}catch( Throwable ex ){
								log.e( ex, "openStatusRemote: can't parse status id from HTML data." );
							}
							if( local_status_id == - 1L ){
								result = new TootApiResult( activity.getString( R.string.status_id_conversion_failed ) );
							}
						}
					}else{
						// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
						String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( remote_status_url ) );
						path = path + "&resolve=1";
						result = client.request( path );
						if( result != null && result.object != null ){
							TootResults tmp = new TootParser( activity, access_info).results( result.object );
							if( tmp != null && tmp.statuses != null && ! tmp.statuses.isEmpty() ){
								TootStatus status = tmp.statuses.get( 0 );
								local_status_id = status.id;
								log.d( "status id conversion %s => %s", remote_status_url, status.id );
							}
							if( local_status_id == - 1L ){
								result = new TootApiResult( activity.getString( R.string.status_id_conversion_failed ) );
							}
						}
					}
					return result;
				}
				
				long local_status_id = - 1L;
				
				@Override public void handleResult( @Nullable TootApiResult result ){
					if( result == null ){
						// cancelled.
					}else if( local_status_id != - 1L ){
						conversationLocal( activity, pos, access_info, local_status_id );
					}else{
						Utils.showToast( activity, true, result.error );
					}
					
				}
			} );
		
	}
	
	////////////////////////////////////////
	// profile pin
	
	public static void pin(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootStatusLike status
		, final boolean bSet
	){
		
		new TootTaskRunner( activity, true )
			.progressPrefix( activity.getString( R.string.profile_pin_progress ) )
			
			.run( access_info, new TootTask() {
				@Override public TootApiResult background( @NonNull TootApiClient client ){
					TootApiResult result;
					
					Request.Builder request_builder = new Request.Builder()
						.post( RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, ""
						) );
					
					result = client.request(
						( bSet
							? "/api/v1/statuses/" + status.id + "/pin"
							: "/api/v1/statuses/" + status.id + "/unpin"
						)
						, request_builder );
					if( result != null && result.object != null ){
						new_status = new TootParser( activity, access_info).status(  result.object );
					}
					
					return result;
				}
				
				TootStatus new_status;
				
				@Override public void handleResult( @Nullable TootApiResult result ){
					//noinspection StatementWithEmptyBody
					if( result == null ){
						// cancelled.
					}else if( new_status != null ){
						
						for( Column column : App1.app_state.column_list ){
							column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
								@Override
								public boolean onIterate( SavedAccount account, TootStatus status ){
									status.pinned = bSet;
									return true;
								}
							} );
						}
					}else{
						Utils.showToast( activity, true, result.error );
					}
					
					// 結果に関わらず、更新中状態から復帰させる
					activity.showColumnMatchAccount( access_info );
					
				}
			} );
		
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	// reply
	
	public static void reply(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull  final TootStatus arg_status
	){
		ActPost.open( activity, ActMain.REQUEST_CODE_POST, access_info.db_id, arg_status );
	}
	
	public static void replyFromAnotherAccount(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount timeline_account
		, @Nullable final TootStatusLike status
	){
		if( status == null ) return;
		String who_host = status.account == null ? null : timeline_account.getAccountHost( status.account );
		AccountPicker.pick( activity, false, false
			, activity.getString( R.string.account_picker_reply )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					if( status instanceof TootStatus ){
						if( ai.host.equalsIgnoreCase( status.host_access ) ){
							// アクセス元ホストが同じならステータスIDを使って返信できる
							reply( activity, ai, (TootStatus) status );
							return;
						}
					}
					// それ以外の場合、ステータスのURLを検索APIに投げることで返信できる
					replyRemote( activity, ai, status.url );
				}
			} );
	}
	
	private static void replyRemote(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String remote_status_url
	){
		new TootTaskRunner( activity, true )
			.progressPrefix( activity.getString( R.string.progress_synchronize_toot ) )
			
			.run( access_info, new TootTask() {
				@Override public TootApiResult background( @NonNull TootApiClient client ){
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( remote_status_url ) );
					path = path + "&resolve=1";
					
					TootApiResult result = client.request( path );
					if( result != null && result.object != null ){
						TootResults tmp = new TootParser( activity, access_info).results(   result.object );
						if( tmp != null && tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							local_status = tmp.statuses.get( 0 );
							log.d( "status id conversion %s => %s", remote_status_url, local_status.id );
						}
						if( local_status == null ){
							return new TootApiResult( activity.getString( R.string.status_id_conversion_failed ) );
						}
					}
					return result;
				}
				
				TootStatus local_status;
				
				@Override public void handleResult( @Nullable TootApiResult result ){
					
					if( result == null ){
						// cancelled.
					}else if( local_status != null ){
						reply( activity, access_info, local_status );
					}else{
						Utils.showToast( activity, true, result.error );
					}
					
				}
			} );
	}
	
	////////////////////////////////////////
	
	public static void muteConversation(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootStatusLike status
	){
		// toggle change
		final boolean bMute = ! status.muted;
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" ) );
				
				TootApiResult result = client.request(
					"/api/v1/statuses/" + status.id + ( bMute ? "/mute" : "/unmute" )
					, request_builder
				);
				
				if( result != null && result.object != null ){
					local_status = new TootParser( activity, access_info).status( result.object );
				}
				
				return result;
			}
			
			TootStatus local_status;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ){
					// cancelled.
				}else if( local_status != null ){
					for( Column column : App1.app_state.column_list ){
						column.findStatus( access_info.host, local_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.muted = bMute;
								}
								return true;
							}
						} );
					}
					Utils.showToast( activity, true, bMute ? R.string.mute_succeeded : R.string.unmute_succeeded );
				}else{
					Utils.showToast( activity, true, result.error );
				}
			}
		} );
	}
	
}
