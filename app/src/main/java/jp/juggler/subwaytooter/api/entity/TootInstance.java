package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.VersionString;

@SuppressWarnings("WeakerAccess")
public class TootInstance {
	private static final LogCategory log = new LogCategory( "TootInstance" );
	
	//	URI of the current instance
	public String uri;
	
	//	The instance's title
	public String title;
	
	//	A description for the instance
	public String description;
	
	//	An email address which can be used to contact the instance administrator
	public String email;
	
	public String version;
	
	// インスタンスのサムネイル。推奨サイズ1200x630px。マストドン1.6.1以降。
	public String thumbnail;
	
	// ユーザ数等の数字。マストドン1.6以降。
	public Stats stats;
	
	public static class Stats {
		public long user_count;
		public long status_count;
		public long domain_count;
	}
	
	////////////////////////////////////////////////
	// 以下は内部で使用する
	
	// バージョンの内部表現
	public VersionString decoded_version;
	
	// いつ取得したか
	public long time_parse;
	
	@Nullable
	public static TootInstance parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootInstance dst = new TootInstance();
			dst.uri = Utils.optStringX( src, "uri" );
			dst.title = Utils.optStringX( src, "title" );
			dst.description = Utils.optStringX( src, "description" );
			dst.email = Utils.optStringX( src, "email" );
			dst.version = Utils.optStringX( src, "version" );
			dst.decoded_version = new VersionString( dst.version );
			dst.time_parse = System.currentTimeMillis();
			
			dst.stats = parseStats( src.optJSONObject( "stats" ) );
			
			dst.thumbnail = Utils.optStringX( src, "thumbnail" );
			
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootInstance.parse failed." );
			return null;
		}
	}
	
	private static @Nullable Stats parseStats( JSONObject src ){
		if( src == null ) return null;
		Stats dst = new Stats();
		dst.user_count = Utils.optLongX( src, "user_count", - 1L );
		dst.status_count = Utils.optLongX( src, "status_count", - 1L );
		dst.domain_count = Utils.optLongX( src, "domain_count", - 1L );
		return dst;
	}
	
	public boolean isEnoughVersion( @NonNull VersionString check ){
		if( decoded_version.isEmpty() || check.isEmpty() ) return false;
		int i = VersionString.compare( decoded_version, check );
		return i >= 0;
	}
}
