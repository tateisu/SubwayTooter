package jp.juggler.subwaytooter.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class TootApiResult {
	public String error;
	public JSONObject object;
	public JSONArray array;
	public String json;
	public JSONObject token_info;
	public TootApiResult( String error ){
		this.error = error;
	}
	
	public TootApiResult( JSONObject token_info,String json,JSONObject object ){
		this.token_info = token_info;
		this.json = json;
		this.object = object;
	}

	public TootApiResult(JSONObject token_info, String json,JSONArray array ){
		this.token_info = token_info;
		this.json = json;
		this.array = array;
	}
	
}
