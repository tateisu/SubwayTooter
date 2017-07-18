package jp.juggler.subwaytooter.api_msp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MSPClient {
	static final LogCategory log = new LogCategory("MSPClient");
	
	private static final String url_token = "http://mastodonsearch.jp/api/v1.0.1/utoken";
	private static final String url_search = "http://mastodonsearch.jp/api/v1.0.1/cross";
	private static final String api_key = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc";
	
	private static final OkHttpClient ok_http_client = App1.ok_http_client;
	
	
	public interface Callback {
		boolean isApiCancelled();
		
		void publishApiProgress( String s );
	}
	
	public static MSPApiResult search( @NonNull Context context, @NonNull String query, @NonNull String max_id ,@NonNull Callback callback ){
		// ユーザトークンを読む
		SharedPreferences pref = Pref.pref(context);
		String user_token = pref.getString(Pref.KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN,null);
		
		Response response;
		
		for(;;){
			// ユーザトークンがなければ取得する
			if( TextUtils.isEmpty( user_token ) ){
				
				callback.publishApiProgress( "get MSP user token..." );
				
				String url = url_token + "?apikey=" + Uri.encode( api_key );
				
				try{
					Request request = new Request.Builder()
						.url( url )
						.build();
					
					Call call = ok_http_client.newCall( request );
					response = call.execute();
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new MSPApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
				}
				
				if( callback.isApiCancelled() ) return null;
				
				if( ! response.isSuccessful() ){
					if( response.code() >= 400 ){
						try{
							String json = response.body().string();
							JSONObject object = new JSONObject( json );
							JSONObject error = object.getJSONObject( "error" );
							return new MSPApiResult( String.format( "API returns error. %s: %s"
								, error.optString( "type" )
								, error.optString( "detail" )
							) );
						}catch( Throwable ex ){
							ex.printStackTrace();
							return new MSPApiResult( Utils.formatError( ex, "API returns error response %s, but can't parse response body.", response.code() ) );
						}
					}else{
						return new MSPApiResult( context.getString( R.string.network_error_arg, response ) );
					}
				}
				
				try{
					//noinspection ConstantConditions
					String json = response.body().string();
					JSONObject object = new JSONObject( json );
					user_token = object.getJSONObject( "result" ).getString( "token" );
					if( TextUtils.isEmpty( user_token ) ){
						return new MSPApiResult( String.format( "Can't get MSP user token. response=%s", json ) );
					}else{
						pref.edit().putString( Pref.KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN, user_token ).apply();
					}
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new MSPApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
			// ユーザトークンを使って検索APIを呼び出す
			{
				callback.publishApiProgress( "waiting search result..." );
				String url = url_search
					+ "?apikey=" + Uri.encode( api_key )
					+ "&utoken=" + Uri.encode( user_token )
					+ "&max=" + Uri.encode( max_id )
					+ "&q=" + Uri.encode( query );
				
				try{
					Request request = new Request.Builder()
						.url( url )
						.build();
					
					Call call = ok_http_client.newCall( request );
					response = call.execute();
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new MSPApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
				}
				
				if( callback.isApiCancelled() ) return null;
				
				if( ! response.isSuccessful() ){
					if( response.code() >= 400 ){
						try{
							String json = response.body().string();
							JSONObject object = new JSONObject( json );
							JSONObject error = object.getJSONObject( "error" );
							// ユーザトークンがダメなら生成しなおす
							if( "utoken".equals( error.optString( "detail" ) ) ){
								user_token = null;
								continue;
							}
							return new MSPApiResult( String.format( "API returns error. %s: %s"
								, error.optString( "type" )
								, error.optString( "detail" )
							) );
						}catch( Throwable ex ){
							ex.printStackTrace();
							return new MSPApiResult( Utils.formatError( ex, "API returns error response %s, but can't parse response body.", response.code() ) );
						}
					}else{
						return new MSPApiResult( context.getString( R.string.network_error_arg, response ) );
					}
				}
				
				try{
					//noinspection ConstantConditions
					String json = response.body().string();
					JSONArray array = new JSONArray( json );
					return new MSPApiResult( response, json, array );
				}catch( Throwable ex ){
					ex.printStackTrace();
					return new MSPApiResult( Utils.formatError( ex, "API data error" ) );
				}
			}
		}
	}
	
	public static String getMaxId( JSONArray array, String max_id ){
		// max_id の更新
		int size = array.length();
		if( size > 0 ){
			JSONObject item = array.optJSONObject( size - 1 );
			if( item != null ){
				String sv = item.optString( "msp_id" );
				if( ! TextUtils.isEmpty( sv ) ){
					return sv;
				}
			}
		}
		return max_id;
	}

}
