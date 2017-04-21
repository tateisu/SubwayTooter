package jp.juggler.subwaytooter.api;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.CancelChecker;
import jp.juggler.subwaytooter.util.HTTPClient;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.table.ClientInfo;

public class TootApiClient {
	private static final LogCategory log = new LogCategory( "TootApiClient" );
	
	public interface Callback {
		boolean isCancelled();
		
		void publishProgress( String s );
	}
	
	private final Context context;
	public final Callback callback;
	
	public TootApiClient( @NonNull Context context, @NonNull Callback callback ){
		this.context = context;
		this.callback = callback;
	}
	
	private String instance;
	private String user_mail;
	private String password;
	
	private SavedAccount account;
	
	public void setUserInfo( String instance, String user_mail, String password ){
		this.instance = instance;
		this.user_mail = user_mail;
		this.password = password;
	}
	
	public void setAccount( SavedAccount account ){
		this.instance = account.host;
		this.account = account;
	}
	
	public TootApiResult get( String path ){
		
		final HTTPClient client = new HTTPClient( 60000, 10, "account", new CancelChecker() {
			@Override
			public boolean isCancelled(){
				return callback.isCancelled();
			}
		} );
		
		JSONObject client_info = null;
		JSONObject token_info = ( account == null ? null : account.token_info );
		
		for( ; ; ){
			if( callback.isCancelled() ) return null;
			if( token_info == null ){
				if( client_info == null ){
					// DBにあるならそれを使う
					client_info = ClientInfo.load( instance );
					if( client_info != null ) continue;
					
					callback.publishProgress( context.getString( R.string.register_app_to_server, instance ) );
					
					// OAuth2 クライアント登録
					String client_name = "jp.juggler.subwaytooter." + UUID.randomUUID().toString();
					client.post_content = Utils.encodeUTF8(
						"client_name=" + Uri.encode( client_name )
							+ "&redirect_uris=urn:ietf:wg:oauth:2.0:oob"
							+ "&scopes=read write follow"
					);
					byte[] data = client.getHTTP( log, "https://" + instance + "/api/v1/apps" );
					if( callback.isCancelled() ) return null;
					
					if( data == null ){
						return new TootApiResult( context.getString( R.string.network_error, client.last_error ) );
					}
					try{
						String result = Utils.decodeUTF8( data );
						// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
						client_info = new JSONObject( result );
						String error = Utils.optStringX( client_info, "error" );
						if( ! TextUtils.isEmpty( error ) ){
							return new TootApiResult( context.getString( R.string.api_error, error ) );
						}
						ClientInfo.save( instance, result );
						continue;
					}catch( JSONException ex ){
						ex.printStackTrace();
						return new TootApiResult( Utils.formatError( ex, "API data error" ) );
					}
				}
				
				if( password == null ){
					// 手動でアクセストークンを再取得しなければいけない
					return new TootApiResult( context.getString( R.string.login_required ) );
				}
				
				callback.publishProgress( context.getString( R.string.request_access_token ) );
				
				// アクセストークンの取得
//
				client.post_content = Utils.encodeUTF8(
					"client_id=" + Uri.encode( Utils.optStringX( client_info, "client_id" ) )
						+ "&client_secret=" + Uri.encode( Utils.optStringX( client_info, "client_secret" ) )
						+ "&grant_type=password"
						+ "&username=" + Uri.encode( user_mail )
						+ "&password=" + Uri.encode( password )
				);
				byte[] data = client.getHTTP( log, "https://" + instance + "/oauth/token" );
				if( callback.isCancelled() ) return null;
				
				// TODO: アプリIDが無効な場合はどんなエラーが出る？
				
				if( data == null ){
					return new TootApiResult( context.getString( R.string.network_error, client.last_error ) );
				}
				
				try{
					String result = Utils.decodeUTF8( data );
					// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
					token_info = new JSONObject( result );
					String error = Utils.optStringX( client_info, "error" );
					if( ! TextUtils.isEmpty( error ) ){
						return new TootApiResult( context.getString( R.string.api_error, error ) );
					}
					if( account != null ){
						account.updateTokenInfo( token_info );
					}
					continue;
				}catch( JSONException ex ){
					ex.printStackTrace();
					return new TootApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
		
		// アクセストークンを使ってAPIを呼び出す
		{
			callback.publishProgress( context.getString( R.string.request_api, path ) );
			
			client.post_content = null;
			client.extra_header = new String[]{
				"Authorization", "Bearer " + Utils.optStringX( token_info, "access_token" )
			};
			byte[] data = client.getHTTP( log, "https://" + instance + path );
			if( callback.isCancelled() ) return null;
			
			// TODO: アクセストークンが無効な場合はどうなる？
			// TODO: アプリIDが無効な場合はどうなる？
			
			if( data == null ){
				return new TootApiResult( context.getString( R.string.network_error, client.last_error ) );
			}
			
			try{
				String result = Utils.decodeUTF8( data );
				if( result.startsWith( "[" ) ){
					JSONArray array = new JSONArray( result );
					return new TootApiResult( token_info, result, array );
				}else{
					JSONObject json = new JSONObject( result );
					
					String error = Utils.optStringX( json, "error" );
					if( ! TextUtils.isEmpty( error ) ){
						return new TootApiResult( context.getString( R.string.api_error, error ) );
					}
					return new TootApiResult( token_info, result, json );
				}
			}catch( JSONException ex ){
				ex.printStackTrace();
				return new TootApiResult( Utils.formatError( ex, "API data error" ) );
			}
		}
	}
}
}
	

