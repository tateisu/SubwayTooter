package jp.juggler.subwaytooter;

import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

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
import jp.juggler.subwaytooter.api.entity.TootContext;
import jp.juggler.subwaytooter.api.entity.TootId;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

class Column {
	private static final LogCategory log = new LogCategory( "Column" );
	
	private static Object getParamAt( Object[] params, int idx ){
		if( params == null || idx >= params.length ){
			throw new IndexOutOfBoundsException( "getParamAt idx=" + idx );
		}
		return params[ idx ];
	}
	
	private static final String KEY_ACCOUNT_ROW_ID = "account_id";
	private static final String KEY_TYPE = "type";
	private static final String KEY_WHO_ID = "who_id";
	private static final String KEY_STATUS_ID = "status_id";
	private static final String KEY_HASHTAG = "hashtag";
	private static final String KEY_SEARCH_QUERY = "search_query";
	private static final String KEY_SEARCH_RESOLVE = "search_resolve";
	
	static final String KEY_COLUMN_ACCESS = "column_access";
	static final String KEY_COLUMN_NAME = "column_name";
	static final String KEY_OLD_INDEX = "old_index";
	
	private final ActMain activity;
	
	final SavedAccount access_info;
	
	final int type;
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
	
	private long who_id;
	
	private long status_id;
	
	private String hashtag;
	
	String search_query;
	boolean search_resolve;
	
	int profile_tab = 0;
	
	int scroll_pos;
	int scroll_y;
	
	Column( ActMain activity, SavedAccount access_info, int type, Object... params ){
		this.activity = activity;
		this.access_info = access_info;
		this.type = type;
		switch( type ){
		case TYPE_CONVERSATION:
			this.status_id = (Long) getParamAt( params, 0 );
			break;
		case TYPE_PROFILE:
			this.who_id = (Long) getParamAt( params, 0 );
			break;
		case TYPE_HASHTAG:
			this.hashtag = (String) getParamAt( params, 0 );
			break;
		case TYPE_SEARCH:
			this.search_query = (String) getParamAt( params, 0 );
			this.search_resolve = (Boolean) getParamAt( params, 1 );
		}
		
		startLoading();
	}
	
	void encodeJSON( JSONObject item, int old_index ) throws JSONException{
		item.put( KEY_ACCOUNT_ROW_ID, access_info.db_id );
		item.put( KEY_TYPE, type );
		
		switch( type ){
		case TYPE_CONVERSATION:
			item.put( KEY_STATUS_ID, status_id );
			break;
		case TYPE_PROFILE:
			item.put( KEY_WHO_ID, who_id );
			break;
		case TYPE_HASHTAG:
			item.put( KEY_HASHTAG, hashtag );
			break;
		case TYPE_SEARCH:
			item.put( KEY_SEARCH_QUERY, search_query );
			item.put( KEY_SEARCH_RESOLVE, search_resolve );
		}
		
		// 以下は保存には必要ないが、カラムリスト画面で使う
		item.put( KEY_COLUMN_ACCESS, access_info.user );
		item.put( KEY_COLUMN_NAME, getColumnName( true ) );
		item.put( KEY_OLD_INDEX, old_index );
	}
	
	Column( ActMain activity, JSONObject src ){
		this.activity = activity;
		this.access_info = SavedAccount.loadAccount( log, src.optLong( KEY_ACCOUNT_ROW_ID ) );
		if( access_info == null ) throw new RuntimeException( "missing account" );
		this.type = src.optInt( KEY_TYPE );
		switch( type ){
		case TYPE_CONVERSATION:
			this.status_id = src.optLong( KEY_STATUS_ID );
			break;
		case TYPE_PROFILE:
			this.who_id = src.optLong( KEY_WHO_ID );
			break;
		case TYPE_HASHTAG:
			this.hashtag = src.optString( KEY_HASHTAG );
			break;
		case TYPE_SEARCH:
			this.search_query = src.optString( KEY_SEARCH_QUERY );
			this.search_resolve = src.optBoolean( KEY_SEARCH_RESOLVE, false );
		}
		startLoading();
	}
	
	final AtomicBoolean is_dispose = new AtomicBoolean();
	
	void dispose(){
		is_dispose.set( true );
	}
	
