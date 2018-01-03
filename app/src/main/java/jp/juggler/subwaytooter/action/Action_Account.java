package jp.juggler.subwaytooter.action;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

import jp.juggler.subwaytooter.ActAccountSetting;
import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.ActPost;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgTextInput;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class Action_Account {
	private static final LogCategory log = new LogCategory( "Action_Account" );
	
	// アカウントの追加
	public static void add( @NonNull final ActMain activity ){
		
		LoginForm.showLoginForm(
			activity
			, null
			, new LoginForm.LoginFormCallback() {
				
				@Override public void startLogin(
					@NonNull final Dialog dialog
					, @NonNull final String instance
					, final boolean bPseudoAccount
					, final boolean bInputAccessToken
				){
					new TootTaskRunner( activity, true ).run( instance, new TootTask() {
						@Override public TootApiResult background( @NonNull TootApiClient client ){
							if( bPseudoAccount ){
								return client.checkInstance();
							}else{
								String client_name = Pref.pref( activity ).getString( Pref.KEY_CLIENT_NAME, "" );
								return client.authorize1( client_name );
							}
						}
						
						@Override public void handleResult( @Nullable TootApiResult result ){
							if( result == null ) return; // cancelled.
							
							if( result.error != null ){
								String sv = result.error;
								
								// エラーはブラウザ用URLかもしれない
								if( sv.startsWith( "https" ) ){
									
									if( bInputAccessToken ){
										// アクセストークンの手動入力
										DlgTextInput.show( activity, activity.getString( R.string.access_token ), null, new DlgTextInput.Callback() {
											@Override
											public void onOK( Dialog dialog_token, String access_token ){
												activity.checkAccessToken( dialog, dialog_token, instance, access_token, null );
											}
											
											@Override public void onEmptyError(){
												Utils.showToast( activity, true, R.string.token_not_specified );
											}
										} );
									}else{
										// OAuth認証が必要
										Intent data = new Intent();
										data.setData( Uri.parse( sv ) );
										activity.startAccessTokenUpdate( data );
										try{
											dialog.dismiss();
										}catch( Throwable ignored ){
											// IllegalArgumentException がたまに出る
										}
									}
									return;
								}
								
								log.e( result.error );
								
								if( sv.contains( "SSLHandshakeException" )
									&& ( Build.VERSION.RELEASE.startsWith( "7.0" )
									|| ( Build.VERSION.RELEASE.startsWith( "7.1" ) && ! Build.VERSION.RELEASE.startsWith( "7.1." ) ) )
									){
									new AlertDialog.Builder( activity )
										.setMessage( sv + "\n\n" + activity.getString( R.string.ssl_bug_7_0 ) )
										.setNeutralButton( R.string.close, null )
										.show();
									return;
								}
								
								// 他のエラー
								Utils.showToast( activity, true, sv );
							}else{
								
								SavedAccount a = ActionUtils.addPseudoAccount( activity, instance );
								if( a != null ){
									// 疑似アカウントが追加された
									Utils.showToast( activity, false, R.string.server_confirmed );
									int pos = App1.app_state.column_list.size();
									activity.addColumn( pos, a, Column.TYPE_LOCAL );
									
									try{
										dialog.dismiss();
									}catch( Throwable ignored ){
										// IllegalArgumentException がたまに出る
									}
								}
							}
							
						}
					} );
				}
			}
		);
		
	}
	
	// アカウント設定
	public static void setting( @NonNull final ActMain activity ){
		AccountPicker.pick(
			activity
			, true
			, true
			, activity.getString( R.string.account_picker_open_setting )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					ActAccountSetting.open( activity, ai, ActMain.REQUEST_CODE_ACCOUNT_SETTING );
				}
			} );
	}
	
	// アカウントを選んでタイムラインカラムを追加
	public static void timeline(
		@NonNull final ActMain activity
		, final int pos
		, boolean bAllowPseudo
		, final int type
		, final Object... args
	){
		AccountPicker.pick( activity, bAllowPseudo, true
			, activity.getString( R.string.account_picker_add_timeline_of, Column.getColumnTypeName( activity, type ) )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					switch( type ){
					default:
						activity.addColumn( pos, ai, type, args );
						break;
					
					case Column.TYPE_PROFILE:
						activity.addColumn( pos, ai, type, ai.id );
						break;
					}
				}
			} );
	}
	
	// 投稿画面を開く。簡易入力があれば初期値はそれになる
	public static void openPost( @NonNull final ActMain activity ){
		openPost( activity, activity.getQuickTootText() );
	}
	
	// 投稿画面を開く。初期テキストを指定する
	public static void openPost(
		@NonNull final ActMain activity
		, @Nullable final String initial_text
	){
		activity.post_helper.closeAcctPopup();
		
		long db_id = activity.getCurrentPostTargetId();
		if( db_id != - 1L ){
			ActPost.open( activity, ActMain.REQUEST_CODE_POST, db_id, initial_text );
		}else{
			AccountPicker.pick(
				activity
				, false
				, true
				, activity.getString( R.string.account_picker_toot )
				, new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						ActPost.open( activity, ActMain.REQUEST_CODE_POST, ai.db_id, initial_text );
					}
				}
			);
		}
	}
}
