package jp.juggler.subwaytooter.api;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.util.LogCategory;
import okhttp3.Response;
import okhttp3.WebSocket;

public class TootApiResult {
	public String error;
	public JSONObject object;
	public JSONArray array;
	public String json;
	public JSONObject token_info;
	public Response response;
	public WebSocket socket;
	
	
	public TootApiResult( String error ){
		this.error = error;
	}
	
	TootApiResult( Response response, JSONObject token_info, String json, JSONObject object ){
		this.token_info = token_info;
		this.json = json;
		this.object = object;
		this.response = response;
	}

	TootApiResult( LogCategory log, Response response, JSONObject token_info
		, String json, JSONArray array ){
		this.token_info = token_info;
		this.json = json;
		this.array = array;
		this.response = response;
		parseLinkHeader(log,response,array);
	}
	
	TootApiResult( WebSocket socket ){
		this.socket = socket;
	}
	
	public String link_older; // より古いデータへのリンク
	public String link_newer; // より新しいデータへの
	
	private static final Pattern reLinkURL = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"");
	
	private void parseLinkHeader(LogCategory log,Response response, JSONArray array){
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
			log.d("Link %s,%s",rel,url);
			if( "next".equals( rel )) link_older = url;
			if( "prev".equals( rel )) link_newer = url;
		}
	}
	
}