	String getColumnName( boolean bLong ){
		switch( type ){
		
		default:
			return "?";
		
		case TYPE_HOME:
			return activity.getString( R.string.home );
		
		case TYPE_LOCAL:
			return activity.getString( R.string.local_timeline );
		
		case TYPE_FEDERATE:
			return activity.getString( R.string.federate_timeline );
		
		case TYPE_PROFILE:
			return activity.getString( R.string.statuses_of
				, who_account != null ? access_info.getFullAcct( who_account ) : Long.toString( who_id )
			);
		
		case TYPE_FAVOURITES:
			return activity.getString( R.string.favourites );
		
		case TYPE_REPORTS:
			return activity.getString( R.string.reports );
		
		case TYPE_NOTIFICATIONS:
			return activity.getString( R.string.notifications );
		
		case TYPE_CONVERSATION:
			return activity.getString( R.string.conversation_around, status_id );
		
		case TYPE_HASHTAG:
			return activity.getString( R.string.hashtag_of, hashtag );
		case TYPE_SEARCH:
			if( bLong ){
				return activity.getString( R.string.search_of, search_query );
			}else{
				return activity.getString( R.string.search );
			}
		}
	}
	
	boolean isSameSpec( SavedAccount ai, int type, Object[] params ){
		if( type != this.type || ! Utils.equalsNullable( ai.user, access_info.user ) ) return false;
		switch( type ){
		default:
			return true;
		
		case TYPE_PROFILE:
			try{
				long who_id = (Long) getParamAt( params, 0 );
				return who_id == this.who_id;
			}catch( Throwable ex ){
				return false;
			}
		
		case TYPE_CONVERSATION:
			try{
				long status_id = (Long) getParamAt( params, 0 );
				return status_id == this.status_id;
			}catch( Throwable ex ){
				return false;
			}
		
		case TYPE_HASHTAG:
			try{
				long status_id = (Long) getParamAt( params, 0 );
				return status_id == this.status_id;
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
	
	interface StatusEntryCallback {
		void onIterate( TootStatus status );
	}
	
	// ブーストやお気に入りの更新に使う。ステータスを列挙する。
	void findStatus( SavedAccount target_account, long target_status_id, StatusEntryCallback callback ){
		if( target_account.user.equals( access_info.user ) ){
			for( int i = 0, ie = status_list.size() ; i < ie ; ++ i ){
				//
				TootStatus status = status_list.get( i );
				if( target_status_id == status.id ){
					callback.onIterate( status );
				}
				//
				TootStatus reblog = status.reblog;
				if( reblog != null ){
					if( target_status_id == reblog.id ){
						callback.onIterate( reblog );
					}
				}
			}
		}
	}
	
	// ミュート、ブロックが成功した時に呼ばれる
	void removeStatusByAccount( SavedAccount target_account, long who_id ){
		if( ! target_account.user.equals( access_info.user ) ) return;
		{
			// remove from status_list
			TootStatus.List tmp_list = new TootStatus.List( status_list.size() );
			for( TootStatus status : status_list ){
				if( status.account.id == who_id
					|| ( status.reblog != null && status.reblog.account.id == who_id )
					){
					continue;
				}
				tmp_list.add( status );
			}
			status_list.clear();
			status_list.addAll( tmp_list );
		}
		{
			// remove from notification_list
			TootNotification.List tmp_list = new TootNotification.List( notification_list.size() );
			for( TootNotification item : notification_list ){
				if( item.account.id == who_id ) continue;
				if( item.status != null ){
					if( item.status.account.id == who_id ) continue;
					if( item.status.reblog != null && item.status.reblog.account.id == who_id )
						continue;
				}
				tmp_list.add( item );
			}
			notification_list.clear();
			notification_list.addAll( tmp_list );
		}
	}
	
	interface VisualCallback {
		void onVisualColumn();
	}
	
	private final LinkedList< VisualCallback > visual_callback = new LinkedList<>();
	
	void addVisualListener( VisualCallback listener ){
		if( listener == null ) return;
		for( VisualCallback vc : visual_callback ){
			if( vc == listener ) return;
		}
		visual_callback.add( listener );
	}
	
	void removeVisualListener( VisualCallback listener ){
		if( listener == null ) return;
		Iterator< VisualCallback > it = visual_callback.iterator();
		while( it.hasNext() ){
			VisualCallback vc = it.next();
			if( vc == listener ) it.remove();
		}
	}
	
	private final Runnable proc_fireVisualCallback = new Runnable() {
		@Override public void run(){
			for( VisualCallback aVisual_callback : visual_callback ){
				aVisual_callback.onVisualColumn();
			}
		}
	};
	
	void fireVisualCallback(){
		Utils.runOnMainThread( proc_fireVisualCallback );
	}
	
	private AsyncTask< Void, Void, TootApiResult > last_task;
	
	private void cancelLastTask(){
		if( last_task != null ){
			last_task.cancel( true );
			last_task = null;
			//
			bInitialLoading = false;
			bRefreshLoading = false;
			mInitialLoadingError = activity.getString( R.string.cancelled );
			//
		}
	}
	
	boolean bInitialLoading;
	boolean bRefreshLoading;
	
	String mInitialLoadingError;
	String mRefreshLoadingError;
	
	String task_progress;
	
	final TootStatus.List status_list = new TootStatus.List();
	final TootNotification.List notification_list = new TootNotification.List();
	final TootReport.List report_list = new TootReport.List();
	final ArrayList< Object > result_list = new ArrayList<>();
	
	volatile TootAccount who_account;
	
	void reload(){
		status_list.clear();
		notification_list.clear();
		report_list.clear();
		result_list.clear();
		startLoading();
	}
	
	private static final String PATH_HOME = "/api/v1/timelines/home?limit=80";
	private static final String PATH_LOCAL = "/api/v1/timelines/public?limit=80&local=1";
	private static final String PATH_FEDERATE = "/api/v1/timelines/public?limit=80";
	private static final String PATH_FAVOURITES = "/api/v1/favourites?limit=80";
	private static final String PATH_REPORTS = "/api/v1/reports?limit=80";
	private static final String PATH_NOTIFICATIONS = "/api/v1/notifications?limit=80";
	private static final String PATH_PROFILE1_FORMAT = "/api/v1/accounts/%d?limit=80"; // 1:account_id
	private static final String PATH_PROFILE2_FORMAT = "/api/v1/accounts/%d/statuses?limit=80"; // 1:account_id
	private static final String PATH_HASHTAG_FORMAT = "/api/v1/timelines/tag/%s?limit=80"; // 1: tashtag(url encoded)
	
	private static final String PATH_CONVERSATION1_FORMAT = "/api/v1/statuses/%d"; // 1:status_id
	private static final String PATH_CONVERSATION2_FORMAT = "/api/v1/statuses/%d/context"; // 1:status_id
	
	private static final String PATH_SEARCH_FORMAT = "/api/v1/search?limit=80&q=%s"; // 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts
	
	private void startLoading(){
		cancelLastTask();
		
		mRefreshLoadingError = null;
		bRefreshLoading = false;
		mInitialLoadingError = null;
		bInitialLoading = true;
		max_id = null;
		since_id = null;
		
		fireVisualCallback();
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootStatus.List tmp_list_status;
			TootReport.List tmp_list_report;
			TootNotification.List tmp_list_notification;
			ArrayList< Object > tmp_list_result;
			
			TootApiResult parseStatuses( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					tmp_list_status = TootStatus.parseList( log, access_info, result.array );
				}
				return result;
			}
			
			TootApiResult parseAccount( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					who_account = TootAccount.parse( log, access_info, result.object );
				}
				return result;
			}
			
			TootApiResult parseReports( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					tmp_list_report = TootReport.parseList( log, result.array );
				}
				return result;
			}
			
			TootApiResult parseNotifications( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					tmp_list_notification = TootNotification.parseList( log, access_info, result.array );
				}
				return result;
			}
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
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
								fireVisualCallback();
							}
						} );
					}
				} );
				
				client.setAccount( access_info );
				
				TootApiResult result;
				
				switch( type ){
				
				default:
				case TYPE_HOME:
					return parseStatuses( client.request( PATH_HOME ) );
				
				case TYPE_LOCAL:
					return parseStatuses( client.request( PATH_LOCAL ) );
				
				case TYPE_FEDERATE:
					return parseStatuses( client.request( PATH_FEDERATE ) );
				
				case TYPE_PROFILE:
					if( who_account == null ){
						parseAccount( client.request(
							String.format( Locale.JAPAN, PATH_PROFILE1_FORMAT, who_id ) ) );
						client.callback.publishApiProgress( "" );
					}
					
					return parseStatuses( client.request(
						String.format( Locale.JAPAN, PATH_PROFILE2_FORMAT, who_id ) ) );
				
				case TYPE_FAVOURITES:
					return parseStatuses( client.request( PATH_FAVOURITES ) );
				
				case TYPE_HASHTAG:
					return parseStatuses( client.request(
						String.format( Locale.JAPAN, PATH_HASHTAG_FORMAT, Uri.encode( hashtag ) ) ) );
				
				case TYPE_REPORTS:
					return parseReports( client.request( PATH_REPORTS ) );
				
				case TYPE_NOTIFICATIONS:
					return parseNotifications( client.request( PATH_NOTIFICATIONS ) );
				
				case TYPE_CONVERSATION:
					
					// 指定された発言そのもの
					result = client.request(
						String.format( Locale.JAPAN, PATH_CONVERSATION1_FORMAT, status_id ) );
					if( result == null || result.object == null ) return result;
					TootStatus target_status = TootStatus.parse( log, access_info, result.object );
					target_status.conversation_main = true;
					
					// 前後の会話
					result = client.request(
						String.format( Locale.JAPAN, PATH_CONVERSATION2_FORMAT, status_id ) );
					if( result == null || result.object == null ) return result;
					
					// 一つのリストにまとめる
					TootContext context = TootContext.parse( log, access_info, result.object );
					tmp_list_status = new TootStatus.List();
					if( context.ancestors != null ) tmp_list_status.addAll( context.ancestors );
					tmp_list_status.add( target_status );
					if( context.descendants != null ) tmp_list_status.addAll( context.descendants );
					
					//
					return result;
				
				case TYPE_SEARCH:
					String path = String.format( Locale.JAPAN, PATH_SEARCH_FORMAT, Uri.encode( search_query ) );
					if( search_resolve ) path = path + "&resolve=1";
					
					result = client.request( path );
					if( result == null || result.object == null ) return result;
					
					TootResults tmp = TootResults.parse( log, access_info, result.object );
					if( tmp != null ){
						tmp_list_result = new ArrayList<>();
						tmp_list_result.addAll( tmp.accounts );
						tmp_list_result.addAll( tmp.hashtags );
						tmp_list_result.addAll( tmp.statuses );
					}
					return result;
					
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				
				if( isCancelled() || result == null ){
					return;
				}
				
				bInitialLoading = false;
				last_task = null;
				
				if( result.error != null ){
					Column.this.mInitialLoadingError = result.error;
				}else{
					switch( type ){
					default:
					case TYPE_HOME:
					case TYPE_LOCAL:
					case TYPE_FEDERATE:
					case TYPE_PROFILE:
					case TYPE_FAVOURITES:
					case TYPE_CONVERSATION:
					case TYPE_HASHTAG:
						initList( status_list, tmp_list_status );
						break;
					
					case TYPE_REPORTS:
						initList( report_list, tmp_list_report );
						break;
					
					case TYPE_NOTIFICATIONS:
						initList( notification_list, tmp_list_notification );
						break;
					case TYPE_SEARCH:
						initList( result_list, tmp_list_result );
						
					}
					
				}
				fireVisualCallback();
			}
		};
		
		AsyncTaskCompat.executeParallel( task );
	}
	
	static final Pattern reMaxId = Pattern.compile( "&max_id=(\\d+)" ); // より古いデータの取得に使う
	static final Pattern reSinceId = Pattern.compile( "&since_id=(\\d+)" ); // より新しいデータの取得に使う
	
	String max_id;
	String since_id;
	
	private void saveRange( TootApiResult result, boolean bBottom, boolean bTop ){
		// Link:  <https://mastodon.juggler.jp/api/v1/timelines/home?limit=80&max_id=405228>; rel="next",
		//        <https://mastodon.juggler.jp/api/v1/timelines/home?limit=80&since_id=436946>; rel="prev"
		
		if( result.response != null ){
			String sv = result.response.header( "Link" );
			if( ! TextUtils.isEmpty( sv ) ){
				if( bBottom ){
					Matcher m = reMaxId.matcher( sv );
					if( m.find() ){
						max_id = m.group( 1 );
						log.d( "col=%s,max_id=%s", this.hashCode(), max_id );
					}
				}
				if( bTop ){
					Matcher m = reSinceId.matcher( sv );
					if( m.find() ){
						since_id = m.group( 1 );
						log.d( "col=%s,since_id=%s", this.hashCode(), since_id );
					}
				}
			}
		}
	}
	
	String addRange( boolean bBottom, String path ){
		char delm = ( - 1 != path.indexOf( '?' ) ? '&' : '?' );
		if( bBottom ){
			if( max_id != null ) return path + delm + "max_id=" + max_id;
		}else{
			if( since_id != null ) return path + delm + "since_id=" + since_id;
		}
		return path;
	}
	
	< T > void initList( ArrayList< T > dst, ArrayList< T > src ){
		if( src == null ) return;
		dst.clear();
		dst.addAll( src );
	}
	
	< T extends TootId > void mergeList( ArrayList< T > dst, ArrayList< T > src, boolean bBottom ){
		// 古いリストにある要素の集合
		HashSet< Long > id_set = new HashSet();
		for( T t : dst ){
			id_set.add( t.id );
		}
		ArrayList< T > tmp_list = new ArrayList<>( src.size() );
		for( T t : src ){
			if( id_set.contains( t.id ) ) continue;
			tmp_list.add( t );
		}
		
		if( ! bBottom ){
			tmp_list.addAll( dst );
			dst.clear();
			dst.addAll( tmp_list );
		}else{
			dst.addAll( tmp_list );
		}
	}
	
	public boolean startRefresh( final boolean bBottom ){
		if( last_task != null ){
			log.d( "busy" );
			return false;
		}else if( bBottom && max_id == null ){
			log.d( "startRefresh failed. missing max_id" );
			return false;
		}else if( ! bBottom && since_id == null ){
			log.d( "startRefresh failed. missing since_id" );
			return false;
		}
		bRefreshLoading = true;
		mRefreshLoadingError = null;
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootStatus.List tmp_list_status;
			TootReport.List tmp_list_report;
			TootNotification.List tmp_list_notification;
			
			TootApiResult parseStatuses( TootApiResult result ){
				if( result != null ){
					saveRange( result, bBottom, ! bBottom );
					tmp_list_status = TootStatus.parseList( log, access_info, result.array );
				}
				return result;
			}
			
			TootApiResult parseAccount( TootApiResult result ){
				if( result != null ){
					saveRange( result, bBottom, ! bBottom );
					who_account = TootAccount.parse( log, access_info, result.object );
				}
				return result;
			}
			
			TootApiResult parseReports( TootApiResult result ){
				if( result != null ){
					saveRange( result, bBottom, ! bBottom );
					tmp_list_report = TootReport.parseList( log, result.array );
				}
				return result;
			}
			
			TootApiResult parseNotifications( TootApiResult result ){
				if( result != null ){
					saveRange( result, bBottom, ! bBottom );
					tmp_list_notification = TootNotification.parseList( log, access_info, result.array );
				}
				return result;
			}
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
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
								fireVisualCallback();
							}
						} );
					}
				} );
				
				client.setAccount( access_info );
				
				switch( type ){
				default:
				case TYPE_HOME:
					return parseStatuses( client.request( addRange( bBottom, PATH_HOME ) ) );
				
				case TYPE_LOCAL:
					return parseStatuses( client.request( addRange( bBottom, PATH_LOCAL ) ) );
				
				case TYPE_FEDERATE:
					return parseStatuses( client.request( addRange( bBottom, PATH_FEDERATE ) ) );
				
				case TYPE_FAVOURITES:
					return parseStatuses( client.request( addRange( bBottom, PATH_FAVOURITES ) ) );
				
				case TYPE_REPORTS:
					return parseReports( client.request( addRange( bBottom, PATH_REPORTS ) ) );
				
				case TYPE_NOTIFICATIONS:
					return parseNotifications( client.request( addRange( bBottom, PATH_NOTIFICATIONS ) ) );
				
				case TYPE_PROFILE:
					if( who_account == null ){
						parseAccount( client.request(
							String.format( Locale.JAPAN, PATH_PROFILE1_FORMAT, who_id ) ) );
						
						client.callback.publishApiProgress( "" );
					}
					
					return parseStatuses( client.request( addRange( bBottom,
						String.format( Locale.JAPAN, PATH_PROFILE2_FORMAT, who_id ) ) ) );
				
				case TYPE_HASHTAG:
					return parseStatuses( client.request( addRange( bBottom,
						String.format( Locale.JAPAN, PATH_HASHTAG_FORMAT, Uri.encode( hashtag ) ) ) ) );
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				
				if( isCancelled() || result == null ){
					return;
				}
				last_task = null;
				bRefreshLoading = false;
				
				if( result.error != null ){
					Column.this.mRefreshLoadingError = result.error;
				}else{
					switch( type ){
					default:
					case TYPE_HOME:
					case TYPE_LOCAL:
					case TYPE_FEDERATE:
					case TYPE_PROFILE:
					case TYPE_FAVOURITES:
					case TYPE_HASHTAG:
						mergeList( status_list, tmp_list_status, bBottom );
						break;
					
					case TYPE_REPORTS:
						mergeList( report_list, tmp_list_report, bBottom );
						break;
					
					case TYPE_NOTIFICATIONS:
						mergeList( notification_list, tmp_list_notification, bBottom );
						break;
					}
					
				}
				fireVisualCallback();
			}
		};
		
		AsyncTaskCompat.executeParallel( task );
		return true;
	}
	
}
