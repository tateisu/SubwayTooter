package jp.juggler.subwaytooter.api.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.TextUtils;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootParser;
import jp.juggler.subwaytooter.api_msp.entity.MSPToot;
import jp.juggler.subwaytooter.api_tootsearch.entity.TSToot;
import jp.juggler.subwaytooter.table.HighlightWord;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public abstract class TootStatusLike extends TootId {
	
	static final LogCategory log = new LogCategory( "TootStatusLike" );
	
	//URL to the status page (can be remote)
	public String url;
	
	public String host_original;
	public String host_access;
	
	// The TootAccount which posted the status
	@Nullable public TootAccount account;
	
	//The number of reblogs for the status
	public long reblogs_count;
	
	//The number of favourites for the status
	public long favourites_count;
	
	//	Whether the authenticated user has reblogged the status
	public boolean reblogged;
	
	//	Whether the authenticated user has favourited the status
	public boolean favourited;
	
	//Whether media attachments should be hidden by default
	public boolean sensitive;
	
	// Whether the authenticated user has muted the conversation this status from
	public boolean muted;
	
	// 固定されたトゥート
	public boolean pinned;
	
	// The detected language for the status, if detected
	public String language;
	
	//If not empty, warning text that should be displayed before the actual content
	public String spoiler_text;
	public Spannable decoded_spoiler_text;
	
	//	Body of the status; this will contain HTML (remote HTML already sanitized)
	public String content;
	public Spannable decoded_content;
	
	//Application from which the status was posted
	@Nullable public TootApplication application;
	
	@Nullable public CustomEmoji.Map custom_emojis;
	
	@Nullable public NicoProfileEmoji.Map profile_emojis;
	
	@Nullable public TootCard card;
	
	/////////////////////////
	// 以下はアプリ内部で使用する
	
	public long time_created_at;
	
	public JSONObject json;
	
	public abstract boolean hasMedia();
	
	public String getHostAccessOrOriginal(){
		return ( TextUtils.isEmpty( host_access ) || "?".equals( host_access ) ) ? host_original : host_access;
	}
	
	public long getIdAccessOrOriginal(){
		return id; // id != -1L ? id : parseStatusId();
	}
	
	public String getBusyKey(){
		return getHostAccessOrOriginal() + ":" + getIdAccessOrOriginal();
	}
	
	private static final Pattern reWhitespace = Pattern.compile( "[\\s\\t\\x0d\\x0a]+" );
	
	@Nullable public HighlightWord highlight_sound;

	public boolean hasHighlight;
	
	public void setSpoilerText( @NonNull TootParser parser, String sv ){
		if( TextUtils.isEmpty( sv ) ){
			this.spoiler_text = null;
			this.decoded_spoiler_text = null;
		}else{
			this.spoiler_text = Utils.sanitizeBDI( sv );
			// remove white spaces
			sv = reWhitespace.matcher( this.spoiler_text ).replaceAll( " " );
			// decode emoji code
			
			DecodeOptions options = new DecodeOptions()
				.setCustomEmojiMap( custom_emojis )
				.setProfileEmojis( this.profile_emojis )
				.setHighlightTrie(parser.highlight_trie)
				;
			
			this.decoded_spoiler_text = options.decodeEmoji( parser.context, sv );

			this.hasHighlight = this.hasHighlight || options.hasHighlight;

			if( options.highlight_sound != null && this.highlight_sound == null  ){
				this.highlight_sound = options.highlight_sound;
			}
		}
	}
	
	public void setContent( @NonNull TootParser parser, TootAttachment.List list_attachment, @Nullable String content ){
		this.content = content;
		
		DecodeOptions options =new DecodeOptions()
			.setShort( true )
			.setDecodeEmoji( true )
			.setCustomEmojiMap( this.custom_emojis )
			.setProfileEmojis( this.profile_emojis )
			.setLinkTag( this )
			.setAttachment( list_attachment )
			.setHighlightTrie(parser.highlight_trie)
			;
			
		this.decoded_content = options.decodeHTML( parser.context, parser.access_info, content );
		
		this.hasHighlight = this.hasHighlight || options.hasHighlight;
		
		if( options.highlight_sound != null && this.highlight_sound == null  ){
			this.highlight_sound = options.highlight_sound;
		}
	}
	
	public abstract boolean canPin( SavedAccount access_info );
	
	public static class AutoCW {
		public WeakReference< Object > refActivity;
		public int cell_width;
		public Spannable decoded_spoiler_text;
		public int originalLineCount;
	}
	
	public AutoCW auto_cw;
	
	// OStatus
	static final Pattern reTootUriOS = Pattern.compile( "tag:([^,]*),[^:]*:objectId=(\\d+):objectType=Status", Pattern.CASE_INSENSITIVE );
	// ActivityPub 1
	static final Pattern reTootUriAP1 = Pattern.compile( "https?://([^/]+)/users/[A-Za-z0-9_]+/statuses/(\\d+)" );
	// ActivityPub 2
	static final Pattern reTootUriAP2 = Pattern.compile( "https?://([^/]+)/@[A-Za-z0-9_]+/(\\d+)" );
	
	// 投稿元タンスでのステータスIDを調べる
	public long parseStatusId(){
		return TootStatusLike.parseStatusId( this );
	}
	
	// 投稿元タンスでのステータスIDを調べる
	public static long parseStatusId( @NonNull TootStatusLike status ){
		
		String uri;
		if( status instanceof TootStatus ){
			uri = ( (TootStatus) status ).uri;
		}else if( status instanceof TSToot ){
			uri = ( (TSToot) status ).uri;
		}else{
			log.d( "parseStatusId: unsupported status type: %s", status.getClass().getSimpleName() );
			return - 1L;
		}
		
		try{
			Matcher m;
			
			// https://friends.nico/users/(who)/statuses/(status_id)
			m = reTootUriAP1.matcher( uri );
			if( m.find() ){
				return Long.parseLong( m.group( 2 ), 10 );
			}
			
			// tag:mstdn.osaka,2017-12-19:objectId=5672321:objectType=Status
			m = reTootUriOS.matcher( uri );
			if( m.find() ){
				return Long.parseLong( m.group( 2 ), 10 );
			}
			
			//
			m = reTootUriAP2.matcher( uri );
			if( m.find() ){
				return Long.parseLong( m.group( 2 ), 10 );
			}
			
			log.d( "parseStatusId: unsupported status uri: %s", uri );
			
		}catch( Throwable ex ){
			log.e( ex, "parseStatusId: cant parse tag: %s", uri );
		}
		
		return - 1L;
	}
}
