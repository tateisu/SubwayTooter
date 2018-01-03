package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.WordTrieTree;

public class HighlightWord {
	
	private static final LogCategory log = new LogCategory( "HighlightWord" );
	
	public static final int SOUND_TYPE_NONE = 0;
	public static final int SOUND_TYPE_DEFAULT = 1;
	public static final int SOUND_TYPE_CUSTOM = 2;
	
	public static final String table = "highlight_word";
	public static final String COL_ID = "_id";
	public static final String COL_NAME = "name";
	private static final String COL_TIME_SAVE = "time_save";
	private static final String COL_COLOR_BG = "color_bg";
	private static final String COL_COLOR_FG = "color_fg";
	private static final String COL_SOUND_TYPE = "sound_type";
	private static final String COL_SOUND_URI = "sound_uri";
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",name text not null"
				+ ",time_save integer not null"
				+ ",color_bg integer not null default 0"
				+ ",color_fg integer not null default 0"
				+ ",sound_type integer not null default 1"
				+ ",sound_uri text default null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_name on " + table + "(name)"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 21 && newVersion >= 21 ){
			onDBCreate( db );
		}
	}
	
	public long id = -1L;
	@NonNull public String name;
	public int color_bg;
	public int color_fg;
	public int sound_type;
	@Nullable public String sound_uri;
	
	public JSONObject encodeJson() throws JSONException{
		JSONObject dst = new JSONObject(  );
		dst.put(COL_ID,id);
		dst.put(COL_NAME,name);
		dst.put(COL_COLOR_BG,color_bg);
		dst.put(COL_COLOR_FG,color_fg);
		dst.put(COL_SOUND_TYPE,sound_type);
		if( sound_uri != null ) dst.put(COL_SOUND_URI,sound_uri);
		return dst;
	}

	public HighlightWord(@NonNull JSONObject src ){
		this.id = Utils.optLongX( src,COL_ID );
		String sv = Utils.optStringX(src,COL_NAME);
		if( TextUtils.isEmpty( sv )) throw new RuntimeException( "HighlightWord: name is empty" );
		this.name = sv;
		this.color_bg = src.optInt( COL_COLOR_BG );
		this.color_fg = src.optInt( COL_COLOR_FG );
		this.sound_type = src.optInt( COL_SOUND_TYPE );
		this.sound_uri = Utils.optStringX( src,COL_SOUND_URI );
	}
	
	
	public HighlightWord(@NonNull String name){
		this.name = name;
		this.sound_type = SOUND_TYPE_DEFAULT;
		this.color_fg = 0xFFFF0000;
	}
	
	public HighlightWord(@NonNull Cursor cursor){
		this.id = cursor.getLong( cursor.getColumnIndex( COL_ID ));
		this.name = cursor.getString( cursor.getColumnIndex( COL_NAME ));
		this.color_bg = cursor.getInt( cursor.getColumnIndex( COL_COLOR_BG ));
		this.color_fg = cursor.getInt( cursor.getColumnIndex( COL_COLOR_FG ));
		this.sound_type = cursor.getInt( cursor.getColumnIndex( COL_SOUND_TYPE ));
		int colIdx_sound_uri = cursor.getColumnIndex( COL_SOUND_URI );
		this.sound_uri = cursor.isNull( colIdx_sound_uri ) ? null : cursor.getString( colIdx_sound_uri);
	}
	
	private static final String selection_name = COL_NAME+"=?";
	private static final String selection_id = COL_ID+"=?";
	
	
	@Nullable public static HighlightWord load(@NonNull String name){
		try{
			Cursor cursor = App1.getDB().query( table, null, selection_name, new String[]{ name }, null, null, null );
			if( cursor != null ){
				try{
					if( cursor.moveToNext() ){
						return new HighlightWord( cursor );
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	public void save(){
		
		if( TextUtils.isEmpty( name )) throw new RuntimeException( "HighlightWord: name is empty" );
		
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_NAME, name );
			cv.put( COL_TIME_SAVE, System.currentTimeMillis() );
			cv.put( COL_COLOR_BG, color_bg );
			cv.put( COL_COLOR_FG, color_fg );
			cv.put( COL_SOUND_TYPE, sound_type );
			if( TextUtils.isEmpty(sound_uri) ){
				cv.putNull( COL_SOUND_URI );
				
			}else{
				cv.put( COL_SOUND_URI, sound_uri );
			}
			if( id == -1L ){
				App1.getDB().replace( table, null, cv );
			}else{
				App1.getDB().update( table, cv, selection_id, new String[]{ Long.toString( id ) } );
			}
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	
	public static Cursor createCursor(){
		return App1.getDB().query( table, null, null, null, null, null, COL_NAME + " asc" );
	}
	
	public void delete(){
		try{
			App1.getDB().delete( table, selection_id, new String[]{ Long.toString( id ) } );
		}catch( Throwable ex ){
			log.e( ex, "delete failed." );
		}
	}
	
	private static final String[] columns_name = new String[]{ COL_NAME };
	
	
	@Nullable public static WordTrieTree getNameSet(){
		WordTrieTree dst = null;
		try{
			Cursor cursor = App1.getDB().query( table, columns_name, null, null, null, null, null );
			if( cursor != null ){
				try{
					int idx_name = cursor.getColumnIndex( COL_NAME );
					while( cursor.moveToNext() ){
						if( dst == null) dst = new WordTrieTree();
						String s = cursor.getString( idx_name );
						dst.add( s );
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return dst;
	}
	
}
