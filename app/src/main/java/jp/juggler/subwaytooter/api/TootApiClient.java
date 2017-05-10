package jp.juggler.subwaytooter.api;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.table.ClientInfo;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TootApiClient {
	private static final LogCategory log = new LogCategory( "TootApiClient" );
	
	private static final OkHttpClient ok_http_client = App1.ok_http_client;
	
	public interface Callback {
		boolean isApiCancelled();
		
		void publishApiProgress( String s );
	}
	
	private final Context context;
	public final Callback callback;
	
	public TootApiClient( @NonNull Context context, @NonNull Callback callback ){
		this.context = context;
		this.callback = callback;
	}
	
	// インスタンスのホスト名
	public String instance;
	
	// アカウント追加時に使用する
	public void setInstance( String instance ){
		this.instance = instance;
	}
	
	// アカウントがある場合に使用する
	public SavedAccount account;
	
	public void setAccount( SavedAccount account ){
		this.instance = account.host;
		this.account = account;
	}
	
	public boolean isCancelled(){
		return callback.isApiCancelled();
	}
	
	public static final MediaType MEDIA_TYPE_FORM_URL_ENCODED = MediaType.parse( "application/x-www-form-urlencoded" );
	
	public TootApiResult request( String path ){
		return request( path, new Request.Builder() );
	}
	
	public TootApiResult request( String path, Request.Builder request_builder ){
		log.d( "request: %s", path );
		TootApiResult result = request_sub( path, request_builder );
		if( result != null && result.error != null ){
			log.d( "error: %s", result.error );
		}
		return result;
	}
	
	private static final String KEY_AUTH_VERSION = "SubwayTooterAuthVersion";
	private static final int AUTH_VERSION = 1;
	private static final String REDIRECT_URL = "subwaytooter://oauth";
	
	private TootApiResult request_sub( String path, Request.Builder request_builder ){
		
		if( callback.isApiCancelled() ) return null;
		
		// アクセストークンを使ってAPIを呼び出す
		callback.publishApiProgress( context.getString( R.string.request_api, path ) );

		if( account == null ){
			return new TootApiResult( "account is null" );
		}
		
		JSONObject token_info = account.token_info;
		
		
		request_builder.url( "https://" + instance + path );
		
		String access_token = Utils.optStringX( token_info, "access_token" );
		if( !TextUtils.isEmpty( access_token ) ){
			request_builder.header( "Authorization", "Bearer " + access_token );
		}
		
		Call call = ok_http_client.newCall( request_builder.build() );
		Response response;
		try{
			response = call.execute();
		}catch( Throwable ex ){
			ex.printStackTrace(  );
			return new TootApiResult(
				Utils.formatError( ex, context.getResources(), R.string.network_error )
			);
		}
		
		if( callback.isApiCancelled() ) return null;
		
		if( ! response.isSuccessful() ){
			return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
		}
		
		try{
			String json = response.body().string();
			
			if( TextUtils.isEmpty( json ) || json.startsWith( "<" ) ){
				return new TootApiResult( context.getString( R.string.response_not_json ) + "\n" + json );
			}else if( json.startsWith( "[" ) ){
				JSONArray array = new JSONArray( json );
				return new TootApiResult( log,response, token_info, json, array );
			}else{
				JSONObject object = new JSONObject( json );
				
				String error = Utils.optStringX( object, "error" );
				if( ! TextUtils.isEmpty( error ) ){
					return new TootApiResult( context.getString( R.string.api_error, error ) );
				}
				return new TootApiResult( response, token_info, json, object );
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			return new TootApiResult( Utils.formatError( ex, "API data error" ) );
		}
	}
	
	
	public TootApiResult webSocket( String path, Request.Builder request_builder , WebSocketListener ws_listener ){
		
		if( callback.isApiCancelled() ) return null;
		
		// アクセストークンを使ってAPIを呼び出す
		callback.publishApiProgress( context.getString( R.string.request_api, path ) );
		
		
		if( account == null ){
			return new TootApiResult( "account is null" );
		}
		
		String url = "wss://" + instance + path;
		
		JSONObject token_info = account.token_info;
		String access_token = Utils.optStringX( token_info, "access_token" );
		if( !TextUtils.isEmpty( access_token ) ){
			char delm = (-1!= url.indexOf( '?' ) ? '&':'?');
			url = url + delm + "access_token="+ access_token;
		}
		
		request_builder.url( url );
		
		try{
			WebSocket ws = ok_http_client.newWebSocket( request_builder.build() ,ws_listener );
			if( callback.isApiCancelled() ){
				ws.cancel();
				return null;
			}
			return new TootApiResult( ws );
		}catch( Throwable ex ){
			ex.printStackTrace(  );
			return new TootApiResult(
				Utils.formatError( ex, context.getResources(), R.string.network_error )
			);
		}
	}
	
	
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	public TootApiResult checkInstance(){
		
		// サーバ情報APIを使う
		String path = "/api/v1/instance";
		callback.publishApiProgress( context.getString( R.string.request_api, path ) );
		
		Request request = new Request.Builder()
			.url( "https://" + instance + path )
			.build();
		
		Call call = ok_http_client.newCall( request );
		
		Response response;
		try{
			response = call.execute();
		}catch( Throwable ex ){
			ex.printStackTrace(  );
			return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
		}
		
		if( callback.isApiCancelled() ) return null;
		
		if( ! response.isSuccessful() ){
			return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
		}
		
		try{
			String json = response.body().string();
			
			if( TextUtils.isEmpty( json ) || json.startsWith( "<" ) ){
				return new TootApiResult( context.getString( R.string.response_not_json ) + "\n" + json );
			}else if( json.startsWith( "[" ) ){
				JSONArray array = new JSONArray( json );
				return new TootApiResult( log,response, null, json, array );
			}else{
				JSONObject object = new JSONObject( json );
				
				String error = Utils.optStringX( object, "error" );
				if( ! TextUtils.isEmpty( error ) ){
					return new TootApiResult( context.getString( R.string.api_error, error ) );
				}
				return new TootApiResult( response, null, json, object );
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			return new TootApiResult( Utils.formatError( ex, "API data error" ) );
		}
	}
	
	
	public TootApiResult authorize1(){
		
		JSONObject client_info = null;
		
		for( ; ; ){
			if( callback.isApiCancelled() ) return null;
			
			if( client_info == null ){
				// DBからまず探す
				client_info = ClientInfo.load( instance );
				if( client_info != null && client_info.optInt( KEY_AUTH_VERSION, 0 ) < AUTH_VERSION ){
					// このトークンは形式が古くて使えないよ
					client_info = null;
				}
				
				if( client_info != null ) continue;
				
				callback.publishApiProgress( context.getString( R.string.register_app_to_server, instance ) );
				
				// OAuth2 クライアント登録
				String client_name = "SubwayTooter"; // + UUID.randomUUID().toString();
				
				Request request = new Request.Builder()
					.url( "https://" + instance + "/api/v1/apps" )
					.post( RequestBody.create( MEDIA_TYPE_FORM_URL_ENCODED
						, "client_name=" + Uri.encode( client_name )
							+ "&redirect_uris=" + Uri.encode( REDIRECT_URL )
							+ "&scopes=read write follow"
					) )
					.build();
				Call call = ok_http_client.newCall( request );
				
				Response response;
				try{
					response = call.execute();
				}catch( Throwable ex ){
					ex.printStackTrace(  );
					return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
				}
				if( callback.isApiCancelled() ) return null;
				
				if( ! response.isSuccessful() ){
					return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
				}
				try{
					String json = response.body().string();
					if( TextUtils.isEmpty( json ) || json.startsWith( "<" ) ){
						return new TootApiResult( context.getString( R.string.response_not_json ) + "\n" + json );
					}
					// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
					client_info = new JSONObject( json );
					String error = Utils.optStringX( client_info, "error" );
					if( ! TextUtils.isEmpty( error ) ){
						return new TootApiResult( context.getString( R.string.api_error, error ) );
					}
					client_info.put( KEY_AUTH_VERSION, AUTH_VERSION );
					ClientInfo.save( instance, client_info.toString() );
					continue;
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new TootApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
			
			// 認証ページURLを作る
			final String browser_url = "https://" + instance + "/oauth/authorize"
				+ "?client_id=" + Uri.encode( Utils.optStringX( client_info, "client_id" ) )
				// この段階では要らない		+ "&client_secret=" + Uri.encode( Utils.optStringX( client_info, "client_secret" ) )
				+ "&response_type=code"
				+ "&redirect_uri=" + Uri.encode( REDIRECT_URL )
				+ "&scope=read write follow"
				+ "&scopes=read write follow"
				+ "&state=" + ( account != null ? "db:" + account.db_id : "host:" + instance )
				+ "&grant_type=authorization_code"
				//	+ "&username=" + Uri.encode( user_mail )
				//	+ "&password=" + Uri.encode( password )
				+ "&approval_prompt=force"
				//		+"&access_type=offline"
				;
			// APIリクエストは失敗?する
			// URLをエラーとして返す
			return new TootApiResult( browser_url );
		}
		
	}
	
	public TootApiResult authorize2( String code ){
		
		JSONObject client_info = ClientInfo.load( instance );
		if( client_info != null && client_info.optInt( KEY_AUTH_VERSION, 0 ) < AUTH_VERSION ){
			client_info = null;
		}
		if( client_info == null ){
			return new TootApiResult( "missing client id" );
		}
		
		// コードを使ってトークンを取得する
		callback.publishApiProgress( context.getString( R.string.request_access_token ) );
		
		JSONObject token_info;
		
		String post_content =
			"grant_type=authorization_code"
				+ "&code=" + Uri.encode( code )
				+ "&client_id=" + Uri.encode( Utils.optStringX( client_info, "client_id" ) )
				+ "&redirect_uri=" + Uri.encode( REDIRECT_URL )
				+ "&client_secret=" + Uri.encode( Utils.optStringX( client_info, "client_secret" ) )
				+ "&scope=read write follow"
				+ "&scopes=read write follow";
		
		Request request = new Request.Builder()
			.url( "https://" + instance + "/oauth/token" )
			.post( RequestBody.create( MEDIA_TYPE_FORM_URL_ENCODED, post_content ) )
			.build();
		Call call = ok_http_client.newCall( request );
		
		Response response;
		try{
			response = call.execute();
		}catch( Throwable ex ){
			ex.printStackTrace(  );
			return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
		}
		if( callback.isApiCancelled() ) return null;
		
		if( ! response.isSuccessful() ){
			return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
		}
		
		try{
			String json = response.body().string();
			
			// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
			if( TextUtils.isEmpty( json ) || json.charAt( 0 ) == '<' ){
				return new TootApiResult( context.getString( R.string.login_failed ) );
			}
			token_info = new JSONObject( json );
			String error = Utils.optStringX( client_info, "error" );
			if( ! TextUtils.isEmpty( error ) ){
				return new TootApiResult( context.getString( R.string.api_error, error ) );
			}
			token_info.put( KEY_AUTH_VERSION, AUTH_VERSION );
		}catch( Throwable ex ){
			ex.printStackTrace();
			return new TootApiResult( Utils.formatError( ex, "API data error" ) );
		}
		
		// 認証されたアカウントのユーザ名を取得する
		String path = "/api/v1/accounts/verify_credentials";
		callback.publishApiProgress( context.getString( R.string.request_api, path ) );
		
		request = new Request.Builder()
			.url( "https://" + instance + path )
			.header( "Authorization", "Bearer " + Utils.optStringX( token_info, "access_token" ) )
			.build();
		
		call = ok_http_client.newCall( request );
		
		try{
			response = call.execute();
		}catch( Throwable ex ){
			ex.printStackTrace(  );
			return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
		}
		
		if( callback.isApiCancelled() ) return null;
		
		if( ! response.isSuccessful() ){
			return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
		}
		
		try{
			String json = response.body().string();
			
			if( TextUtils.isEmpty( json ) || json.startsWith( "<" ) ){
				return new TootApiResult( context.getString( R.string.response_not_json ) + "\n" + json );
			}else if( json.startsWith( "[" ) ){
				JSONArray array = new JSONArray( json );
				return new TootApiResult( log,response, token_info, json, array );
			}else{
				JSONObject object = new JSONObject( json );
				
				String error = Utils.optStringX( object, "error" );
				if( ! TextUtils.isEmpty( error ) ){
					return new TootApiResult( context.getString( R.string.api_error, error ) );
				}
				return new TootApiResult( response, token_info, json, object );
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			return new TootApiResult( Utils.formatError( ex, "API data error" ) );
		}
	}
	
	
	
}
