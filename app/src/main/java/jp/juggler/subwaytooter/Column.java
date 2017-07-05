package jp.juggler.subwaytooter;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootContext;
import jp.juggler.subwaytooter.api.entity.TootDomainBlock;
import jp.juggler.subwaytooter.api.entity.TootGap;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootTag;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.AcctSet;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.TagSet;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.BucketList;
import jp.juggler.subwaytooter.api.DuplicateMap;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.WordTrieTree;
import jp.juggler.subwaytooter.view.MyListView;
import jp.juggler.subwaytooter.util.ScrollPosition;
import jp.juggler.subwaytooter.util.Utils;

class Column implements StreamReader.Callback {
	private static final LogCategory log = new LogCategory( "Column" );
	
	
	interface Callback {
		boolean isActivityResume();
	}
	
	private WeakReference< Callback > callback_ref;
	
	private boolean isResume(){
		if( callback_ref == null ){
			log.d( "isResume: callback_ref is not set" );
			return false;
		}
		Callback cb = callback_ref.get();
		if( cb == null ){
			log.d( "isResume: callback was lost." );
			return false;
		}
		return cb.isActivityResume();
	}
	
	private static Object getParamAt( Object[] params, int idx ){
		if( params == null || idx >= params.length ){
			throw new IndexOutOfBoundsException( "getParamAt idx=" + idx );
		}
		return params[ idx ];
	}
	
	private static final int READ_LIMIT = 80; // API側の上限が80です。ただし指定しても40しか返ってこないことが多い
	private static final long LOOP_TIMEOUT = 10000L;
	private static final int LOOP_READ_ENOUGH = 30; // フィルタ後のデータ数がコレ以上ならループを諦めます
	private static final int RELATIONSHIP_LOAD_STEP = 40;
	private static final int ACCT_DB_STEP = 100;
	
	// ステータスのリストを返すAPI
	private static final String PATH_HOME = "/api/v1/timelines/home?limit=" + READ_LIMIT;
	private static final String PATH_LOCAL = "/api/v1/timelines/public?limit=" + READ_LIMIT + "&local=1";
	private static final String PATH_FEDERATE = "/api/v1/timelines/public?limit=" + READ_LIMIT;
	private static final String PATH_FAVOURITES = "/api/v1/favourites?limit=" + READ_LIMIT;
	private static final String PATH_ACCOUNT_STATUSES = "/api/v1/accounts/%d/statuses?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_HASHTAG = "/api/v1/timelines/tag/%s?limit=" + READ_LIMIT; // 1: hashtag(url encoded)
	
	// アカウントのリストを返すAPI
	private static final String PATH_ACCOUNT_FOLLOWING = "/api/v1/accounts/%d/following?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_ACCOUNT_FOLLOWERS = "/api/v1/accounts/%d/followers?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_MUTES = "/api/v1/mutes?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_BLOCKS = "/api/v1/blocks?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_FOLLOW_REQUESTS = "/api/v1/follow_requests?limit=" + READ_LIMIT; // 1:account_id
	private static final String PATH_BOOSTED_BY = "/api/v1/statuses/%s/reblogged_by?limit=" + READ_LIMIT; // 1:status_id
	private static final String PATH_FAVOURITED_BY = "/api/v1/statuses/%s/favourited_by?limit=" + READ_LIMIT; // 1:status_id
	
	// 他のリストを返すAPI
	private static final String PATH_REPORTS = "/api/v1/reports?limit=" + READ_LIMIT;
	private static final String PATH_NOTIFICATIONS = "/api/v1/notifications?limit=" + READ_LIMIT;
	private static final String PATH_DOMAIN_BLOCK = "/api/v1/domain_blocks?limit=" + READ_LIMIT;
	
	// リストではなくオブジェクトを返すAPI
	private static final String PATH_ACCOUNT = "/api/v1/accounts/%d"; // 1:account_id
	private static final String PATH_STATUSES = "/api/v1/statuses/%d"; // 1:status_id
	private static final String PATH_STATUSES_CONTEXT = "/api/v1/statuses/%d/context"; // 1:status_id
	static final String PATH_SEARCH = "/api/v1/search?q=%s"; // 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts
	private static final String PATH_INSTANCE = "/api/v1/instance";
	
	static final String KEY_ACCOUNT_ROW_ID = "account_id";
	static final String KEY_TYPE = "type";
	static final String KEY_DONT_CLOSE = "dont_close";
	private static final String KEY_WITH_ATTACHMENT = "with_attachment";
	private static final String KEY_DONT_SHOW_BOOST = "dont_show_boost";
	private static final String KEY_DONT_SHOW_REPLY = "dont_show_reply";
	private static final String KEY_DONT_STREAMING = "dont_streaming";
	private static final String KEY_DONT_AUTO_REFRESH = "dont_auto_refresh";
	private static final String KEY_HIDE_MEDIA_DEFAULT = "hide_media_default";
	private static final String KEY_ENABLE_SPEECH = "enable_speech";
	
	private static final String KEY_REGEX_TEXT = "regex_text";
	
	private static final String KEY_HEADER_BACKGROUND_COLOR = "header_background_color";
	private static final String KEY_HEADER_TEXT_COLOR = "header_text_color";
	private static final String KEY_COLUMN_BACKGROUND_COLOR = "column_background_color";
	private static final String KEY_COLUMN_BACKGROUND_IMAGE = "column_background_image";
	private static final String KEY_COLUMN_BACKGROUND_IMAGE_ALPHA = "column_background_image_alpha";
	
	private static final String KEY_PROFILE_ID = "profile_id";
	private static final String KEY_PROFILE_TAB = "tab";
	private static final String KEY_STATUS_ID = "status_id";
	private static final String KEY_HASHTAG = "hashtag";
	private static final String KEY_SEARCH_QUERY = "search_query";
	private static final String KEY_SEARCH_RESOLVE = "search_resolve";
	
	static final String KEY_COLUMN_ACCESS = "column_access";
	static final String KEY_COLUMN_ACCESS_COLOR = "column_access_color";
	static final String KEY_COLUMN_ACCESS_COLOR_BG = "column_access_color_bg";
	static final String KEY_COLUMN_NAME = "column_name";
	static final String KEY_OLD_INDEX = "old_index";
	
	static final int TYPE_HOME = 1;
	static final int TYPE_LOCAL = 2;
	static final int TYPE_FEDERATE = 3;
	static final int TYPE_PROFILE = 4;
	static final int TYPE_FAVOURITES = 5;
	static final int TYPE_REPORTS = 6;
	static final int TYPE_NOTIFICATIONS = 7;
	static final int TYPE_CONVERSATION = 8;
	static final int TYPE_HASHTAG = 9;
	static final int TYPE_SEARCH = 10;
	static final int TYPE_MUTES = 11;
	static final int TYPE_BLOCKS = 12;
	static final int TYPE_FOLLOW_REQUESTS = 13;
	static final int TYPE_BOOSTED_BY = 14;
	static final int TYPE_FAVOURITED_BY = 15;
	static final int TYPE_DOMAIN_BLOCKS = 16;
	
	@NonNull final Context context;
	@NonNull private final AppState app_state;
	@NonNull final SavedAccount access_info;
	
	final int column_type;
	
	boolean dont_close;
	
	boolean with_attachment;
	boolean dont_show_boost;
	boolean dont_show_reply;
	boolean dont_streaming;
	boolean dont_auto_refresh;
	boolean hide_media_default;
	boolean enable_speech;
	
	String regex_text;
	
	int header_bg_color;
	int header_fg_color;
	int column_bg_color;
	String column_bg_image;
	float column_bg_image_alpha = 1f;
	
	private long profile_id;
	volatile TootAccount who_account;
	int profile_tab = TAB_STATUS;
	static final int TAB_STATUS = 0;
	static final int TAB_FOLLOWING = 1;
	static final int TAB_FOLLOWERS = 2;
	
	private long status_id;
	
	private String hashtag;
	
	String search_query;
	boolean search_resolve;
	
	ScrollPosition scroll_save;
	
	Column( @NonNull AppState app_state, @NonNull SavedAccount access_info, @NonNull Callback callback, int type, Object... params ){
		this.app_state = app_state;
		this.context = app_state.context;
		this.access_info = access_info;
		this.column_type = type;
		this.callback_ref = new WeakReference<>( callback );
		switch( type ){
		
		case TYPE_CONVERSATION:
		case TYPE_BOOSTED_BY:
		case TYPE_FAVOURITED_BY:
			this.status_id = (Long) getParamAt( params, 0 );
			break;
		
		case TYPE_PROFILE:
			this.profile_id = (Long) getParamAt( params, 0 );
			break;
		
		case TYPE_HASHTAG:
			this.hashtag = (String) getParamAt( params, 0 );
			break;
		
		case TYPE_SEARCH:
			this.search_query = (String) getParamAt( params, 0 );
			this.search_resolve = (Boolean) getParamAt( params, 1 );
			break;
			
		}
		init();
	}
	
