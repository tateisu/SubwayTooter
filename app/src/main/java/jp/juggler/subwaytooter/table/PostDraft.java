package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class PostDraft {
	
	private static final LogCategory log = new LogCategory( "PostDraft" );
	
	private static final String table = "post_draft";
	private static final String COL_TIME_SAVE = "time_save";
	private static final String COL_JSON = "json";
	private static final String COL_HASH = "hash";
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ "," + COL_TIME_SAVE + " integer not null"
				+ "," + COL_JSON + " text not null"
				+ "," + COL_HASH + " text not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_hash on " + table + "(" + COL_HASH + ")"
		);
		db.execSQL(
			"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 12 && newVersion >= 12 ){
			onDBCreate( db );
		}
	}
	
	public static void deleteOld( long now ){
		try{
			// 古いデータを掃除する
			long expire = now - 86400000L * 30;
			App1.getDB().delete( table, COL_TIME_SAVE + "<?", new String[]{ Long.toString( expire ) } );
		}catch( Throwable ex ){
			log.e( ex, "deleteOld failed." );
		}
	}
	
	public static void save( long now, @NonNull JSONObject json ){

		deleteOld(now);

		try{
			// make hash
			StringBuilder sb = new StringBuilder(  );
			ArrayList<String> keys = new ArrayList<>(  );
			for( Iterator<String> it = json.keys(); it.hasNext() ;){
				keys.add( it.next() );
			}
			Collections.sort( keys );
			for( String k : keys ){
				String v = json.isNull( k ) ? "(null)" : json.opt(k).toString();
				sb.append( "&");
				sb.append( k );
				sb.append( "=");
				sb.append( v );
			}
			String hash = Utils.digestSHA256( sb.toString() );

			// save to db
			ContentValues cv = new ContentValues();
			cv.put( COL_TIME_SAVE, now );
			cv.put( COL_JSON, json.toString() );
			cv.put( COL_HASH, hash );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	
//	public static void saveList( long now, String[] src_list, int offset, int length ){
//
//		try{
//			ContentValues cv = new ContentValues();
//			cv.put( COL_TIME_SAVE, now );
//
//			boolean bOK = false;
//			SQLiteDatabase db = App1.getDB();
//			db.execSQL( "BEGIN TRANSACTION" );
//			try{
//				for( int i = 0 ; i < length ; ++ i ){
//					String acct = src_list[ i + offset ];
//					cv.put( COL_ACCT, acct );
//					db.replace( table, null, cv );
//				}
//				bOK = true;
//			}catch( Throwable ex ){
//				ex.printStackTrace();
//				log.e( ex, "saveList failed." );
//			}
//			if( bOK ){
//				db.execSQL( "COMMIT TRANSACTION" );
//			}else{
//				db.execSQL( "ROLLBACK TRANSACTION" );
//			}
//		}catch( Throwable ex ){
//			ex.printStackTrace();
//			log.e( ex, "saveList failed." );
//		}
//	}
	
	
	@NonNull public static ArrayList< JSONObject > loadList(int limit){
		ArrayList< JSONObject > result = new ArrayList<>(  );
		try{
			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, COL_TIME_SAVE + " desc limit "+limit );
			if( cursor != null ){
				try{
					int idx_json = cursor.getColumnIndex( COL_JSON );
					while( cursor.moveToNext() ){
						result.add( new JSONObject( cursor.getString( idx_json ) ));
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "searchPrefix failed." );
		}
		return result;
	}
	
}
