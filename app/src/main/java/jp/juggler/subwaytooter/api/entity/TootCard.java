package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootCard {
	private static final LogCategory log = new LogCategory( "TootCard" );
	
	
	//	The url associated with the card
	public String url;

	//	The title of the card
	public String title;

	//	The card description
	public String description;

	//	The image associated with the card, if any
	public String image;
	
	@Nullable
	public static TootCard parse( JSONObject src ){
		if( src==null) return null;
		try{
			TootCard dst = new TootCard();
			dst.url = Utils.optStringX( src, "url" );
			dst.title = Utils.optStringX( src, "title" );
			dst.description = Utils.optStringX( src, "description" );
			dst.image = Utils.optStringX( src, "image" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e(ex,"TootCard.parse failed.");
			return null;
		}
	}
	
}
