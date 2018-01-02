package jp.juggler.subwaytooter.action;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;

public class Action_HashTag {
	
	// ハッシュタグへの操作を選択する
	public static void dialog(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final String url
		, @NonNull final String host
		, @NonNull final String tag_without_sharp
		, @Nullable final ArrayList< String > tag_list
	){
		final String tag_with_sharp = "#" + tag_without_sharp;
		
		ActionsDialog d = new ActionsDialog()
			.addAction( activity.getString( R.string.open_hashtag_column ), new Runnable() {
				@Override public void run(){
					
					timelineOtherInstance( activity, pos, url, host, tag_without_sharp );
				}
			} )
			.addAction( activity.getString( R.string.open_in_browser ), new Runnable() {
				@Override public void run(){
					App1.openCustomTab( activity, url );
				}
			} )
			.addAction( activity.getString( R.string.quote_hashtag_of, tag_with_sharp ), new Runnable() {
				@Override public void run(){
					Action_Account.openPost( activity, tag_with_sharp + " " );
				}
			} );
		
		if( tag_list != null && tag_list.size() > 1 ){
			StringBuilder sb = new StringBuilder();
			for( String s : tag_list ){
				if( sb.length() > 0 ) sb.append( ' ' );
				sb.append( s );
			}
			final String tag_all = sb.toString();
			d.addAction( activity.getString( R.string.quote_all_hashtag_of, tag_all ), new Runnable() {
				@Override public void run(){
					Action_Account.openPost( activity, tag_all + " " );
				}
			} );
		}
		
		d.show( activity, tag_with_sharp );
	}
	
	public static void timeline(
		@NonNull final ActMain activity
		, int pos
		, @NonNull SavedAccount access_info
		, @NonNull String tag_without_sharp
	){
		activity.addColumn( pos, access_info, Column.TYPE_HASHTAG, tag_without_sharp );
	}
	
	// 他インスタンスのハッシュタグの表示
	private static void timelineOtherInstance(
		@NonNull final ActMain activity
		, int pos
		, @NonNull String url
		, @NonNull String host
		, @NonNull String tag_without_sharp
	){
		timelineOtherInstance_sub( activity, pos, url, host, tag_without_sharp );
	}
	
	// 他インスタンスのハッシュタグの表示
	private static void timelineOtherInstance_sub(
		@NonNull final ActMain activity
		, final int pos
		, @NonNull final String url
		, @NonNull final String host
		, @NonNull final String tag_without_sharp
	){
		
		ActionsDialog dialog = new ActionsDialog();
		
		// 各アカウント
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( activity );
		
		// ソートする
		SavedAccount.sort( account_list );
		
		ArrayList< SavedAccount > list_original = new ArrayList<>();
		ArrayList< SavedAccount > list_original_pseudo = new ArrayList<>();
		ArrayList< SavedAccount > list_other = new ArrayList<>();
		for( SavedAccount a : account_list ){
			if( ! host.equalsIgnoreCase( a.host ) ){
				list_other.add( a );
			}else if( a.isPseudo() ){
				list_original_pseudo.add( a );
			}else{
				list_original.add( a );
			}
		}
		
		// ブラウザで表示する
		dialog.addAction( activity.getString( R.string.open_web_on_host, host ), new Runnable() {
			@Override public void run(){
				App1.openCustomTab( activity, url );
			}
		} );
		
		if( list_original.isEmpty() && list_original_pseudo.isEmpty() ){
			// 疑似アカウントを作成して開く
			dialog.addAction( activity.getString( R.string.open_in_pseudo_account, "?@" + host ), new Runnable() {
				@Override public void run(){
					SavedAccount sa = ActionUtils.addPseudoAccount( activity, host );
					if( sa != null ){
						timeline( activity, pos, sa, tag_without_sharp );
					}
				}
			} );
		}
		
		//
		for( SavedAccount a : list_original ){
			final SavedAccount _a = a;
			
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					timeline( activity, pos, _a, tag_without_sharp );
				}
			} );
		}
		//
		for( SavedAccount a : list_original_pseudo ){
			final SavedAccount _a = a;
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					timeline( activity, pos, _a, tag_without_sharp );
				}
			} );
		}
		//
		for( SavedAccount a : list_other ){
			final SavedAccount _a = a;
			dialog.addAction( AcctColor.getStringWithNickname( activity, R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					timeline( activity, pos, _a, tag_without_sharp );
				}
			} );
		}
		
		dialog.show( activity, "#" + tag_without_sharp );
	}
	
}
