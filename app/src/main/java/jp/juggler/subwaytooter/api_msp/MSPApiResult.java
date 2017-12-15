package jp.juggler.subwaytooter.api_msp;

import org.json.JSONArray;

import jp.juggler.subwaytooter.api.TootApiResult;
import okhttp3.Response;


public class MSPApiResult extends TootApiResult {

	MSPApiResult( String error ){
		super( error );
	}

	MSPApiResult( Response response, String json, JSONArray array ){
		super( null );
		this.json = json;
		this.array = array;
		this.response = response;
	}
}
