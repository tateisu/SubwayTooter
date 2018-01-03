package jp.juggler.subwaytooter.util;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootInstance;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.TootParser;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.dialog.EmojiPicker;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.AcctSet;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.TagSet;
import jp.juggler.subwaytooter.view.MyEditText;
import okhttp3.Request;
import okhttp3.RequestBody;

public class PostHelper implements CustomEmojiLister.Callback, EmojiPicker.Callback {
	private static final LogCategory log = new LogCategory( "PostHelper" );
	
	public interface Callback {
		void onPostComplete( SavedAccount target_account, TootStatus status );
	}
	
	private final AppCompatActivity activity;
	private final SharedPreferences pref;
	private final Handler handler;
	private final String picker_caption_emoji;
//	private final String picker_caption_tag;
//	private final String picker_caption_mention;
	
	public PostHelper( AppCompatActivity activity, SharedPreferences pref, Handler handler ){
		this.activity = activity;
		this.pref = pref;
		this.handler = handler;
		
		this.picker_caption_emoji = activity.getString( R.string.open_picker_emoji );
//		this.picker_caption_tag = activity.getString( R.string.open_picker_tag );
//		this.picker_caption_mention = activity.getString( R.string.open_picker_mention );
		
	}
	
	// [:word:] 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
	// [:alpha:] 英字 (Letter | Mark)
	
	private static final String word = "[_\\p{L}\\p{M}\\p{Nd}\\p{Pc}]";
	private static final String alpha = "[_\\p{L}\\p{M}]";
	
	private static final Pattern reTag = Pattern.compile(
		"(?:^|[^/)\\w])#(" + word + "*" + alpha + word + "*)"
		, Pattern.CASE_INSENSITIVE
	);
	
	private static final Pattern reCharsNotTag = Pattern.compile( "[・\\s\\-+.,:;/]" );
	private static final Pattern reCharsNotEmoji = Pattern.compile( "[^0-9A-Za-z_-]" );
	
	public String content;
	public String spoiler_text;
	public String visibility;
	public boolean bNSFW;
	public long in_reply_to_id;
	public ArrayList< PostAttachment > attachment_list;
	public ArrayList< String > enquete_items;
	
	private static final VersionString version_1_6 = new VersionString( "1.6" );
	
