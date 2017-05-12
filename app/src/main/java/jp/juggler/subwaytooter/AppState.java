package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.PostAttachment;
import jp.juggler.subwaytooter.util.Utils;

class AppState {
	
	final Context context;
	final float density;
	final SharedPreferences pref;
	final Handler handler;
	
	final StreamReader stream_reader;
	
	AppState( Context applicationContext ,SharedPreferences pref){
		this.context = applicationContext;
		this.pref = pref;
		this.density = context.getResources().getDisplayMetrics().density;
		this.stream_reader = new StreamReader(applicationContext,pref);
		this.handler = new Handler();
		
		loadColumnList();
	}
	
	// データ保存用 および カラム一覧への伝達用
	static void saveColumnList( Context context,String fileName, JSONArray array ){
		
		try{
			OutputStream os = context.openFileOutput( fileName, Context.MODE_PRIVATE );
			try{
				os.write( Utils.encodeUTF8( array.toString() ) );
			}finally{
				os.close();
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( context, ex, "saveColumnList failed." );
		}
	}
	
	// データ保存用 および カラム一覧への伝達用
	static JSONArray loadColumnList( Context context,String fileName ){
		try{
			InputStream is = context.openFileInput( fileName );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream( is.available() );
				IOUtils.copy( is,bao);
				return new JSONArray( Utils.decodeUTF8( bao.toByteArray() ) );
			}finally{
				is.close();
			}
		}catch( FileNotFoundException ignored ){
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( context, ex, "loadColumnList failed." );
		}
		return null;
	}

	
	private static final String FILE_COLUMN_LIST = "column_list";
	final ArrayList< Column > column_list = new ArrayList<>();
	
	JSONArray encodeColumnList(){
		JSONArray array = new JSONArray();
		for( int i = 0, ie = column_list.size() ; i < ie ; ++ i ){
			Column column = column_list.get( i );
			try{
				JSONObject dst = new JSONObject();
				column.encodeJSON( dst, i );
				array.put( dst );
			}catch( JSONException ex ){
				ex.printStackTrace();
			}
		}
		return array;
	}
	
	void saveColumnList(){
		JSONArray array = encodeColumnList();
		saveColumnList( context,FILE_COLUMN_LIST, array );
		
	}
	
	private void loadColumnList(){
		JSONArray array = loadColumnList( context,FILE_COLUMN_LIST );
		if( array != null ){
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				try{
					JSONObject src = array.optJSONObject( i );
					Column col = new Column( this,src );
					column_list.add( col );
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
			}
		}
	}
	
	//////////////////////////////////////////////////////
	
	final HashSet< String > map_busy_fav = new HashSet<>();
	
	boolean isBusyFav( SavedAccount account, TootStatus status ){
		String busy_key = account.host + ":" + status.id;
		return map_busy_fav.contains( busy_key );
	}
	
	//////////////////////////////////////////////////////

	final HashSet< String > map_busy_boost = new HashSet<>();
	
	boolean isBusyBoost( SavedAccount account, TootStatus status ){
		String busy_key = account.host + ":" + status.id;
		return map_busy_boost.contains( busy_key );
	}
	
	//////////////////////////////////////////////////////
	

	ArrayList< PostAttachment > attachment_list = null;
	
}
