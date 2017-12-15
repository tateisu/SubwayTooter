package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
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
	private static final String COL_ID = BaseColumns._ID;
	private static final String COL_TIME_SAVE = "time_save";
	private static final String COL_JSON = "json";
	private static final String COL_HASH = "hash";
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(" + COL_ID + " INTEGER PRIMARY KEY"
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
	
	private static void deleteOld( long now ){
		try{
			// 古いデータを掃除する
			long expire = now - 86400000L * 30;
			App1.getDB().delete( table, COL_TIME_SAVE + "<?", new String[]{ Long.toString( expire ) } );
		}catch( Throwable ex ){
			log.e( ex, "deleteOld failed." );
		}
	}
	
	public static void save( long now, @NonNull JSONObject json ){
		
		deleteOld( now );
		
		try{
			// make hash
			StringBuilder sb = new StringBuilder();
			ArrayList< String > keys = new ArrayList<>();
			for( Iterator< String > it = json.keys() ; it.hasNext() ; ){
				keys.add( it.next() );
			}
			Collections.sort( keys );
			for( String k : keys ){
				String v = json.isNull( k ) ? "(null)" : json.opt( k ).toString();
				sb.append( "&" );
				sb.append( k );
				sb.append( "=" );
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

	public static boolean hasDraft(){
		try{
			Cursor cursor = App1.getDB().query( table, new String[]{ "count(*)" }, null, null, null, null, null );
			if( cursor != null ){
				try{
					if( cursor.moveToNext() ){
						int count = cursor.getInt( 0 );
						return count > 0;
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "hasDraft failed." );
		}
		return false;
	}
	
	public static Cursor createCursor(){
		try{
			return App1.getDB().query( table, null, null, null, null, null, COL_TIME_SAVE + " desc" );
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "createCursor failed." );
		}
		return null;
	}
	
	public static class ColIdx {
		final int idx_id;
		final int idx_time_save;
		final int idx_json;
		final int idx_hash;
		
		public ColIdx( Cursor cursor ){
			idx_id = cursor.getColumnIndex( COL_ID );
			idx_time_save = cursor.getColumnIndex( COL_TIME_SAVE );
			idx_json = cursor.getColumnIndex( COL_JSON );
			idx_hash = cursor.getColumnIndex( COL_HASH );
		}
		
	}
	
	@SuppressWarnings("WeakerAccess") public long id;
	@SuppressWarnings("WeakerAccess") public long time_save;
	@SuppressWarnings("WeakerAccess") public JSONObject json;
	@SuppressWarnings("WeakerAccess") public String hash;
	
	public static PostDraft loadFromCursor( Cursor cursor, ColIdx colIdx, int position ){
		if( colIdx == null ) colIdx = new ColIdx( cursor );

		if( ! cursor.moveToPosition( position ) ){
			log.d("loadFromCursor: move failed. position=%s",position);
			return null;
		}

		PostDraft dst = new PostDraft();
		dst.id = cursor.getLong( colIdx.idx_id );
		dst.time_save = cursor.getLong( colIdx.idx_time_save );
		try{
			dst.json = new JSONObject( cursor.getString( colIdx.idx_json ) );
		}catch( Throwable ex ){
			log.trace( ex );
			dst.json = new JSONObject();
		}
		dst.hash = cursor.getString( colIdx.idx_hash );
		return dst;
	}
	
	public void delete(){
		try{
			App1.getDB().delete( table, COL_ID + "=?", new String[]{ Long.toString( id ) } );
		}catch( Throwable ex ){
			log.e( ex, "delete failed." );
		}
	}
	
}
