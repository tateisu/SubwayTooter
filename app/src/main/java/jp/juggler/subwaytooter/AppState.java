package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
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
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyClickableSpan;
import jp.juggler.subwaytooter.util.PostAttachment;
import jp.juggler.subwaytooter.util.Utils;

public class AppState {
	static final LogCategory log = new LogCategory( "AppState" );
	final Context context;
	final float density;
	final SharedPreferences pref;
	final Handler handler;
	
	final StreamReader stream_reader;
	
	int media_thumb_height;
	
	AppState( Context applicationContext, SharedPreferences pref ){
		this.context = applicationContext;
		this.handler = new Handler();
		this.pref = pref;
		this.density = context.getResources().getDisplayMetrics().density;
		this.stream_reader = new StreamReader( applicationContext, handler, pref );
		
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
			log.trace( ex );
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
			log.trace( ex );
			Utils.showToast( context, ex, "loadColumnList failed." );
		}
		return null;
	}
	
	private static final String FILE_COLUMN_LIST = "column_list";
	public final ArrayList< Column > column_list = new ArrayList<>();
	
	JSONArray encodeColumnList(){
		JSONArray array = new JSONArray();
		for( int i = 0, ie = column_list.size() ; i < ie ; ++ i ){
			Column column = column_list.get( i );
			try{
				JSONObject dst = new JSONObject();
				column.encodeJSON( dst, i );
				array.put( dst );
			}catch( JSONException ex ){
				log.trace( ex );
			}
		}
		return array;
	}
	
	void saveColumnList(){
		JSONArray array = encodeColumnList();
		saveColumnList( context, FILE_COLUMN_LIST, array );
		enableSpeech();
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
					log.trace( ex );
				}
			}
		}
		enableSpeech();
	}
	
	//////////////////////////////////////////////////////
	
	private final HashSet< String > map_busy_fav = new HashSet<>();
	
	public boolean isBusyFav( SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		return map_busy_fav.contains( key );
	}
	
	public void setBusyFav( SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		map_busy_fav.add( key );
	}
	
	public void resetBusyFav( SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		map_busy_fav.remove( key );
	}
	
	//////////////////////////////////////////////////////
	
	private final HashSet< String > map_busy_boost = new HashSet<>();
	
	public boolean isBusyBoost( @NonNull SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		return map_busy_boost.contains( key );
	}
	
	public void setBusyBoost( SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		map_busy_boost.add( key );
	}
	
	public void resetBusyBoost( SavedAccount account, @NonNull TootStatusLike status ){
		final String key = account.acct + ":" + status.getBusyKey();
		map_busy_boost.remove( key );
	}
	
	//////////////////////////////////////////////////////
	
	ArrayList< PostAttachment > attachment_list = null;
	
	//////////////////////////////////////////////////////
	// TextToSpeech
	
	private boolean willSpeechEnabled;
	private TextToSpeech tts;
	
	private static final int TTS_STATUS_NONE = 0;
	private static final int TTS_STATUS_INITIALIZING = 1;
	private static final int TTS_STATUS_INITIALIZED = 2;
	private int tts_status = TTS_STATUS_NONE;
	
	private boolean isTextToSpeechRequired(){
		boolean b = false;
		for( Column c : column_list ){
			if( c.enable_speech ){
				b = true;
				break;
			}
		}
		return b;
	}
	
	private static final long tts_speak_wait_expire = 1000L * 100;
	private long tts_speak_start = 0L;
	private long tts_speak_end = 0L;
	
	private final BroadcastReceiver tts_receiver = new BroadcastReceiver() {
		@Override public void onReceive( Context context, Intent intent ){
			if( intent != null ){
				if( TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED.equals( intent.getAction() ) ){
					log.d( "tts_receiver: speech completed." );
					tts_speak_end = SystemClock.elapsedRealtime();
					handler.post( proc_flushSpeechQueue );
				}
			}
		}
	};
	
	private final ArrayList< Voice > voice_list = new ArrayList<>();
	private static final Random random = new Random();
	
	@SuppressLint("StaticFieldLeak")
	private void enableSpeech(){
		this.willSpeechEnabled = isTextToSpeechRequired();
		
		if( willSpeechEnabled && tts == null && tts_status == TTS_STATUS_NONE ){
			tts_status = TTS_STATUS_INITIALIZING;
			Utils.showToast( context, false, R.string.text_to_speech_initializing );
			log.d( "initializing TextToSpeech…" );

			new AsyncTask< Void, Void, TextToSpeech >() {
				TextToSpeech tmp_tts;
				
				@Override protected TextToSpeech doInBackground( Void... params ){
					tmp_tts = new TextToSpeech( context, tts_init_listener );
					return tmp_tts;
				}
				
				final TextToSpeech.OnInitListener tts_init_listener = new TextToSpeech.OnInitListener() {
					@Override public void onInit( int status ){
						if( TextToSpeech.SUCCESS != status ){
							Utils.showToast( context, false, R.string.text_to_speech_initialize_failed, status );
							log.d( "speech initialize failed. status=%s", status );
							return;
						}
						
						Utils.runOnMainThread( new Runnable() {
							@Override public void run(){
								if( ! willSpeechEnabled ){
									Utils.showToast( context, false, R.string.text_to_speech_shutdown );
									log.d( "shutdown TextToSpeech…" );
									tmp_tts.shutdown();
								}else{
									
									tts = tmp_tts;
									tts_status = TTS_STATUS_INITIALIZED;
									tts_speak_start = 0L;
									tts_speak_end = 0L;
									
									voice_list.clear();
									try{
										Set< Voice > voice_set = tts.getVoices();
										if( voice_set == null || voice_set.isEmpty() ){
											log.d( "TextToSpeech.getVoices returns null or empty set." );
										}else{
											String lang = Locale.getDefault().toLanguageTag();
											for( Voice v : voice_set ){
												log.d( "Voice %s %s %s"
													, v.getName()
													, v.getLocale().toLanguageTag()
													, lang
												);
												if( ! lang.equals( v.getLocale().toLanguageTag() ) ){
													continue;
												}
												voice_list.add( v );
											}
										}
									}catch( Throwable ex ){
										log.trace( ex );
										log.e( ex, "TextToSpeech.getVoices raises exception." );
									}
									
									handler.post( proc_flushSpeechQueue );
									
									context.registerReceiver( tts_receiver, new IntentFilter( TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED ) );
									
									//									tts.setOnUtteranceProgressListener( new UtteranceProgressListener() {
									//										@Override public void onStart( String utteranceId ){
									//											log.d( "UtteranceProgressListener.onStart id=%s", utteranceId );
									//										}
									//
									//										@Override public void onDone( String utteranceId ){
									//											log.d( "UtteranceProgressListener.onDone id=%s", utteranceId );
									//											handler.post( proc_flushSpeechQueue );
									//										}
									//
									//										@Override public void onError( String utteranceId ){
									//											log.d( "UtteranceProgressListener.onError id=%s", utteranceId );
									//											handler.post( proc_flushSpeechQueue );
									//										}
									//									} );
								}
							}
						} );
					}
				};
			}.executeOnExecutor( App1.task_executor );
		}
		if( ! willSpeechEnabled && tts != null ){
			Utils.showToast( context, false, R.string.text_to_speech_shutdown );
			log.d( "shutdown TextToSpeech…" );
			tts.shutdown();
			tts = null;
			tts_status = TTS_STATUS_NONE;
		}
	}
	
	private static final Pattern reSpaces = Pattern.compile( "[\\s　]+" );
	
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
		if( has_url ){
			sb.append( context.getString( R.string.url_omitted ) );
		}
		addSpeech( sb.toString() );
	}
	
	private final LinkedList< String > tts_queue = new LinkedList<>();
	private final LinkedList< String > duplication_check = new LinkedList<>();
	
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
		
		handler.post( proc_flushSpeechQueue );
	}
	
	private static int utteranceIdSeed = 0;
	
	private final Runnable proc_flushSpeechQueue = new Runnable() {
		@Override public void run(){
			try{
				handler.removeCallbacks( proc_flushSpeechQueue );
				
				int queue_count = tts_queue.size();
				if( queue_count <= 0 ){
					return;
				}
				
				if( tts == null ){
					log.d( "proc_flushSpeechQueue: tts is null" );
					return;
				}
				
				long now = SystemClock.elapsedRealtime();
				
				if( tts_speak_start > 0L ){
					if( tts_speak_start >= tts_speak_end ){
						// まだ終了イベントを受け取っていない
						long expire_remain = tts_speak_wait_expire + tts_speak_start - now;
						if( expire_remain <= 0 ){
							log.d( "proc_flushSpeechQueue: tts_speak wait expired." );
							restartTTS();
						}else{
							log.d( "proc_flushSpeechQueue: tts is speaking. queue_count=%d, expire_remain=%.3f"
								, queue_count
								, ( expire_remain / 1000f )
							);
							handler.postDelayed( proc_flushSpeechQueue, expire_remain );
							return;
						}
						return;
					}
				}
				
				String sv = tts_queue.removeFirst();
				log.d( "proc_flushSpeechQueue: speak %s", sv );
				
				int voice_count = voice_list.size();
				if( voice_count > 0 ){
					int n = random.nextInt( voice_count );
					tts.setVoice( voice_list.get( n ) );
				}
				
				tts_speak_start = now;
				tts.speak(
					sv
					, TextToSpeech.QUEUE_ADD
					, null // Bundle params
					, Integer.toString( ++ utteranceIdSeed ) // String utteranceId
				);
			}catch( Throwable ex ){
				log.trace( ex );
				log.e( ex, "proc_flushSpeechQueue catch exception." );
				restartTTS();
			}
		}
		
		void restartTTS(){
			log.d( "restart TextToSpeech" );
			tts.shutdown();
			tts = null;
			tts_status = TTS_STATUS_NONE;
			enableSpeech();
		}
	};
	
}
