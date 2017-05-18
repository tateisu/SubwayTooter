package jp.juggler.subwaytooter;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootMention;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.AcctSet;
import jp.juggler.subwaytooter.table.PostDraft;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.view.MyEditText;
import jp.juggler.subwaytooter.view.MyNetworkImageView;
import jp.juggler.subwaytooter.util.PostAttachment;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class ActPost extends AppCompatActivity implements View.OnClickListener, PostAttachment.Callback {
	static final LogCategory log = new LogCategory( "ActPost" );
	
	static final String EXTRA_POSTED_ACCT = "posted_acct";
	static final String EXTRA_POSTED_STATUS_ID = "posted_status_id";
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	static final String KEY_REPLY_STATUS = "reply_status";
	static final String KEY_INITIAL_TEXT = "initial_text";
	static final String KEY_SENT_INTENT = "sent_intent";
	
	static final String KEY_ATTACHMENT_LIST = "attachment_list";
	static final String KEY_VISIBILITY = "visibility";
	static final String KEY_IN_REPLY_TO_ID = "in_reply_to_id";
	static final String KEY_IN_REPLY_TO_TEXT = "in_reply_to_text";
	static final String KEY_IN_REPLY_TO_IMAGE = "in_reply_to_image";
	
	public static void open( Activity activity, int request_code, long account_db_id, TootStatus reply_status ){
		Intent intent = new Intent( activity, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		if( reply_status != null ){
			intent.putExtra( KEY_REPLY_STATUS, reply_status.json.toString() );
		}
		activity.startActivityForResult( intent, request_code );
	}
	
	public static void open( Activity activity, int request_code, long account_db_id, String initial_text ){
		Intent intent = new Intent( activity, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		if( initial_text != null ){
			intent.putExtra( KEY_INITIAL_TEXT, initial_text );
		}
		activity.startActivityForResult( intent, request_code );
	}
	
	public static void open( Activity activity, int request_code, long account_db_id, Intent sent_intent ){
		Intent intent = new Intent( activity, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		if( sent_intent != null ){
			intent.putExtra( KEY_SENT_INTENT, sent_intent );
		}
		activity.startActivityForResult( intent, request_code );
	}
	
	////////////////////////////////////////////////////////////////
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAccount:
			performAccountChooser();
			break;
		
		case R.id.btnVisibility:
			performVisibility();
			break;
		
		case R.id.btnAttachment:
			openAttachment();
			break;
		
		case R.id.ivMedia1:
			performAttachmentDelete( 0 );
			break;
		case R.id.ivMedia2:
			performAttachmentDelete( 1 );
			break;
		case R.id.ivMedia3:
			performAttachmentDelete( 2 );
			break;
		case R.id.ivMedia4:
			performAttachmentDelete( 3 );
			break;
		
		case R.id.btnPost:
			performPost( false, false );
			break;
		
		case R.id.btnRemoveReply:
			removeReply();
			break;
		
		case R.id.btnMore:
			performMore();
			break;
		}
	}
	
	private static final int REQUEST_CODE_ATTACHMENT = 1;
	private static final int REQUEST_CODE_CAMERA = 2;
	
	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( requestCode == REQUEST_CODE_ATTACHMENT && resultCode == RESULT_OK ){
			if( data != null ){
				Uri uri = data.getData();
				if( uri != null ){
					// 単一選択
					String type = data.getType();
					if( TextUtils.isEmpty( type ) ){
						type = getContentResolver().getType( uri );
					}
					addAttachment( uri, type );
				}
				ClipData cd = data.getClipData();
				if( cd != null ){
					int count = cd.getItemCount();
					for( int i = 0 ; i < count ; ++ i ){
						ClipData.Item item = cd.getItemAt( i );
						uri = item.getUri();
						String type = getContentResolver().getType( uri );
						addAttachment( uri, type );
					}
				}
			}
		}else if( requestCode == REQUEST_CODE_CAMERA ){
			
			if( resultCode != RESULT_OK ){
				// 失敗したら DBからデータを削除
				if( uriCameraImage != null ){
					getContentResolver().delete( uriCameraImage, null, null );
					uriCameraImage = null;
				}
			}else{
				// 画像のURL
				Uri uri = ( data == null ? null : data.getData() );
				if( uri == null ) uri = uriCameraImage;
				
				if( uri != null ){
					String type = getContentResolver().getType( uri );
					addAttachment( uri, type );
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override public void onBackPressed(){
		saveDraft();
		super.onBackPressed();
	}
	
	SharedPreferences pref;
	ArrayList< PostAttachment > attachment_list;
	AppState app_state;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, true );
		
		app_state = App1.getAppState( this );
		pref = app_state.pref;
		
		initUI();
		
		if( account_list.isEmpty() ){
			Utils.showToast( this, true, R.string.please_add_account );
			finish();
			return;
		}
		
		if( savedInstanceState != null ){
			long account_db_id = savedInstanceState.getLong( KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_ID );
			if( account_db_id != SavedAccount.INVALID_ID ){
				for( int i = 0, ie = account_list.size() ; i < ie ; ++ i ){
					SavedAccount a = account_list.get( i );
					if( a.db_id == account_db_id ){
						setAccount( a );
						break;
					}
				}
			}
			
			this.visibility = savedInstanceState.getString( KEY_VISIBILITY );
			
			String sv = savedInstanceState.getString( KEY_ATTACHMENT_LIST );
			
			if( app_state.attachment_list != null ){
				
				// static なデータが残ってるならそれを使う
				this.attachment_list = app_state.attachment_list;
				
				// コールバックを新しい画面に差し替える
				for( PostAttachment pa : attachment_list ){
					pa.callback = this;
				}
				
			}else if( ! TextUtils.isEmpty( sv ) ){
				
				// state から復元する
				this.attachment_list = app_state.attachment_list = new ArrayList<>();
				try{
					JSONArray array = new JSONArray( sv );
					for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
						try{
							TootAttachment a = TootAttachment.parse( log, array.optJSONObject( i ) );
							if( a != null ){
								attachment_list.add( new PostAttachment( a ) );
							}
						}catch( Throwable ex2 ){
							ex2.printStackTrace();
						}
					}
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
			}
			
			this.in_reply_to_id = savedInstanceState.getLong( KEY_IN_REPLY_TO_ID, - 1L );
			this.in_reply_to_text = savedInstanceState.getString( KEY_IN_REPLY_TO_TEXT );
			this.in_reply_to_image = savedInstanceState.getString( KEY_IN_REPLY_TO_IMAGE );
		}else{
			this.attachment_list = app_state.attachment_list = null;
			
			Intent intent = getIntent();
			long account_db_id = intent.getLongExtra( KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_ID );
			if( account_db_id != SavedAccount.INVALID_ID ){
				for( int i = 0, ie = account_list.size() ; i < ie ; ++ i ){
					SavedAccount a = account_list.get( i );
					if( a.db_id == account_db_id ){
						setAccount( a );
						break;
					}
				}
			}
			
			Intent sent_intent = intent.getParcelableExtra( KEY_SENT_INTENT );
			if( sent_intent != null ){
				String action = sent_intent.getAction();
				String type = sent_intent.getType();
				//noinspection StatementWithEmptyBody
				if( type == null ){
					//
				}else if( type.startsWith( "image/" ) ){
					if( Intent.ACTION_SEND.equals( action ) ){
						Uri uri = sent_intent.getParcelableExtra( Intent.EXTRA_STREAM );
						if( uri != null ){
							addAttachment( uri, type );
						}
					}else if( Intent.ACTION_SEND_MULTIPLE.equals( action ) ){
						ArrayList< Uri > list_uri = sent_intent.getParcelableArrayListExtra( Intent.EXTRA_STREAM );
						if( list_uri != null ){
							for( Uri uri : list_uri ){
								if( uri != null ){
									addAttachment( uri, type );
								}
							}
						}
					}
				}else if( type.startsWith( "text/" ) ){
					if( Intent.ACTION_SEND.equals( action ) ){
						String sv = sent_intent.getStringExtra( Intent.EXTRA_TEXT );
						if( sv != null ){
							etContent.setText( sv );
							etContent.setSelection( sv.length() );
						}
					}
					
				}
			}
			
			String sv = intent.getStringExtra( KEY_INITIAL_TEXT );
			if( sv != null ){
				etContent.setText( sv );
				etContent.setSelection( sv.length() );
			}
			
			sv = intent.getStringExtra( KEY_REPLY_STATUS );
			if( sv != null ){
				try{
					TootStatus reply_status = TootStatus.parse( log, account, new JSONObject( sv ) );
					
					// CW をリプライ元に合わせる
					if( ! TextUtils.isEmpty( reply_status.spoiler_text ) ){
						cbContentWarning.setChecked( true );
						etContentWarning.setText( reply_status.spoiler_text );
					}
					
					ArrayList< String > mention_list = new ArrayList<>();
					// 元レスにあった mention
					if( reply_status.mentions != null ){
						for( TootMention mention : reply_status.mentions ){
							
							if( account.isMe( mention.acct ) ) continue;
							
							sv = "@" + account.getFullAcct( mention.acct );
							if( ! mention_list.contains( sv ) ){
								mention_list.add( sv );
							}
						}
					}
					// 今回メンションを追加する？
					{
						sv = account.getFullAcct( reply_status.account );
						//noinspection StatementWithEmptyBody
						if( mention_list.contains( "@" + sv ) ){
							// 既に含まれている
						}else if( ! account.isMe( reply_status.account ) || mention_list.isEmpty() ){
							// 自分ではない、もしくは、メンションが空
							mention_list.add( "@" + sv );
						}
					}
					
					StringBuilder sb = new StringBuilder();
					for( String acct : mention_list ){
						if( sb.length() > 0 ) sb.append( ' ' );
						sb.append( acct );
					}
					if( sb.length() > 0 ){
						sb.append( ' ' );
						etContent.setText( sb.toString() );
						etContent.setSelection( sb.length() );
					}
					
					// リプライ表示をつける
					in_reply_to_id = reply_status.id;
					in_reply_to_text = reply_status.content;
					in_reply_to_image = reply_status.account.avatar_static;
					
					// 公開範囲
					try{
						// 比較する前にデフォルトの公開範囲を計算する
						if( TextUtils.isEmpty( visibility ) ){
							visibility = account.visibility;
							if( TextUtils.isEmpty( visibility ) ){
								visibility = TootStatus.VISIBILITY_PUBLIC;
							}
						}
						
						// デフォルトの方が公開範囲が大きい場合、リプライ元に合わせて公開範囲を狭める
						int i = TootStatus.compareVisibility( this.visibility, reply_status.visibility );
						if( i > 0 ){ // より大きい=>より公開範囲が広い
							this.visibility = reply_status.visibility;
						}
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
					
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
			}
		}
		
		if( TextUtils.isEmpty( visibility ) ){
			visibility = account.visibility;
			if( TextUtils.isEmpty( visibility ) ){
				visibility = TootStatus.VISIBILITY_PUBLIC;
			}
		}
		
		if( this.account == null ){
			// 表示を未選択に更新
			setAccount( null );
		}
		
		updateContentWarning();
		showMediaAttachment();
		showVisibility();
		updateTextCount();
		showReplyTo();
	}
	
	@Override protected void onDestroy(){
		handler.removeCallbacks( proc_text_changed );
		closeAcctPopup();
		super.onDestroy();
	}
	
	@Override
	protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		
		if( account != null ){
			outState.putLong( KEY_ACCOUNT_DB_ID, account.db_id );
		}
		
		if( visibility != null ){
			outState.putString( KEY_VISIBILITY, visibility );
		}
		
		if( attachment_list != null && ! attachment_list.isEmpty() ){
			JSONArray array = new JSONArray();
			for( PostAttachment pa : attachment_list ){
				if( pa.status == PostAttachment.ATTACHMENT_UPLOADED ){
					// アップロード完了したものだけ保持する
					array.put( pa.attachment.json );
				}
			}
			outState.putString( KEY_ATTACHMENT_LIST, array.toString() );
		}
		
		outState.putLong( KEY_IN_REPLY_TO_ID, in_reply_to_id );
		outState.putString( KEY_IN_REPLY_TO_TEXT, in_reply_to_text );
		outState.putString( KEY_IN_REPLY_TO_IMAGE, in_reply_to_image );
	}
	
	@Override protected void onRestoreInstanceState( Bundle savedInstanceState ){
		super.onRestoreInstanceState( savedInstanceState );
		updateContentWarning();
		showMediaAttachment();
		showVisibility();
		updateTextCount();
		showReplyTo();
	}
	
	Button btnAccount;
	ImageButton btnVisibility;
	View btnAttachment;
	View btnPost;
	View llAttachment;
	final MyNetworkImageView[] ivMedia = new MyNetworkImageView[ 4 ];
	CheckBox cbNSFW;
	CheckBox cbContentWarning;
	MyEditText etContentWarning;
	MyEditText etContent;
	TextView tvCharCount;
	Handler handler;
	View formRoot;
	float density;
	
	ArrayList< SavedAccount > account_list;
	
	View llReply;
	TextView tvReplyTo;
	View btnRemoveReply;
	MyNetworkImageView ivReply;
	ScrollView scrollView;
	
	private void initUI(){
		handler = new Handler();
		density = getResources().getDisplayMetrics().density;
		
		setContentView( R.layout.act_post );
		
		Styler.fixHorizontalPadding( findViewById( R.id.llContent ) );
		Styler.fixHorizontalMargin( findViewById( R.id.llFooterBar ) );
		
		formRoot = findViewById( R.id.viewRoot );
		scrollView = (ScrollView) findViewById( R.id.scrollView );
		btnAccount = (Button) findViewById( R.id.btnAccount );
		btnVisibility = (ImageButton) findViewById( R.id.btnVisibility );
		btnAttachment = findViewById( R.id.btnAttachment );
		btnPost = findViewById( R.id.btnPost );
		llAttachment = findViewById( R.id.llAttachment );
		ivMedia[ 0 ] = (MyNetworkImageView) findViewById( R.id.ivMedia1 );
		ivMedia[ 1 ] = (MyNetworkImageView) findViewById( R.id.ivMedia2 );
		ivMedia[ 2 ] = (MyNetworkImageView) findViewById( R.id.ivMedia3 );
		ivMedia[ 3 ] = (MyNetworkImageView) findViewById( R.id.ivMedia4 );
		cbNSFW = (CheckBox) findViewById( R.id.cbNSFW );
		cbContentWarning = (CheckBox) findViewById( R.id.cbContentWarning );
		etContentWarning = (MyEditText) findViewById( R.id.etContentWarning );
		etContent = (MyEditText) findViewById( R.id.etContent );
		tvCharCount = (TextView) findViewById( R.id.tvCharCount );
		
		llReply = findViewById( R.id.llReply );
		tvReplyTo = (TextView) findViewById( R.id.tvReplyTo );
		btnRemoveReply = findViewById( R.id.btnRemoveReply );
		ivReply = (MyNetworkImageView) findViewById( R.id.ivReply );
		
		account_list = SavedAccount.loadAccountList( log );
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override
			public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
				
			}
		} );
		
		btnAccount.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		btnAttachment.setOnClickListener( this );
		btnPost.setOnClickListener( this );
		btnRemoveReply.setOnClickListener( this );
		
		for( MyNetworkImageView iv : ivMedia ){
			iv.setOnClickListener( this );
			iv.setDefaultImageResId( Styler.getAttributeResourceId( this, R.attr.ic_loading ) );
			iv.setErrorImageResId( Styler.getAttributeResourceId( this, R.attr.ic_unknown ) );
		}
		
		cbContentWarning.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
				updateContentWarning();
			}
		} );
		
		etContent.addTextChangedListener( new TextWatcher() {
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
				updateTextCount();
			}
		} );
		
		etContent.setOnSelectionChangeListener( new MyEditText.OnSelectionChangeListener() {
			
			@Override public void onSelectionChanged( int selStart, int selEnd ){
				if( selStart != selEnd ){
					// 範囲選択されてるならポップアップは閉じる
					log.d( "onSelectionChanged: range selected" );
					closeAcctPopup();
				}
			}
		} );
		
		scrollView.getViewTreeObserver().addOnScrollChangedListener( scroll_listener );
		
		View v = findViewById( R.id.btnMore );
		v.setOnClickListener( this );
	}
	
	final ViewTreeObserver.OnScrollChangedListener scroll_listener = new ViewTreeObserver.OnScrollChangedListener() {
		@Override public void onScrollChanged(){
			if( popup != null && popup.isShowing() ){
				popup.updatePosition();
			}
		}
	};
	
	final Runnable proc_text_changed = new Runnable() {
		@Override public void run(){
			int start = etContent.getSelectionStart();
			int end = etContent.getSelectionEnd();
			if( start != end ){
				closeAcctPopup();
				return;
			}
			String src = etContent.getText().toString();
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
				closeAcctPopup();
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
					popup = new PopupAutoCompleteAcct( ActPost.this, etContent, formRoot );
				}
				popup.setList( acct_list, start, end );
			}
		}
	};
	
	PopupAutoCompleteAcct popup;
	
	private void closeAcctPopup(){
		if( popup != null ){
			popup.dismiss();
			popup = null;
		}
	}
	
	private void updateTextCount(){
		String s = etContent.getText().toString();
		int count_content = s.codePointCount( 0, s.length() );
		s = cbContentWarning.isChecked() ? etContentWarning.getText().toString() : "";
		int count_spoiler = s.codePointCount( 0, s.length() );
		
		int remain = 500 - count_content - count_spoiler;
		tvCharCount.setText( Integer.toString( remain ) );
		int color = Styler.getAttributeColor( this, remain < 0 ? R.attr.colorRegexFilterError : android.R.attr.textColorPrimary );
		tvCharCount.setTextColor( color );
	}
	
	private void updateContentWarning(){
		etContentWarning.setVisibility( cbContentWarning.isChecked() ? View.VISIBLE : View.GONE );
	}
	
	//////////////////////////////////////////////////////////
	// Account
	
	SavedAccount account;
	
	void setAccount( SavedAccount a ){
		this.account = a;
		btnAccount.setText(
			( a == null ? getString( R.string.not_selected ) : a.getFullAcct( a ) )
		);
	}
	
	private void performAccountChooser(){
		
		if( attachment_list != null && ! attachment_list.isEmpty() ){
			// 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
			Utils.showToast( this, false, R.string.cant_change_account_when_attachment_specified );
			return;
		}
		
		final ArrayList< SavedAccount > tmp_account_list = new ArrayList<>();
		if( in_reply_to_id != - 1L ){
			// リプライは数値IDなのでサーバが同じじゃないと選択できない
			for( SavedAccount a : account_list ){
				if( ! a.host.equals( account.host ) ) continue;
				tmp_account_list.add( a );
			}
		}else{
			tmp_account_list.addAll( account_list );
		}
		
		String[] caption_list = new String[ tmp_account_list.size() ];
		for( int i = 0, ie = tmp_account_list.size() ; i < ie ; ++ i ){
			caption_list[ i ] = tmp_account_list.get( i ).acct;
		}
		
		new AlertDialog.Builder( this )
			.setTitle( R.string.choose_account )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					if( which >= 0 && which < tmp_account_list.size() ){
						SavedAccount account = tmp_account_list.get( which );
						setAccount( account );
						try{
							if( account.visibility != null && TootStatus.compareVisibility( visibility, account.visibility ) > 0 ){
								Utils.showToast( ActPost.this, true, R.string.spoil_visibility_for_account );
								visibility = account.visibility;
								showVisibility();
							}
						}catch( Throwable ex ){
							ex.printStackTrace();
						}
					}
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
	}
	
	//////////////////////////////////////////////////////////
	// Attachment
	
	private void showMediaAttachment(){
		
		if( isFinishing() ) return;
		
		if( attachment_list == null || attachment_list.isEmpty() ){
			llAttachment.setVisibility( View.GONE );
		}else{
			llAttachment.setVisibility( View.VISIBLE );
			for( int i = 0, ie = ivMedia.length ; i < ie ; ++ i ){
				showAttachment_sub( ivMedia[ i ], i );
			}
		}
	}
	
	private void showAttachment_sub( MyNetworkImageView iv, int idx ){
		if( idx >= attachment_list.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			iv.setCornerRadius( pref, 16f );
			PostAttachment a = attachment_list.get( idx );
			if( a.attachment != null && a.status == PostAttachment.ATTACHMENT_UPLOADED ){
				iv.setImageUrl( a.attachment.preview_url );
			}else{
				iv.setImageUrl( null );
			}
		}
	}
	
	// 添付した画像をタップ
	void performAttachmentDelete( int idx ){
		final PostAttachment pa = attachment_list.get( idx );
		new AlertDialog.Builder( this )
			.setTitle( R.string.confirm_delete_attachment )
			.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					try{
						attachment_list.remove( pa );
					}catch( Throwable ignored ){
					}
					showMediaAttachment();
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
		
	}
	
	void openAttachment(){
		int permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.WRITE_EXTERNAL_STORAGE );
		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
			preparePermission();
			return;
		}
		
		ActionsDialog a = new ActionsDialog();
		a.addAction( getString( R.string.image_pick ), new Runnable() {
			@Override public void run(){
				performAttachment();
			}
		} );
		a.addAction( getString( R.string.image_capture ), new Runnable() {
			@Override public void run(){
				performCamera();
			}
		} );
		a.show( this, null );
		
	}
	
	private void performAttachment(){
		
		if( attachment_list != null && attachment_list.size() >= 4 ){
			Utils.showToast( this, false, R.string.attachment_too_many );
			return;
		}
		
		if( account == null ){
			Utils.showToast( this, false, R.string.account_select_please );
			return;
		}
		
		// SAFのIntentで開く
		try{
			Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
			intent.addCategory( Intent.CATEGORY_OPENABLE );
			intent.setType( "*/*" );
			intent.putExtra( Intent.EXTRA_ALLOW_MULTIPLE, true );
			intent.putExtra( Intent.EXTRA_MIME_TYPES, new String[]{ "image/*", "video/*" } );
			startActivityForResult( intent, REQUEST_CODE_ATTACHMENT );
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "ACTION_OPEN_DOCUMENT failed." );
		}
	}
	
	interface InputStreamOpener {
		InputStream open() throws IOException;
		
		String getMimeType();
		
		void deleteTempFile();
	}
	
	static final int[] list_resize_max = new int[]{
		0
		, 640
		, 800
		, 1024
		, 1280
		, 1600
		, 2048
	};
	static final String MIME_TYPE_JPEG = "image/jpeg";
	static final String MIME_TYPE_PNG = "image/png";
	
	private InputStreamOpener createOpener( final Uri uri, final String mime_type ){
		//noinspection LoopStatementThatDoesntLoop
		for( ; ; ){
			try{
				
				// 画像の種別
				boolean is_jpeg = MIME_TYPE_JPEG.equals( mime_type );
				boolean is_png = MIME_TYPE_PNG.equals( mime_type );
				if( ! is_jpeg && ! is_png ){
					log.d( "createOpener: source is not jpeg or png" );
					break;
				}
				
				// 設定からリサイズ指定を読む
				int resize_to = list_resize_max[ pref.getInt( Pref.KEY_RESIZE_IMAGE, 4 ) ];
				
				Bitmap bitmap = Utils.createResizedBitmap( log, this, uri, true, resize_to );
				if( bitmap != null ){
					try{
						File cache_dir = getExternalCacheDir();
						if( cache_dir == null ){
							Utils.showToast( this, false, "getExternalCacheDir returns null." );
							break;
						}
						
						//noinspection ResultOfMethodCallIgnored
						cache_dir.mkdir();
						
						final File temp_file = new File( cache_dir, "tmp." + Thread.currentThread().getId() );
						FileOutputStream os = new FileOutputStream( temp_file );
						try{
							if( is_jpeg ){
								bitmap.compress( Bitmap.CompressFormat.JPEG, 95, os );
							}else{
								bitmap.compress( Bitmap.CompressFormat.PNG, 100, os );
							}
						}finally{
							os.close();
						}
						
						return new ActPost.InputStreamOpener() {
							@Override public InputStream open() throws IOException{
								return new FileInputStream( temp_file );
							}
							
							@Override public String getMimeType(){
								return mime_type;
							}
							
							@Override public void deleteTempFile(){
								//noinspection ResultOfMethodCallIgnored
								temp_file.delete();
							}
						};
					}finally{
						bitmap.recycle();
					}
				}
				
			}catch( Throwable ex ){
				ex.printStackTrace();
				Utils.showToast( this, ex, "Resizing image failed." );
			}
			
			break;
		}
		return new InputStreamOpener() {
			@Override public InputStream open() throws IOException{
				return getContentResolver().openInputStream( uri );
			}
			
			@Override public String getMimeType(){
				return mime_type;
			}
			
			@Override public void deleteTempFile(){
				
			}
		};
	}
	
	void addAttachment( final Uri uri, final String mime_type ){
		if( attachment_list != null && attachment_list.size() >= 4 ){
			Utils.showToast( this, false, R.string.attachment_too_many );
			return;
		}
		if( account == null ){
			Utils.showToast( this, false, R.string.account_select_please );
			return;
		}
		
		if( attachment_list == null ){
			this.attachment_list = app_state.attachment_list = new ArrayList<>();
		}
		
		final PostAttachment pa = new PostAttachment( this );
		attachment_list.add( pa );
		showMediaAttachment();
		Utils.showToast( this, false, R.string.attachment_uploading );
		
		new AsyncTask< Void, Void, TootApiResult >() {
			final SavedAccount target_account = account;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
					}
				} );
				client.setAccount( target_account );
				
				if( TextUtils.isEmpty( mime_type ) ){
					return new TootApiResult( "mime_type is null." );
				}
				
				try{
					final InputStreamOpener opener = createOpener( uri, mime_type );
					
					final long content_length = getStreamSize( true, opener.open() );
					if( content_length > 8000000 ){
						return new TootApiResult( getString( R.string.file_size_too_big ) );
					}
					RequestBody multipart_body = new MultipartBody.Builder()
						.setType( MultipartBody.FORM )
						.addFormDataPart(
							"file"
							, getDocumentName( uri )
							, new RequestBody() {
								@Override
								public MediaType contentType(){
									return MediaType.parse( opener.getMimeType() );
								}
								
								@Override
								public long contentLength() throws IOException{
									return content_length;
								}
								
								@Override
								public void writeTo( BufferedSink sink ) throws IOException{
									InputStream is = opener.open();
									if( is == null )
										throw new IOException( "openInputStream() failed. uri=" + uri );
									try{
										byte[] tmp = new byte[ 4096 ];
										for( ; ; ){
											int r = is.read( tmp, 0, tmp.length );
											if( r <= 0 ) break;
											sink.write( tmp, 0, r );
										}
									}finally{
										IOUtils.closeQuietly( is );
									}
									
								}
							}
						)
						.build();
					
					Request.Builder request_builder = new Request.Builder()
						.post( multipart_body );
					
					TootApiResult result = client.request( "/api/v1/media", request_builder );
					
					opener.deleteTempFile();
					
					if( result.object != null ){
						pa.attachment = TootAttachment.parse( log, result.object );
						if( pa.attachment == null ){
							result.error = "TootAttachment.parse failed";
						}
					}
					return result;
					
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "read failed." ) );
				}
				
			}
			
			@Override
			protected void onCancelled(){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( pa.attachment == null ){
					pa.status = PostAttachment.ATTACHMENT_UPLOAD_FAILED;
					if( result != null ){
						Utils.showToast( ActPost.this, true, result.error );
					}
				}else{
					pa.status = PostAttachment.ATTACHMENT_UPLOADED;
				}
				// 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
				pa.callback.onPostAttachmentComplete( pa );
			}
			
		}.executeOnExecutor( App1.task_executor );
	}
	
	// 添付メディア投稿が完了したら呼ばれる
	@Override public void onPostAttachmentComplete( PostAttachment pa ){
		if( pa.status != PostAttachment.ATTACHMENT_UPLOADED ){
			if( attachment_list != null ){
				attachment_list.remove( pa );
				showMediaAttachment();
			}
		}else{
			if( attachment_list != null && attachment_list.contains( pa ) ){
				
				Utils.showToast( ActPost.this, false, R.string.attachment_uploaded );
				
				// 投稿欄に追記する
				String sv = etContent.getText().toString();
				int l = sv.length();
				if( l > 0 ){
					char c = sv.charAt( l - 1 );
					if( c > 0x20 ) sv = sv + " ";
				}
				sv = sv + pa.attachment.text_url + " ";
				etContent.setText( sv );
				etContent.setSelection( sv.length() );
				
				showMediaAttachment();
			}
		}
	}
	
	Uri uriCameraImage;
	
	private void performCamera(){
		
		try{
			// カメラで撮影
			String filename = System.currentTimeMillis() + ".jpg";
			ContentValues values = new ContentValues();
			values.put( MediaStore.Images.Media.TITLE, filename );
			values.put( MediaStore.Images.Media.MIME_TYPE, "image/jpeg" );
			uriCameraImage = getContentResolver().insert( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values );
			
			Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
			intent.putExtra( MediaStore.EXTRA_OUTPUT, uriCameraImage );
			
			startActivityForResult( intent, REQUEST_CODE_CAMERA );
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "opening camera app failed." );
		}
	}
	
	private static final int PERMISSION_REQUEST_CODE = 1;
	
	private void preparePermission(){
		if( Build.VERSION.SDK_INT >= 23 ){
			// No explanation needed, we can request the permission.
			
			ActivityCompat.requestPermissions( this
				, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }
				, PERMISSION_REQUEST_CODE
			);
			return;
		}
		Utils.showToast( this, true, R.string.missing_storage_permission );
	}
	
	@Override public void onRequestPermissionsResult(
		int requestCode
		, @NonNull String permissions[]
		, @NonNull int[] grantResults
	){
		switch( requestCode ){
		case PERMISSION_REQUEST_CODE:
			// If request is cancelled, the result arrays are empty.
			if( grantResults.length > 0 &&
				grantResults[ 0 ] == PackageManager.PERMISSION_GRANTED
				){
				openAttachment();
			}else{
				Utils.showToast( this, true, R.string.missing_storage_permission );
			}
			break;
		}
	}
	
	public String getDocumentName( Uri uri ){
		
		Cursor cursor = getContentResolver().query( uri, null, null, null, null, null );
		if( cursor != null ){
			try{
				if( cursor.moveToFirst() ){
					return cursor.getString( cursor.getColumnIndex( OpenableColumns.DISPLAY_NAME ) );
				}
			}finally{
				cursor.close();
			}
		}
		return "no_name";
	}
	
	long getStreamSize( boolean bClose, InputStream is ) throws IOException{
		try{
			long size = 0L;
			for( ; ; ){
				long r = IOUtils.skip( is, 16384 );
				if( r <= 0 ) break;
				size += r;
			}
			return size;
		}finally{
			if( bClose ) IOUtils.closeQuietly( is );
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// visibility
	
	String visibility;
	
	private void showVisibility(){
		btnVisibility.setImageResource( Styler.getVisibilityIcon( this, visibility ) );
	}
	
	private void performVisibility(){
		final String[] caption_list = new String[]{
			getString( R.string.visibility_public ),
			getString( R.string.visibility_unlisted ),
			getString( R.string.visibility_private ),
			getString( R.string.visibility_direct ),
		};
		
		new AlertDialog.Builder( this )
			.setTitle( R.string.choose_visibility )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					switch( which ){
					case 0:
						visibility = TootStatus.VISIBILITY_PUBLIC;
						break;
					case 1:
						visibility = TootStatus.VISIBILITY_UNLISTED;
						break;
					case 2:
						visibility = TootStatus.VISIBILITY_PRIVATE;
						break;
					case 3:
						visibility = TootStatus.VISIBILITY_DIRECT;
						break;
					}
					showVisibility();
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	
	private void performMore(){
		ActionsDialog dialog = new ActionsDialog();
		
		final ArrayList< JSONObject > list_draft = PostDraft.loadList( 20 );
		
		if( ! list_draft.isEmpty() ){
			dialog.addAction(
				getString( R.string.restore_draft )
				, new Runnable() {
					@Override public void run(){
						openDraftPicker( list_draft );
					}
				}
			);
		}
		
		dialog.addAction(
			getString( R.string.clear_text )
			, new Runnable() {
				@Override public void run(){
					etContent.setText( "" );
					etContentWarning.setText( "" );
				}
			}
		);
		dialog.addAction(
			getString( R.string.clear_text_and_media )
			, new Runnable() {
				@Override public void run(){
					etContent.setText( "" );
					etContentWarning.setText( "" );
					if( attachment_list != null ){
						attachment_list.clear();
						showMediaAttachment();
					}
				}
			}
		);
		dialog.show( this, null );
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	// post
	
	// [:word:] 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
	// [:alpha:] 英字 (Letter | Mark)
	
	static final String word = "[_\\p{L}\\p{M}\\p{Nd}\\p{Pc}]";
	static final String alpha = "[_\\p{L}\\p{M}]";
	
	static final Pattern reTag = Pattern.compile(
		"(?:^|[^/)\\w])#(" + word + "*" + alpha + word + "*)"
		, Pattern.CASE_INSENSITIVE
	);
	
	private void performPost( final boolean bConfirmTag, final boolean bConfirmAccount ){
		final String content = etContent.getText().toString().trim();
		if( TextUtils.isEmpty( content ) ){
			Utils.showToast( this, true, R.string.post_error_contents_empty );
			return;
		}
		
		final String spoiler_text;
		if( ! cbContentWarning.isChecked() ){
			spoiler_text = null;
		}else{
			spoiler_text = etContentWarning.getText().toString().trim();
			if( TextUtils.isEmpty( spoiler_text ) ){
				Utils.showToast( this, true, R.string.post_error_contents_warning_empty );
				return;
			}
		}
		
		if( ! bConfirmAccount ){
			DlgConfirm.open( this
				, getString( R.string.confirm_post_from, AcctColor.getNickname( account.acct ) )
				, new DlgConfirm.Callback() {
					@Override public boolean isConfirmEnabled(){
						return account.confirm_post;
					}
					
					@Override public void setConfirmEnabled( boolean bv ){
						account.confirm_post = bv;
						account.saveSetting();
					}
					
					@Override public void onOK(){
						performPost( bConfirmTag, true );
					}
				} );
			return;
		}
		
		if( ! bConfirmTag ){
			Matcher m = reTag.matcher( content );
			if( m.find() && ! TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ){
				new AlertDialog.Builder( this )
					.setCancelable( true )
					.setMessage( R.string.hashtag_and_visibility_not_match )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							//noinspection ConstantConditions
							performPost( true, bConfirmAccount );
						}
					} )
					.show();
				return;
			}
		}
		
		final StringBuilder sb = new StringBuilder();
		
		sb.append( "status=" );
		sb.append( Uri.encode( content ) );
		
		sb.append( "&visibility=" );
		sb.append( Uri.encode( visibility ) );
		
		if( cbNSFW.isChecked() ){
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
		
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			final SavedAccount target_account = account;
			
			TootStatus status;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
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
				String post_content = sb.toString();
				String digest = Utils.digestSHA256( post_content + target_account.acct );
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, post_content
					) )
					.header( "Idempotency-Key", digest );
				
				TootApiResult result = client.request( "/api/v1/statuses", request_builder );
				if( result.object != null ){
					status = TootStatus.parse( log, account, result.object );
				}
				return result;
				
			}
			
			@Override
			protected void onCancelled(){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				progress.dismiss();
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( status != null ){
					// 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
					Intent data = new Intent();
					data.putExtra( EXTRA_POSTED_ACCT, target_account.acct );
					data.putExtra( EXTRA_POSTED_STATUS_ID, status.id );
					
					setResult( RESULT_OK, data );
					ActPost.this.finish();
				}else{
					Utils.showToast( ActPost.this, true, result.error );
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
	
	/////////////////////////////////////////////////
	
	long in_reply_to_id = - 1L;
	String in_reply_to_text;
	String in_reply_to_image;
	
	void showReplyTo(){
		if( in_reply_to_id == - 1L ){
			llReply.setVisibility( View.GONE );
		}else{
			llReply.setVisibility( View.VISIBLE );
			tvReplyTo.setText( HTMLDecoder.decodeHTML( account, in_reply_to_text ) );
			ivReply.setCornerRadius( pref, 16f );
			ivReply.setImageUrl( in_reply_to_image );
			
		}
	}
	
	private void removeReply(){
		in_reply_to_id = - 1L;
		in_reply_to_text = null;
		in_reply_to_image = null;
		showReplyTo();
	}
	
	/////////////////////////////////////////////////
	
	static final String DRAFT_CONTENT = "content";
	static final String DRAFT_CONTENT_WARNING = "content_warning";
	static final String DRAFT_CONTENT_WARNING_CHECK = "content_warning_check";
	static final String DRAFT_NSFW_CHECK = "nsfw_check";
	static final String DRAFT_VISIBILITY = "visibility";
	static final String DRAFT_ACCOUNT_DB_ID = "account_db_id";
	static final String DRAFT_ATTACHMENT_LIST = "attachment_list";
	static final String DRAFT_REPLY_ID = "reply_id";
	static final String DRAFT_REPLY_TEXT = "reply_text";
	static final String DRAFT_REPLY_IMAGE = "reply_image";
	
	private void saveDraft(){
		String content = etContent.getText().toString();
		String content_warning = etContentWarning.getText().toString();
		if( TextUtils.isEmpty( content.trim() ) && TextUtils.isEmpty( content_warning.trim() ) ){
			log.d( "saveDraft: dont save empty content" );
			return;
		}
		try{
			JSONArray tmp_attachment_list = new JSONArray();
			if( attachment_list != null ){
				for( PostAttachment pa : attachment_list ){
					if( pa.attachment != null ) tmp_attachment_list.put( pa.attachment.json );
				}
			}
			
			JSONObject json = new JSONObject();
			json.put( DRAFT_CONTENT, content );
			json.put( DRAFT_CONTENT_WARNING, content_warning );
			json.put( DRAFT_CONTENT_WARNING_CHECK, cbContentWarning.isChecked() );
			json.put( DRAFT_NSFW_CHECK, cbNSFW.isChecked() );
			json.put( DRAFT_VISIBILITY, visibility );
			json.put( DRAFT_ACCOUNT_DB_ID, ( account == null ? - 1L : account.db_id ) );
			json.put( DRAFT_ATTACHMENT_LIST, tmp_attachment_list );
			json.put( DRAFT_REPLY_ID, in_reply_to_id );
			json.put( DRAFT_REPLY_TEXT, in_reply_to_text );
			json.put( DRAFT_REPLY_IMAGE, in_reply_to_image );
			
			PostDraft.save( System.currentTimeMillis(), json );
			
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	private void openDraftPicker( @NonNull ArrayList< JSONObject > list_draft ){
		
		ActionsDialog dialog = new ActionsDialog();
		for( JSONObject o : list_draft ){
			final JSONObject draft = o;
			String cw = draft.optString( DRAFT_CONTENT_WARNING );
			String c = draft.optString( DRAFT_CONTENT );
			StringBuilder sb = new StringBuilder();
			if( ! TextUtils.isEmpty( cw.trim() ) ){
				sb.append( cw );
			}
			if( ! TextUtils.isEmpty( c.trim() ) ){
				if( sb.length() > 0 ) sb.append( "\n" );
				sb.append( c );
			}
			String caption = sb.toString();
			dialog.addAction(
				caption
				, new Runnable() {
					@Override public void run(){
						restoreDraft( draft );
					}
				}
			);
		}
		
		dialog.show( this, getString( R.string.select_draft ) );
	}
	
	static boolean check_exist( String url ){
		try{
			Request request = new Request.Builder().url( url ).build();
			Call call = App1.ok_http_client.newCall( request );
			return call.execute().isSuccessful();
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		return false;
	}
	
	private void restoreDraft( final JSONObject draft ){
		
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, String, String > task = new AsyncTask< Void, String, String >() {
			
			ArrayList< String > list_warning = new ArrayList<>();
			SavedAccount account;
			
			@Override protected String doInBackground( Void... params ){
				
				String content = draft.optString( DRAFT_CONTENT );
				String content_warning = draft.optString( DRAFT_CONTENT_WARNING );
				boolean content_warning_checked = draft.optBoolean( DRAFT_CONTENT_WARNING_CHECK );
				boolean nsfw_checked = draft.optBoolean( DRAFT_NSFW_CHECK );
				long account_db_id = draft.optLong( DRAFT_ACCOUNT_DB_ID );
				JSONArray tmp_attachment_list = draft.optJSONArray( DRAFT_ATTACHMENT_LIST );
				long reply_id = draft.optLong( DRAFT_REPLY_ID, in_reply_to_id );
				String reply_text = draft.optString( DRAFT_REPLY_TEXT, in_reply_to_text );
				String reply_image = draft.optString( DRAFT_REPLY_IMAGE, in_reply_to_image );
				
				account = SavedAccount.loadAccount( log, account_db_id );
				if( account == null ){
					list_warning.add( getString( R.string.account_in_draft_is_lost ) );
					try{
						for( int i = 0, ie = tmp_attachment_list.length() ; i < ie ; ++ i ){
							TootAttachment ta = TootAttachment.parse( log, tmp_attachment_list.optJSONObject( i ) );
							if( ta != null ){
								content = content.replace( ta.text_url, "" );
							}
						}
						tmp_attachment_list = new JSONArray();
						draft.put( DRAFT_ATTACHMENT_LIST, tmp_attachment_list );
						draft.put( DRAFT_CONTENT, content );
						draft.remove( DRAFT_REPLY_ID );
						draft.remove( DRAFT_REPLY_TEXT );
						draft.remove( DRAFT_REPLY_IMAGE );
					}catch( JSONException ignored ){
					}
					return "OK";
				}
				// アカウントがあるなら基本的にはすべての情報を復元できるはずだが、いくつか確認が必要だ
				TootApiClient api_client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
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
				api_client.setAccount( account );
				
				if( in_reply_to_id != - 1L ){
					TootApiResult result = api_client.request( "/api/v1/statuses/" + in_reply_to_id );
					if( isCancelled() ) return null;
					if( result != null && result.object == null ){
						list_warning.add( getString( R.string.reply_to_in_draft_is_lost ) );
						draft.remove( DRAFT_REPLY_ID );
						draft.remove( DRAFT_REPLY_TEXT );
						draft.remove( DRAFT_REPLY_IMAGE );
					}
				}
				try{
					boolean isSomeAttachmentRemoved = false;
					for( int i = tmp_attachment_list.length() - 1 ; i >= 0 ; -- i ){
						if( isCancelled() ) return null;
						TootAttachment ta = TootAttachment.parse( log, tmp_attachment_list.optJSONObject( i ) );
						if( ta == null ){
							isSomeAttachmentRemoved = true;
							tmp_attachment_list.remove( i );
						}else if( ! check_exist( ta.url ) ){
							isSomeAttachmentRemoved = true;
							tmp_attachment_list.remove( i );
							content = content.replace( ta.text_url, "" );
						}
					}
					if( isSomeAttachmentRemoved ){
						list_warning.add( getString( R.string.attachment_in_draft_is_lost ) );
						draft.put( DRAFT_ATTACHMENT_LIST, tmp_attachment_list );
						draft.put( DRAFT_CONTENT, content );
					}
				}catch( JSONException ex ){
					ex.printStackTrace();
				}
				
				return "OK";
			}
			
			@Override protected void onCancelled( String result ){
				super.onCancelled( result );
			}
			
			@Override protected void onPostExecute( String result ){
				progress.dismiss();
				
				if( isCancelled() || result == null ){
					// cancelled.
					return;
				}
				
				String content = draft.optString( DRAFT_CONTENT );
				String content_warning = draft.optString( DRAFT_CONTENT_WARNING );
				boolean content_warning_checked = draft.optBoolean( DRAFT_CONTENT_WARNING_CHECK );
				boolean nsfw_checked = draft.optBoolean( DRAFT_NSFW_CHECK );
				JSONArray tmp_attachment_list = draft.optJSONArray( DRAFT_ATTACHMENT_LIST );
				long reply_id = draft.optLong( DRAFT_REPLY_ID, in_reply_to_id );
				String reply_text = draft.optString( DRAFT_REPLY_TEXT, in_reply_to_text );
				String reply_image = draft.optString( DRAFT_REPLY_IMAGE, in_reply_to_image );
				
				etContent.setText( content );
				etContent.setSelection( content.length() );
				etContentWarning.setText( content_warning );
				etContentWarning.setSelection( content_warning.length() );
				cbContentWarning.setChecked( content_warning_checked );
				cbNSFW.setChecked( nsfw_checked );
				ActPost.this.visibility = visibility;
				
				if( account != null ) setAccount( account );
				
				if( tmp_attachment_list.length() > 0 ){
					if( attachment_list != null ){
						attachment_list.clear();
					}else{
						attachment_list = new ArrayList<>();
					}
					for( int i = 0, ie = tmp_attachment_list.length() ; i < ie ; ++ i ){
						TootAttachment ta = TootAttachment.parse( log, tmp_attachment_list.optJSONObject( i ) );
						if( ta != null ){
							PostAttachment pa = new PostAttachment( ta );
							attachment_list.add( pa );
						}
					}
				}
				if( reply_id != - 1L ){
					in_reply_to_id = reply_id;
					in_reply_to_text = reply_text;
					in_reply_to_image = reply_image;
				}
				
				updateContentWarning();
				showMediaAttachment();
				showVisibility();
				updateTextCount();
				showReplyTo();
				
				if( ! list_warning.isEmpty() ){
					StringBuilder sb = new StringBuilder();
					for( String s : list_warning ){
						if( sb.length() > 0 ) sb.append( "\n" );
						sb.append( s );
					}
					new AlertDialog.Builder( ActPost.this )
						.setMessage( sb )
						.setNeutralButton( R.string.close, null )
						.show();
				}
			}
		};
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
}