	public void post( final SavedAccount account, final boolean bConfirmTag, final boolean bConfirmAccount, final Callback callback ){
		if( TextUtils.isEmpty( content ) ){
			Utils.showToast( activity, true, R.string.post_error_contents_empty );
			return;
		}
		
		if( spoiler_text != null && spoiler_text.isEmpty() ){
			Utils.showToast( activity, true, R.string.post_error_contents_warning_empty );
			return;
		}
		
		if( enquete_items != null && ! enquete_items.isEmpty() ){
			for( int n = 0, ne = enquete_items.size() ; n < ne ; ++ n ){
				String item = enquete_items.get( n );
				if( TextUtils.isEmpty( item ) ){
					if( n < 2 ){
						Utils.showToast( activity, true, R.string.enquete_item_is_empty, n + 1 );
						return;
					}
				}else{
					int code_count = item.codePointCount( 0, item.length() );
					if( code_count > 15 ){
						int over = code_count - 15;
						Utils.showToast( activity, true, R.string.enquete_item_too_long, n + 1, over );
						return;
					}else if( n > 0 ){
						for( int i = 0 ; i < n ; ++ i ){
							if( item.equals( enquete_items.get( i ) ) ){
								Utils.showToast( activity, true, R.string.enquete_item_duplicate, n + 1 );
								return;
							}
						}
					}
				}
			}
		}
		
		if( ! bConfirmAccount ){
			DlgConfirm.open( activity
				, activity.getString( R.string.confirm_post_from, AcctColor.getNickname( account.acct ) )
				, new DlgConfirm.Callback() {
					@Override public boolean isConfirmEnabled(){
						return account.confirm_post;
					}
					
					@Override public void setConfirmEnabled( boolean bv ){
						account.confirm_post = bv;
						account.saveSetting();
					}
					
					@Override public void onOK(){
						post( account, bConfirmTag, true, callback );
					}
				} );
			return;
		}
		
		if( ! bConfirmTag ){
			Matcher m = reTag.matcher( content );
			if( m.find() && ! TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ){
				new AlertDialog.Builder( activity )
					.setCancelable( true )
					.setMessage( R.string.hashtag_and_visibility_not_match )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							//noinspection ConstantConditions
							post( account, true, bConfirmAccount, callback );
						}
					} )
					.show();
				return;
			}
		}
		
		new TootTaskRunner( activity, true ).run( account, new TootTask() {
			
			final SavedAccount target_account = account;
			
			TootStatus status;
			
			TootInstance instance_tmp;
			
			TootApiResult getInstanceInformation( @NonNull TootApiClient client ){
				instance_tmp = null;
				TootApiResult result = client.request( "/api/v1/instance" );
				if( result != null && result.object != null ){
					instance_tmp = TootInstance.parse( result.object );
					
				}
				return result;
			}
			
			TootAccount credential_tmp;
			
			TootApiResult getCredential( @NonNull TootApiClient client ){
				credential_tmp = null;
				TootApiResult result = client.request( "/api/v1/accounts/verify_credentials" );
				if( result != null && result.object != null ){
					credential_tmp = TootAccount.parse( activity, target_account, result.object );
					
				}
				return result;
			}
			
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				if( TextUtils.isEmpty( visibility ) ){
					visibility = TootStatus.VISIBILITY_PUBLIC;
				}
				String visibility_checked = visibility;
				if( TootStatus.VISIBILITY_WEB_SETTING.equals( visibility ) ){
					TootInstance instance = target_account.getInstance();
					if( instance == null ){
						TootApiResult r2 = getInstanceInformation( client );
						if( instance_tmp == null ) return r2;
						instance = instance_tmp;
						target_account.setInstance( instance_tmp );
					}
					if( instance.isEnoughVersion( version_1_6 ) ){
						visibility_checked = null;
					}else{
						TootApiResult r2 = getCredential( client );
						if( credential_tmp == null ){
							return r2;
						}else if( credential_tmp.source == null || credential_tmp.source.privacy == null ){
							return new TootApiResult( activity.getString( R.string.cant_get_web_setting_visibility ) );
						}else{
							visibility_checked = credential_tmp.source.privacy;
						}
					}
				}
				
				RequestBody request_body;
				String body_string;
				
				if( enquete_items == null || enquete_items.isEmpty() ){
					StringBuilder sb = new StringBuilder();
					
					sb.append( "status=" );
					sb.append( Uri.encode( EmojiDecoder.decodeShortCode(content ) ) );
					
					if( visibility_checked != null ){
						sb.append( "&visibility=" );
						sb.append( Uri.encode( visibility_checked ) );
					}
					
					if( bNSFW ){
						sb.append( "&sensitive=1" );
					}
					
					if( spoiler_text != null ){
						sb.append( "&spoiler_text=" );
						sb.append( Uri.encode( EmojiDecoder.decodeShortCode(spoiler_text) ) );
					}
					
					if( in_reply_to_id != - 1L ){
						sb.append( "&in_reply_to_id=" );
						sb.append( Long.toString( in_reply_to_id ) );
					}
					
					if( attachment_list != null ){
						for( PostAttachment pa : attachment_list ){
							if( pa.attachment != null ){
								sb.append( "&media_ids[]=" ).append( pa.attachment.id );
							}
						}
					}
					
					body_string = sb.toString();
					request_body = RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, body_string
					);
				}else{
					
					JSONObject json = new JSONObject();
					try{
						json.put( "status", EmojiDecoder.decodeShortCode(content) );
						if( visibility_checked != null ){
							json.put( "visibility", visibility_checked );
						}
						json.put( "sensitive", bNSFW );
						json.put( "spoiler_text", TextUtils.isEmpty( spoiler_text ) ? "" : EmojiDecoder.decodeShortCode(spoiler_text) );
						json.put( "in_reply_to_id", in_reply_to_id == - 1L ? null : in_reply_to_id );
						JSONArray array = new JSONArray();
						if( attachment_list != null ){
							for( PostAttachment pa : attachment_list ){
								if( pa.attachment != null ){
									array.put( pa.attachment.id );
								}
							}
						}
						json.put( "media_ids", array );
						json.put( "isEnquete", true );
						array = new JSONArray();
						for( String item : enquete_items ){
							array.put( EmojiDecoder.decodeShortCode(item) );
						}
						json.put( "enquete_items", array );
					}catch( JSONException ex ){
						log.trace( ex );
						log.e( ex, "status encoding failed." );
					}
					
					body_string = json.toString();
					request_body = RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON
						, body_string
					);
				}
				
				final Request.Builder request_builder = new Request.Builder()
					.post( request_body );
				final String digest = Utils.digestSHA256( body_string + account.acct );
				
				if( ! pref.getBoolean( Pref.KEY_DONT_DUPLICATION_CHECK, false ) ){
					request_builder.header( "Idempotency-Key", digest );
				}
				
				TootApiResult result = client.request( "/api/v1/statuses", request_builder );
				if( result != null && result.object != null ){
					status = new TootParser( activity, account).status(  result.object );
					if( status != null ){
						Spannable s = status.decoded_content;
						MyClickableSpan[] span_list = s.getSpans( 0, s.length(), MyClickableSpan.class );
						if( span_list != null ){
							ArrayList< String > tag_list = new ArrayList<>();
							for( MyClickableSpan span : span_list ){
								int start = s.getSpanStart( span );
								int end = s.getSpanEnd( span );
								String text = s.subSequence( start, end ).toString();
								if( text.startsWith( "#" ) ){
									tag_list.add( text.substring( 1 ) );
								}
							}
							int count = tag_list.size();
							if( count > 0 ){
								TagSet.saveList(
									System.currentTimeMillis()
									, tag_list.toArray( new String[ count ] )
									, 0
									, count
								);
							}
							
						}
					}
					
				}
				return result;
				
			}
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				if( result == null ) return; // cancelled.
				
				if( status != null ){
					// 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
					callback.onPostComplete( target_account, status );
				}else{
					Utils.showToast( activity, true, result.error );
				}
				
			}
		} );
	}
	
	public interface Callback2 {
		void onTextUpdate();
		
		boolean canOpenPopup();
	}
	
	private Callback2 callback2;
	
	private MyEditText et;
	private PopupAutoCompleteAcct popup;
	private View formRoot;
	private boolean bMainScreen;
	
	private String instance;
	
	public void setInstance( String instance ){
		this.instance = instance == null ? null : instance.toLowerCase();
		
		if( instance != null ){
			App1.custom_emoji_lister.get( this.instance, PostHelper.this );
		}
		
		if( popup != null && popup.isShowing() ){
			proc_text_changed.run();
		}
	}
	
	public void closeAcctPopup(){
		if( popup != null ){
			popup.dismiss();
			popup = null;
		}
	}
	
	public void onScrollChanged(){
		if( popup != null && popup.isShowing() ){
			popup.updatePosition();
		}
	}
	
	public void onDestroy(){
		handler.removeCallbacks( proc_text_changed );
		closeAcctPopup();
	}
	
	public void attachEditText( View _formRoot, MyEditText _et, boolean bMainScreen, Callback2 _callback2 ){
		this.formRoot = _formRoot;
		this.et = _et;
		this.callback2 = _callback2;
		this.bMainScreen = bMainScreen;
		
		et.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence s, int start, int count, int after ){
				
			}
			
			@Override
			public void onTextChanged( CharSequence s, int start, int before, int count ){
				handler.removeCallbacks( proc_text_changed );
				handler.postDelayed( proc_text_changed, ( popup != null && popup.isShowing() ? 100L : 500L ) );
			}
			
			@Override
			public void afterTextChanged( Editable s ){
				callback2.onTextUpdate();
			}
		} );
		
		et.setOnSelectionChangeListener( new MyEditText.OnSelectionChangeListener() {
			@Override public void onSelectionChanged( int selStart, int selEnd ){
				if( selStart != selEnd ){
					// 範囲選択されてるならポップアップは閉じる
					log.d( "onSelectionChanged: range selected" );
					closeAcctPopup();
				}
			}
		} );
		
		// 全然動いてなさそう…
		// et.setCustomSelectionActionModeCallback( action_mode_callback );
		
	}
	
	//	final ActionMode.Callback action_mode_callback = new ActionMode.Callback() {
	//		@Override public boolean onCreateActionMode( ActionMode actionMode, Menu menu ){
	//			actionMode.getMenuInflater().inflate(R.menu.toot_long_tap, menu);
	//			return true;
	//		}
	//		@Override public void onDestroyActionMode( ActionMode actionMode ){
	//
	//		}
	//		@Override public boolean onPrepareActionMode( ActionMode actionMode, Menu menu ){
	//			return false;
	//		}
	//
	//		@Override
	//		public boolean onActionItemClicked( ActionMode actionMode, MenuItem item ){
	//			if (item.getItemId() == R.id.action_pick_emoji) {
	//				actionMode.finish();
	//				EmojiPicker.open( activity, instance, new EmojiPicker.Callback() {
	//					@Override public void onPickedEmoji( String name ){
	//						int end = et.getSelectionEnd();
	//						String src = et.getText().toString();
	//						CharSequence svInsert = ":" + name + ":";
	//						src = src.substring( 0, end ) + svInsert + " " + ( end >= src.length() ? "" : src.substring( end ) );
	//						et.setText( src );
	//						et.setSelection( end + svInsert.length() + 1 );
	//
	//						proc_text_changed.run();
	//					}
	//				} );
	//				return true;
	//			}
	//
	//			return false;
	//		}
	//	};
	
	private final Runnable proc_text_changed = new Runnable() {
		@Override public void run(){
			if( ! callback2.canOpenPopup() ){
				closeAcctPopup();
				return;
			}
			
			int start = et.getSelectionStart();
			int end = et.getSelectionEnd();
			if( start != end ){
				closeAcctPopup();
				return;
			}
			String src = et.getText().toString();
			int count_atMark = 0;
			int[] pos_atMark = new int[ 2 ];
			for( ; ; ){
				if( count_atMark >= 2 ) break;
				
				if( start == 0 ) break;
				char c = src.charAt( start - 1 );
				
				if( c == '@' ){
					-- start;
					pos_atMark[ count_atMark++ ] = start;
					continue;
				}else if( ( '0' <= c && c <= '9' )
					|| ( 'A' <= c && c <= 'Z' )
					|| ( 'a' <= c && c <= 'z' )
					|| c == '_' || c == '-' || c == '.'
					){
					-- start;
					continue;
				}
				// その他の文字種が出たら探索打ち切り
				break;
			}
			// 登場した@の数
			if( count_atMark == 0 ){
				// 次はAcctじゃなくてHashtagの補完を試みる
				checkTag();
				return;
			}else if( count_atMark == 1 ){
				start = pos_atMark[ 0 ];
			}else if( count_atMark == 2 ){
				start = pos_atMark[ 1 ];
			}
			// 最低でも2文字ないと補完しない
			if( end - start < 2 ){
				closeAcctPopup();
				return;
			}
			int limit = 100;
			String s = src.substring( start, end );
			ArrayList< CharSequence > acct_list = AcctSet.searchPrefix( s, limit );
			log.d( "search for %s, result=%d", s, acct_list.size() );
			if( acct_list.isEmpty() ){
				closeAcctPopup();
			}else{
				if( popup == null || ! popup.isShowing() ){
					popup = new PopupAutoCompleteAcct( activity, et, formRoot, bMainScreen );
				}
				popup.setList( et, start, end, acct_list, null, null );
			}
		}
		
		private void checkTag(){
			int end = et.getSelectionEnd();
			
			String src = et.getText().toString();
			int last_sharp = src.lastIndexOf( '#', end - 1 );
			
			if( last_sharp == - 1 || end - last_sharp < 2 ){
				checkEmoji();
				return;
			}
			
			String part = src.substring( last_sharp + 1, end );
			if( reCharsNotTag.matcher( part ).find() ){
				// log.d( "checkTag: character not tag in string %s", part );
				checkEmoji();
				return;
			}
			
			int limit = 100;
			String s = src.substring( last_sharp + 1, end );
			ArrayList< CharSequence > tag_list = TagSet.searchPrefix( s, limit );
			log.d( "search for %s, result=%d", s, tag_list.size() );
			if( tag_list.isEmpty() ){
				closeAcctPopup();
			}else{
				if( popup == null || ! popup.isShowing() ){
					popup = new PopupAutoCompleteAcct( activity, et, formRoot, bMainScreen );
				}
				popup.setList( et, last_sharp, end, tag_list, null, null );
			}
		}
		
		
		
		private void checkEmoji(){
			int end = et.getSelectionEnd();
			
			String src = et.getText().toString();
			int last_colon = src.lastIndexOf( ':', end - 1 );
			
			if( last_colon == - 1 || end - last_colon < 1 ){
				closeAcctPopup();
				return;
			}
			String part = src.substring( last_colon + 1, end );
			
			if( reCharsNotEmoji.matcher( part ).find() ){
				log.d( "checkEmoji: character not short code in string %s", part );
				closeAcctPopup();
				return;
			}
			
			// : の手前は始端か改行か空白でなければならない
			if( last_colon > 0 && ! CharacterGroup.isWhitespace( src.codePointBefore( last_colon ) ) ){
				log.d( "checkEmoji: invalid character before shortcode." );
				closeAcctPopup();
				return;
			}
			
			
			if( part.length() == 0 ){
				if( popup == null || ! popup.isShowing() ){
					popup = new PopupAutoCompleteAcct( activity, et, formRoot, bMainScreen );
				}
				popup.setList(
					et, last_colon, end
					, null
					, picker_caption_emoji
					, open_picker_emoji
				);
				return;
			}
			
			// 絵文字を部分一致で検索
			int limit = 100;
			String s = src.substring( last_colon + 1, end ).toLowerCase().replace( '-', '_' );
			ArrayList< CharSequence > code_list = EmojiDecoder.searchShortCode( activity, s, limit );
			log.d( "checkEmoji: search for %s, result=%d", s, code_list.size() );
			
			// カスタム絵文字を検索
			if( ! TextUtils.isEmpty( instance ) ){
				CustomEmoji.List custom_list = App1.custom_emoji_lister.get( instance, PostHelper.this );
				if( custom_list != null ){
					String needle = src.substring( last_colon + 1, end );
					for( CustomEmoji item : custom_list ){
						if( code_list.size() >= limit ) break;
						if( ! item.shortcode.contains( needle ) ) continue;
						
						SpannableStringBuilder sb = new SpannableStringBuilder();
						sb.append( ' ' );
						sb.setSpan( new NetworkEmojiSpan( item.url ), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
						sb.append( ' ' );
						sb.append( ':' );
						sb.append( item.shortcode );
						sb.append( ':' );
						code_list.add( sb );
					}
				}
			}

			if( popup == null || ! popup.isShowing() ){
				popup = new PopupAutoCompleteAcct( activity, et, formRoot, bMainScreen );
			}
			popup.setList( et, last_colon, end, code_list, picker_caption_emoji, open_picker_emoji );
		}
	};
	
	@Override public void onListLoadComplete( CustomEmoji.List list ){
		if( popup != null && popup.isShowing() ){
			proc_text_changed.run();
		}
	}
	
	private final Runnable open_picker_emoji = new Runnable() {
		@Override public void run(){
			EmojiPicker.open( activity, instance, PostHelper.this );
		}
	};
	
	@Override public void onPickedEmoji( String name ){
		int end = et.getSelectionEnd();
		
		String src = et.getText().toString();
		int last_colon = src.lastIndexOf( ':', end - 1 );
		
		if( last_colon == - 1 || end - last_colon < 1 ){
			return;
		}
		
		CharSequence svInsert = ":" + name + ":";
		src = src.substring( 0, last_colon ) + svInsert + " " + ( end >= src.length() ? "" : src.substring( end ) );
		et.setText( src );
		et.setSelection( last_colon + svInsert.length() + 1 );
		
		proc_text_changed.run();
		
		// キーボードを再度表示する
		new Handler( activity.getMainLooper() ).post( new Runnable() {
			@Override public void run(){
				Utils.showKeyboard( activity, et );
			}
		} );
	}
	
}
