package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;

public class AppDataExporter {
	
	static final LogCategory log = new LogCategory( "AppDataExporter" );
	
	private static void writeJSONObject( @NonNull JsonWriter writer, @NonNull JSONObject src ) throws IOException, JSONException{
		writer.beginObject();
		for( Iterator< String > it = src.keys() ; it.hasNext() ; ){
			String k = it.next();
			if( src.isNull( k ) ){
				writer.name( k );
				writer.nullValue();
			}else{
				Object o = src.get( k );
				
				if( o instanceof String ){
					writer.name( k );
					writer.value( (String) o );
					
				}else if( o instanceof Boolean ){
					writer.name( k );
					writer.value( (Boolean) o );
					
				}else if( o instanceof Number ){
					
					writer.name( k );
					writer.value( (Number) o );
					
				}else{
					throw new RuntimeException( String.format( Locale.JAPAN, "bad data type: JSONObject key =%s", k ) );
				}
			}
		}
		writer.endObject();
	}
	
	private static JSONObject readJsonObject( JsonReader reader ) throws IOException, JSONException{
		JSONObject dst = new JSONObject();
		
		reader.beginObject();
		while( reader.hasNext() ){
			String name = reader.nextName();
			JsonToken token = reader.peek();
			switch( token ){
			
			case NULL:
				reader.nextNull();
				break;
			
			case STRING:
				dst.put( name, reader.nextString() );
				break;
			
			case BOOLEAN:
				dst.put( name, reader.nextBoolean() );
				break;
			
			case NUMBER:
				dst.put( name, reader.nextDouble() );
				break;
			
			default:
				throw new RuntimeException( String.format( Locale.JAPAN, "bad data type: %s key =%s", token, name ) );
			}
		}
		reader.endObject();
		
		return dst;
	}
	
	private static void writeFromTable( @NonNull JsonWriter writer, String json_key, String table ) throws IOException{
		
		writer.name( json_key );
		writer.beginArray();
		
		Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
		try{
			ArrayList< String > names = new ArrayList<>();
			int column_count = cursor.getColumnCount();
			for( int i = 0 ; i < column_count ; ++ i ){
				names.add( cursor.getColumnName( i ) );
			}
			while( cursor.moveToNext() ){
				writer.beginObject();
				
				for( int i = 0 ; i < column_count ; ++ i ){
					switch( cursor.getType( i ) ){
					case Cursor.FIELD_TYPE_NULL:
						writer.name( names.get( i ) );
						writer.nullValue();
						break;
					case Cursor.FIELD_TYPE_INTEGER:
						writer.name( names.get( i ) );
						writer.value( cursor.getLong( i ) );
						break;
					case Cursor.FIELD_TYPE_STRING:
						writer.name( names.get( i ) );
						writer.value( cursor.getString( i ) );
						break;
					
					case Cursor.FIELD_TYPE_FLOAT:
						Double d = cursor.getDouble( i );
						if( Double.isNaN( d ) || Double.isInfinite( d ) ){
							log.w( "column %s is nan or infinite value.", names.get( i ) );
						}else{
							writer.name( names.get( i ) );
							writer.value( d );
						}
						break;
					case Cursor.FIELD_TYPE_BLOB:
						log.w( "column %s is blob.", names.get( i ) );
						break;
					}
				}
				
				writer.endObject();
			}
		}finally{
			cursor.close();
		}
		writer.endArray();
	}
	
