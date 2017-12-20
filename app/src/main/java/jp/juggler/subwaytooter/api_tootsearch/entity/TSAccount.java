package jp.juggler.subwaytooter.api_tootsearch.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TSAccount extends TootAccount {
	private static final LogCategory log = new LogCategory( "TSAccount" );
	
	private static final Pattern reAccountUrl = Pattern.compile( "\\Ahttps://([^/#?]+)/@([^/#?]+)\\z" );
	
	@Nullable
	static TootAccount parseAccount( @NonNull Context context, SavedAccount access_info, JSONObject src ){
		
		if( src == null ) return null;
		
		TSAccount dst = new TSAccount();

		dst.url = Utils.optStringX( src, "url" );
		if( TextUtils.isEmpty( dst.url ) ){
			log.e( "parseAccount: missing url" );
			return null;
		}
		
		// tootsearch のアカウントのIDはどのタンス上のものか分からない
		////// dst.id = Utils.optLongX( src, "id" );
		dst.id = -1L;
		dst.username = Utils.optStringX( src, "username" );
		
		dst.id = Utils.optLongX( src, "id", - 1L );
		dst.username = Utils.optStringX( src, "username" );

		dst.acct = Utils.optStringX( src, "acct" );
		if( dst.acct == null ){
			dst.acct = "?@?";
		}else if( -1 == dst.acct.indexOf( '@' ) ){
			Matcher m = reAccountUrl.matcher( dst.url );
			if( ! m.find() ){
				log.e( "parseAccount: not account url: %s", dst.url );
				return null;
			}else{
				dst.acct = dst.username + "@" + m.group( 1 );
			}
		}
		
		// 絵文字データは先に読んでおく
		dst.profile_emojis = NicoProfileEmoji.parseMap( src.optJSONArray( "profile_emojis" ) );
		
		String sv = Utils.optStringX( src, "display_name" );
		dst.setDisplayName( context, dst.username, sv );
		
		dst.locked = src.optBoolean( "locked" );
		dst.created_at = Utils.optStringX( src, "created_at" );
		dst.followers_count = Utils.optLongX( src, "followers_count" );
		dst.following_count = Utils.optLongX( src, "following_count" );
		dst.statuses_count = Utils.optLongX( src, "statuses_count" );
		
		dst.note = Utils.optStringX( src, "note" );
		dst.decoded_note = new DecodeOptions()
			.setShort( true )
			.setDecodeEmoji( true )
			.setProfileEmojis( dst.profile_emojis )
			.decodeHTML( context, access_info, dst.note );
		
		dst.avatar = Utils.optStringX( src, "avatar" ); // "https:\/\/mastodon.juggler.jp\/system\/accounts\/avatars\/000\/000\/148\/original\/0a468974fac5a448.PNG?1492081886",
		dst.avatar_static = Utils.optStringX( src, "avatar_static" ); // "https:\/\/mastodon.juggler.jp\/system\/accounts\/avatars\/000\/000\/148\/original\/0a468974fac5a448.PNG?1492081886",
		dst.header = Utils.optStringX( src, "header" ); // "https:\/\/mastodon.juggler.jp\/headers\/original\/missing.png"
		dst.header_static = Utils.optStringX( src, "header_static" ); // "https:\/\/mastodon.juggler.jp\/headers\/original\/missing.png"}
		
		//			,"nico_url":null
		
		dst.time_created_at = TootStatus.parseTime( dst.created_at );
		
		dst.source = parseSource( src.optJSONObject( "source" ) );
		
//		JSONObject o = src.optJSONObject( "moved" );
//		if( o != null ){
//			dst.moved = TootAccount.parse( context, account, o);
//		}
		
		return dst;
		
	}
}
