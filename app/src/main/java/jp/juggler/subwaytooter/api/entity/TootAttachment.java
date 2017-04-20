package jp.juggler.subwaytooter.api.entity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootAttachment {
	
	public static class List extends ArrayList< TootAttachment > {
		
	}
	
	//	ID of the attachment
	public long id;
	
	//One of: "image", "video", "gifv". or may null ? may "unknown" ?
	public String type;
	public static final String TYPE_IMAGE = "image";
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_GIFV = "gifv";
	
	//URL of the locally hosted version of the image
	public String url;
	
	//For remote images, the remote URL of the original image
	public String remote_url;
	
	//	URL of the preview image
	public String preview_url;
	
	//	Shorter URL for the image, for insertion into text (only present on local images)
	public String text_url;
	
	public static TootAttachment parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootAttachment dst = new TootAttachment();
			dst.id = src.optLong( "id" );
			dst.type = Utils.optStringX( src, "type" );
			dst.url = Utils.optStringX( src, "url" );
			dst.remote_url = Utils.optStringX( src, "remote_url" );
			dst.preview_url = Utils.optStringX( src, "preview_url" );
			dst.text_url = Utils.optStringX( src, "text_url" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootAttachment.parse failed." );
			return null;
		}
	}
	
	public static List parseList( LogCategory log, JSONArray array ){
		List result = new List();
		for( int i = array.length() - 1 ; i >= 0 ; -- i ){
			JSONObject src = array.optJSONObject( i );
			if( src == null ) continue;
			TootAttachment item = parse( log, src );
			if( item != null ) result.add( 0, item );
		}
		return result;
	}
	
}
