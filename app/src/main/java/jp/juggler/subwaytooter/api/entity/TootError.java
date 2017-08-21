package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootError {
	private static final LogCategory log = new LogCategory( "TootError" );
	
	//	A textual description of the error
	public String error;
	
	@Nullable
	public static TootError parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootError dst = new TootError();
			dst.error = Utils.optStringX( src, "error" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootError.parse failed." );
			return null;
		}
	}
	
}
