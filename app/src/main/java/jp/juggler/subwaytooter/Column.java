package jp.juggler.subwaytooter;

import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;
import android.text.TextUtils;
import android.util.SparseLongArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.RunnableFuture;
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
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Headers;

public class Column {
	static final LogCategory log = new LogCategory( "Column" );
	
	static final String KEY_ACCOUNT_ROW_ID = "account_id";
	static final String KEY_TYPE = "type";
	static final String KEY_WHO_ID = "who_id";
	static final String KEY_STATUS_ID = "status_id";
	static final String KEY_HASHTAG = "hashtag";

	static final String KEY_COLUMN_ACCESS = "column_access";
	static final String KEY_COLUMN_NAME = "column_name";
	static final String KEY_OLD_INDEX = "old_index";
	
	private final ActMain activity;
	final SavedAccount access_info;
	final int type;
	
	long who_id;
	long status_id;
	String hashtag;
	int profile_tab = 0;
	
	int scroll_pos;
	int scroll_y;
	
	static final int TYPE_TL_HOME = 1;
	static final int TYPE_TL_LOCAL = 2;
	static final int TYPE_TL_FEDERATE = 3;
	static final int TYPE_TL_STATUSES = 4;
	static final int TYPE_TL_FAVOURITES = 5;
	static final int TYPE_TL_REPORTS = 6;
	static final int TYPE_TL_NOTIFICATIONS = 7;
	static final int TYPE_TL_CONVERSATION = 8;
	static final int TYPE_TL_HASHTAG = 9;
	

	Column( ActMain activity, SavedAccount access_info, int type, Object... params ){
		this.activity = activity;
		this.access_info = access_info;
		this.type = type;
		switch(type){
		case TYPE_TL_CONVERSATION:
			this.status_id = (Long)getParamAt( params,0 );
			break;
		case TYPE_TL_STATUSES:
			this.who_id = (Long)getParamAt( params,0 );
			break;
		case TYPE_TL_HASHTAG:
			this.hashtag = (String)getParamAt( params,0 );
			break;
		}

		startLoading();
	}
	
	public void encodeJSON( JSONObject item, int old_index ) throws JSONException{
		item.put( KEY_ACCOUNT_ROW_ID, access_info.db_id );
		item.put( KEY_TYPE, type );
		
		switch(type){
		case TYPE_TL_CONVERSATION:
			item.put( KEY_STATUS_ID,status_id);
			break;
		case TYPE_TL_STATUSES:
			item.put( KEY_WHO_ID, who_id );
			break;
		case TYPE_TL_HASHTAG:
			item.put( KEY_HASHTAG,hashtag );
			break;
		}
		
		// 以下は保存には必要ないが、カラムリスト画面で使う
		item.put( KEY_COLUMN_ACCESS, access_info.user );
		item.put( KEY_COLUMN_NAME, getColumnName() );
		item.put( KEY_OLD_INDEX, old_index );
	}
	
	
	Column( ActMain activity, JSONObject src ){
		this.activity = activity;
		this.access_info = SavedAccount.loadAccount( log, src.optLong( KEY_ACCOUNT_ROW_ID ) );
		if( access_info == null ) throw new RuntimeException( "missing account" );
		this.type = src.optInt( KEY_TYPE );
		switch(type){
		case TYPE_TL_CONVERSATION:
			this.status_id = src.optLong( KEY_STATUS_ID );
			break;
		case TYPE_TL_STATUSES:
			this.who_id = src.optLong( KEY_WHO_ID );
			break;
		case TYPE_TL_HASHTAG:
			this.hashtag = src.optString( KEY_HASHTAG );
			break;
		}
		startLoading();
	}

	final AtomicBoolean is_dispose = new AtomicBoolean();
	
	void dispose(){
		is_dispose.set( true );
	}
	
	public String getColumnName(){
		switch( type ){

		default:
			return "?";

		case TYPE_TL_HOME:
			return activity.getString( R.string.home );

		case TYPE_TL_LOCAL:
			return activity.getString( R.string.local_timeline );

		case TYPE_TL_FEDERATE:
			return activity.getString( R.string.federate_timeline );
		
		case TYPE_TL_STATUSES:
			return activity.getString( R.string.statuses_of
				, who_account != null ? access_info.getFullAcct( who_account ) : Long.toString( who_id )
			);
		
		case TYPE_TL_FAVOURITES:
			return activity.getString( R.string.favourites );
		
		case TYPE_TL_REPORTS:
			return activity.getString( R.string.reports );
		
		case TYPE_TL_NOTIFICATIONS:
			return activity.getString( R.string.notifications );

		case TYPE_TL_CONVERSATION:
			return activity.getString( R.string.conversation_around,status_id );
		
		case TYPE_TL_HASHTAG:
			return activity.getString( R.string.hashtag_of ,hashtag );
		}
	}
	
	
	Object getParamAt(Object[] params,int idx){
		if( params == null || idx >= params.length){
			throw new IndexOutOfBoundsException( "getParamAt idx="+idx );
		}
		return params[idx];
	}
	
