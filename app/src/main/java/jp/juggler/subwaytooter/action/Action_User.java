package jp.juggler.subwaytooter.action;

import android.app.Dialog;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import java.util.Locale;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.ActPost;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.TootParser;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.ReportForm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Action_User {
	
	// ユーザをミュート/ミュート解除する
	public static void mute(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bMute
		, final boolean bMuteNotification
	){
		
		if( access_info.isMe( who ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
				Request.Builder request_builder = new Request.Builder().post(
					! bMute ? RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" )
						: bMuteNotification ? RequestBody.create( TootApiClient.MEDIA_TYPE_JSON, "{\"notifications\": true}" )
						: RequestBody.create( TootApiClient.MEDIA_TYPE_JSON, "{\"notifications\": false}" )
				);
				
				TootApiResult result = client.request( "/api/v1/accounts/" + who.id + ( bMute ? "/mute" : "/unmute" )
					, request_builder );
				if( result != null && result.object != null ){
					relation = ActionUtils.saveUserRelation( access_info, TootRelationShip.parse( result.object ) );
				}
				return result;
			}
			
			UserRelation relation;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( relation != null ){
					// 未確認だが、自分をミュートしようとするとリクエストは成功するがレスポンス中のmutingはfalseになるはず
					if( bMute && ! relation.muting ){
						Utils.showToast( activity, false, R.string.not_muted );
						return;
					}
					
					if( relation.muting ){
						for( Column column : App1.app_state.column_list ){
							column.removeAccountInTimeline( access_info, who.id );
						}
					}else{
						for( Column column : App1.app_state.column_list ){
							column.removeFromMuteList( access_info, who.id );
						}
					}
					
					Utils.showToast( activity, false, relation.muting ? R.string.mute_succeeded : R.string.unmute_succeeded );
					
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		} );
		
	}
	
	// ユーザをブロック/ブロック解除する
	public static void block(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bBlock
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
				
				TootApiResult result = client.request(
					"/api/v1/accounts/" + who.id + ( bBlock ? "/block" : "/unblock" )
					, request_builder
				);
				
				if( result != null ){
					if( result.object != null ){
						relation = ActionUtils.saveUserRelation( access_info, TootRelationShip.parse( result.object ) );
						
					}
				}
				
				return result;
			}
			
			UserRelation relation;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( relation != null ){
					
					// 自分をブロックしようとすると、blocking==falseで帰ってくる
					if( bBlock && ! relation.blocking ){
						Utils.showToast( activity, false, R.string.not_blocked );
						return;
					}
					
					for( Column column : App1.app_state.column_list ){
						if( relation.blocking ){
							column.removeAccountInTimeline( access_info, who.id );
						}else{
							column.removeFromBlockList( access_info, who.id );
						}
					}
					
					Utils.showToast( activity, false, relation.blocking ? R.string.block_succeeded : R.string.unblock_succeeded );
					
				}else{
					Utils.showToast( activity, false, result.error );
				}
			}
		} );
		
	}
	
	// アカウントを選んでユーザプロフを開く
	public static void profileFromAnotherAccount(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final SavedAccount access_info
		, @Nullable final TootAccount who
	){
		if( who == null ) return;
		String who_host = access_info.getAccountHost( who );
		
		AccountPicker.pick(
			activity
			, false
			, false
			, activity.getString( R.string.account_picker_open_user_who, AcctColor.getNickname( who.acct ) )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					if( ai.host.equalsIgnoreCase( access_info.host ) ){
						activity.addColumn( pos, ai, Column.TYPE_PROFILE, who.id );
					}else{
						profile( activity, pos, ai, who.url );
					}
				}
			} );
	}
	
	// 今のアカウントでユーザプロフを開く
	public static void profile(
		@NonNull final ActMain activity
		, int pos
		, @NonNull SavedAccount access_info
		, @Nullable TootAccount who
	){
		if( who == null ){
			Utils.showToast( activity, false, "user is null" );
		}else if( access_info.isPseudo() ){
			profileFromAnotherAccount( activity, pos, access_info, who );
		}else{
			activity.addColumn( pos, access_info, Column.TYPE_PROFILE, who.id );
		}
	}
	
	// URLからユーザを検索してプロフを開く
	public static void profile(
		@NonNull final ActMain activity
		, final int pos
		, final SavedAccount access_info
		, final String who_url
	){
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				// 検索APIに他タンスのユーザのURLを投げると、自タンスのURLを得られる
				String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( who_url ) );
				path = path + "&resolve=1";
				
				TootApiResult result = client.request( path );
				
				if( result != null && result.object != null ){
					
					TootResults tmp = new TootParser( activity, access_info ).results( result.object );
					if( tmp != null ){
						if( tmp.accounts != null && ! tmp.accounts.isEmpty() ){
							who_local = tmp.accounts.get( 0 );
						}
					}
					
					if( who_local == null ){
						return new TootApiResult( activity.getString( R.string.user_id_conversion_failed ) );
					}
				}
				
				return result;
			}
			
			TootAccount who_local;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ){
					// cancelled.
				}else if( who_local != null ){
					activity.addColumn( pos, access_info, Column.TYPE_PROFILE, who_local.id );
				}else{
					Utils.showToast( activity, true, result.error );
					
					// 仕方ないのでchrome tab で開く
					App1.openCustomTab( activity, who_url );
				}
			}
		} );
		
	}
	
	// Intent-FilterからUser URL で指定されたユーザのプロフを開く
	// openChromeTabからUser URL で指定されたユーザのプロフを開く
	public static void profile(
		@NonNull final ActMain activity
		, final int pos
		, @Nullable final SavedAccount access_info
		, @NonNull final String url
		, @NonNull final String host
		, @NonNull final String user
	){
		// リンクタップした文脈のアカウントが疑似でないなら
		if( access_info != null && ! access_info.isPseudo() ){
			if( access_info.host.equalsIgnoreCase( host ) ){
				// 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
				ActionUtils.findAccountByName( activity, access_info, host, user, new ActionUtils.FindAccountCallback() {
					@Override public void onFindAccount( TootAccount who ){
						if( who != null ){
							profile( activity, pos, access_info, who );
							return;
						}
						// ダメならchromeで開く
						App1.openCustomTab( activity, url );
					}
				} );
			}else{
				// 文脈のアカウント異なるインスタンスなら、別アカウントで開く
				profile( activity, pos, access_info, url );
			}
			return;
		}
		
		// 文脈がない、もしくは疑似アカウントだった
		
		// 疑似ではないアカウントの一覧
		
		if( ! SavedAccount.hasRealAccount() ){
			// 疑似アカウントではユーザ情報APIを呼べないし検索APIも使えない
			// chrome tab で開くしかない
			App1.openCustomTab( activity, url );
		}else{
			// アカウントを選択して開く
			AccountPicker.pick( activity, false, false
				, activity.getString( R.string.account_picker_open_user_who, AcctColor.getNickname( user + "@" + host ) )
				, ActionUtils.makeAccountListNonPseudo( activity, host )
				, new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						profile( activity, pos, ai, url );
					}
				}
			);
		}
		
	}
	
	// 通報フォームを開く
	public static void reportForm(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount account
		, @NonNull final TootAccount who
		, @NonNull final TootStatus status
	){
		ReportForm.showReportForm( activity, who, status, new ReportForm.ReportFormCallback() {
			
			@Override public void startReport( final Dialog dialog, String comment ){
				report( activity, account, who, status, comment, new ReportCompleteCallback() {
					@Override public void onReportComplete( @NonNull TootApiResult result ){
						// 成功したらダイアログを閉じる
						try{
							dialog.dismiss();
						}catch( Throwable ignored ){
							// IllegalArgumentException がたまに出る
						}
					}
				} );
			}
		} );
	}
	
	interface ReportCompleteCallback {
		void onReportComplete( @NonNull TootApiResult result );
	}
	
	// 通報する
	private static void report(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, @NonNull final TootStatus status
		, @NonNull final String comment
		, @Nullable final ReportCompleteCallback callback
	){
		if( access_info.isMe( who ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				String sb = "account_id=" + Long.toString( who.id )
					+ "&comment=" + Uri.encode( comment )
					+ "&status_ids[]=" + Long.toString( status.id );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, sb
					) );
				
				return client.request( "/api/v1/reports", request_builder );
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					if( callback != null ) callback.onReportComplete( result );
					
					Utils.showToast( activity, false, R.string.report_completed );
				}else{
					Utils.showToast( activity, true, result.error );
				}
			}
		} );
	}
	
	// show/hide boosts from (following) user
	public static void showBoosts(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bShow
	){
		if( access_info.isMe( who ) ){
			Utils.showToast( activity, false, R.string.it_is_you );
			return;
		}
		
		new TootTaskRunner( activity, true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
				JSONObject content = new JSONObject();
				try{
					content.put( "reblogs", bShow );
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "json encoding error" ) );
				}
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON
						, content.toString()
					) );
				
				TootApiResult result = client.request( "/api/v1/accounts/" + who.id + "/follow", request_builder );
				if( result != null && result.object != null ){
					relation = TootRelationShip.parse( result.object );
				}
				return result;
			}
			
			TootRelationShip relation;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				
				if( result == null ) return; // cancelled.
				
				if( relation != null ){
					ActionUtils.saveUserRelation( access_info, relation );
					Utils.showToast( activity, true, R.string.operation_succeeded );
				}else{
					Utils.showToast( activity, true, result.error );
				}
			}
		} );
	}
	
	// メンションを含むトゥートを作る
	private static void mention(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount account
		, @NonNull final String initial_text
	){
		ActPost.open(
			activity
			, ActMain.REQUEST_CODE_POST
			, account.db_id
			, initial_text
		);
	}
	
	// メンションを含むトゥートを作る
	public static void mention(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount account
		, @NonNull final TootAccount who
	){
		mention( activity, account, "@" + account.getFullAcct( who ) + " " );
	}
	
	// メンションを含むトゥートを作る
	public static void mentionFromAnotherAccount(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @Nullable final TootAccount who
	){
		if( who == null ) return;
		String who_host = access_info.getAccountHost( who );
		
		final String initial_text = "@" + access_info.getFullAcct( who ) + " ";
		AccountPicker.pick(
			activity
			, false
			, false
			, activity.getString( R.string.account_picker_toot )
			, ActionUtils.makeAccountListNonPseudo( activity, who_host )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					mention( activity, ai, initial_text );
				}
			} );
	}
}
