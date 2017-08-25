package jp.juggler.subwaytooter.util;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
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
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.AcctSet;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.TagSet;
import jp.juggler.subwaytooter.view.MyEditText;
import okhttp3.Request;
import okhttp3.RequestBody;


public class PostHelper {
	private static final LogCategory log = new LogCategory( "PostHelper" );
	
	public interface Callback{
		void onPostComplete(SavedAccount target_account,TootStatus status);
	}
	
	private final AppCompatActivity activity;
	private final SharedPreferences pref;
	private final Handler handler;
	
	public PostHelper( AppCompatActivity activity ,SharedPreferences pref,Handler handler ){
		this.activity = activity;
		this.pref = pref;
		this.handler = handler;
	}
	
	// [:word:] 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
	// [:alpha:] 英字 (Letter | Mark)
	
	private static final String word = "[_\\p{L}\\p{M}\\p{Nd}\\p{Pc}]";
	private static final String alpha = "[_\\p{L}\\p{M}]";
	
	private static final Pattern reTag = Pattern.compile(
		"(?:^|[^/)\\w])#(" + word + "*" + alpha + word + "*)"
		, Pattern.CASE_INSENSITIVE
	);

	private static final Pattern reCharsNotTag = Pattern.compile( "[\\s\\-+.,:;/]" );
	
	
	public String content;
	public String spoiler_text;
	public String visibility;
	public boolean bNSFW;
	public long in_reply_to_id;
	public ArrayList< PostAttachment > attachment_list;
	public ArrayList< String > enquete_items;
	
	public void post( final SavedAccount account,final boolean bConfirmTag, final boolean bConfirmAccount ,final Callback callback){
		if( TextUtils.isEmpty( content ) ){
			Utils.showToast( activity, true, R.string.post_error_contents_empty );
			return;
		}
		
		if( spoiler_text != null && spoiler_text.isEmpty() ){
			Utils.showToast( activity, true, R.string.post_error_contents_warning_empty );
			return;
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
						post( account,bConfirmTag, true ,callback);
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
							post( account,true, bConfirmAccount  ,callback);
						}
					} )
					.show();
				return;
			}
		}
		
		RequestBody request_body;
		String body_string;
		
		if( enquete_items == null || enquete_items.isEmpty() ){
			StringBuilder sb = new StringBuilder();
			
			sb.append( "status=" );
			sb.append( Uri.encode( content ) );
			
			sb.append( "&visibility=" );
			sb.append( Uri.encode( visibility ) );
			
			if( bNSFW ){
				sb.append( "&sensitive=1" );
			}
			
			if( spoiler_text != null ){
				sb.append( "&spoiler_text=" );
				sb.append( Uri.encode( spoiler_text ) );
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
			JSONObject json = new JSONObject(  );
			try{
				json.put("status",content);
				json.put("visibility",visibility);
				json.put("sensitive",bNSFW);
				json.put("spoiler_text",TextUtils.isEmpty( spoiler_text ) ? "" : spoiler_text );
				json.put("in_reply_to_id", in_reply_to_id == -1L ? null : in_reply_to_id );
				JSONArray array = new JSONArray();
				if( attachment_list != null ){
					for( PostAttachment pa : attachment_list ){
						if( pa.attachment != null ){
							array.put( pa.attachment.id );
						}
					}
				}
				json.put("media_ids",array);
				json.put("isEnquete",true);
				array = new JSONArray();
				for( String item  : enquete_items ){
					array.put( item );
				}
				json.put("enquete_items",array);
			}catch(JSONException ex){
				log.trace( ex );
				log.e(ex,"status encoding failed.");
			}
			
			body_string = json.toString();
			request_body = RequestBody.create(
				TootApiClient.MEDIA_TYPE_JSON
				, body_string
			);
		}
		
		final Request.Builder request_builder = new Request.Builder()
			.post(request_body );
		final String digest =Utils.digestSHA256( body_string + account.acct );
		
		final ProgressDialog progress = new ProgressDialog( activity );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			final SavedAccount target_account = account;
			
			TootStatus status;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override public void run(){
								progress.setMessage( s );
							}
						} );
					}
				} );
				
				client.setAccount( target_account );
				
				
				if( ! pref.getBoolean( Pref.KEY_DONT_DUPLICATION_CHECK, false ) ){
					request_builder.header( "Idempotency-Key", digest );
				}
				
				TootApiResult result = client.request( "/api/v1/statuses", request_builder );
				if( result != null && result.object != null ){
					status = TootStatus.parse( activity,  account, result.object );
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
			
			@Override
			protected void onCancelled(){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
					//							java.lang.IllegalArgumentException:
					//							at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:396)
					//							at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:322)
					//							at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:116)
					//							at android.app.Dialog.dismissDialog(Dialog.java:341)
					//							at android.app.Dialog.dismiss(Dialog.java:324)
					//							at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:867)
					//							at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:837)
				}
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( status != null ){
					// 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
					callback.onPostComplete( target_account,status );
				}else{
					Utils.showToast( activity, true, result.error );
				}
			}
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
	public interface Callback2{
		void onTextUpdate();
		boolean canOpenPopup();
	}
	
	private Callback2 callback2;

	private MyEditText et;
	private PopupAutoCompleteAcct popup;
	private View formRoot;
	private boolean bMainScreen;
	
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
	
	
	
	public void attachEditText( View _formRoot, MyEditText _et, boolean bMainScreen, Callback2 _callback2){
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
				handler.postDelayed( proc_text_changed, ( popup != null && popup.isShowing() ? 100L : 1000L ) );
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
	}
	

	
	
	private final Runnable proc_text_changed = new Runnable() {
		@Override public void run(){
			if(! callback2.canOpenPopup() ){
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
			ArrayList< String > acct_list = AcctSet.searchPrefix( s, limit );
			log.d( "search for %s, result=%d", s, acct_list.size() );
			if( acct_list.isEmpty() ){
				closeAcctPopup();
			}else{
				if( popup == null || ! popup.isShowing() ){
					popup = new PopupAutoCompleteAcct( activity, et, formRoot,bMainScreen );
				}
				popup.setList( acct_list, start, end );
			}
		}
		
		private void checkTag(){
			int end = et.getSelectionEnd();
			
			String src = et.getText().toString();
			int last_sharp = src.lastIndexOf( '#', end - 1 );
			
			if( last_sharp == - 1 || end - last_sharp < 3 ){
				closeAcctPopup();
				return;
			}
			
			String part = src.substring( last_sharp + 1, end );
			if( reCharsNotTag.matcher( part ).find() ){
				log.d("checkTag: character not tag in string %s",part);
				closeAcctPopup();
				return;
			}
			
			int limit = 100;
			String s = src.substring( last_sharp + 1, end );
			ArrayList< String > tag_list = TagSet.searchPrefix( s, limit );
			log.d( "search for %s, result=%d", s, tag_list.size() );
			if( tag_list.isEmpty() ){
				closeAcctPopup();
			}else{
				if( popup == null || ! popup.isShowing() ){
					popup = new PopupAutoCompleteAcct( activity, et, formRoot ,bMainScreen);
				}
				popup.setList( tag_list, last_sharp, end );
			}
		}
	};
	
	
	
	
	
}