	public boolean isSameSpec( SavedAccount ai, int type, Object[] params ){
		if( type != this.type || ! Utils.equalsNullable(ai.user,access_info.user ) ) return false;
		switch( type ){
		default:
			return true;

		case TYPE_TL_STATUSES:
			// プロフィール画面
			try{
				long who_id = (Long)getParamAt( params, 0 );
				return who_id == this.who_id;
			}catch(Throwable ex){
				return false;
			}

		case TYPE_TL_CONVERSATION:
			// 会話画面
			try{
				long status_id = (Long)getParamAt( params, 0 );
				return status_id == this.status_id;
			}catch(Throwable ex){
				return false;
			}

		case TYPE_TL_HASHTAG:
			// 会話画面
			try{
				long status_id = (Long)getParamAt( params, 0 );
				return status_id == this.status_id;
			}catch(Throwable ex){
				return false;
			}
			
		}
	}
	
	public interface StatusEntryCallback {
		void onIterate( TootStatus status );
	}
	
	// ブーストやお気に入りの更新に使う。ステータスを列挙する。
	public void findStatus( SavedAccount target_account, long target_status_id, StatusEntryCallback callback ){
		if( target_account.user.equals( access_info.user ) ){
			for( int i = 0, ie = status_list.size() ; i < ie ; ++ i ){
				TootStatus status = status_list.get( i );
				if( target_status_id == status.id ){
					callback.onIterate( status );
				}
				TootStatus reblog = status.reblog;
				if( reblog != null ){
					if( target_status_id == reblog.id ){
						callback.onIterate( status );
					}
				}
			}
		}
	}
	// ミュート、ブロックが成功した時に呼ばれる
	public void removeStatusByAccount( SavedAccount target_account, long who_id ){
		if( target_account.user.equals( access_info.user ) ){
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
						if( item.status.reblog != null && item.status.reblog.account.id == who_id ) continue;
					}
					tmp_list.add( item );
				}
				notification_list.clear();
				notification_list.addAll( tmp_list );
			}
		}
	}
	
	
	
	public interface VisualCallback {
		void onVisualColumn();
	}
	
	final LinkedList< VisualCallback > visual_callback = new LinkedList<>();
	
	void addVisualListener( VisualCallback listener ){
		if( listener == null ) return;
		Iterator< VisualCallback > it = visual_callback.iterator();
		while( it.hasNext() ){
			VisualCallback vc = it.next();
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
		@Override
		public void run(){
			Iterator< VisualCallback > it = visual_callback.iterator();
			while( it.hasNext() ){
				it.next().onVisualColumn();
			}
		}
	};
	
	
	public void fireVisualCallback(){
		Utils.runOnMainThread( proc_fireVisualCallback  );
	}
	
	AsyncTask< Void, Void, TootApiResult > last_task;
	
	void cancelLastTask(){
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
	final TootReport.List report_list = new TootReport.List();
	final TootNotification.List notification_list = new TootNotification.List();
	volatile TootAccount who_account;
	
	public void reload(){
		status_list.clear();
		startLoading();
	}
	
	static final String PATH_TL_HOME = "/api/v1/timelines/home?limit=80";
	static final String PATH_TL_LOCAL = "/api/v1/timelines/public?limit=80&local=1";
	static final String PATH_TL_FEDERATE = "/api/v1/timelines/public?limit=80";
	static final String PATH_TL_FAVOURITES = "/api/v1/favourites?limit=80";
	static final String PATH_TL_REPORTS = "/api/v1/reports?limit=80";
	static final String PATH_TL_NOTIFICATIONS = "/api/v1/notifications?limit=80";
	
	void startLoading(){
		cancelLastTask();
		
		mInitialLoadingError = null;
		bInitialLoading = true;
		max_id = null;
		since_id = null;

		fireVisualCallback();
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootStatus.List tmp_list_status;
			TootReport.List tmp_list_report;
			TootNotification.List tmp_list_notification;
			
			TootApiResult parseStatuses( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					tmp_list_status = TootStatus.parseList( log, access_info,result.array );
				}
				return result;
			}
			
			TootApiResult parseAccount( TootApiResult result ){
				if( result != null ){
					saveRange( result, true, true );
					who_account = TootAccount.parse( log,  access_info,result.object );
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
					tmp_list_notification = TootNotification.parseList( log, access_info,result.array );
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
				case TYPE_TL_HOME:
					return parseStatuses( client.request( PATH_TL_HOME ) );
				
				case TYPE_TL_LOCAL:
					return parseStatuses( client.request( PATH_TL_LOCAL ) );
				
				case TYPE_TL_FEDERATE:
					return parseStatuses( client.request( PATH_TL_FEDERATE ) );
				
				case TYPE_TL_STATUSES:
					if( who_account == null ){
						parseAccount( client.request( "/api/v1/accounts/" + who_id + "?limit=80" ) );
						client.callback.publishApiProgress( "" );
					}
					
					return parseStatuses( client.request( "/api/v1/accounts/" + who_id + "/statuses?limit=80" ) );
				
				case TYPE_TL_FAVOURITES:
					return parseStatuses( client.request( PATH_TL_FAVOURITES ) );

				case TYPE_TL_HASHTAG:
					return parseStatuses( client.request( "/api/v1/timelines/tag/"+hashtag+"?limit=80" ) );
				
				case TYPE_TL_REPORTS:
					return parseReports( client.request( PATH_TL_REPORTS ) );
				
				case TYPE_TL_NOTIFICATIONS:
					return parseNotifications( client.request( PATH_TL_NOTIFICATIONS ) );
				
				case TYPE_TL_CONVERSATION:
					TootApiResult result = client.request( "/api/v1/statuses/"+status_id );
					if( result== null || result.object == null ) return result;
					TootStatus target_status = TootStatus.parse( log, access_info,result.object );
					target_status.conversation_main = true;
					//
					result = client.request( "/api/v1/statuses/"+status_id+"/context" );
					if( result== null || result.object == null ) return result;
					TootContext context = TootContext.parse( log,access_info,result.object );
					tmp_list_status = new TootStatus.List();
					if( context.ancestors != null ) tmp_list_status.addAll( context.ancestors);
					tmp_list_status.add(target_status);
					if( context.descendants != null ) tmp_list_status.addAll( context.descendants);
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
					case TYPE_TL_HOME:
					case TYPE_TL_LOCAL:
					case TYPE_TL_FEDERATE:
					case TYPE_TL_STATUSES:
					case TYPE_TL_FAVOURITES:
					case TYPE_TL_CONVERSATION:
					case TYPE_TL_HASHTAG:
						initList( status_list, tmp_list_status );
						break;
					
					case TYPE_TL_REPORTS:
						initList( report_list, tmp_list_report );
						break;
					
					case TYPE_TL_NOTIFICATIONS:
						initList( notification_list, tmp_list_notification );
						break;
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
	
	< T extends TootId > void initList( ArrayList< T > dst, ArrayList< T > src ){
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
		}else if( !bBottom && since_id == null ){
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
					who_account = TootAccount.parse( log,  access_info,result.object );
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
					tmp_list_notification = TootNotification.parseList( log, access_info,result.array );
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
				case TYPE_TL_HOME:
					return parseStatuses( client.request( addRange( bBottom, PATH_TL_HOME ) ) );
				
				case TYPE_TL_LOCAL:
					return parseStatuses( client.request( addRange( bBottom, PATH_TL_LOCAL ) ) );
				
				case TYPE_TL_FEDERATE:
					return parseStatuses( client.request( addRange( bBottom, PATH_TL_FEDERATE ) ) );
				
				case TYPE_TL_STATUSES:
					if( who_account == null ){
						parseAccount( client.request( "/api/v1/accounts/" + who_id + "?limit=80" ) );
						client.callback.publishApiProgress( "" );
					}
					
					return parseStatuses( client.request( addRange( bBottom, "/api/v1/accounts/" + who_id + "/statuses?limit=80" ) ) );
				
				case TYPE_TL_FAVOURITES:
					return parseStatuses( client.request( addRange( bBottom, PATH_TL_FAVOURITES ) ) );
				
				case TYPE_TL_HASHTAG:
					return parseStatuses( client.request(  addRange( bBottom,"/api/v1/timelines/tag/"+hashtag+"?limit=80" ) ) );
				
				case TYPE_TL_REPORTS:
					return parseReports( client.request( addRange( bBottom, PATH_TL_REPORTS ) ) );
				
				case TYPE_TL_NOTIFICATIONS:
					return parseNotifications( client.request( addRange( bBottom, PATH_TL_NOTIFICATIONS ) ) );
					
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
					case TYPE_TL_HOME:
					case TYPE_TL_LOCAL:
					case TYPE_TL_FEDERATE:
					case TYPE_TL_STATUSES:
					case TYPE_TL_FAVOURITES:
					case TYPE_TL_HASHTAG:
						mergeList( status_list, tmp_list_status, bBottom );
						break;
					
					case TYPE_TL_REPORTS:
						mergeList( report_list, tmp_list_report, bBottom );
						break;
					
					case TYPE_TL_NOTIFICATIONS:
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