	private static void importTable( JsonReader reader, String table, HashMap< Long, Long > id_map ) throws IOException{
		SQLiteDatabase db = App1.getDB();
		if( table.equals( SavedAccount.table ) ){
			SavedAccount.onDBDelete( db );
			SavedAccount.onDBCreate( db );
		}
		
		db.execSQL( "BEGIN TRANSACTION" );
		try{
			db.execSQL( "delete from " + table );
			
			ContentValues cv = new ContentValues();
			
			reader.beginArray();
			while( reader.hasNext() ){
				
				long old_id = - 1L;
				cv.clear();
				
				reader.beginObject();
				while( reader.hasNext() ){
					String name = reader.nextName();
					
					if( BaseColumns._ID.equals( name ) ){
						old_id = reader.nextLong();
						continue;
					}
					
					if( SavedAccount.table.equals( table ) ){
						// 一時的に存在したが現在のDBスキーマにはない項目は読み飛ばす
						if( "nickname".equals( name )
							|| "color".equals( name )
							){
							reader.skipValue();
							continue;
						}
						
						// リアルタイム通知に関連する項目は読み飛ばす
						if( SavedAccount.COL_NOTIFICATION_TAG.equals( name )
							|| SavedAccount.COL_REGISTER_KEY.equals( name )
							|| SavedAccount.COL_REGISTER_TIME.equals( name )
							){
							reader.skipValue();
							continue;
						}
					}
					
					JsonToken token = reader.peek();
					switch( token ){
					case NULL:
						reader.skipValue();
						cv.putNull( name );
						break;
					
					case BOOLEAN:
						cv.put( name, reader.nextBoolean() ? 1 : 0 );
						break;
					
					case NUMBER:
						cv.put( name, reader.nextLong() );
						break;
					
					case STRING:
						cv.put( name, reader.nextString() );
						break;
					
					default:
						reader.skipValue();
						// 無視する
						break;
					}
				}
				reader.endObject();
				long new_id = db.insertWithOnConflict( table, null, cv, SQLiteDatabase.CONFLICT_REPLACE );
				if( new_id == - 1L ){
					throw new RuntimeException( "importTable: invalid row_id" );
				}
				if( id_map != null ){
					id_map.put( old_id, new_id );
				}
			}
			reader.endArray();
			db.execSQL( "COMMIT TRANSACTION" );
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "importTable failed." );
			try{
				db.execSQL( "ROLLBACK TRANSACTION" );
			}catch( Throwable ignored ){
			}
			throw ex;
		}
	}
	
	static final double MAGIC_NAN = (double) -76287755398823900d;
	
	private static void writePref( JsonWriter writer, SharedPreferences pref ) throws IOException{
		writer.beginObject();
		for( Map.Entry< String, ? > entry : pref.getAll().entrySet() ){
			String k = entry.getKey();
			Object v = entry.getValue();
			writer.name( k );
			if( v == null ){
				writer.nullValue();
			}else if( v instanceof String ){
				writer.value( (String) v );
			}else if( v instanceof Boolean ){
				writer.value( (Boolean) v );
			}else if( v instanceof Number ){
				if( v instanceof Double ){
					if( Double.isNaN( (Double) v ) ){
						writer.value( MAGIC_NAN );
						continue;
					}
				}else if( v instanceof Float ){
					if( Float.isNaN( (Float) v ) ){
						writer.value( MAGIC_NAN );
						continue;
					}
				}
				writer.value( (Number) v );
			}else{
				throw new RuntimeException( String.format( Locale.JAPAN, "writePref. bad data type: Preference key =%s", k ) );
			}
		}
		writer.endObject();
	}
	
	private static void importPref( JsonReader reader, SharedPreferences pref ) throws IOException{
		SharedPreferences.Editor e = pref.edit();
		reader.beginObject();
		while( reader.hasNext() ){
			String k = reader.nextName();
			if( k == null ){
				throw new RuntimeException( "importPref: name is null" );
			}
			JsonToken token = reader.peek();
			if( token == JsonToken.NULL ){
				reader.nextNull();
				e.remove( k );
				continue;
			}
			switch( k ){
			// boolean
			case Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN:
			case Pref.KEY_PRIOR_LOCAL_URL:
			case Pref.KEY_DISABLE_FAST_SCROLLER:
			case Pref.KEY_SIMPLE_LIST:
			case Pref.KEY_NOTIFICATION_SOUND:
			case Pref.KEY_NOTIFICATION_VIBRATION:
			case Pref.KEY_NOTIFICATION_LED:
			case Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN:
			case Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR:
			case Pref.KEY_DONT_ROUND:
			case Pref.KEY_DONT_USE_STREAMING:
			case Pref.KEY_DONT_REFRESH_ON_RESUME:
			case Pref.KEY_DONT_SCREEN_OFF:
			case Pref.KEY_DISABLE_TABLET_MODE:
			case Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL:
			case Pref.KEY_PRIOR_CHROME:
			case Pref.KEY_POST_BUTTON_BAR_AT_TOP:
			case Pref.KEY_DONT_DUPLICATION_CHECK:
			case Pref.KEY_QUICK_TOOT_BAR:
				boolean bv = reader.nextBoolean();
				e.putBoolean( k, bv );
				break;
			
			// int
			case Pref.KEY_BACK_BUTTON_ACTION:
			case Pref.KEY_UI_THEME:
			case Pref.KEY_RESIZE_IMAGE:
			case Pref.KEY_REFRESH_AFTER_TOOT:
			case Pref.KEY_FOOTER_BUTTON_BG_COLOR:
			case Pref.KEY_FOOTER_BUTTON_FG_COLOR:
			case Pref.KEY_FOOTER_TAB_BG_COLOR:
			case Pref.KEY_FOOTER_TAB_DIVIDER_COLOR:
			case Pref.KEY_FOOTER_TAB_INDICATOR_COLOR:
			case Pref.KEY_LAST_COLUMN_POS:
				int iv = reader.nextInt();
				e.putInt( k, iv );
				break;
			
			// long
			case Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT:
				long lv = reader.nextLong();
				e.putLong( k, lv );
				break;
			
			// string
			case Pref.KEY_COLUMN_WIDTH:
			case Pref.KEY_MEDIA_THUMB_HEIGHT:
			case Pref.KEY_STREAM_LISTENER_CONFIG_URL:
			case Pref.KEY_STREAM_LISTENER_SECRET:
			case Pref.KEY_STREAM_LISTENER_CONFIG_DATA:
			case Pref.KEY_CLIENT_NAME:
			case Pref.KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN:
				String sv = reader.nextString();
				e.putString( k, sv );
				break;
			
			// double
			case Pref.KEY_TIMELINE_FONT_SIZE:
			case Pref.KEY_ACCT_FONT_SIZE:
				double dv = reader.nextDouble();
				if( dv <= MAGIC_NAN ){
					e.putFloat( k, Float.NaN );
				}else{
					e.putFloat( k, (float) dv );
				}
				break;
			
			// force reset
			default:
			case Pref.KEY_TIMELINE_FONT:
				reader.skipValue();
				e.remove( k );
				break;
			
			// just ignore
			case "device_token":
			case "install_id":
				reader.skipValue();
				e.remove( k );
				break;
			}
		}
		reader.endObject();
		e.apply();
	}
	
	private static void writeColumn( AppState app_state, JsonWriter writer ) throws IOException, JSONException{
		writer.beginArray();
		for( Column column : app_state.column_list ){
			JSONObject dst = new JSONObject();
			column.encodeJSON( dst, 0 );
			writeJSONObject( writer, dst );
		}
		writer.endArray();
	}
	
	private static @NonNull
	ArrayList< Column > readColumn( AppState app_state, JsonReader reader, HashMap< Long, Long > id_map ) throws IOException, JSONException{
		ArrayList< Column > result = new ArrayList<>();
		reader.beginArray();
		while( reader.hasNext() ){
			JSONObject item = readJsonObject( reader );
			long old_id = item.optLong( Column.KEY_ACCOUNT_ROW_ID, - 1L );
			if( old_id == -1L ){
				// 検索カラムは NAアカウントと紐ついている。変換の必要はない
			}else{
				Long new_id = id_map.get( old_id );
				if( new_id == null ){
					throw new RuntimeException( "readColumn: can't convert account id" );
				}
				item.put( Column.KEY_ACCOUNT_ROW_ID, (long) new_id );
			}
			try{
				result.add( new Column( app_state, item ) );
			}catch( Throwable ex ){
				ex.printStackTrace();
				log.e( ex, "column load failed." );
				throw ex;
			}
		}
		reader.endArray();
		return result;
	}
	
	private static final String KEY_PREF = "pref";
	private static final String KEY_ACCOUNT = "account";
	private static final String KEY_COLUMN = "column";
	private static final String KEY_ACCT_COLOR = "acct_color";
	private static final String KEY_MUTED_APP = "muted_app";
	private static final String KEY_MUTED_WORD = "muted_word";
	
	static void encodeAppData( Context context, JsonWriter writer )
		throws IOException, JSONException{
		writer.setIndent( " " );
		writer.beginObject();
		
		AppState app_state = App1.getAppState( context );
		//////////////////////////////////////
		{
			writer.name( KEY_PREF );
			writePref( writer, app_state.pref );
		}
		//////////////////////////////////////
		writeFromTable( writer, KEY_ACCOUNT, SavedAccount.table );
		writeFromTable( writer, KEY_ACCT_COLOR, AcctColor.table );
		writeFromTable( writer, KEY_MUTED_APP, MutedApp.table );
		writeFromTable( writer, KEY_MUTED_WORD, MutedWord.table );
		
		//////////////////////////////////////
		{
			writer.name( KEY_COLUMN );
			writeColumn( app_state, writer );
			
		}
		
		writer.endObject();
	}
	
	static ArrayList< Column > decodeAppData( Context context, JsonReader reader )
		throws IOException, JSONException{
		
		ArrayList< Column > result = null;
		
		AppState app_state = App1.getAppState( context );
		reader.beginObject();
		
		@SuppressLint("UseSparseArrays")
		HashMap< Long, Long > account_id_map = new HashMap<>();
		
		while( reader.hasNext() ){
			String name = reader.nextName();
			
			if( KEY_PREF.equals( name ) ){
				importPref( reader, app_state.pref );
				
			}else if( KEY_ACCOUNT.equals( name ) ){
				importTable( reader, SavedAccount.table, account_id_map );
				
			}else if( KEY_ACCT_COLOR.equals( name ) ){
				importTable( reader, AcctColor.table, null );
				AcctColor.clearMemoryCache();
			}else if( KEY_MUTED_APP.equals( name ) ){
				importTable( reader, MutedApp.table, null );
				
			}else if( KEY_MUTED_WORD.equals( name ) ){
				importTable( reader, MutedWord.table, null );
				
			}else if( KEY_COLUMN.equals( name ) ){
				result = readColumn( app_state, reader, account_id_map );
			}
		}
		
		{
			long old_id = app_state.pref.getLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L );
			if( old_id != -1L ){
				Long new_id = account_id_map.get( old_id );
				app_state.pref.edit().putLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, ( new_id != null ? new_id : - 1L ) ).apply();
			}
		}
		
		if( result == null ){
			throw new RuntimeException( "import data does not includes column list!" );
		}
		
		return result;
	}
	
}
