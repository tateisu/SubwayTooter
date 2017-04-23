package jp.juggler.subwaytooter.api;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Response;

public class TootApiResult {
	public String error;
	public JSONObject object;
	public JSONArray array;
	public String json;
	public JSONObject token_info;
	public Response response;
	
	public TootApiResult( String error ){
		this.error = error;
	}
	
	public TootApiResult( Response response,JSONObject token_info,String json,JSONObject object ){
		this.token_info = token_info;
		this.json = json;
		this.object = object;
		this.response = response;
	}

	public TootApiResult( Response response, JSONObject token_info, String json, JSONArray array ){
		this.token_info = token_info;
		this.json = json;
		this.array = array;
		this.response = response;
	}
	
}
