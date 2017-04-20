package jp.juggler.subwaytooter.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class TootApiResult {
	public String error;
	public JSONObject object;
	public JSONArray array;
	public String json;
	public TootApiResult( String error ){
		this.error = error;
	}
	
	public TootApiResult( String json,JSONObject object ){
		this.json = json;
		this.object = object;
	}

	public TootApiResult( String json,JSONArray array ){
		this.json = json;
		this.array = array;
	}
	
}
