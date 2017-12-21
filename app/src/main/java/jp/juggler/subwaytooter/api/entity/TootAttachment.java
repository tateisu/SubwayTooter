package jp.juggler.subwaytooter.api.entity;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootAttachment {
	
	private static final LogCategory log = new LogCategory( "TootAttachment" );

	public static class List extends ArrayList< TootAttachment > {
		
	}
	
	//	ID of the attachment
	public long id;
	
	//One of: "image", "video", "gifv". or may null ? may "unknown" ?
	public String type;
	public static final String TYPE_IMAGE = "image";
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_GIFV = "gifv";
	public static final String TYPE_UNKNOWN = "unknown";
	
	//URL of the locally hosted version of the image
	@Nullable public String url;
	
	//For remote images, the remote URL of the original image
	@Nullable public String remote_url;
	
	//	URL of the preview image
	@Nullable public String preview_url;
	
	//	Shorter URL for the image, for insertion into text (only present on local images)
	@Nullable public String text_url;
	
	// ALT text (Mastodon 2.0.0 or later)
	@Nullable public String description;
	
	public JSONObject json;
	
	@Nullable
	public static TootAttachment parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootAttachment dst = new TootAttachment();
			dst.json = src;
			dst.id = Utils.optLongX( src, "id" );
			dst.type = Utils.optStringX( src, "type" );
			dst.url = Utils.optStringX( src, "url" );
			dst.remote_url = Utils.optStringX( src, "remote_url" );
			dst.preview_url = Utils.optStringX( src, "preview_url" );
			dst.text_url = Utils.optStringX( src, "text_url" );
			dst.description = Utils.optStringX( src, "description" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootAttachment.parse failed." );
			return null;
		}
	}
	
	@NonNull public JSONObject encodeJSON() throws JSONException{
		JSONObject dst = new JSONObject();
		dst.put( "id", Long.toString( id ) );
		if( type != null ) dst.put( "type", type );
		if( url != null ) dst.put( "url", url );
		if( remote_url != null ) dst.put( "remote_url", remote_url );
		if( preview_url != null ) dst.put( "preview_url", preview_url );
		if( text_url != null ) dst.put( "text_url", text_url );
		if( description != null ) dst.put( "description", description );
		return dst;
	}
	
	@NonNull public static List parseList( JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootAttachment item = parse( src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
	
	
	
	@Nullable public String getLargeUrl( SharedPreferences pref ){
		String sv;
		if( pref.getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) ){
			sv = this.url;
			if( TextUtils.isEmpty( sv ) ){
				sv = this.remote_url;
			}
		}else{
			sv = this.remote_url;
			if( TextUtils.isEmpty( sv ) ){
				sv = this.url;
			}
		}
		return sv;
	}
	
}

// v1.3 から 添付ファイルの画像のピクセルサイズが取得できるようになった
// https://github.com/tootsuite/mastodon/issues/1985
// "media_attachments" : [
//	 {
//	 "id" : 4,
//	 "type" : "image",
//	 "remote_url" : "",
//	 "meta" : {
//	 "original" : {
//	 "width" : 600,
//	 "size" : "600x400",
//	 "height" : 400,
//	 "aspect" : 1.5
//	 },
//	 "small" : {
//	 "aspect" : 1.49812734082397,
//	 "height" : 267,
//	 "size" : "400x267",
//	 "width" : 400
//	 }
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/original/3416fc5188c656da.jpg?1493138517",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/small/3416fc5188c656da.jpg?1493138517",
//	 "text_url" : "http://127.0.0.1:3000/media/4hfW3Kt4U9UxDvV_xug"
//	 },
//	 {
//	 "text_url" : "http://127.0.0.1:3000/media/0vTH_B1kjvIvlUBhGBw",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/small/23519a5e64064e32.png?1493138030",
//	 "meta" : {
//	 "fps" : 15,
//	 "duration" : 5.06,
//	 "width" : 320,
//	 "size" : "320x180",
//	 "height" : 180,
//	 "length" : "0:00:05.06",
//	 "aspect" : 1.77777777777778
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/original/23519a5e64064e32.mp4?1493138030",
//	 "remote_url" : "",
//	 "type" : "gifv",
//	 "id" : 3
//	 }
//	 ],