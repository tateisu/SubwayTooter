package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootCard {
	private static final LogCategory log = new LogCategory( "TootCard" );
	
	
	//	The url associated with the card
	@Nullable public String url;

	//	The title of the card
	@Nullable public String title;

	//	The card description
	@Nullable public String description;

	//	The image associated with the card, if any
	@Nullable public String image;
	
	@Nullable public String type;
	@Nullable public String author_name;
	@Nullable public String author_url;
	@Nullable public String provider_name;
	@Nullable public String provider_url;
	
	@Nullable
	public static TootCard parse( JSONObject src ){
		if( src==null) return null;
		try{
			TootCard dst = new TootCard();
			dst.url = Utils.optStringX( src, "url" );
			dst.title = Utils.optStringX( src, "title" );
			dst.description = Utils.optStringX( src, "description" );
			dst.image = Utils.optStringX( src, "image" );
			
			dst.type = Utils.optStringX( src, "type" );
			dst.author_name = Utils.optStringX( src, "author_name" );
			dst.author_url = Utils.optStringX( src, "author_url" );
			dst.provider_name = Utils.optStringX( src, "provider_name" );
			dst.provider_url = Utils.optStringX( src, "provider_url" );

			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e(ex,"TootCard.parse failed.");
			return null;
		}
	}
	
}
