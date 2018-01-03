package jp.juggler.subwaytooter.action;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActionUtils {
	private static final LogCategory log = new LogCategory( "ActionUtils" );
	
	interface FindAccountCallback {
		// return account information
		// if failed, account is null.
		void onFindAccount( @Nullable TootAccount account );
	}
	
	// ユーザ名からアカウントIDを取得する
	static void findAccountByName(
		@NonNull final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull final String host
		, @NonNull final String user
		, @NonNull final FindAccountCallback callback
	){
		new TootTaskRunner( activity,  true ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				String path = "/api/v1/accounts/search" + "?q=" + Uri.encode( user );
				
				TootApiResult result = client.request( path );
				if( result != null && result.array != null ){
					for( int i = 0, ie = result.array.length() ; i < ie ; ++ i ){
						
						TootAccount a = TootAccount.parse( activity, access_info, result.array.optJSONObject( i ) );
						
						if( ! a.username.equals( user ) ) continue;
						
						if( access_info.getFullAcct( a ).equalsIgnoreCase( user + "@" + host ) ){
							who = a;
							break;
						}
					}
				}
				return result;
			}
			
			TootAccount who;

			@Override public void handleResult( @Nullable TootApiResult result ){
				callback.onFindAccount( who );
			}
		} );
		
	}
	
	// 疑似アカウントを作成する
	// 既に存在する場合は再利用する
	@Nullable static SavedAccount addPseudoAccount(
		@NonNull Context context
		, @NonNull String host
	){
		try{
			String username = "?";
			String full_acct = username + "@" + host;
			
			SavedAccount account = SavedAccount.loadAccountByAcct( context, full_acct );
			if( account != null ){
				return account;
			}
			
			JSONObject account_info = new JSONObject();
			account_info.put( "username", username );
			account_info.put( "acct", username );
			
			long row_id = SavedAccount.insert( host, full_acct, account_info, new JSONObject() );
			account = SavedAccount.loadAccount( context, row_id );
			if( account == null ){
				throw new RuntimeException( "loadAccount returns null." );
			}
			account.notification_follow = false;
			account.notification_favourite = false;
			account.notification_boost = false;
			account.notification_mention = false;
			account.saveSetting();
			return account;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "addPseudoAccount failed." );
			Utils.showToast( context, ex, "addPseudoAccount failed." );
		}
		return null;
	}
	
	// 疑似アカ以外のアカウントのリスト
	public static ArrayList< SavedAccount > makeAccountListNonPseudo(
		@NonNull Context context
		, @Nullable String pickup_host
	){
		
		ArrayList< SavedAccount > list_same_host = new ArrayList<>();
		ArrayList< SavedAccount > list_other_host = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( context ) ){
			if( a.isPseudo() ) continue;
			( pickup_host == null || pickup_host.equalsIgnoreCase( a.host ) ? list_same_host : list_other_host ).add( a );
		}
		SavedAccount.sort( list_same_host );
		SavedAccount.sort( list_other_host );
		list_same_host.addAll( list_other_host );
		return list_same_host;
	}
	
	@Nullable static UserRelation saveUserRelation(
		@NonNull SavedAccount access_info
		, @Nullable TootRelationShip src
	){
		if( src == null ) return null;
		long now = System.currentTimeMillis();
		return UserRelation.save1( now, access_info.db_id, src );
	}
	
	// relationshipを取得
	@NonNull static RelationResult loadRelation1(
		@NonNull TootApiClient client
		, @NonNull SavedAccount access_info
		, long who_id
	){
		RelationResult rr = new RelationResult();
		TootApiResult r2 = rr.result = client.request( "/api/v1/accounts/relationships?id=" + who_id );
		if( r2 != null && r2.array != null ){
			TootRelationShip.List list = TootRelationShip.parseList( r2.array );
			if( ! list.isEmpty() ){
				rr.relation = saveUserRelation( access_info, list.get( 0 ) );
			}
		}
		return rr;
	}
	
	// 別アカ操作と別タンスの関係
	public static final int NOT_CROSS_ACCOUNT = 1;
	private static final int CROSS_ACCOUNT_SAME_INSTANCE = 2;
	static final int CROSS_ACCOUNT_REMOTE_INSTANCE = 3;
	
	static int calcCrossAccountMode( @NonNull final SavedAccount timeline_account, @NonNull final SavedAccount action_account ){
		if( ! timeline_account.host.equalsIgnoreCase( action_account.host ) ){
			return CROSS_ACCOUNT_REMOTE_INSTANCE;
		}else if( ! timeline_account.acct.equalsIgnoreCase( action_account.acct ) ){
			return CROSS_ACCOUNT_SAME_INSTANCE;
		}else{
			return NOT_CROSS_ACCOUNT;
		}
	}
	
}
