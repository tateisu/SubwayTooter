package jp.juggler.subwaytooter;

import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class Column {
	static final LogCategory log = new LogCategory( "Column" );
	
	final ActMain activity;
	final SavedAccount access_info;
	final int type;
	final long who_id;
	
	static final int TYPE_TL_HOME = 1;
	static final int TYPE_TL_LOCAL = 2;
	static final int TYPE_TL_FEDERATE = 3;
	static final int TYPE_TL_STATUSES = 4;
	static final int TYPE_TL_FAVOURITES = 5;
	static final int TYPE_TL_REPORTS = 6;
	static final int TYPE_TL_NOTIFICATIONS = 7;
	
	public Column( ActMain activity, SavedAccount access_info, int type ){
		this( activity,access_info,type,access_info.id);
	}
	
	public Column( ActMain activity, SavedAccount access_info, int type ,long who_id,Object... params){
		this.activity = activity;
		this.access_info = access_info;
		this.type = type;
		this.who_id = who_id;
		startLoading();
	}
	
	final AtomicBoolean is_dispose = new AtomicBoolean();
	
	void dispose(){
		is_dispose.set( true );
	}
	
	public String getColumnName(){
		switch( type ){
		default:
			return access_info.getFullAcct( access_info ) + "\n" + "?";
		case TYPE_TL_HOME:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.home );
		case TYPE_TL_LOCAL:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.local_timeline );
		case TYPE_TL_FEDERATE:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.federate_timeline );
		
		case TYPE_TL_STATUSES:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.statuses_of
				, who_account != null ? access_info.getFullAcct( who_account ) : Long.toString( who_id )
			);
		
		case TYPE_TL_FAVOURITES:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.favourites );
		
		case TYPE_TL_REPORTS:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.reports );
		
		case TYPE_TL_NOTIFICATIONS:
			return access_info.getFullAcct( access_info ) + "\n" + activity.getString( R.string.notifications );
			
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
	
	private void fireVisualCallback(){
		Iterator< VisualCallback > it = visual_callback.iterator();
		while( it.hasNext() ){
			it.next().onVisualColumn();
		}
	}
	
	AsyncTask< Void, Void, TootApiResult > last_task;
	
	void cancelLastTask(){
		if( last_task != null ) last_task.cancel( true );
	}
	
	boolean is_loading = false;
	String task_progress;
	String error = null;
	
	final TootStatus.List status_list = new TootStatus.List();
	final TootReport.List report_list = new TootReport.List();
	final TootNotification.List notification_list = new TootNotification.List();
	volatile TootAccount who_account;
	
	public void reload(){
		status_list.clear();
		startLoading();
	}
	
	void startLoading(){
		error = null;
		is_loading = true;
		fireVisualCallback();
		cancelLastTask();
		
		AsyncTask< Void, Void, TootApiResult > task = this.last_task = new AsyncTask< Void, Void, TootApiResult >() {
			boolean __isCancelled(){
				return isCancelled();
			}
			
			TootStatus.List tmp_list_status;
			TootReport.List tmp_list_report;
			TootNotification.List tmp_list_notification;
			
			TootApiResult parseStatuses( TootApiResult result ){
				if( result != null ){
					tmp_list_status = TootStatus.parseList( log, result.array );
				}
				return result;
			}
			
			TootApiResult parseAccount( TootApiResult result ){
				if( result != null ){
					who_account = TootAccount.parse( log, result.object );
				}
				return result;
			}
			
			TootApiResult parseReports( TootApiResult result ){
				if( result != null ){
					tmp_list_report = TootReport.parseList( log, result.array );
				}
				return result;
			}
			
			TootApiResult parseNotifications( TootApiResult result ){
				if( result != null ){
					tmp_list_notification = TootNotification.parseList( log, result.array );
				}
				return result;
			}
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
					@Override
					public boolean isCancelled(){
						return __isCancelled() || is_dispose.get();
					}
					
					@Override
					public void publishProgress( final String s ){
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
				
				client.setAccessInfo( access_info );
				
				switch( type ){
				default:
				case TYPE_TL_HOME:
					return parseStatuses( client.get( "/api/v1/timelines/home" ) );
				
				case TYPE_TL_LOCAL:
					return parseStatuses( client.get( "/api/v1/timelines/public?local=1" ) );
				
				case TYPE_TL_FEDERATE:
					return parseStatuses( client.get( "/api/v1/timelines/public" ) );
				
				case TYPE_TL_STATUSES:
					if( who_account == null ){
						parseAccount( client.get( "/api/v1/accounts/" + who_id ) );
						client.callback.publishProgress( "" );
					}
					
					return parseStatuses( client.get( "/api/v1/accounts/"+who_id+"/statuses" ) );
				
				case TYPE_TL_FAVOURITES:
					return parseStatuses( client.get( "/api/v1/favourites" ) );
				
				case TYPE_TL_REPORTS:
					return parseReports( client.get( "/api/v1/reports" ) );
				
				case TYPE_TL_NOTIFICATIONS:
					return parseNotifications( client.get( "/api/v1/notifications" ) );
				}
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				is_loading = false;
				if( result == null ){
					Column.this.error = activity.getString( R.string.cancelled );
				}else if( result.error != null ){
					Column.this.error = result.error;
				}else{
					switch( type ){
					default:
					case TYPE_TL_HOME:
					case TYPE_TL_LOCAL:
					case TYPE_TL_FEDERATE:
					case TYPE_TL_STATUSES:
					case TYPE_TL_FAVOURITES:
						if( tmp_list_status != null ){
							for( int i = tmp_list_status.size() - 1 ; i >= 0 ; -- i ){
								status_list.add( 0, tmp_list_status.get( i ) );
							}
						}
						break;
					
					case TYPE_TL_REPORTS:
						if( tmp_list_report != null ){
							for( int i = tmp_list_report.size() - 1 ; i >= 0 ; -- i ){
								report_list.add( 0, tmp_list_report.get( i ) );
							}
						}
						break;
					
					case TYPE_TL_NOTIFICATIONS:
						if( tmp_list_notification != null ){
							for( int i = tmp_list_notification.size() - 1 ; i >= 0 ; -- i ){
								notification_list.add( 0, tmp_list_notification.get( i ) );
							}
						}
						break;
					}
					
				}
				fireVisualCallback();
			}
		};
		
		AsyncTaskCompat.executeParallel( task );
	}
}
