package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashSet;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.util.LogCategory;

public class UserRelation {
	
	private static final LogCategory log = new LogCategory( "UserRelation" );
	
	private static final String table = "user_relation";
	private static final String COL_TIME_SAVE = "time_save";
	public static final String COL_DB_ID = "db_id"; // SavedAccount のDB_ID
	public static final String COL_WHO_ID = "who_id"; // ターゲットアカウントのID
	private static final String COL_FOLLOWING = "following";
	private static final String COL_FOLLOWED_BY = "followed_by";
	private static final String COL_BLOCKING = "blocking";
	private static final String COL_MUTING = "muting";
	private static final String COL_REQUESTED = "requested";
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ","+COL_TIME_SAVE+" integer not null"
				+ ","+COL_DB_ID+" integer not null"
				+ ","+COL_WHO_ID+" integer not null"
				+ ","+COL_FOLLOWING+" integer not null"
				+ ","+COL_FOLLOWED_BY+" integer not null"
				+ ","+COL_BLOCKING+" integer not null"
				+ ","+COL_MUTING+" integer not null"
				+ ","+COL_REQUESTED+" integer not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_id on " + table + "("+COL_DB_ID+","+COL_WHO_ID+")"
		);
		db.execSQL(
			"create index if not exists " + table + "_time on " + table + "("+COL_TIME_SAVE+")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if(oldVersion < 6 && newVersion >= 6){
			onDBCreate( db );
		}
	}
	
	public static void deleteOld( long now){
		try{
			// 古いデータを掃除する
			long expire = now - 86400000L * 365;
			App1.getDB().delete( table,COL_TIME_SAVE+"<?",new String[]{Long.toString(expire)});
			
		}catch( Throwable ex ){
			log.e( ex, "deleteOld failed." );
		}
	}
	
	public static void save1( long now, long db_id, TootRelationShip src  ){
		try{

			ContentValues cv = new ContentValues();
			cv.put( COL_TIME_SAVE, now );
			cv.put( COL_DB_ID, db_id );
			cv.put( COL_WHO_ID, src.id );
			cv.put( COL_FOLLOWING, src.following ? 1: 0 );
			cv.put( COL_FOLLOWED_BY, src.followed_by ? 1: 0 );
			cv.put( COL_BLOCKING, src.blocking ? 1: 0 );
			cv.put( COL_MUTING, src.muting ? 1: 0 );
			cv.put( COL_REQUESTED, src.requested ? 1: 0 );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	public static void saveList( long now, long db_id, TootRelationShip.List src_list ){

		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_TIME_SAVE, now );
			cv.put( COL_DB_ID, db_id );

			boolean bOK = false;
			SQLiteDatabase db = App1.getDB();
			db.execSQL( "BEGIN TRANSACTION" );
			try{
				for( TootRelationShip src : src_list ){
					cv.put( COL_WHO_ID, src.id );
					cv.put( COL_FOLLOWING, src.following ? 1 : 0 );
					cv.put( COL_FOLLOWED_BY, src.followed_by ? 1 : 0 );
					cv.put( COL_BLOCKING, src.blocking ? 1 : 0 );
					cv.put( COL_MUTING, src.muting ? 1 : 0 );
					cv.put( COL_REQUESTED, src.requested ? 1 : 0 );
					db.replace( table, null, cv );
				}
				bOK = true;
			}catch( Throwable ex ){
				ex.printStackTrace();
				log.e( ex, "saveList failed." );
			}
			if( bOK ){
				db.execSQL( "COMMIT TRANSACTION" );
			}else{
				db.execSQL( "ROLLBACK TRANSACTION" );
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "saveList failed." );
		}
	}
	
	static final String load_where = COL_DB_ID+"=? and "+COL_WHO_ID+"=?";
		private static final ThreadLocal<String[]> load_where_arg = new ThreadLocal<String[]>() {
		@Override protected String[] initialValue() {
			return new String[2];
		}
	};
	
	public boolean following;
	public boolean followed_by;
	public boolean blocking;
	public boolean muting;
	public boolean requested;

	private UserRelation(){
	}

	public static UserRelation load( long db_id, long who_id ){
		try{
			String[] where_arg = load_where_arg.get();
			where_arg[0] = Long.toString( db_id );
			where_arg[1] = Long.toString( who_id );
			Cursor cursor = App1.getDB().query( table,null,load_where,where_arg,null,null,null );
			if( cursor != null){
				try{
					if( cursor.moveToNext() ){
						UserRelation dst = new UserRelation();
						dst.following = (0!=cursor.getInt( cursor.getColumnIndex( COL_FOLLOWING ) ));
						dst.followed_by = (0!=cursor.getInt( cursor.getColumnIndex( COL_FOLLOWED_BY ) ));
						dst.blocking = (0!=cursor.getInt( cursor.getColumnIndex( COL_BLOCKING ) ));
						dst.muting = (0!=cursor.getInt( cursor.getColumnIndex( COL_MUTING ) ));
						dst.requested = (0!=cursor.getInt( cursor.getColumnIndex( COL_REQUESTED ) ));
						return dst;
					}
				}finally{
					cursor.close();
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace(  );
			log.e(ex,"load failed.");
		}
		return new UserRelation();
	}

//	public static Cursor createCursor(){
//		return App1.getDB().query( table, null, null, null, null, null, COL_NAME + " asc" );
//	}
//
//	public static void delete( String name ){
//		try{
//			App1.getDB().delete( table, COL_NAME + "=?", new String[]{ name } );
//		}catch( Throwable ex ){
//			log.e( ex, "delete failed." );
//		}
//	}
//
//	public static HashSet< String > getNameSet(){
//		HashSet< String > dst = new HashSet<>();
//		try{
//			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
//			if( cursor != null ){
//				try{
//					int idx_name = cursor.getColumnIndex( COL_NAME );
//					while( cursor.moveToNext() ){
//						String s = cursor.getString( idx_name );
//						dst.add( s );
//					}
//				}finally{
//					cursor.close();
//				}
//			}
//		}catch( Throwable ex ){
//			ex.printStackTrace();
//		}
//		return dst;
//	}

//	private static final String[] isMuted_projection = new String[]{COL_NAME};
//	private static final String   isMuted_where = COL_NAME+"=?";
//	private static final ThreadLocal<String[]> isMuted_where_arg = new ThreadLocal<String[]>() {
//		@Override protected String[] initialValue() {
//			return new String[1];
//		}
//	};
//	public static boolean isMuted( String app_name ){
//		if( app_name == null ) return false;
//		try{
//			String[] where_arg = isMuted_where_arg.get();
//			where_arg[0] = app_name;
//			Cursor cursor = App1.getDB().query( table, isMuted_projection,isMuted_where , where_arg, null, null, null );
//			try{
//				if( cursor.moveToFirst() ){
//					return true;
//				}
//			}finally{
//				cursor.close();
//			}
//		}catch( Throwable ex ){
//			log.e( ex, "load failed." );
//		}
//		return false;
//	}
	
}
