package jp.juggler.subwaytooter.api.entity;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootInstance {
	
	//	URI of the current instance
	public String uri;
	
	//	The instance's title
	public String title;
	
	//	A description for the instance
	public String description;
	
	//	An email address which can be used to contact the instance administrator
	public String email;
	
	public static TootInstance parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootInstance dst = new TootInstance();
			dst.uri = Utils.optStringX( src, "uri" );
			dst.title = Utils.optStringX( src, "title" );
			dst.description = Utils.optStringX( src, "description" );
			dst.email = Utils.optStringX( src, "email" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootInstance.parse failed." );
			return null;
		}
	}
}
