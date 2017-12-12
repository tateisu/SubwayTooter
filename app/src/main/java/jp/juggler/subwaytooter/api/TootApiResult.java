package jp.juggler.subwaytooter.api;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.util.LogCategory;
import okhttp3.Response;
import okhttp3.WebSocket;

public class TootApiResult {
	@Nullable public String error;
	@Nullable public JSONObject object;
	@Nullable public JSONArray array;
	@Nullable public String json;
	@Nullable public JSONObject token_info;
	@Nullable public Response response;
	@Nullable public WebSocket socket;
	
	public TootApiResult(){
	}

	public TootApiResult( @NonNull String error ){
		this.error = error;
	}
	public TootApiResult( @NonNull Response response, @NonNull String error ){
		this.response =response;
		this.error = error;
	}
	
	TootApiResult(
		@NonNull Response response
		, @NonNull JSONObject token_info
		, @NonNull String json
		, @NonNull JSONObject object
	){
		this.token_info = token_info;
		this.json = json;
		this.object = object;
		this.response = response;
	}

	TootApiResult(
		@NonNull LogCategory log
		, @NonNull Response response
		, @NonNull JSONObject token_info
		, @NonNull String json
		, @NonNull JSONArray array
	){
		this.token_info = token_info;
		this.json = json;
		this.array = array;
		this.response = response;
		parseLinkHeader(log,response,array);
	}
	
	TootApiResult( @NonNull WebSocket socket ){
		this.socket = socket;
	}
	
	public String link_older; // より古いデータへのリンク
	public String link_newer; // より新しいデータへの
	
	private static final Pattern reLinkURL = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"");
	

	
	private void parseLinkHeader(@NonNull LogCategory log,@NonNull Response response, @Nullable JSONArray array){
		// Link:  <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&max_id=405228>; rel="next",
		//        <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&since_id=436946>; rel="prev"
		
		log.d("array size=%s",array==null?-1:array.length() );

		String sv = response.header( "Link" );
		if( TextUtils.isEmpty( sv ) ){
			log.d("missing Link header");
			return;
		}
		
		Matcher m = reLinkURL.matcher( sv );
		while( m.find()){
			String url = m.group(1);
			String rel = m.group(2);
		//	log.d("Link %s,%s",rel,url);
			if( "next".equals( rel )) link_older = url;
			if( "prev".equals( rel )) link_newer = url;
		}
	}
	
}