	void encodeJSON( JSONObject item, int old_index ) throws JSONException{
		item.put( KEY_ACCOUNT_ROW_ID, access_info.db_id );
		item.put( KEY_TYPE, column_type );
		item.put( KEY_DONT_CLOSE, dont_close );
		item.put( KEY_WITH_ATTACHMENT, with_attachment );
		item.put( KEY_DONT_SHOW_BOOST, dont_show_boost );
		item.put( KEY_DONT_SHOW_REPLY, dont_show_reply );
		item.put( KEY_DONT_STREAMING, dont_streaming );
		item.put( KEY_DONT_AUTO_REFRESH, dont_auto_refresh );
		item.put( KEY_HIDE_MEDIA_DEFAULT, hide_media_default );
		item.put( KEY_ENABLE_SPEECH, enable_speech );
		
		item.put( KEY_REGEX_TEXT, regex_text );
		
		item.put( KEY_HEADER_BACKGROUND_COLOR, header_bg_color );
		item.put( KEY_HEADER_TEXT_COLOR, header_fg_color );
		item.put( KEY_COLUMN_BACKGROUND_COLOR, column_bg_color );
		item.put( KEY_COLUMN_BACKGROUND_IMAGE, column_bg_image );
		item.put( KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, (double) column_bg_image_alpha );
		
		switch( column_type ){
		case TYPE_CONVERSATION:
		case TYPE_BOOSTED_BY:
		case TYPE_FAVOURITED_BY:
			item.put( KEY_STATUS_ID, status_id );
			break;
		case TYPE_PROFILE:
			item.put( KEY_PROFILE_ID, profile_id );
			item.put( KEY_PROFILE_TAB, profile_tab );
			break;
		case TYPE_HASHTAG:
			item.put( KEY_HASHTAG, hashtag );
			break;
		case TYPE_SEARCH:
			item.put( KEY_SEARCH_QUERY, search_query );
			item.put( KEY_SEARCH_RESOLVE, search_resolve );
			break;
		}
		
		// 以下は保存には必要ないが、カラムリスト画面で使う
		AcctColor ac = AcctColor.load( access_info.acct );
		item.put( KEY_COLUMN_ACCESS, AcctColor.hasNickname( ac ) ? ac.nickname : access_info.acct );
		item.put( KEY_COLUMN_ACCESS_COLOR, AcctColor.hasColorForeground( ac ) ? ac.color_fg : 0 );
		item.put( KEY_COLUMN_ACCESS_COLOR_BG, AcctColor.hasColorBackground( ac ) ? ac.color_bg : 0 );
		item.put( KEY_COLUMN_NAME, getColumnName( true ) );
		item.put( KEY_OLD_INDEX, old_index );
	}
	
	Column( @NonNull AppState app_state, JSONObject src ){
		this.app_state = app_state;
		this.context = app_state.context;
		
		SavedAccount ac = SavedAccount.loadAccount( log, src.optLong( KEY_ACCOUNT_ROW_ID ) );
		if( ac == null ) throw new RuntimeException( "missing account" );
		this.access_info = ac;
		this.column_type = src.optInt( KEY_TYPE );
		this.dont_close = src.optBoolean( KEY_DONT_CLOSE );
		this.with_attachment = src.optBoolean( KEY_WITH_ATTACHMENT );
		this.dont_show_boost = src.optBoolean( KEY_DONT_SHOW_BOOST );
		this.dont_show_reply = src.optBoolean( KEY_DONT_SHOW_REPLY );
		this.dont_streaming = src.optBoolean( KEY_DONT_STREAMING );
		this.dont_auto_refresh = src.optBoolean( KEY_DONT_AUTO_REFRESH );
		this.hide_media_default = src.optBoolean( KEY_HIDE_MEDIA_DEFAULT );
		this.enable_speech = src.optBoolean( KEY_ENABLE_SPEECH );
		
		this.regex_text = Utils.optStringX( src, KEY_REGEX_TEXT );
		
		this.header_bg_color = src.optInt( KEY_HEADER_BACKGROUND_COLOR );
		this.header_fg_color = src.optInt( KEY_HEADER_TEXT_COLOR );
		this.column_bg_color = src.optInt( KEY_COLUMN_BACKGROUND_COLOR );
		this.column_bg_image = Utils.optStringX( src, KEY_COLUMN_BACKGROUND_IMAGE );
		this.column_bg_image_alpha = (float) src.optDouble( KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1.0f );
		
		switch( column_type ){
		
		case TYPE_CONVERSATION:
		case TYPE_BOOSTED_BY:
		case TYPE_FAVOURITED_BY:
			this.status_id = src.optLong( KEY_STATUS_ID );
			break;
		
		case TYPE_PROFILE:
			this.profile_id = src.optLong( KEY_PROFILE_ID );
			this.profile_tab = src.optInt( KEY_PROFILE_TAB );
			break;
		
		case TYPE_HASHTAG:
			this.hashtag = src.optString( KEY_HASHTAG );
			break;
		
		case TYPE_SEARCH:
			this.search_query = src.optString( KEY_SEARCH_QUERY );
			this.search_resolve = src.optBoolean( KEY_SEARCH_RESOLVE, false );
			break;
			
		}
		init();
	}
	
	boolean isSameSpec( SavedAccount ai, int type, Object[] params ){
		if( type != column_type || ! Utils.equalsNullable( ai.acct, access_info.acct ) )
			return false;
		switch( type ){
		default:
			return true;
		
		case TYPE_PROFILE:
			try{
				long who_id = (Long) getParamAt( params, 0 );
				return who_id == this.profile_id;
			}catch( Throwable ex ){
				return false;
			}
		
		case TYPE_CONVERSATION:
		case TYPE_BOOSTED_BY:
		case TYPE_FAVOURITED_BY:
			try{
				long status_id = (Long) getParamAt( params, 0 );
				return status_id == this.status_id;
			}catch( Throwable ex ){
				return false;
			}
		
		case TYPE_HASHTAG:
			try{
				String hashtag = (String) getParamAt( params, 0 );
				return Utils.equalsNullable( hashtag, this.hashtag );
			}catch( Throwable ex ){
				return false;
			}
		
		case TYPE_SEARCH:
			try{
				String q = (String) getParamAt( params, 0 );
				boolean r = (Boolean) getParamAt( params, 1 );
				return Utils.equalsNullable( q, this.search_query )
					&& r == this.search_resolve;
			}catch( Throwable ex ){
				return false;
			}
			
		}
	}
	
	final AtomicBoolean is_dispose = new AtomicBoolean();
	
	void dispose(){
		is_dispose.set( true );
		stopStreaming();
		
		for( ColumnViewHolder vh : _holder_list ){
			try{
				vh.getListView().setAdapter( null );
			}catch( Throwable ignored ){
			}
		}
	}
	
	String getColumnName( boolean bLong ){
		switch( column_type ){
		default:
			return getColumnTypeName( context, column_type );
		
		case TYPE_PROFILE:
			
			return context.getString( R.string.profile_of
				, who_account != null ? AcctColor.getNickname( access_info.getFullAcct( who_account ) ) : Long.toString( profile_id )
			);
		
		case TYPE_CONVERSATION:
			return context.getString( R.string.conversation_around, status_id );
		
		case TYPE_HASHTAG:
			return context.getString( R.string.hashtag_of, hashtag );
		
		case TYPE_SEARCH:
			if( bLong ){
				return context.getString( R.string.search_of, search_query );
			}else{
				return getColumnTypeName( context, column_type );
			}
			
		}
	}
	
	static String getColumnTypeName( Context context, int type ){
		switch( type ){
		default:
			return "?";
		
		case TYPE_HOME:
			return context.getString( R.string.home );
		
		case TYPE_LOCAL:
			return context.getString( R.string.local_timeline );
		
		case TYPE_FEDERATE:
			return context.getString( R.string.federate_timeline );
		
		case TYPE_PROFILE:
			
			return context.getString( R.string.profile );
		
		case TYPE_FAVOURITES:
			return context.getString( R.string.favourites );
		
		case TYPE_REPORTS:
			return context.getString( R.string.reports );
		
		case TYPE_NOTIFICATIONS:
			return context.getString( R.string.notifications );
		
		case TYPE_CONVERSATION:
			return context.getString( R.string.conversation );
		
		case TYPE_BOOSTED_BY:
			return context.getString( R.string.boosted_by );
		
		case TYPE_FAVOURITED_BY:
			return context.getString( R.string.favourited_by );
		
		case TYPE_HASHTAG:
			return context.getString( R.string.hashtag );
		
		case TYPE_MUTES:
			return context.getString( R.string.muted_users );
		
		case TYPE_BLOCKS:
			return context.getString( R.string.blocked_users );

		case TYPE_DOMAIN_BLOCKS:
			return context.getString( R.string.blocked_domains );
		
		case TYPE_SEARCH:
			return context.getString( R.string.search );
		
		case TYPE_FOLLOW_REQUESTS:
			return context.getString( R.string.follow_requests );
		}
	}
	
	static int getIconAttrId( int type ){
		switch( type ){
		
		default:
		case TYPE_REPORTS:
			return R.attr.ic_info;
		
		case TYPE_HOME:
			return R.attr.btn_home;
		
		case TYPE_LOCAL:
			return R.attr.btn_local_tl;
		
		case TYPE_FEDERATE:
			return R.attr.btn_federate_tl;
		
		case TYPE_PROFILE:
			return R.attr.btn_statuses;
		
		case TYPE_FAVOURITES:
			return R.attr.btn_favourite;
		
		case TYPE_NOTIFICATIONS:
			return R.attr.btn_notification;
		
		case TYPE_CONVERSATION:
			return R.attr.ic_conversation;
		
		case TYPE_BOOSTED_BY:
			return R.attr.btn_boost;
		
		case TYPE_FAVOURITED_BY:
			return R.attr.btn_favourite;
		
		case TYPE_HASHTAG:
			return R.attr.ic_hashtag;
		
		case TYPE_MUTES:
			return R.attr.ic_mute;
		
		case TYPE_BLOCKS:
			return R.attr.ic_block;

		case TYPE_DOMAIN_BLOCKS:
			return R.attr.ic_domain_block;
		
		case TYPE_SEARCH:
			return R.attr.ic_search;
		
		case TYPE_FOLLOW_REQUESTS:
			return R.attr.ic_account_add;
		}
	}
	
	boolean bFirstInitialized = false;
	
	private void init(){
	}
	
	interface StatusEntryCallback {
		boolean onIterate( SavedAccount account,TootStatus status );
	}
	
	// ブーストやお気に入りの更新に使う。ステータスを列挙する。
	void findStatus( String target_instance, long target_status_id, StatusEntryCallback callback ){
		if( access_info.host.equalsIgnoreCase( target_instance ) ){
			boolean bChanged = false;
			for( Object data: list_data ){
				//
				if( data instanceof TootNotification ){
					data = ( (TootNotification) data ).status;
				}
				//
				if( data instanceof TootStatus ){
					//
					TootStatus status = (TootStatus) data;
					if( target_status_id == status.id ){
						if(callback.onIterate( access_info,status )){
							bChanged = true;
						}
					}
					//
					TootStatus reblog = status.reblog;
					if( reblog != null && target_status_id == reblog.id ){
						if(callback.onIterate( access_info,reblog )){
							bChanged = true;
						}
					}
				}
			}
			if(bChanged){
				fireShowContent();
			}
		}
	}
	
