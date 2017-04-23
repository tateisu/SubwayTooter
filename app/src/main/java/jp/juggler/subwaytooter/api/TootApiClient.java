package jp.juggler.subwaytooter.api;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.CancelChecker;
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

public class TootApiClient {
	private static final LogCategory log = new LogCategory( "TootApiClient" );
	
	static final OkHttpClient ok_http_client = App1.ok_http_client;
	
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
	
	public static final MediaType MEDIA_TYPE_FORM_URL_ENCODED = MediaType.parse( "application/x-www-form-urlencoded" );
	
	public TootApiResult request( String path ){
		return request(path,new Request.Builder() );
	}

	public TootApiResult request( String path, Request.Builder request_builder ){
		log.d("request: %s",path);
		TootApiResult result = request_sub( path,request_builder );
		if( result.error != null ){
			log.d("error: %s",result.error);
		}
		return result;
	}

	public TootApiResult request_sub( String path, Request.Builder request_builder ){
			
			JSONObject client_info = null;
		JSONObject token_info = ( account == null ? null : account.token_info );
		
		for( ; ; ){
			if( callback.isApiCancelled() ) return null;
			if( token_info == null ){
				if( client_info == null ){
					// DBにあるならそれを使う
					client_info = ClientInfo.load( instance );
					if( client_info != null ) continue;
					
					callback.publishApiProgress( context.getString( R.string.register_app_to_server, instance ) );
					
					// OAuth2 クライアント登録
					String client_name = "SubwayTooter" ; // + UUID.randomUUID().toString();
					
					Request request = new Request.Builder()
						.url( "https://" + instance + "/api/v1/apps" )
						.post( RequestBody.create( MEDIA_TYPE_FORM_URL_ENCODED
							, "client_name=" + Uri.encode( client_name )
								+ "&redirect_uris=urn:ietf:wg:oauth:2.0:oob"
								+ "&scopes=read write follow"
						) )
						.build();
					Call call = ok_http_client.newCall( request );
					
					Response response;
					try{
						response = call.execute();
					}catch( Throwable ex ){
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
						ClientInfo.save( instance, json );
						continue;
					}catch( Throwable ex ){
						ex.printStackTrace();
						return new TootApiResult( Utils.formatError( ex, "API data error" ) );
					}
				}
				
				if( password == null ){
					// 手動でアクセストークンを再取得しなければいけない
					return new TootApiResult( context.getString( R.string.login_required ) );
				}
				
				callback.publishApiProgress( context.getString( R.string.request_access_token ) );
				
				// アクセストークンの取得
				
				Request request = new Request.Builder()
					.url( "https://" + instance + "/oauth/token" )
					.post( RequestBody.create(
						MEDIA_TYPE_FORM_URL_ENCODED
						,"client_id=" + Uri.encode( Utils.optStringX( client_info, "client_id" ) )
							+ "&client_secret=" + Uri.encode( Utils.optStringX( client_info, "client_secret" ) )
							+ "&grant_type=password"
							+ "&username=" + Uri.encode( user_mail )
							+ "&password=" + Uri.encode( password )
							+ "&scope=read write follow"
							+ "&scopes=read write follow"
					))
					.build();
				Call call = ok_http_client.newCall( request );
				
				Response response;
				try{
					response = call.execute();
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
				}
				if( callback.isApiCancelled() ) return null;
				
				// TODO: アプリIDが無効な場合はどんなエラーが出る？
				
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
					if( account != null ){
						account.updateTokenInfo( token_info );
					}
					continue;
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new TootApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
			
			// アクセストークンを使ってAPIを呼び出す
			{
				callback.publishApiProgress( context.getString( R.string.request_api, path ) );
				
				Request request  = request_builder
					.url("https://" + instance + path)
					.header( "Authorization", "Bearer " + Utils.optStringX( token_info, "access_token" ) )
					.build();
				
				Call call = ok_http_client.newCall( request );
				Response response;
				try{
					response = call.execute();
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
				}

				if( callback.isApiCancelled() ) return null;
				
				// TODO: アクセストークンが無効な場合はどうなる？
				// TODO: アプリIDが無効な場合はどうなる？
				
				if( ! response.isSuccessful() ){
					return new TootApiResult( context.getString( R.string.network_error_arg, response ) );
				}
				
				try{
					String json = response.body().string();
		
					if( TextUtils.isEmpty( json ) || json.startsWith( "<" ) ){
						return new TootApiResult( context.getString( R.string.response_not_json ) + "\n" + json );
					}else if( json.startsWith( "[" ) ){
						JSONArray array = new JSONArray( json );
						return new TootApiResult( response,token_info, json, array );
					}else{
						JSONObject object = new JSONObject( json );
						
						String error = Utils.optStringX( object, "error" );
						if( ! TextUtils.isEmpty( error ) ){
							return new TootApiResult( context.getString( R.string.api_error, error ) );
						}
						return new TootApiResult( response,token_info, json, object );
					}
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new TootApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
		}
	}
}
