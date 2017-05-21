package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;

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
	public static final String TYPE_UNKNOWN = "unknown";
	
	//URL of the locally hosted version of the image
	public String url;
	
	//For remote images, the remote URL of the original image
	public String remote_url;
	
	//	URL of the preview image
	public String preview_url;
	
	//	Shorter URL for the image, for insertion into text (only present on local images)
	public String text_url;
	
	public JSONObject json;
	
	public static TootAttachment parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootAttachment dst = new TootAttachment();
			dst.json = src;
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
	
	@NonNull public static List parseList( LogCategory log, JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i=0;i<array_size;++i){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootAttachment item = parse( log, src );
				if( item != null ) result.add( item );
			}
		}
		return result;
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