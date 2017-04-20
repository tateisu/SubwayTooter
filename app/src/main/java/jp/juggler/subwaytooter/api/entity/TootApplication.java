package jp.juggler.subwaytooter.api.entity;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootApplication {
	public String name;
	public String website;
	
	public static TootApplication parse( LogCategory log, JSONObject src ){
		try{
			TootApplication dst = new TootApplication();
			dst.name = Utils.optStringX( src, "name" );
			dst.website = Utils.optStringX( src, "website" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootApplication.parse failed." );
			return null;
		}
	}
}
