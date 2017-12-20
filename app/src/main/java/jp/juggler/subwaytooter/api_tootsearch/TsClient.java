package jp.juggler.subwaytooter.api_tootsearch;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TSClient {
	private static final LogCategory log = new LogCategory( "TSClient" );
	
	private static final OkHttpClient ok_http_client = App1.ok_http_client;
	
	public interface Callback {
		boolean isApiCancelled();
		
		void publishApiProgress( String s );
	}
	
	public static TootApiResult search(
		@NonNull Context context
		, @NonNull String query
		, @NonNull String max_id // 空文字列、もしくはfromに指定するパラメータ
		, @NonNull Callback callback
	){
		String url = "https://tootsearch.chotto.moe/api/v1/search"
			+ "?sort=" + Uri.encode( "created_at:desc" )
			+ "&from=" + max_id
			+ "&q=" + Uri.encode( query );
		
		Response response;
		try{
			Request request = new Request.Builder()
				.url( url )
				.build();
			
			callback.publishApiProgress( "waiting search result..." );
			Call call = ok_http_client.newCall( request );
			response = call.execute();
		}catch( Throwable ex ){
			log.trace( ex );
			return new TootApiResult( Utils.formatError( ex, context.getResources(), R.string.network_error ) );
		}
		
		if( callback.isApiCancelled() ) return null;
		
		if( ! response.isSuccessful() ){
			log.d( "response failed." );
			return new TootApiResult( Utils.formatResponse( response, url ) );
		}
		
		try{
			//noinspection ConstantConditions
			String json = response.body().string();
			JSONObject object = new JSONObject( json );
			return new TootApiResult( response, null, json, object );
		}catch( Throwable ex ){
			log.trace( ex );
			return new TootApiResult( Utils.formatError( ex, "API data error" ) );
		}
	}
	
	public static JSONArray getHits( @NonNull JSONObject root ){
		JSONObject hits = root.optJSONObject( "hits" );
		if( hits != null ){
			return hits.optJSONArray( "hits" );
		}
		return null;
	}
	
	// returns the number for "from" parameter of next page.
	// returns "" if no more next page.
	public static String getMaxId( @NonNull JSONObject root, String old ){
		int old_from = Utils.parse_int( old, 0 );
		JSONArray hits2 = getHits( root );
		if( hits2 != null ){
			int size = hits2.length();
			return size == 0 ? "" : Integer.toString( old_from + hits2.length() );
		}
		return "";
	}
	
}
