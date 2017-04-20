package jp.juggler.subwaytooter.api.entity;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootError {
	
	//	A textual description of the error
	public String error;
	
	public static TootError parse( LogCategory log, JSONObject src ){
		if( src==null ) return null;
		try{
			TootError dst = new TootError();
			dst.error = Utils.optStringX( src, "error" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e(ex,"TootError.parse failed.");
			return null;
		}
	}
	
}