	// ミュート、ブロックが成功した時に呼ばれる
	void removeStatusByAccount( SavedAccount target_account, long who_id ){
		if( ! target_account.acct.equals( access_info.acct ) ) return;
		
		ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
		for( Object o : list_data ){
			if( o instanceof TootStatus ){
				TootStatus item = (TootStatus) o;
				if( item.account.id == who_id
					|| ( item.reblog != null && item.reblog.account.id == who_id )
					){
					continue;
				}
			}
			if( o instanceof TootNotification ){
				TootNotification item = (TootNotification) o;
				if( item.account.id == who_id ) continue;
				if( item.status != null ){
					if( item.status.account.id == who_id ) continue;
					if( item.status.reblog != null && item.status.reblog.account.id == who_id )
						continue;
				}
			}
			
			tmp_list.add( o );
		}
		if( tmp_list.size() != list_data.size() ){
			list_data.clear();
			list_data.addAll( tmp_list );
			fireShowContent();
			
		}
	}
	
	// ミュート解除が成功した時に呼ばれる
	void removeFromMuteList( SavedAccount target_account, long who_id ){
		if( ! target_account.acct.equals( access_info.acct ) ) return;
		if( column_type != TYPE_MUTES ) return;
		
		ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
		for( Object o : list_data ){
			if( o instanceof TootAccount ){
				TootAccount item = (TootAccount) o;
				if( item.id == who_id ) continue;
			}
			
			tmp_list.add( o );
		}
		if( tmp_list.size() != list_data.size() ){
			list_data.clear();
			list_data.addAll( tmp_list );
			fireShowContent();
		}
	}
	
	// ブロック解除が成功したので、ブロックリストから削除する
	void removeFromBlockList( SavedAccount target_account, long who_id ){
		if( ! target_account.acct.equals( access_info.acct ) ) return;
		if( column_type != TYPE_BLOCKS ) return;
		
		ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
		for( Object o : list_data ){
			if( o instanceof TootAccount ){
				TootAccount item = (TootAccount) o;
				if( item.id == who_id ) continue;
			}
			tmp_list.add( o );
		}
		if( tmp_list.size() != list_data.size() ){
			list_data.clear();
			list_data.addAll( tmp_list );
			fireShowContent();
		}
	}
	
	void removeFollowRequest( SavedAccount target_account, long who_id ){
		if( ! target_account.acct.equals( access_info.acct ) ) return;
		
		if( column_type == TYPE_FOLLOW_REQUESTS ){
			ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
			for( Object o : list_data ){
				if( o instanceof TootAccount ){
					TootAccount item = (TootAccount) o;
					if( item.id == who_id ) continue;
				}
				tmp_list.add( o );
			}
			if( tmp_list.size() != list_data.size() ){
				list_data.clear();
				list_data.addAll( tmp_list );
				fireShowContent();
			}
		}else{
			// 他のカラムでもフォロー状態の表示更新が必要
			fireShowContent();
		}
	}
	
	// 自分のステータスを削除した時に呼ばれる
	void removeStatus( SavedAccount target_account, long status_id ){
		
		if( ! target_account.host.equals( access_info.host ) ) return;
		
		ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
		for( Object o : list_data ){
			if( o instanceof TootStatus ){
				TootStatus item = (TootStatus) o;
				if( item.id == status_id
					|| ( item.reblog != null && item.reblog.id == status_id )
					){
					continue;
				}
			}
			if( o instanceof TootNotification ){
				TootNotification item = (TootNotification) o;
				if( item.status != null ){
					if( item.status.id == status_id ) continue;
					if( item.status.reblog != null && item.status.reblog.id == status_id )
						continue;
				}
			}
			
			tmp_list.add( o );
		}
		if( tmp_list.size() != list_data.size() ){
			list_data.clear();
			list_data.addAll( tmp_list );
			fireShowContent();
		}
	}
	
	void removeNotifications(){
		cancelLastTask();
		
		list_data.clear();
		mRefreshLoadingError = null;
		bRefreshLoading = false;
		mInitialLoadingError = null;
		bInitialLoading = false;
		max_id = null;
		since_id = null;
		
		fireShowContent();
		
		AlarmService.dataRemoved( context, access_info.db_id );
	}
	
