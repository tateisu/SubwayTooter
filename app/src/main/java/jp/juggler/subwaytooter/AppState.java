package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.TextUtils;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyClickableSpan;
import jp.juggler.subwaytooter.util.PostAttachment;
import jp.juggler.subwaytooter.util.Utils;

class AppState {
	static final LogCategory log = new LogCategory( "AppState" );
	final Context context;
	final float density;
	final SharedPreferences pref;
	final Handler handler;
	
	final StreamReader stream_reader;
	
	int media_thumb_height;
	
	AppState( Context applicationContext, SharedPreferences pref ){
		this.context = applicationContext;
		this.pref = pref;
		this.density = context.getResources().getDisplayMetrics().density;
		this.stream_reader = new StreamReader( applicationContext, pref );
		this.handler = new Handler();
		
		loadColumnList();
	}
	
	// データ保存用 および カラム一覧への伝達用
	static void saveColumnList( Context context, String fileName, JSONArray array ){
		
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
	static JSONArray loadColumnList( Context context, String fileName ){
		try{
			InputStream is = context.openFileInput( fileName );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream( is.available() );
				IOUtils.copy( is, bao );
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
		saveColumnList( context, FILE_COLUMN_LIST, array );
		
	}
	
	private void loadColumnList(){
		JSONArray array = loadColumnList( context, FILE_COLUMN_LIST );
		if( array != null ){
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				try{
					JSONObject src = array.optJSONObject( i );
					Column col = new Column( this, src );
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
	
	TextToSpeech tts;
	
	void setTextToSpeech( @NonNull TextToSpeech tmp_tts ){
		this.tts = tmp_tts;
		tts.setOnUtteranceProgressListener( new UtteranceProgressListener() {
			@Override public void onStart( String utteranceId ){
				log.d( "UtteranceProgressListener.onStart id=%s", utteranceId );
			}
			
			@Override public void onDone( String utteranceId ){
				log.d( "UtteranceProgressListener.onStart id=%s", utteranceId );
				flushSpeechQueue();
			}
			
			@Override public void onError( String utteranceId ){
				log.d( "UtteranceProgressListener.onError id=%s", utteranceId );
				flushSpeechQueue();
			}
		} );
	}
	
	static final Pattern reURL = Pattern.compile( "\\bhttp(s)?://[:/?#@!$&'.,;=%()*+\\w\\-\\[\\]]+\\b" );
	static final Pattern reSpaces = Pattern.compile( "[\\s　]+" );
	
	private static Spannable getStatusText( TootStatus status ){
		if( status == null ){
			return null;
		}
		
		if( ! TextUtils.isEmpty( status.decoded_spoiler_text ) ){
			return status.decoded_spoiler_text;
		}
		
		if( ! TextUtils.isEmpty( status.decoded_content ) ){
			return status.decoded_content;
		}
		
		return null;
	}
	
	void addSpeech( TootStatus status ){
		
		if( tts == null ) return;
		
		final Spannable text = getStatusText( status );
		if( text == null || text.length() == 0 ) return;
		
		MyClickableSpan[] span_list = text.getSpans( 0, text.length(), MyClickableSpan.class );
		if( span_list == null || span_list.length == 0 ){
			addSpeech( text.toString() );
			return;
		}
		Arrays.sort( span_list, new Comparator< MyClickableSpan >() {
			@Override public int compare( MyClickableSpan a, MyClickableSpan b ){
				int a_start = text.getSpanStart( a );
				int b_start = text.getSpanStart( b );
				return a_start - b_start;
			}
		} );
		String str_text = text.toString();
		StringBuilder sb = new StringBuilder();
		int last_end = 0;
		boolean has_url = false;
		for( MyClickableSpan span : span_list ){
			int start = text.getSpanStart( span );
			int end = text.getSpanEnd( span );
			//
			if( start > last_end ){
				sb.append( str_text.substring( last_end, start ) );
			}
			last_end = end;
			//
			String span_text = str_text.substring( start, end );
			if( span_text.length() > 0 ){
				char c = span_text.charAt( 0 );
				if( c == '#' || c == '@' ){
					// #hashtag や @user はそのまま読み上げる
					sb.append( span_text );
				}else{
					// それ以外はURL省略
					has_url = true;
					sb.append( " " );
				}
			}
		}
		int text_end = str_text.length();
		if( text_end > last_end ){
			sb.append( str_text.substring( last_end, text_end ) );
		}
		if( has_url){
			sb.append( context.getString( R.string.url_omitted ) );
		}
		addSpeech( sb.toString() );
	}
	
	final LinkedList< String > tts_queue = new LinkedList<>();
	final LinkedList< String > duplication_check = new LinkedList<>();
	
	private void addSpeech( String sv ){
		if( tts == null ) return;
		
		sv = reSpaces.matcher( sv ).replaceAll( " " ).trim();
		if( TextUtils.isEmpty( sv ) ) return;
		
		for( String check : duplication_check ){
			if( check.equals( sv ) ) return;
		}
		duplication_check.addLast( sv );
		if( duplication_check.size() >= 60 ){
			duplication_check.removeFirst();
		}
		
		tts_queue.add( sv );
		if( tts_queue.size() > 30 ) tts_queue.removeFirst();
		
		flushSpeechQueue();
	}
	
	static int utteranceIdSeed = 0;
	
	void flushSpeechQueue(){
		if( tts_queue.isEmpty() ) return;
		if( tts.isSpeaking() ) return;
		String sv = tts_queue.removeFirst();
		tts.speak(
			sv
			, TextToSpeech.QUEUE_ADD // int queueMode
			, null // Bundle params
			, Integer.toString( ++ utteranceIdSeed ) // String utteranceId
		);
	}
}
