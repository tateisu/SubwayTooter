package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootApplication {
	
	private static final LogCategory log = new LogCategory( "TootApplication" );
	
	public String name;
	public String website;
	
	@Nullable
	public static TootApplication parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootApplication dst = new TootApplication();
			dst.name = Utils.optStringX( src, "name" );
			dst.website = Utils.optStringX( src, "website" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootApplication.parse failed." );
			return null;
		}
	}
}