	void onDomainBlockChanged( SavedAccount target_account, String domain, boolean bBlocked ){
		if( ! target_account.host.equals( access_info.host ) ) return;
		if( access_info.isPseudo() ) return;
		
		if( column_type == TYPE_DOMAIN_BLOCKS ){
			// ドメインブロック一覧を読み直す
			startLoading();
			return;
		}
		
		if( bBlocked ){
			// ブロックしたのとドメイン部分が一致するアカウントからのステータスと通知をすべて除去する

			Pattern reDomain = Pattern.compile( "[^@]+@\\Q" + domain +"\\E\\z",Pattern.CASE_INSENSITIVE );
			
			ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
			
			for( Object o : list_data ){
				if( o instanceof TootStatus ){
					TootStatus item = (TootStatus) o;
					if( reDomain.matcher( item.account.acct).find() ) continue;
					if( item.reblog != null && reDomain.matcher( item.reblog.account.acct).find() ) continue;
				}else if( o instanceof TootNotification ){
					TootNotification item = (TootNotification) o;
					if( item.account != null ){
						if( reDomain.matcher( item.account.acct).find() ) continue;
					}
					if( item.status != null ){
						if( reDomain.matcher( item.status.account.acct).find() ) continue;
						if( item.status.reblog != null && reDomain.matcher( item.status.reblog.account.acct).find() ) continue;
					}
				}
				tmp_list.add( o );
			}
			if( tmp_list.size() != list_data.size() ){
				list_data.clear();
				list_data.addAll( tmp_list );
				fireShowContent();
			}

		}

	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	// カラムを閉じた後のnotifyDataSetChangedのタイミングで、add/removeされる順序が期待通りにならないので
	// 参照を１つだけ持つのではなく、リストを保持して先頭の要素を使うことにする
	private final LinkedList< ColumnViewHolder > _holder_list = new LinkedList<>();
	
	void addColumnViewHolder( @NonNull ColumnViewHolder cvh ){

		// 現在のリストにあるなら削除する
		removeColumnViewHolder( cvh );

		// 最後に追加されたものが先頭にくるようにする
		// 呼び出しの後に必ず追加されているようにする
		_holder_list.addFirst( cvh );
	}
	
	void removeColumnViewHolder( @NonNull ColumnViewHolder cvh ){
		for( Iterator< ColumnViewHolder > it = _holder_list.iterator() ; it.hasNext() ; ){
			if( cvh == it.next() ) it.remove();
		}
	}

	void removeAllColumnViewHolder(  ){
		_holder_list.clear();
	}
	
	boolean hasMultipleViewHolder(){
		return _holder_list.size() > 1;
	}
	
	private ColumnViewHolder getViewHolder(){
		if( is_dispose.get() ) return null;
		return _holder_list.isEmpty() ? null : _holder_list.getFirst();
	}
	
	void fireShowContent(){
		if( ! Utils.isMainThread() ){
			throw new RuntimeException( "fireShowColumnHeader: not on main thread." );
		}
		ColumnViewHolder holder = getViewHolder();
		if( holder != null ) holder.showContent();
	}
	
	void fireShowColumnHeader(){
		if( ! Utils.isMainThread() ){
			throw new RuntimeException( "fireShowColumnHeader: not on main thread." );
		}
		ColumnViewHolder holder = getViewHolder();
		if( holder != null ) holder.showColumnHeader();
	}
	
	void fireColumnColor(){
		if( ! Utils.isMainThread() ){
			throw new RuntimeException( "fireShowColumnHeader: not on main thread." );
		}
		ColumnViewHolder holder = getViewHolder();
		if( holder != null ) holder.showColumnColor();
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	private AsyncTask< Void, Void, TootApiResult > last_task;
	
	private void cancelLastTask(){
		if( last_task != null ){
			last_task.cancel( true );
			last_task = null;
			//
			bInitialLoading = false;
			bRefreshLoading = false;
			mInitialLoadingError = context.getString( R.string.cancelled );
			//
		}
	}
	
	boolean bInitialLoading;
	boolean bRefreshLoading;
	
	String mInitialLoadingError;
	String mRefreshLoadingError;
	
	String task_progress;
	
	final BucketList< Object > list_data = new BucketList<>();
	private final DuplicateMap duplicate_map = new DuplicateMap();
	
	private static boolean hasMedia( TootStatus status ){
		if( status == null ) return false;
		TootAttachment.List list = status.media_attachments;
		return ! ( list == null || list.isEmpty() );
	}
	
	private boolean isFiltered(){
		return ( with_attachment
			|| dont_show_boost
			|| dont_show_reply
			|| ! TextUtils.isEmpty( regex_text )
		);
	}
	
	void removeMuteApp(){
		ArrayList< Object > tmp_list = new ArrayList<>( list_data.size() );
		
		HashSet< String > muted_app = MutedApp.getNameSet();
		WordTrieTree muted_word = MutedWord.getNameSet();
		
		for( Object o : list_data ){
			if( o instanceof TootStatus ){
				TootStatus item = (TootStatus) o;
				if( item.checkMuted( muted_app, muted_word ) ){
					continue;
					
				}
			}
			if( o instanceof TootNotification ){
				TootNotification item = (TootNotification) o;
				TootStatus status = item.status;
				
				if( status != null ){
					if( status.checkMuted( muted_app, muted_word ) ){
						continue;
					}
				}
			}
			tmp_list.add( o );
		}
		if( tmp_list.size() != list_data.size() ){
			list_data.clear();
			list_data.addAll( tmp_list );
			fireShowContent();
		}
	}
	
	private Pattern column_regex_filter;
	private HashSet< String > muted_app;
	private WordTrieTree muted_word;
	
	private void initFilter(){
		column_regex_filter = null;
		if( ! TextUtils.isEmpty( regex_text ) ){
			try{
				column_regex_filter = Pattern.compile( regex_text );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		
		muted_app = MutedApp.getNameSet();
		muted_word = MutedWord.getNameSet();
	}
	
	private boolean isFiltered( TootStatus status ){
		if( with_attachment ){
			if( ! hasMedia( status ) && ! hasMedia( status.reblog ) ) return true;
		}
		
		if( dont_show_boost ){
			if( status.reblog != null ) return true;
		}
		
		if( dont_show_reply ){
			if( status.in_reply_to_id != null
				|| ( status.reblog != null && status.reblog.in_reply_to_id != null )
				) return true;
		}
		
		if( column_regex_filter != null ){
			if( status.reblog != null ){
				if( column_regex_filter.matcher( status.reblog.decoded_content.toString() ).find() )
					return true;
			}else{
				if( column_regex_filter.matcher( status.decoded_content.toString() ).find() )
					return true;
			}
		}
		
		//noinspection RedundantIfStatement
		if( status.checkMuted( muted_app, muted_word ) ){
			return true;
		}
		
		return false;
	}
	
	@SuppressWarnings("ConstantConditions")
	private void addWithFilter( ArrayList< Object > dst, TootStatus.List src ){
		for( TootStatus status : src ){
			if( ! isFiltered( status ) ){
				dst.add( status );
			}
		}
	}
	
	private boolean isFiltered( TootNotification item ){
		
		TootStatus status = item.status;
		if( status != null ){
			if( status.checkMuted( muted_app, muted_word ) ){
				log.d( "addWithFilter: status muted." );
				return true;
			}
		}
		return false;
	}
	
	@SuppressWarnings("ConstantConditions")
	private void addWithFilter( ArrayList< Object > dst, TootNotification.List src ){
		
		for( TootNotification item : src ){
			if( ! isFiltered( item ) ){
				dst.add( item );
			}
		}
	}
	
	void startLoading(){
		cancelLastTask();
		
		stopStreaming();
		
		initFilter();
		
		mRefreshLoadingError = null;
		mInitialLoadingError = null;
		bFirstInitialized = true;
		bInitialLoading = true;
		bRefreshLoading = false;
		max_id = null;
		since_id = null;
		
		duplicate_map.clear();
		list_data.clear();
		fireShowContent();
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootApiResult parseAccount1( TootApiClient client, String path_base ){
				TootApiResult result = client.request( path_base );
				if( result != null && result.object != null ){
					Column.this.who_account = TootAccount.parse( log, access_info, result.object );
				}
				return result;
			}
			
			ArrayList< Object > list_tmp;
			
			TootApiResult getStatuses( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				TootApiResult result = client.request( path_base );
				if( result != null && result.array != null ){
					saveRange( result, true, true );
					//
					TootStatus.List src = TootStatus.parseList( log, access_info, result.array );
					list_tmp = new ArrayList<>( src.size() );
					addWithFilter( list_tmp, src );
					//
					char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
					for( ; ; ){
						if( client.isCancelled() ){
							log.d( "loading-statuses: cancelled." );
							break;
						}
						if( ! isFiltered() ){
							log.d( "loading-statuses: isFiltered is false." );
							break;
						}
						if( max_id == null ){
							log.d( "loading-statuses: max_id is null." );
							break;
						}
						if( list_tmp.size() >= LOOP_READ_ENOUGH ){
							log.d( "loading-statuses: read enough data." );
							break;
						}
						if( src.isEmpty() ){
							log.d( "loading-statuses: previous response is empty." );
							break;
						}
						if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
							log.d( "loading-statuses: timeout." );
							break;
						}
						String path = path_base + delimiter + "max_id=" + max_id;
						TootApiResult result2 = client.request( path );
						if( result2 == null || result2.array == null ){
							log.d( "loading-statuses: error or cancelled." );
							break;
						}
						
						src = TootStatus.parseList( log, access_info, result2.array );
						
						addWithFilter( list_tmp, src );
						
						if( ! saveRangeEnd( result2 ) ){
							log.d( "loading-statuses: missing range info." );
							break;
						}
					}
				}
				return result;
			}
			
			TootApiResult parseAccountList( TootApiClient client, String path_base ){
				TootApiResult result = client.request( path_base );
				if( result != null ){
					saveRange( result, true, true );
					list_tmp = new ArrayList<>();
					list_tmp.addAll( TootAccount.parseList( log, access_info, result.array ) );
				}
				return result;
			}
			
			TootApiResult parseDomainList( TootApiClient client, String path_base ){
				TootApiResult result = client.request( path_base );
				if( result != null ){
					saveRange( result, true, true );
					list_tmp = new ArrayList<>();
					list_tmp.addAll( TootDomainBlock.parseList( log, result.array ) );
				}
				return result;
			}
			
			TootApiResult parseReports( TootApiClient client, String path_base ){
				TootApiResult result = client.request( path_base );
				if( result != null ){
					saveRange( result, true, true );
					list_tmp = new ArrayList<>();
					list_tmp.addAll( TootReport.parseList( log, result.array ) );
				}
				return result;
			}
			
			TootApiResult parseNotifications( TootApiClient client, String path_base ){
				TootApiResult result = client.request( path_base );
				if( result != null ){
					saveRange( result, true, true );
					TootNotification.List src = TootNotification.parseList( log, access_info, result.array );
					
					list_tmp = new ArrayList<>();
					addWithFilter( list_tmp, src );
					//
					if( ! src.isEmpty() ){
						AlarmService.injectData( context, access_info.db_id, src );
					}
					
				}
				return result;
			}
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( context, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled() || is_dispose.get();
					}
					
					@Override
					public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override
							public void run(){
								if( isCancelled() ) return;
								task_progress = s;
								fireShowContent();
							}
						} );
					}
				} );
				
				client.setAccount( access_info );
				
				try{
					TootApiResult result;
					
					switch( column_type ){
					
					default:
					case TYPE_HOME:
						return getStatuses( client, PATH_HOME );
					
					case TYPE_LOCAL:
						return getStatuses( client, PATH_LOCAL );
					
					case TYPE_FEDERATE:
						return getStatuses( client, PATH_FEDERATE );
					
					case TYPE_PROFILE:
						if( who_account == null ){
							parseAccount1( client, String.format( Locale.JAPAN, PATH_ACCOUNT, profile_id ) );
							client.callback.publishApiProgress( "" );
						}
						switch( profile_tab ){
						
						default:
						case TAB_STATUS:
							if( access_info.isPseudo() ){
								return client.request( PATH_INSTANCE );
								
							}else{
								String s = String.format( Locale.JAPAN, PATH_ACCOUNT_STATUSES, profile_id );
								if( with_attachment ) s = s + "&only_media=1";
								return getStatuses( client, s );
								
							}
						
						case TAB_FOLLOWING:
							return parseAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id ) );
						
						case TAB_FOLLOWERS:
							return parseAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id ) );
							
						}
					
					case TYPE_MUTES:
						return parseAccountList( client, PATH_MUTES );
					
					case TYPE_BLOCKS:
						return parseAccountList( client, PATH_BLOCKS );

					case TYPE_DOMAIN_BLOCKS:
						return parseDomainList( client, PATH_DOMAIN_BLOCK );
					
					case TYPE_FOLLOW_REQUESTS:
						return parseAccountList( client, PATH_FOLLOW_REQUESTS );
					
					case TYPE_FAVOURITES:
						return getStatuses( client, PATH_FAVOURITES );
					
					case TYPE_HASHTAG:
						return getStatuses( client,
							String.format( Locale.JAPAN, PATH_HASHTAG, Uri.encode( hashtag ) ) );
					
					case TYPE_REPORTS:
						return parseReports( client, PATH_REPORTS );
					
					case TYPE_NOTIFICATIONS:
						return parseNotifications( client, PATH_NOTIFICATIONS );
					
					case TYPE_BOOSTED_BY:
						return parseAccountList( client, String.format( Locale.JAPAN, PATH_BOOSTED_BY, status_id ) );
					
					case TYPE_FAVOURITED_BY:
						return parseAccountList( client, String.format( Locale.JAPAN, PATH_FAVOURITED_BY, status_id ) );
					
					case TYPE_CONVERSATION:
						
						// 指定された発言そのもの
						result = client.request(
							String.format( Locale.JAPAN, PATH_STATUSES, status_id ) );
						if( result == null || result.object == null ) return result;
						TootStatus target_status = TootStatus.parse( log, access_info, result.object );
						target_status.conversation_main = true;
						
						// 前後の会話
						result = client.request(
							String.format( Locale.JAPAN, PATH_STATUSES_CONTEXT, status_id ) );
						if( result == null || result.object == null ) return result;
						
						// 一つのリストにまとめる
						TootContext context = TootContext.parse( log, access_info, result.object );
						list_tmp = new ArrayList<>( 1 + context.ancestors.size() + context.descendants.size() );
						if( context.ancestors != null )
							addWithFilter( list_tmp, context.ancestors );
						list_tmp.add( target_status );
						if( context.descendants != null )
							addWithFilter( list_tmp, context.descendants );
						
						//
						return result;
					
					case TYPE_SEARCH:
						String path = String.format( Locale.JAPAN, PATH_SEARCH, Uri.encode( search_query ) );
						if( search_resolve ) path = path + "&resolve=1";
						
						result = client.request( path );
						if( result == null || result.object == null ) return result;
						
						TootResults tmp = TootResults.parse( log, access_info, result.object );
						if( tmp != null ){
							list_tmp = new ArrayList<>();
							list_tmp.addAll( tmp.hashtags );
							list_tmp.addAll( tmp.accounts );
							list_tmp.addAll( tmp.statuses );
						}
						return result;
						
					}
				}finally{
					try{
						updateRelation( client, list_tmp );
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( is_dispose.get() ) return;
				
				if( isCancelled() || result == null ){
					return;
				}
				
				bInitialLoading = false;
				last_task = null;
				
				if( result.error != null ){
					Column.this.mInitialLoadingError = result.error;
				}else{
					if( list_tmp != null ){
						ArrayList< Object > list_new = duplicate_map.filterDuplicate( list_tmp );
						list_data.clear();
						list_data.addAll( list_new );
					}
					
					resumeStreaming( false );
				}
				fireShowContent();
				
				// 初期ロードの直後は先頭に移動する
				try{
					ColumnViewHolder holder = getViewHolder();
					if( holder != null ) holder.getListView().setSelection( 0 );
				}catch( Throwable ignored ){
				}
			}
		};
		
		task.executeOnExecutor( App1.task_executor );
	}
	
	private static final Pattern reMaxId = Pattern.compile( "&max_id=(\\d+)" ); // より古いデータの取得に使う
	private static final Pattern reSinceId = Pattern.compile( "&since_id=(\\d+)" ); // より新しいデータの取得に使う
	
	private String max_id;
	private String since_id;
	// int scroll_hack;
	
	private void saveRange( TootApiResult result, boolean bBottom, boolean bTop ){
		if( result != null ){
			if( bBottom && result.link_older != null ){
				Matcher m = reMaxId.matcher( result.link_older );
				if( m.find() ) max_id = m.group( 1 );
			}
			if( bTop && result.link_newer != null ){
				Matcher m = reSinceId.matcher( result.link_newer );
				if( m.find() ) since_id = m.group( 1 );
			}
		}
	}
	
	private boolean saveRangeEnd( TootApiResult result ){
		if( result != null ){
			if( result.link_older != null ){
				Matcher m = reMaxId.matcher( result.link_older );
				if( m.find() ){
					max_id = m.group( 1 );
					return true;
				}
			}
		}
		return false;
	}
	
	private String addRange( boolean bBottom, String path ){
		char delimiter = ( - 1 != path.indexOf( '?' ) ? '&' : '?' );
		if( bBottom ){
			if( max_id != null ) return path + delimiter + "max_id=" + max_id;
		}else{
			if( since_id != null ) return path + delimiter + "since_id=" + since_id;
		}
		return path;
	}
	
	private void updateRelation( TootApiClient client, ArrayList< Object > list_tmp ){
		if( list_tmp == null || list_tmp.isEmpty() ) return;
		if( access_info.isPseudo() ) return;
		
		HashSet< Long > who_set = new HashSet<>();
		HashSet< String > acct_set = new HashSet<>();
		HashSet< String > tag_set = new HashSet<>();
		{
			TootAccount a;
			TootStatus s;
			TootNotification n;
			for( Object o : list_tmp ){
				if( o instanceof TootAccount ){
					a = (TootAccount) o;
					who_set.add( a.id );
					acct_set.add( "@" + access_info.getFullAcct( a ) );
				}else if( o instanceof TootStatus ){
					s = (TootStatus) o;
					if( s.tags != null ){
						for(TootTag tag : s.tags){
							tag_set.add( tag.name);
						}
					}
					a = s.account;
					if( a != null ){
						who_set.add( a.id );
						acct_set.add( "@" + access_info.getFullAcct( a ) );
					}
					s = s.reblog;
					if( s != null ){

						if( s.tags != null ){
							for(TootTag tag : s.tags){
								tag_set.add( tag.name);
							}
						}

						a = s.account;
						if( a != null ){
							who_set.add( a.id );
							acct_set.add( "@" + access_info.getFullAcct( a ) );
						}
					}
				}else if( o instanceof TootNotification ){
					n = (TootNotification) o;
					//
					a = n.account;
					if( a != null ){
						who_set.add( a.id );
						acct_set.add( "@" + access_info.getFullAcct( a ) );
					}
					//
					s = n.status;
					if( s != null ){
						
						if( s.tags != null ){
							for(TootTag tag : s.tags){
								tag_set.add( tag.name);
							}
						}
						
						a = s.account;
						if( a != null ){
							who_set.add( a.id );
							acct_set.add( "@" + access_info.getFullAcct( a ) );
						}
						s = s.reblog;
						if( s != null ){
							
							if( s.tags != null ){
								for(TootTag tag : s.tags){
									tag_set.add( tag.name);
								}
							}
							
							a = s.account;
							if( a != null ){
								who_set.add( a.id );
								acct_set.add( "@" + access_info.getFullAcct( a ) );
							}
						}
					}
				}
			}
		}
		int size = who_set.size();
		if( size > 0 ){
			long[] who_list = new long[ size ];
			{
				int n = 0;
				for( Long l : who_set ){
					who_list[ n++ ] = l;
				}
			}
			
			long now = System.currentTimeMillis();
			int n = 0;
			while( n < size ){
				StringBuilder sb = new StringBuilder();
				sb.append( "/api/v1/accounts/relationships" );
				for( int i = 0 ; i < RELATIONSHIP_LOAD_STEP ; ++ i ){
					if( n >= size ) break;
					sb.append( i == 0 ? '?' : '&' );
					sb.append( "id[]=" );
					sb.append( Long.toString( who_list[ n++ ] ) );
				}
				TootApiResult result = client.request( sb.toString() );
				if( result == null ){
					// cancelled.
					break;
				}else if( result.array != null ){
					TootRelationShip.List list = TootRelationShip.parseList( log, result.array );
					UserRelation.saveList( now, access_info.db_id, list );
				}
			}
			log.d( "updateRelation: update %d relations.", n );
			
		}
		size = acct_set.size();
		if( size > 0 ){
			String[] acct_list = new String[ size ];
			{
				int n = 0;
				for( String l : acct_set ){
					acct_list[ n++ ] = l;
				}
			}
			long now = System.currentTimeMillis();
			int n = 0;
			while( n < size ){
				int length = size - n;
				if( length > ACCT_DB_STEP ) length = ACCT_DB_STEP;
				AcctSet.saveList( now, acct_list, n, length );
				n += length;
			}
			log.d( "updateRelation: update %d acct.", n );
			
		}
		size = tag_set.size();
		if( size > 0 ){
			String[] tag_list = new String[ size ];
			{
				int n = 0;
				for( String l : tag_set ){
					tag_list[ n++ ] = l;
				}
			}
			long now = System.currentTimeMillis();
			int n = 0;
			while( n < size ){
				int length = size - n;
				if( length > ACCT_DB_STEP ) length = ACCT_DB_STEP;
				TagSet.saveList( now, tag_list, n, length );
				n += length;
			}
			log.d( "updateRelation: update %d tag.", n );
			
		}
	}
	
	void startRefreshForPost( long status_id, int refresh_after_toot ){
		switch( column_type ){
		case TYPE_HOME:
		case TYPE_LOCAL:
		case TYPE_FEDERATE:
			startRefresh( true, false, status_id, refresh_after_toot );
			break;
		
		case TYPE_PROFILE:
			if( profile_tab == TAB_STATUS && profile_id == access_info.id ){
				startRefresh( true, false, status_id, refresh_after_toot );
			}
			break;
		
		case TYPE_CONVERSATION:
			startLoading();
			break;
		}
	}
	
	private boolean bRefreshingTop;
	
	void startRefresh( final boolean bSilent, final boolean bBottom, final long status_id, final int refresh_after_toot ){
		
		if( last_task != null ){
			if( ! bSilent ){
				Utils.showToast( context, true, R.string.column_is_busy );
				ColumnViewHolder holder = getViewHolder();
				if( holder != null ) holder.getRefreshLayout().setRefreshing( false );
			}
			return;
		}else if( bBottom && max_id == null ){
			if( ! bSilent ){
				Utils.showToast( context, true, R.string.end_of_list );
				ColumnViewHolder holder = getViewHolder();
				if( holder != null ) holder.getRefreshLayout().setRefreshing( false );
			}
			return;
		}else if( ! bBottom && since_id == null ){
			ColumnViewHolder holder = getViewHolder();
			if( holder != null ) holder.getRefreshLayout().setRefreshing( false );
			startLoading();
			return;
		}
		
		if( bSilent ){
			ColumnViewHolder holder = getViewHolder();
			if( holder != null ){
				holder.getRefreshLayout().setRefreshing( true );
			}
		}
		
		if( ! bBottom ){
			bRefreshingTop = true;
			stopStreaming();
		}
		
		bRefreshLoading = true;
		mRefreshLoadingError = null;
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootApiResult parseAccount1( TootApiResult result ){
				if( result != null ){
					who_account = TootAccount.parse( log, access_info, result.object );
				}
				return result;
			}
			
			TootApiResult getAccountList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				String last_since_id = since_id;
				
				TootApiResult result = client.request( addRange( bBottom, path_base ) );
				if( result != null && result.array != null ){
					saveRange( result, bBottom, ! bBottom );
					list_tmp = new ArrayList<>();
					TootAccount.List src = TootAccount.parseList( log, access_info, result.array );
					list_tmp.addAll( src );
					if( ! bBottom ){
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-account-top: cancelled." );
								break;
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-account-top: previous size == 0." );
								break;
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							String max_id = Long.toString( src.get( src.size() - 1 ).id );
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								log.d( "refresh-account-top: timeout. make gap." );
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-account-top: error or cancelled. make gap." );
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							src = TootAccount.parseList( log, access_info, result2.array );
							list_tmp.addAll( src );
						}
					}
				}
				return result;
			}
			
			
			TootApiResult getDomainList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				String last_since_id = since_id;
				
				TootApiResult result = client.request( addRange( bBottom, path_base ) );
				if( result != null && result.array != null ){
					saveRange( result, bBottom, ! bBottom );
					list_tmp = new ArrayList<>();
					TootDomainBlock.List src = TootDomainBlock.parseList( log, result.array );
					list_tmp.addAll( src );
					if( ! bBottom ){
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-domain-top: cancelled." );
								break;
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-domain-top: previous size == 0." );
								break;
							}
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								log.d( "refresh-domain-top: timeout." );
								// タイムアウト
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-domain-top: error or cancelled." );
								// エラー
								break;
							}
							
							src = TootDomainBlock.parseList( log, result2.array );
							list_tmp.addAll( src );
						}
					}
				}
				return result;
			}
			
			
			TootApiResult getReportList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				String last_since_id = since_id;
				TootApiResult result = client.request( addRange( bBottom, path_base ) );
				if( result != null && result.array != null ){
					saveRange( result, bBottom, ! bBottom );
					list_tmp = new ArrayList<>();
					TootReport.List src = TootReport.parseList( log, result.array );
					list_tmp.addAll( src );
					if( ! bBottom ){
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-report-top: cancelled." );
								break;
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-report-top: previous size == 0." );
								break;
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							String max_id = Long.toString( src.get( src.size() - 1 ).id );
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								log.d( "refresh-report-top: timeout. make gap." );
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-report-top: timeout. error or retry. make gap." );
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							src = TootReport.parseList( log, result2.array );
							list_tmp.addAll( src );
						}
					}
				}
				return result;
			}
			
			TootApiResult getNotificationList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				String last_since_id = since_id;
				
				TootApiResult result = client.request( addRange( bBottom, path_base ) );
				if( result != null && result.array != null ){
					saveRange( result, bBottom, ! bBottom );
					list_tmp = new ArrayList<>();
					TootNotification.List src = TootNotification.parseList( log, access_info, result.array );
					addWithFilter( list_tmp, src );
					
					if( ! src.isEmpty() ){
						AlarmService.injectData( context, access_info.db_id, src );
					}
					
					if( ! bBottom ){
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-notification-top: cancelled." );
								break;
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-notification-top: previous size == 0." );
								break;
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							String max_id = Long.toString( src.get( src.size() - 1 ).id );
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								log.d( "refresh-notification-top: timeout. make gap." );
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-notification-top: error or cancelled. make gap." );
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							src = TootNotification.parseList( log, access_info, result2.array );
							if( ! src.isEmpty() ){
								addWithFilter( list_tmp, src );
								AlarmService.injectData( context, access_info.db_id, src );
							}
						}
					}
				}
				return result;
			}
			
			ArrayList< Object > list_tmp;
			
			TootApiResult getStatusList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				final String last_since_id = since_id;
				
				TootApiResult result = client.request( addRange( bBottom, path_base ) );
				if( result != null && result.array != null ){
					saveRange( result, bBottom, ! bBottom );
					TootStatus.List src = TootStatus.parseList( log, access_info, result.array );
					list_tmp = new ArrayList<>();
					
					addWithFilter( list_tmp, src );
					
					if( bBottom ){
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-status-bottom: cancelled." );
								break;
							}
							
							// bottomの場合、フィルタなしなら繰り返さない
							if( ! isFiltered() ){
								log.d( "refresh-status-bottom: isFiltered is false." );
								break;
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-status-bottom: previous size == 0." );
								break;
							}
							
							// 十分読んだらそれで終了
							if( list_tmp.size() >= LOOP_READ_ENOUGH ){
								log.d( "refresh-status-bottom: read enough data." );
								break;
							}
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								// タイムアウト
								log.d( "refresh-status-bottom: loop timeout." );
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-status-bottom: error or cancelled." );
								break;
							}
							
							src = TootStatus.parseList( log, access_info, result2.array );
							
							addWithFilter( list_tmp, src );
							
							if( ! saveRangeEnd( result2 ) ){
								log.d( "refresh-status-bottom: saveRangeEnd failed." );
								break;
							}
						}
					}else{
						for( ; ; ){
							if( isCancelled() ){
								log.d( "refresh-status-top: cancelled." );
								break;
							}
							
							// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if( src.isEmpty() ){
								log.d( "refresh-status-top: previous size == 0." );
								break;
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							String max_id = Long.toString( src.get( src.size() - 1 ).id );
							
							if( list_tmp.size() >= LOOP_READ_ENOUGH ){
								log.d( "refresh-status-top: read enough. make gap." );
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							if( SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
								log.d( "refresh-status-top: timeout. make gap." );
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id;
							TootApiResult result2 = client.request( path );
							if( result2 == null || result2.array == null ){
								log.d( "refresh-status-top: error or cancelled. make gap." );
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								TootGap gap = new TootGap( max_id, last_since_id );
								list_tmp.add( gap );
								break;
							}
							
							src = TootStatus.parseList( log, access_info, result2.array );
							addWithFilter( list_tmp, src );
						}
					}
				}
				return result;
			}
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( context, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled() || is_dispose.get();
					}
					
					@Override
					public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override
							public void run(){
								if( isCancelled() ) return;
								task_progress = s;
								fireShowContent();
							}
						} );
					}
				} );
				
				client.setAccount( access_info );
				try{
					
					switch( column_type ){
					
					default:
					case TYPE_HOME:
						return getStatusList( client, PATH_HOME );
					
					case TYPE_LOCAL:
						return getStatusList( client, PATH_LOCAL );
					
					case TYPE_FEDERATE:
						return getStatusList( client, PATH_FEDERATE );
					
					case TYPE_FAVOURITES:
						return getStatusList( client, PATH_FAVOURITES );
					
					case TYPE_REPORTS:
						return getReportList( client, PATH_REPORTS );
					
					case TYPE_NOTIFICATIONS:
						return getNotificationList( client, PATH_NOTIFICATIONS );
					
					case TYPE_BOOSTED_BY:
						return getAccountList( client, String.format( Locale.JAPAN, PATH_BOOSTED_BY, status_id ) );
					
					case TYPE_FAVOURITED_BY:
						return getAccountList( client, String.format( Locale.JAPAN, PATH_FAVOURITED_BY, status_id ) );
					
					case TYPE_PROFILE:
						if( who_account == null ){
							parseAccount1( client.request(
								String.format( Locale.JAPAN, PATH_ACCOUNT, profile_id ) ) );
							
							client.callback.publishApiProgress( "" );
						}
						switch( profile_tab ){
						
						default:
						case TAB_STATUS:
							if( access_info.isPseudo() ){
								return client.request( PATH_INSTANCE );
							}else{
								String s = String.format( Locale.JAPAN, PATH_ACCOUNT_STATUSES, profile_id );
								if( with_attachment ) s = s + "&only_media=1";
								return getStatusList( client, s );
							}
						case TAB_FOLLOWING:
							return getAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id ) );
						
						case TAB_FOLLOWERS:
							return getAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id ) );
							
						}
					case TYPE_MUTES:
						return getAccountList( client, PATH_MUTES );
					
					case TYPE_BLOCKS:
						return getAccountList( client, PATH_BLOCKS );
					
					case TYPE_DOMAIN_BLOCKS:
						return getDomainList( client, PATH_DOMAIN_BLOCK );
					
					case TYPE_FOLLOW_REQUESTS:
						return getAccountList( client, PATH_FOLLOW_REQUESTS );
					
					case TYPE_HASHTAG:
						return getStatusList( client,
							String.format( Locale.JAPAN, PATH_HASHTAG, Uri.encode( hashtag ) ) );
					}
				}finally{
					try{
						updateRelation( client, list_tmp );
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( is_dispose.get() ) return;
				
				if( isCancelled() || result == null ){
					return;
				}
				try{
					last_task = null;
					bRefreshLoading = false;
					
					if( result.error != null ){
						Column.this.mRefreshLoadingError = result.error;
						fireShowContent();
						return;
					}
					if( list_tmp == null || list_tmp.isEmpty() ){
						fireShowContent();
						return;
					}
					
					ArrayList< Object > list_new = duplicate_map.filterDuplicate( list_tmp );
					
					if( list_new.isEmpty() ){
						fireShowContent();
						return;
					}
					
					// 事前にスクロール位置を覚えておく
					ScrollPosition sp = null;
					ColumnViewHolder holder = getViewHolder();
					if( holder != null ){
						sp = holder.getScrollPosition();
					}
					
					if( bBottom ){
						list_data.addAll( list_new );
						fireShowContent();
						
						if( sp != null ){
							holder.setScrollPosition( sp, 20f );
						}
					}else{
						
						int status_index = - 1;
						for( int i = 0, ie = list_new.size() ; i < ie ; ++ i ){
							Object o = list_new.get( i );
							if( o instanceof TootStatus ){
								TootStatus status = (TootStatus) o;
								if( status.id == status_id ){
									status_index = i;
									break;
								}
							}
						}
						
						int added = list_new.size();
						list_data.addAll( 0, list_new );
						fireShowContent();
						
						if( status_index >= 0 && refresh_after_toot == Pref.RAT_REFRESH_SCROLL ){
							if( holder != null ){
								holder.setScrollPosition( new ScrollPosition( status_index, 0 ), 0f );
							}else{
								scroll_save = new ScrollPosition( status_index, 0 );
							}
						}else{
							float delta = bSilent ? 0f : - 20f;
							if( sp != null ){
								sp.pos += added;
								holder.setScrollPosition( sp, delta );
							}else if( scroll_save != null ){
								scroll_save.pos += added;
							}else{
								scroll_save = new ScrollPosition( added, 0 );
							}
						}
					}
				}finally{
					if( ! bBottom ){
						bRefreshingTop = false;
						resumeStreaming( false );
					}
				}
			}
		};
		
		task.executeOnExecutor( App1.task_executor );
	}
	
	void startGap( final TootGap gap ){
		if( gap== null ){
			Utils.showToast( context, true, "gap is null");
			return;
		}
		if( last_task != null ){
			Utils.showToast( context, true, R.string.column_is_busy );
			return;
		}
		
		ColumnViewHolder holder = getViewHolder();
		if( holder != null ){
			holder.getRefreshLayout().setRefreshing( true );
		}
		
		bRefreshLoading = true;
		mRefreshLoadingError = null;
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			String max_id = gap.max_id;
			String since_id = gap.since_id;
			ArrayList< Object > list_tmp;
			
			TootApiResult getAccountList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				list_tmp = new ArrayList<>();
				
				TootApiResult result = null;
				for( ; ; ){
					if( isCancelled() ){
						log.d( "gap-account: cancelled." );
						break;
					}
					
					if( result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
						log.d( "gap-account: timeout. make gap." );
						// タイムアウト
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					
					String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id;
					TootApiResult r2 = client.request( path );
					if( r2 == null || r2.array == null ){
						log.d( "gap-account: error timeout. make gap." );
						
						if( result == null ) result = r2;
						
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					result = r2;
					TootAccount.List src = TootAccount.parseList( log, access_info, r2.array );
					
					if( src.isEmpty() ){
						log.d( "gap-account: empty." );
						break;
					}
					
					list_tmp.addAll( src );
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = Long.toString( src.get( src.size() - 1 ).id );
					
				}
				return result;
			}
			
			TootApiResult getReportList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				list_tmp = new ArrayList<>();
				
				TootApiResult result = null;
				for( ; ; ){
					if( isCancelled() ){
						log.d( "gap-report: cancelled." );
						break;
					}
					
					if( result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
						log.d( "gap-report: timeout. make gap." );
						// タイムアウト
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					
					String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id;
					TootApiResult r2 = client.request( path );
					if( r2 == null || r2.array == null ){
						log.d( "gap-report: error or cancelled. make gap." );
						if( result == null ) result = r2;
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					
					result = r2;
					TootReport.List src = TootReport.parseList( log, r2.array );
					if( src.isEmpty() ){
						log.d( "gap-report: empty." );
						// コレ以上取得する必要はない
						break;
					}
					
					list_tmp.addAll( src );
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = Long.toString( src.get( src.size() - 1 ).id );
				}
				return result;
			}
			
			TootApiResult getNotificationList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				list_tmp = new ArrayList<>();
				
				TootApiResult result = null;
				for( ; ; ){
					if( isCancelled() ){
						log.d( "gap-notification: cancelled." );
						break;
					}
					
					if( result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
						log.d( "gap-notification: timeout. make gap." );
						// タイムアウト
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id;
					TootApiResult r2 = client.request( path );
					if( r2 == null || r2.array == null ){
						// エラー
						log.d( "gap-notification: error or response. make gap." );
						
						if( result == null ) result = r2;
						
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					
					result = r2;
					TootNotification.List src = TootNotification.parseList( log, access_info, r2.array );
					
					if( src.isEmpty() ){
						log.d( "gap-notification: empty." );
						break;
					}
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = Long.toString( src.get( src.size() - 1 ).id );
					
					addWithFilter( list_tmp, src );
					
					AlarmService.injectData( context, access_info.db_id, src );
					
				}
				return result;
			}
			
			TootApiResult getStatusList( TootApiClient client, String path_base ){
				long time_start = SystemClock.elapsedRealtime();
				char delimiter = ( - 1 != path_base.indexOf( '?' ) ? '&' : '?' );
				list_tmp = new ArrayList<>();
				
				TootApiResult result = null;
				for( ; ; ){
					if( isCancelled() ){
						log.d( "gap-statuses: cancelled." );
						break;
					}
					
					if( result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT ){
						log.d( "gap-statuses: timeout." );
						// タイムアウト
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						break;
					}
					
					String path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id;
					
					TootApiResult r2 = client.request( path );
					if( r2 == null || r2.array == null ){
						log.d( "gap-statuses: error or cancelled. make gap." );
						
						// 成功データがない場合だけ、今回のエラーを返すようにする
						if( result == null ) result = r2;
						
						// 隙間が残る
						list_tmp.add( new TootGap( max_id, since_id ) );
						
						break;
					}
					
					// 成功した場合はそれを返したい
					result = r2;
					
					TootStatus.List src = TootStatus.parseList( log, access_info, r2.array );
					if( src.size() == 0 ){
						// 直前の取得でカラのデータが帰ってきたら終了
						log.d( "gap-statuses: empty." );
						break;
					}
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = Long.toString( src.get( src.size() - 1 ).id );
					
					addWithFilter( list_tmp, src );
				}
				return result;
			}
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( context, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled() || is_dispose.get();
					}
					
					@Override public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override
							public void run(){
								if( isCancelled() ) return;
								task_progress = s;
								fireShowContent();
							}
						} );
					}
				} );
				
				client.setAccount( access_info );
				
				try{
					switch( column_type ){
					
					default:
					case TYPE_HOME:
						return getStatusList( client, PATH_HOME );
					
					case TYPE_LOCAL:
						return getStatusList( client, PATH_LOCAL );
					
					case TYPE_FEDERATE:
						return getStatusList( client, PATH_FEDERATE );
					
					case TYPE_FAVOURITES:
						return getStatusList( client, PATH_FAVOURITES );
					
					case TYPE_REPORTS:
						return getReportList( client, PATH_REPORTS );
					
					case TYPE_NOTIFICATIONS:
						return getNotificationList( client, PATH_NOTIFICATIONS );
					
					case TYPE_HASHTAG:
						return getStatusList( client,
							String.format( Locale.JAPAN, PATH_HASHTAG, Uri.encode( hashtag ) ) );
					
					case TYPE_BOOSTED_BY:
						return getAccountList( client, String.format( Locale.JAPAN, PATH_BOOSTED_BY, status_id ) );
					
					case TYPE_FAVOURITED_BY:
						return getAccountList( client, String.format( Locale.JAPAN, PATH_FAVOURITED_BY, status_id ) );
					
					case TYPE_MUTES:
						return getAccountList( client, PATH_MUTES );
					
					case TYPE_BLOCKS:
						return getAccountList( client, PATH_BLOCKS );

					// ドメインブロックはギャップ表示がもともとない
					//case TYPE_DOMAIN_BLOCKS:
					
					case TYPE_FOLLOW_REQUESTS:
						return getAccountList( client, PATH_FOLLOW_REQUESTS );
					
					case TYPE_PROFILE:
						switch( profile_tab ){
						
						default:
						case TAB_STATUS:
							
							if( access_info.isPseudo() ){
								return client.request( PATH_INSTANCE );
								
							}else{
								String s = String.format( Locale.JAPAN, PATH_ACCOUNT_STATUSES, profile_id );
								if( with_attachment ) s = s + "&only_media=1";
								return getStatusList( client, s );
								
							}
						
						case TAB_FOLLOWING:
							return getAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id ) );
						
						case TAB_FOLLOWERS:
							return getAccountList( client,
								String.format( Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id ) );
						}
						
					}
				}finally{
					try{
						updateRelation( client, list_tmp );
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( is_dispose.get() ) return;
				
				if( isCancelled() || result == null ){
					return;
				}
				
				last_task = null;
				bRefreshLoading = false;
				
				if( result.error != null ){
					Column.this.mRefreshLoadingError = result.error;
					fireShowContent();
					return;
				}
				
				if( list_tmp == null ){
					fireShowContent();
					return;
				}
				// 0個でもギャップを消すために以下の処理を続ける
				
				int position = list_data.indexOf( gap );
				if( position == -1 ){
					log.d("gap is not found..");
					return;
				}
				
				ArrayList< Object > list_new = duplicate_map.filterDuplicate( list_tmp );
				
				ColumnViewHolder holder = getViewHolder();
				
				// idx番目の要素がListViewのtopから何ピクセル下にあるか
				int restore_idx = position + 1;
				int restore_y = 0;
				if( holder != null ){
					try{
						restore_y = getItemTop( holder, restore_idx );
					}catch( IndexOutOfBoundsException ex ){
						restore_idx = position;
						try{
							restore_y = getItemTop( holder, restore_idx );
						}catch( IndexOutOfBoundsException ex2 ){
							restore_idx = - 1;
						}
					}
				}
				
				int added = list_new.size(); // may 0
				list_data.remove( position );
				list_data.addAll( position, list_new );
				fireShowContent();
				
				if( holder != null ){
					//noinspection StatementWithEmptyBody
					if( restore_idx >= 0 ){
						setItemTop( holder, restore_idx + added - 1, restore_y );
					}else{
						// ギャップが画面内にない場合、何もしない
					}
				}else{
					if( scroll_save != null ){
						scroll_save.pos += added - 1;
					}
				}
			}
		};
		
		task.executeOnExecutor( App1.task_executor );
	}
	
	private static final int heightSpec = View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED );
	
	private static int getListItemHeight( ListView listView, int idx ){
		int item_width = listView.getWidth() - listView.getPaddingLeft() - listView.getPaddingRight();
		int widthSpec = View.MeasureSpec.makeMeasureSpec( item_width, View.MeasureSpec.EXACTLY );
		View childView = listView.getAdapter().getView( idx, null, listView );
		childView.measure( widthSpec, heightSpec );
		return childView.getMeasuredHeight();
	}
	
	// 特定の要素が特定の位置に来るようにスクロール位置を調整する
	private void setItemTop( @NonNull ColumnViewHolder holder, int idx, int y ){
		
		MyListView listView = holder.getListView();
		boolean hasHeader = holder.hasHeaderView();
		if( hasHeader ){
			// Adapter中から見たpositionとListViewから見たpositionにズレができる
			idx = idx + 1;
		}
		
		while( y > 0 && idx > 0 ){
			-- idx;
			y -= getListItemHeight( listView, idx );
			y -= listView.getDividerHeight();
		}
		listView.setSelectionFromTop( idx, y );
	}
	
	private int getItemTop( @NonNull ColumnViewHolder holder, int idx ){
		
		MyListView listView = holder.getListView();
		boolean hasHeader = holder.hasHeaderView();
		
		if( hasHeader ){
			// Adapter中から見たpositionとListViewから見たpositionにズレができる
			idx = idx + 1;
		}
		
		int vs = listView.getFirstVisiblePosition();
		int ve = listView.getLastVisiblePosition();
		if( idx < vs || ve < idx ){
			throw new IndexOutOfBoundsException( "not in visible range" );
		}
		int child_idx = idx - vs;
		return listView.getChildAt( child_idx ).getTop();
	}
	
	////////////////////////////////////////////////////////////////////////
	// Streaming
	
	private long getId( Object o ){
		if( o instanceof TootNotification ){
			return ( (TootNotification) o ).id;
		}else if( o instanceof TootStatus ){
			return ( (TootStatus) o ).id;
		}else if( o instanceof TootAccount ){
			return ( (TootAccount) o ).id;
		}
		throw new RuntimeException( "getId: object is not status,notification" );
	}
	
	// ListViewの表示更新が追いつかないとスクロール位置が崩れるので
	// 一定時間より短期間にはデータ更新しないようにする
	private long last_show_stream_data;
	private final LinkedList< Object > stream_data_queue = new LinkedList<>();
	
	private final Runnable proc_stream_data = new Runnable() {
		@Override public void run(){
			App1.getAppState( context ).handler.removeCallbacks( proc_stream_data );
			long now = SystemClock.elapsedRealtime();
			long remain = last_show_stream_data + 333L - now;
			if( remain > 0 ){
				App1.getAppState( context ).handler.postDelayed( proc_stream_data, 333L );
				return;
			}
			last_show_stream_data = now;
			
			ArrayList< Object > list_new = duplicate_map.filterDuplicate( stream_data_queue );
			stream_data_queue.clear();
			
			if( list_new.isEmpty() ){
				return;
			}else{
				if( column_type == TYPE_NOTIFICATIONS ){
					TootNotification.List list = new TootNotification.List();
					for( Object o : list_new ){
						if( o instanceof TootNotification ){
							list.add( (TootNotification) o );
						}
					}
					if( ! list.isEmpty() ){
						AlarmService.injectData( context, access_info.db_id, list );
					}
				}
				
				try{
					since_id = Long.toString( getId( list_new.get( 0 ) ) );
				}catch( Throwable ex ){
					// ストリームに来るのは通知かステータスだから、多分ここは通らない
					log.e( ex, "getId() failed. o=", list_new.get( 0 ) );
				}
			}
			ColumnViewHolder holder = getViewHolder();
			
			// 事前にスクロール位置を覚えておく
			ScrollPosition sp = null;
			if( holder != null ){
				sp = holder.getScrollPosition();
			}
			
			// idx番目の要素がListViewのtopから何ピクセル下にあるか
			int restore_idx = - 1;
			int restore_y = 0;
			if( holder != null ){
				if( list_data.size() > 0 ){
					try{
						restore_idx = holder.getListView().getFirstVisiblePosition();
						restore_y = getItemTop( holder, restore_idx );
					}catch( IndexOutOfBoundsException ex ){
						restore_idx = - 1;
						restore_y = 0;
					}
				}
			}
			
			if( bPutGap ){
				bPutGap = false;
				try{
					if( list_new.size() > 0 && list_data.size() > 0 ){
						long max = getId( list_new.get( list_new.size() - 1 ) );
						long since = getId( list_data.get( 0 ) );
						if( max > since ){
							TootGap gap = new TootGap( max, since );
							list_new.add( gap );
						}
					}
				}catch( Throwable ex ){
					log.e( ex, "can't put gap." );
				}
			}
			
			list_data.addAll( 0, list_new );
			fireShowContent();
			int added = list_new.size();
			
			if( holder != null ){
				//noinspection StatementWithEmptyBody
				if( sp.pos == 0 && sp.top == 0 ){
					// スクロール位置が先頭なら先頭のまま
				}else if( restore_idx >= 0 ){
					//
					setItemTop( holder, restore_idx + added, restore_y );
				}else{
					// ギャップが画面内にない場合、何もしない
				}
			}else{
				if( scroll_save == null || ( scroll_save.pos == 0 || scroll_save.top == 0 ) ){
					// スクロール位置が先頭なら先頭のまま
				}else{
					// 現在の要素が表示され続けるようにしたい
					scroll_save.pos += added;
				}
			}
		}
	};
	
	@Override public void onStreamingMessage( String event_type, Object o ){
		if( is_dispose.get() ) return;
		
		if( "delete".equals( event_type ) ){
			if( o instanceof Long ){
				removeStatus( access_info, (Long) o );
			}
		}else{
			if( o instanceof TootNotification ){
				TootNotification notification = (TootNotification) o;
				if( column_type != TYPE_NOTIFICATIONS ) return;
				if( isFiltered( notification ) ) return;
			}else if( o instanceof TootStatus ){
				TootStatus status = (TootStatus) o;
				if( column_type == TYPE_NOTIFICATIONS ) return;
				if( column_type == TYPE_LOCAL && status.account.acct.indexOf( '@' ) != - 1 ) return;
				if( isFiltered( status ) ) return;
				
				if( this.enable_speech ){
					App1.getAppState( context ).addSpeech( status.reblog != null ? status.reblog : status);
				}
			}
			stream_data_queue.addFirst( o );
			proc_stream_data.run();
		}
	}
	
	// onPauseの時はまとめて止められるが
	// カラム破棄やリロード開始時は個別にストリーミングを止める必要がある
	void stopStreaming(){
		
		switch( column_type ){
		case TYPE_HOME:
		case TYPE_NOTIFICATIONS:
			
			app_state.stream_reader.unregister(
				access_info
				, StreamReader.EP_USER
				, this
			);
			break;
		
		
		case TYPE_LOCAL:
			
			app_state.stream_reader.unregister(
				access_info
				, StreamReader.EP_PUBLIC_LOCAL
				, this
			);

			break;
		
		case TYPE_FEDERATE:
			app_state.stream_reader.unregister(
				access_info
				, StreamReader.EP_PUBLIC
				, this
			);
			break;
		
		case TYPE_HASHTAG:
			app_state.stream_reader.unregister(
				access_info
				, StreamReader.EP_HASHTAG + "?tag=" + Uri.encode( hashtag )
				, this
			);
			break;
		}
	}
	
	void onResume( Callback callback ){
		this.callback_ref = new WeakReference<>( callback );
		
		// 破棄されたカラムなら何もしない
		if( is_dispose.get() ){
			log.d( "onResume: column was disposed." );
			return;
		}
		
		// 未初期化なら何もしない
		if( ! bFirstInitialized ){
			log.d( "onResume: column is not initialized." );
			return;
		}
		
		// 初期ロード中なら何もしない
		if( bInitialLoading ){
			log.d( "onResume: column is in initial loading." );
			return;
		}
		
		if( bRefreshingTop ){
			// 始端リフレッシュの最中だった
			// リフレッシュ終了時に自動でストリーミング開始するはず
			log.d( "onResume: bRefreshingTop is true." );
		}else if(
			! bRefreshLoading
				&& canAutoRefresh()
				&& ! App1.getAppState( context ).pref.getBoolean( Pref.KEY_DONT_REFRESH_ON_RESUME, false )
				&& ! dont_auto_refresh
			){
			
			// リフレッシュしてからストリーミング開始
			log.d( "onResume: start auto refresh." );
			startRefresh( true, false, - 1L, - 1 );
		}else{
			// ギャップつきでストリーミング開始
			log.d( "onResume: start streaming with gap." );
			resumeStreaming( true );
		}
	}
	
	boolean canShowMedia(){
		switch( column_type ){
		case TYPE_REPORTS:
		case TYPE_MUTES:
		case TYPE_BLOCKS:
		case TYPE_DOMAIN_BLOCKS:
		case TYPE_FOLLOW_REQUESTS:
		case TYPE_BOOSTED_BY:
		case TYPE_FAVOURITED_BY:
			return false;
		
		default:
			return true;
		}
	}
	
	boolean canAutoRefresh(){
		switch( column_type ){
		default:
			return false;
		
		case TYPE_HOME:
		case TYPE_NOTIFICATIONS:
		case TYPE_LOCAL:
		case TYPE_FEDERATE:
		case TYPE_HASHTAG:
			return true;
		}
	}
	
	boolean canStreaming(){
		switch( column_type ){
		default:
			return false;
		
		case TYPE_HOME:
		case TYPE_NOTIFICATIONS:
		case TYPE_LOCAL:
		case TYPE_FEDERATE:
		case TYPE_HASHTAG:
			return ! access_info.isPseudo();
		}
	}
	
	boolean canSpeech(){
		return canStreaming() && column_type != TYPE_NOTIFICATIONS;
	}
	
	
	private boolean bPutGap;
	
	private void resumeStreaming( boolean bPutGap ){
		
		if( ! canStreaming() ){
			return;
		}
		
		if( ! isResume() ){
			log.d( "resumeStreaming: not resumed." );
			return;
		}
		
		// 破棄されたカラムなら何もしない
		if( is_dispose.get() ){
			log.d( "resumeStreaming: column was disposed." );
			return;
		}
		
		// 未初期化なら何もしない
		if( ! bFirstInitialized ){
			log.d( "resumeStreaming: column is not initialized." );
			return;
		}
		
		// 初期ロード中なら何もしない
		if( bInitialLoading ){
			log.d( "resumeStreaming: is in initial loading." );
			return;
		}
		
		if( App1.getAppState( context ).pref.getBoolean( Pref.KEY_DONT_USE_STREAMING, false ) ){
			log.d( "resumeStreaming: disabled in app setting." );
			return;
		}
		
		if( dont_streaming ){
			log.d( "resumeStreaming: disabled in column setting." );
			return;
		}
		
		if( access_info.isPseudo() ){
			log.d( "resumeStreaming: pseudo account can't streaming." );
			return;
		}
		
		this.bPutGap = bPutGap;
		
		stream_data_queue.clear();
		
		switch( column_type ){
		case TYPE_HOME:
		case TYPE_NOTIFICATIONS:
			
			app_state.stream_reader.register(
				access_info
				, StreamReader.EP_USER
				, this
			);
			break;
		
		case TYPE_LOCAL:
			
			app_state.stream_reader.register(
				access_info
				, StreamReader.EP_PUBLIC_LOCAL
				, this
			);
			break;
		
		case TYPE_FEDERATE:
			
			app_state.stream_reader.register(
				access_info
				, StreamReader.EP_PUBLIC
				, this
			);
			break;
		
		case TYPE_HASHTAG:
			app_state.stream_reader.register(
				access_info
				, StreamReader.EP_HASHTAG + "&tag=" + Uri.encode( hashtag )
				, this
			);
			break;
		}
	}
	
}
