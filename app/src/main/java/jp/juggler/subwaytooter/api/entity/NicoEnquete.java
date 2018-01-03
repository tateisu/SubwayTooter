package jp.juggler.subwaytooter.api.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.EnqueteTimerView;
import okhttp3.Request;
import okhttp3.RequestBody;

public class NicoEnquete {
	static final LogCategory log = new LogCategory( "NicoEnquete" );
	
	// HTML text
	public Spannable question;
	
	// array of text with emoji
	public ArrayList< Spannable > items;
	
	// 結果の数値 // null or array of number
	@SuppressWarnings("unused") private ArrayList< Float > ratios;
	
	// 結果の数値のテキスト // null or array of string
	// public ArrayList< CharSequence > ratios_text;
	
	// one of enquete,enquete_result
	public String type;
	public static final String TYPE_ENQUETE = "enquete";
	@SuppressWarnings("unused") public static final String TYPE_ENQUETE_RESULT = "enquete_result";
	
	private static final Pattern reWhitespace = Pattern.compile( "[\\s\\t\\x0d\\x0a]+" );
	
	private long time_start;
	private long status_id;
	
	public static NicoEnquete parse(
		@NonNull Context context,
		@NonNull SavedAccount access_info,
		@Nullable TootAttachment.List list_attachment,
		@Nullable String sv,
		long status_id,
		long time_start,
		@NonNull TootStatusLike status
	){
		try{
			if( sv != null ){
				JSONObject src = new JSONObject( sv );
				NicoEnquete dst = new NicoEnquete();
				String strQuestion = Utils.optStringX( src, "question" );
				
				ArrayList< String > tmp_items = parseStringArray( src, "items" );
				dst.ratios = parseFloatArray( src, "ratios" );
				//dst.ratios_text = parseStringArray( src, "ratios_text" );
				dst.type = Utils.optStringX( src, "type" );
				dst.time_start = time_start;
				dst.status_id = status_id;
				if( strQuestion != null ){
					dst.question = new DecodeOptions()
						.setShort( true )
						.setDecodeEmoji( true )
						.setAttachment( list_attachment )
						.setLinkTag( status )
						.setCustomEmojiMap( status.custom_emojis )
						.setProfileEmojis( status.profile_emojis )
						.decodeHTML( context, access_info, strQuestion );
				}else{
					dst.question = new SpannableStringBuilder( "?" );
				}
				
				if( tmp_items != null ){
					dst.items = new ArrayList<>();
					for( int i = 0, ie = tmp_items.size() ; i < ie ; ++ i ){
						sv = Utils.sanitizeBDI( tmp_items.get( i ) );
						// remove white spaces
						sv = reWhitespace.matcher( sv ).replaceAll( " " );
						// decode emoji code
						dst.items.add(
							new DecodeOptions()
								.setCustomEmojiMap( status.custom_emojis )
								.setProfileEmojis( status.profile_emojis )
								.decodeEmoji( context, sv )
						);
					}
				}
				
				return dst;
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	private static ArrayList< String > parseStringArray( JSONObject src, @SuppressWarnings("SameParameterValue") String name ){
		JSONArray array = src.optJSONArray( name );
		if( array != null ){
			ArrayList< String > dst = new ArrayList<>();
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				String sv = Utils.optStringX( array, i );
				if( sv != null ) dst.add( sv );
			}
			return dst;
		}
		return null;
	}
	
	private static ArrayList< Float > parseFloatArray( JSONObject src, @SuppressWarnings("SameParameterValue") String name ){
		JSONArray array = src.optJSONArray( name );
		if( array != null ){
			ArrayList< Float > dst = new ArrayList<>();
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				double dv = array.optDouble( i );
				dst.add( (float) dv );
			}
			return dst;
		}
		return null;
	}
	
	private static final long ENQUETE_EXPIRE = 30000L;
	
	public void makeChoiceView(
		final ActMain activity
		, @NonNull final SavedAccount access_info
		, @NonNull LinearLayout llExtra
		, @NonNull ArrayList< NetworkEmojiInvalidator > invalidator_list
		, final int i
		, @NonNull Spannable item
	){
		long now = System.currentTimeMillis();
		long remain = time_start + ENQUETE_EXPIRE - now;
		
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		if( i == 0 )
			lp.topMargin = (int) ( 0.5f + llExtra.getResources().getDisplayMetrics().density * 3f );
		Button b = new Button( activity );
		b.setLayoutParams( lp );
		b.setAllCaps( false );
		b.setText( item );
		NetworkEmojiInvalidator invalidator = new NetworkEmojiInvalidator( activity.handler, b );
		invalidator_list.add( invalidator );
		invalidator.register( item );
		if( remain <= 0 ){
			b.setEnabled( false );
		}else{
			b.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View view ){
					Context c = view.getContext();
					if( c != null ){
						enquete_click( view.getContext(), access_info, i );
					}
				}
			} );
		}
		llExtra.addView( b );
	}
	
	public void makeTimerView( ActMain activity, LinearLayout llExtra ){
		float density = llExtra.getResources().getDisplayMetrics().density;
		int height = (int) ( 0.5f + 6 * density );
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT, height );
		EnqueteTimerView view = new EnqueteTimerView( activity );
		view.setLayoutParams( lp );
		view.setParams( time_start, ENQUETE_EXPIRE );
		llExtra.addView( view );
	}
	
	private void enquete_click( @NonNull final Context context, @NonNull final SavedAccount access_info, final int idx ){
		
		long now = System.currentTimeMillis();
		long remain = time_start + ENQUETE_EXPIRE - now;
		if( remain <= 0 ){
			Utils.showToast( context, false, R.string.enquete_was_end );
			return;
		}
		
		new TootTaskRunner( context ).run( access_info, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				JSONObject form = new JSONObject();
				try{
					form.put( "item_index", Integer.toString( idx ) );
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create( TootApiClient.MEDIA_TYPE_JSON, form.toString() ) );
				
				TootApiResult result;
				{
					result = client.request( "/api/v1/votes/" + status_id, request_builder );
				}
				return result;
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( result.object != null ){
					String message = Utils.optStringX( result.object, "message" );
					boolean valid = result.object.optBoolean( "valid" );
					if( valid ){
						Utils.showToast( context, false, R.string.enquete_voted );
					}else{
						Utils.showToast( context, true, R.string.enquete_vote_failed, message );
					}
				}else{
					Utils.showToast( context, true, result.error );
				}
				
			}
		} );
	}
}
