package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class NotificationTracking {
	
	private static final LogCategory log = new LogCategory( "NotificationTracking" );
	
	private static final String table = "noti_trac";
	
	private static final String COL_ID = BaseColumns._ID;
	
	// アカウントDBの行ID。 サーバ側のIDではない
	private static final String COL_ACCOUNT_DB_ID = "a";
	
	// サーバから通知を取得した時刻
	private static final String COL_LAST_LOAD = "ll";
	
	// サーバから最後に読んだデータ。既読は排除されてるかも
	private static final String COL_LAST_DATA = "ld";
	
	// 通知ID。ここまで既読
	private static final String COL_NID_READ = "nr";
	
	// 通知ID。もっとも最近取得したもの
	private static final String COL_NID_SHOW = "ns";
	
	// 最後に表示した通知のID
	private static final String COL_POST_ID = "pi";
	
	// 最後に表示した通知の作成時刻
	private static final String COL_POST_TIME = "pt";
	
	public static void onDBCreate( SQLiteDatabase db ){

		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",a integer not null"
				+ ",ll integer default 0"
				+ ",ld text"
				+ ",nr integer default 0"
				+ ",ns integer default 0"
				+ ",pi integer default 0"
				+ ",pt integer default 0"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_a on " + table + "(a)"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 2 && newVersion >= 2 ){
			onDBCreate( db );
		}
	}
	
	private long id = -1L;
	private long account_db_id;
	public long last_load;
	public long nid_read;
	public long nid_show;
	
	public long post_id;
	public long post_time;
	
	public String last_data;
	
	private static final String WHERE_AID = COL_ACCOUNT_DB_ID + "=?";
	
	public static NotificationTracking load( long account_db_id ){
		NotificationTracking dst = new NotificationTracking();
		dst.account_db_id = account_db_id;
		try{
			Cursor cursor = App1.getDB().query( table, null,WHERE_AID, new String[]{ Long.toString( account_db_id ) }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					dst.id = cursor.getLong( cursor.getColumnIndex( COL_ID ) );
					dst.last_load = cursor.getLong( cursor.getColumnIndex( COL_LAST_LOAD ) );
					dst.nid_read = cursor.getLong( cursor.getColumnIndex( COL_NID_READ ) );
					dst.nid_show = cursor.getLong( cursor.getColumnIndex( COL_NID_SHOW ) );
					
					dst.post_id = cursor.getLong( cursor.getColumnIndex( COL_POST_ID ) );
					dst.post_time = cursor.getLong( cursor.getColumnIndex( COL_POST_TIME ) );
					
					int idx_last_data = cursor.getColumnIndex( COL_LAST_DATA );
					dst.last_data = cursor.isNull( idx_last_data ) ? null : cursor.getString( idx_last_data );

					log.d("load account_db_id=%s,post=%s,%s last_data=%s"
						,account_db_id
						,dst.post_id
						,dst.post_time
						,(dst.last_data ==null ? "null" : ""+dst.last_data.length())
					);
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "load failed." );
		}
		return dst;
	}
	
	public void save(){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_ACCOUNT_DB_ID, account_db_id );
			cv.put( COL_LAST_LOAD, last_load );
			cv.put( COL_NID_READ, nid_read );
			cv.put( COL_NID_SHOW, nid_show );
			cv.put( COL_LAST_DATA, last_data );
			cv.put( COL_POST_ID, post_id);
			cv.put( COL_POST_TIME, post_time);
			if( id == -1L ){
				id = App1.getDB().insert( table,null,cv );
				log.d("save.insert account_db_id=%s,post=%s,%s last_data=%s"
					,account_db_id
					,post_id
					,post_time
					,(last_data ==null ? "null" : ""+last_data.length())
				);
				
			}else{
				App1.getDB().update( table, cv, WHERE_AID, new String[]{ Long.toString( account_db_id ) } );
				log.d("save.update account_db_id=%s,post=%s,%s last_data=%s"
					,account_db_id
					,post_id
					,post_time
					,(last_data ==null ? "null" : ""+last_data.length())
				);
			}
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	
	public void updatePost(long post_id,long post_time){
		this.post_id = post_id;
		this.post_time = post_time;
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_POST_ID, post_id );
			cv.put( COL_POST_TIME, post_time );
			int rows = App1.getDB().update( table, cv,WHERE_AID, new String[]{ Long.toString( account_db_id ) } );
			log.d("updatePost account_db_id=%s,post=%s,%s last_data=%s,update_rows=%s"
				,account_db_id
				,post_id
				,post_time
				,(last_data ==null ? "null" : ""+last_data.length())
				,rows
			);
		}catch( Throwable ex ){
			log.e( ex, "updatePost failed." );
		}
	}
	
	public static void updateRead(long account_db_id){
		try{
			String[] where_args = new String[]{ Long.toString( account_db_id ) };
			Cursor cursor = App1.getDB().query( table, new String[]{ COL_NID_SHOW,COL_NID_READ }, WHERE_AID, where_args, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					long nid_show = cursor.getLong( cursor.getColumnIndex( COL_NID_SHOW ) );
					long nid_read = cursor.getLong( cursor.getColumnIndex( COL_NID_READ ) );
					log.d("updateRead account_db_id=%s, nid_show=%s, nid_read=%s",account_db_id,nid_show,nid_read);
					ContentValues cv = new ContentValues();
					cv.put( COL_NID_READ, nid_show );
					App1.getDB().update( table, cv, WHERE_AID,where_args );
				}else{
					log.e("updateRead no row in query.");
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "updateRead failed." );
		}
	}
	
	public static void resetPostAll(){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_POST_ID, 0 );
			cv.put( COL_POST_TIME, 0 );
			App1.getDB().update( table, cv,null,null);
			
		}catch( Throwable ex ){
			log.e( ex, "resetPostAll failed." );
		}
	}
	
	public static void resetLastLoad(long db_id){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_LAST_LOAD, 0 );
			App1.getDB().update( table, cv, COL_ACCOUNT_DB_ID+"=?",new String[]{ Long.toString( db_id )});
		}catch( Throwable ex ){
			log.e( ex, "resetLastLoad(db_id) failed." );
		}
	}

	public static void resetLastLoad(){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_LAST_LOAD, 0 );
			App1.getDB().update( table, cv,null,null);
		}catch( Throwable ex ){
			log.e( ex, "resetLastLoad() failed." );
		}
	}
}
