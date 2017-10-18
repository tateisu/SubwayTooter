package jp.juggler.subwaytooter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
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
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootMention;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgDraftPicker;
import jp.juggler.subwaytooter.dialog.DlgTextInput;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.PostDraft;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.EmojiDecoder;
import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyClickableSpan;
import jp.juggler.subwaytooter.util.PostHelper;
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
	static final String KEY_IN_REPLY_TO_URL = "in_reply_to_url";
	
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
			performAttachmentClick( 0 );
			break;
		case R.id.ivMedia2:
			performAttachmentClick( 1 );
			break;
		case R.id.ivMedia3:
			performAttachmentClick( 2 );
			break;
		case R.id.ivMedia4:
			performAttachmentClick( 3 );
			break;
		
		case R.id.btnPost:
			performPost();
			break;
		
		case R.id.btnRemoveReply:
			removeReply();
			break;
		
		case R.id.btnMore:
			performMore();
			break;
		
		case R.id.btnPlugin:
			openMushroom();
		}
	}
	
	private static final int REQUEST_CODE_ATTACHMENT = 1;
	private static final int REQUEST_CODE_CAMERA = 2;
	private static final int REQUEST_CODE_MUSHROOM = 3;
	private static final int REQUEST_CODE_VIDEO = 4;
	
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
		}else if( requestCode == REQUEST_CODE_VIDEO && resultCode == RESULT_OK ){
			Uri uri = ( data == null ? null : data.getData() );
			if( uri != null ){
				String type = getContentResolver().getType( uri );
				addAttachment( uri, type );
			}
			
		}else if( requestCode == REQUEST_CODE_MUSHROOM && resultCode == RESULT_OK ){
			String text = data.getStringExtra( "replace_key" );
			applyMushroomResult( text );
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override public void onBackPressed(){
		saveDraft();
		super.onBackPressed();
	}
	
	@Override protected void onResume(){
		super.onResume();
		MyClickableSpan.link_callback = new WeakReference<>( link_click_listener );
	}
	
	@Override protected void onPause(){
		super.onPause();
		
		// 編集中にホーム画面を押したり他アプリに移動する場合は下書きを保存する
		// やや過剰な気がするが、自アプリに戻ってくるときにランチャーからアイコンタップされると
		// メイン画面より上にあるアクティビティはすべて消されてしまうので
		// このタイミングで保存するしかない
		if( ! isPostComplete ){
			saveDraft();
		}
	}
	
	SharedPreferences pref;
	ArrayList< PostAttachment > attachment_list;
	AppState app_state;
	boolean isPostComplete;
	PostHelper post_helper;
	
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
			
			mushroom_input = savedInstanceState.getInt( STATE_MUSHROOM_INPUT, 0 );
			mushroom_start = savedInstanceState.getInt( STATE_MUSHROOM_START, 0 );
			mushroom_end = savedInstanceState.getInt( STATE_MUSHROOM_END, 0 );
			
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
							TootAttachment a = TootAttachment.parse( array.optJSONObject( i ) );
							if( a != null ){
								attachment_list.add( new PostAttachment( a ) );
							}
						}catch( Throwable ex ){
							log.trace( ex );
						}
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
			
			this.in_reply_to_id = savedInstanceState.getLong( KEY_IN_REPLY_TO_ID, - 1L );
			this.in_reply_to_text = savedInstanceState.getString( KEY_IN_REPLY_TO_TEXT );
			this.in_reply_to_image = savedInstanceState.getString( KEY_IN_REPLY_TO_IMAGE );
			this.in_reply_to_url = savedInstanceState.getString( KEY_IN_REPLY_TO_URL );
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
				}else if( type.startsWith( "image/" ) || type.startsWith( "video/" ) ){
					
					if( Intent.ACTION_VIEW.equals( action ) ){
						Uri uri = sent_intent.getData();
						if( uri != null ){
							addAttachment( uri, type );
						}
					}else if( Intent.ACTION_SEND.equals( action ) ){
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
					TootStatus reply_status = TootStatus.parse( ActPost.this, account, new JSONObject( sv ) );
					
					if( reply_status != null ){
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
						in_reply_to_image = reply_status.account == null ? null : reply_status.account.avatar_static;
						in_reply_to_url = reply_status.url;
						
						// 公開範囲
						try{
							// 比較する前にデフォルトの公開範囲を計算する
							if( TextUtils.isEmpty( visibility ) ){
								visibility = account.visibility;
								if( TextUtils.isEmpty( visibility ) ){
									visibility = TootStatus.VISIBILITY_WEB_SETTING;
								}
							}
							
							if( TootStatus.VISIBILITY_WEB_SETTING.equals( visibility ) ){
								// 「Web設定に合わせる」だった場合は無条件にリプライ元の公開範囲に変更する
								this.visibility = reply_status.visibility;
							}else{
								// デフォルトの方が公開範囲が大きい場合、リプライ元に合わせて公開範囲を狭める
								int i = TootStatus.compareVisibility( this.visibility, reply_status.visibility );
								if( i > 0 ){ // より大きい=>より公開範囲が広い
									this.visibility = reply_status.visibility;
								}
							}
							
						}catch( Throwable ex ){
							log.trace( ex );
						}
						
					}
					
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
		}
		
		if( TextUtils.isEmpty( visibility ) ){
			visibility = account.visibility;
			if( TextUtils.isEmpty( visibility ) ){
				visibility = TootStatus.VISIBILITY_PUBLIC;
				// 2017/9/13 VISIBILITY_WEB_SETTING から VISIBILITY_PUBLICに変更した
				// VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになるので…
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
		showEnquete();
	}
	
	@Override protected void onDestroy(){
		post_helper.onDestroy();
		
		super.onDestroy();
		
	}
	
	@Override
	protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		
		outState.putInt( STATE_MUSHROOM_INPUT, mushroom_input );
		outState.putInt( STATE_MUSHROOM_START, mushroom_start );
		outState.putInt( STATE_MUSHROOM_END, mushroom_end );
		
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
		outState.putString( KEY_IN_REPLY_TO_URL, in_reply_to_url );
	}
	
	@Override protected void onRestoreInstanceState( Bundle savedInstanceState ){
		super.onRestoreInstanceState( savedInstanceState );
		updateContentWarning();
		showMediaAttachment();
		showVisibility();
		updateTextCount();
		showReplyTo();
		showEnquete();
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
	
	CheckBox cbEnquete;
	View llEnquete;
	final MyEditText[] list_etChoice = new MyEditText[ 4 ];
	
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
		
		if( Pref.pref( this ).getBoolean( Pref.KEY_POST_BUTTON_BAR_AT_TOP, false ) ){
			View bar = findViewById( R.id.llFooterBar );
			ViewGroup parent = (ViewGroup) bar.getParent();
			parent.removeView( bar );
			parent.addView( bar, 0 );
		}
		
		Styler.fixHorizontalMargin( findViewById( R.id.scrollView ) );
		Styler.fixHorizontalMargin( findViewById( R.id.llFooterBar ) );
		
		formRoot = findViewById( R.id.viewRoot );
		scrollView = findViewById( R.id.scrollView );
		btnAccount = findViewById( R.id.btnAccount );
		btnVisibility = findViewById( R.id.btnVisibility );
		btnAttachment = findViewById( R.id.btnAttachment );
		btnPost = findViewById( R.id.btnPost );
		llAttachment = findViewById( R.id.llAttachment );
		ivMedia[ 0 ] = findViewById( R.id.ivMedia1 );
		ivMedia[ 1 ] = findViewById( R.id.ivMedia2 );
		ivMedia[ 2 ] = findViewById( R.id.ivMedia3 );
		ivMedia[ 3 ] = findViewById( R.id.ivMedia4 );
		cbNSFW = findViewById( R.id.cbNSFW );
		cbContentWarning = findViewById( R.id.cbContentWarning );
		etContentWarning = findViewById( R.id.etContentWarning );
		etContent = findViewById( R.id.etContent );
		
		cbEnquete = findViewById( R.id.cbEnquete );
		llEnquete = findViewById( R.id.llEnquete );
		list_etChoice[ 0 ] = findViewById( R.id.etChoice1 );
		list_etChoice[ 1 ] = findViewById( R.id.etChoice2 );
		list_etChoice[ 2 ] = findViewById( R.id.etChoice3 );
		list_etChoice[ 3 ] = findViewById( R.id.etChoice4 );
		
		tvCharCount = findViewById( R.id.tvCharCount );
		
		llReply = findViewById( R.id.llReply );
		tvReplyTo = findViewById( R.id.tvReplyTo );
		btnRemoveReply = findViewById( R.id.btnRemoveReply );
		ivReply = findViewById( R.id.ivReply );
		
		account_list = SavedAccount.loadAccountList( ActPost.this, log );
		SavedAccount.sort( account_list );
		
		btnAccount.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		btnAttachment.setOnClickListener( this );
		btnPost.setOnClickListener( this );
		btnRemoveReply.setOnClickListener( this );
		
		findViewById( R.id.btnPlugin ).setOnClickListener( this );
		
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
		cbEnquete.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
				showEnquete();
				updateTextCount();
			}
		} );
		
		post_helper = new PostHelper( this, pref, app_state.handler );
		post_helper.attachEditText( formRoot, etContent, false, new PostHelper.Callback2() {
			@Override public void onTextUpdate(){
				updateTextCount();
			}
			
			@Override public boolean canOpenPopup(){
				return true;
			}
		} );
		
		etContentWarning.addTextChangedListener( text_watcher );
		for( MyEditText et : list_etChoice ){
			et.addTextChangedListener( text_watcher );
		}
		
		scrollView.getViewTreeObserver().addOnScrollChangedListener( scroll_listener );
		
		View v = findViewById( R.id.btnMore );
		v.setOnClickListener( this );
	}
	
	final TextWatcher text_watcher = new TextWatcher() {
		@Override public void beforeTextChanged( CharSequence charSequence, int i, int i1, int i2 ){
			
		}
		
		@Override public void onTextChanged( CharSequence charSequence, int i, int i1, int i2 ){
			
		}
		
		@Override public void afterTextChanged( Editable editable ){
			updateTextCount();
		}
	};
	
	final ViewTreeObserver.OnScrollChangedListener scroll_listener = new ViewTreeObserver.OnScrollChangedListener() {
		@Override public void onScrollChanged(){
			post_helper.onScrollChanged();
			
		}
	};
	
	private void updateTextCount(){
		int length = 0;
		
		String s = EmojiDecoder.decodeShortCode( etContent.getText().toString() );
		length += s.codePointCount( 0, s.length() );
		
		s = cbContentWarning.isChecked() ? EmojiDecoder.decodeShortCode( etContentWarning.getText().toString() ) : "";
		length += s.codePointCount( 0, s.length() );
		
		int max;
		if( ! cbEnquete.isChecked() ){
			max = 500;
		}else{
			max = 350;
			for( MyEditText et : list_etChoice ){
				s = EmojiDecoder.decodeShortCode( et.getText().toString() );
				length += s.codePointCount( 0, s.length() );
			}
		}
		
		int remain = max - length;
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
		if( a == null ){
			if( post_helper != null ) post_helper.setInstance( null );
			btnAccount.setText( getString( R.string.not_selected ) );
			btnAccount.setTextColor( Styler.getAttributeColor( this, android.R.attr.textColorPrimary ) );
			btnAccount.setBackgroundResource( R.drawable.btn_bg_transparent );
		}else{
			if( post_helper != null ) post_helper.setInstance( a.host );
			String acct = a.getFullAcct( a );
			AcctColor ac = AcctColor.load( acct );
			String nickname = AcctColor.hasNickname( ac ) ? ac.nickname : acct;
			btnAccount.setText( nickname );
			
			if( AcctColor.hasColorBackground( ac ) ){
				btnAccount.setBackgroundColor( ac.color_bg );
			}else{
				btnAccount.setBackgroundResource( R.drawable.btn_bg_transparent );
			}
			if( AcctColor.hasColorForeground( ac ) ){
				btnAccount.setTextColor( ac.color_fg );
			}else{
				btnAccount.setTextColor( Styler.getAttributeColor( this, android.R.attr.textColorPrimary ) );
			}
		}
	}
	
	private void performAccountChooser(){
		
		if( attachment_list != null && ! attachment_list.isEmpty() ){
			// 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
			Utils.showToast( this, false, R.string.cant_change_account_when_attachment_specified );
			return;
		}
		
		AccountPicker.pick( this, false, false, getString( R.string.choose_account ), new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( @NonNull SavedAccount ai ){
				
				if( ! ai.host.equals( account.host ) ){
					// 別タンスへの移動
					if( in_reply_to_id != - 1L ){
						// 別タンスのアカウントならin_reply_toの変換が必要
						startReplyConversion( ai );
						
					}
				}
				
				// リプライがないか、同タンスへの移動
				setAccountWithVisibilityConversion( ai );
			}
		} );
		
		//		final ArrayList< SavedAccount > tmp_account_list = new ArrayList<>();
		//		tmp_account_list.addAll( account_list );
		//
		//		String[] caption_list = new String[ tmp_account_list.size() ];
		//		for( int i = 0, ie = tmp_account_list.size() ; i < ie ; ++ i ){
		//			caption_list[ i ] = tmp_account_list.get( i ).acct;
		//		}
		//
		//		new AlertDialog.Builder( this )
		//			.setTitle( R.string.choose_account )
		//			.setItems( caption_list, new DialogInterface.OnClickListener() {
		//				@Override
		//				public void onClick( DialogInterface dialog, int which ){
		//
		//					if( which < 0 || which >= tmp_account_list.size() ){
		//						// 範囲外
		//						return;
		//					}
		//
		//					SavedAccount ai = tmp_account_list.get( which );
		//
		//					if( ! ai.host.equals( account.host ) ){
		//						// 別タンスへの移動
		//						if( in_reply_to_id != - 1L ){
		//							// 別タンスのアカウントならin_reply_toの変換が必要
		//							startReplyConversion( ai );
		//
		//						}
		//					}
		//
		//					// リプライがないか、同タンスへの移動
		//					setAccountWithVisibilityConversion( ai );
		//				}
		//			} )
		//			.setNegativeButton( R.string.cancel, null )
		//			.show();
	}
	
	void setAccountWithVisibilityConversion( @NonNull SavedAccount a ){
		setAccount( a );
		try{
			if( a.visibility != null && TootStatus.compareVisibility( visibility, a.visibility ) > 0 ){
				Utils.showToast( ActPost.this, true, R.string.spoil_visibility_for_account );
				visibility = a.visibility;
				showVisibility();
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	private void startReplyConversion( @NonNull final SavedAccount access_info ){
		if( in_reply_to_url == null ){
			// 下書きが古い形式の場合、URLがないので別タンスへの移動ができない
			new AlertDialog.Builder( ActPost.this )
				.setMessage( R.string.account_change_failed_old_draft_has_no_in_reply_to_url )
				.setNeutralButton( R.string.close, null )
				.show();
			return;
		}
		
		final ProgressDialog progress = new ProgressDialog( this );
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			TootStatus target_status;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( access_info );
				
				// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
				String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( in_reply_to_url ) );
				path = path + "&resolve=1";
				
				TootApiResult result = client.request( path );
				if( result != null && result.object != null ){
					TootResults tmp = TootResults.parse( ActPost.this, access_info, result.object );
					if( tmp != null && tmp.statuses != null && ! tmp.statuses.isEmpty() ){
						target_status = tmp.statuses.get( 0 );
					}
					if( target_status == null ){
						return new TootApiResult( getString( R.string.status_id_conversion_failed ) );
					}
				}
				return result;
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ex ){
					log.trace( ex );
				}
				if( isCancelled() ) return;
				
				if( result == null ){
					// cancelled.
				}else if( target_status != null ){
					in_reply_to_id = target_status.id;
					setAccountWithVisibilityConversion( access_info );
				}else{
					Utils.showToast( ActPost.this, true, getString( R.string.in_reply_to_id_conversion_failed ) + "\n" + result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
		
		progress.setIndeterminate( true );
		progress.setOnDismissListener( new DialogInterface.OnDismissListener() {
			@Override public void onDismiss( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
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
			PostAttachment a = attachment_list.get( idx );
			if( a.attachment != null && a.status == PostAttachment.ATTACHMENT_UPLOADED ){
				iv.setImageUrl( pref, 16f, a.attachment.preview_url );
			}else{
				iv.setImageUrl( pref, 16f, null );
			}
		}
	}
	
	// 添付した画像をタップ
	void performAttachmentClick( int idx ){
		final PostAttachment pa = attachment_list.get( idx );
		
		new AlertDialog.Builder( this )
			.setTitle( R.string.media_attachment )
			.setItems( new CharSequence[]{
				getString( R.string.set_description ),
				getString( R.string.delete )
			}, new DialogInterface.OnClickListener() {
				@Override public void onClick( DialogInterface dialogInterface, int i ){
					switch( i ){
					case 0:
						editAttachmentDescription( pa );
						break;
					case 1:
						deleteAttachment( pa );
						break;
					}
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
	}
	
	void deleteAttachment( @NonNull final PostAttachment pa ){
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
	
	void editAttachmentDescription( @NonNull final PostAttachment pa ){
		if( pa.attachment == null ){
			Utils.showToast( this, true, R.string.attachment_description_cant_edit_while_uploading );
		}
		DlgTextInput.show( this, getString( R.string.attachment_description ), pa.attachment.description, new DlgTextInput.Callback() {
			@Override public void onOK( Dialog dialog, String text ){
				setAttachmentDescription( pa, dialog, text );
			}
			
			@Override public void onEmptyError(){
				Utils.showToast( ActPost.this, true, R.string.description_empty );
			}
		} );
	}
	
	private void setAttachmentDescription( final PostAttachment pa, final Dialog dialog, final String text ){
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( ActPost.this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			final SavedAccount target_account = account;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
						progress.setMessage( s );
					}
				} );
				client.setAccount( target_account );
				
				JSONObject json = new JSONObject();
				try{
					json.put( "description", text );
				}catch( JSONException ex ){
					log.trace( ex );
					log.e( ex, "description encoding failed." );
				}
				
				String body_string = json.toString();
				RequestBody request_body = RequestBody.create(
					TootApiClient.MEDIA_TYPE_JSON
					, body_string
				);
				final Request.Builder request_builder = new Request.Builder().put( request_body );
				
				TootApiResult result = client.request( "/api/v1/media/" + pa.attachment.id, request_builder );
				if( result != null && result.object != null ){
					this.attachment = TootAttachment.parse( result.object );
				}
				return result;
			}
			
			TootAttachment attachment;
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onCancelled( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
					
				}
				
				if( result == null ){
					// cancelled.
				}else if( attachment != null ){
					pa.attachment = attachment;
					showMediaAttachment();
					
					try{
						dialog.dismiss();
					}catch( Throwable ignored ){
						
					}
					
				}else{
					Utils.showToast( ActPost.this, true, result.error );
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
	
	void openAttachment(){
		int permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.WRITE_EXTERNAL_STORAGE );
		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
			preparePermission();
			return;
		}
		
		//		permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.CAMERA );
		//		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
		//			preparePermission();
		//			return;
		//		}
		
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
		
		//		a.addAction( getString( R.string.video_capture ), new Runnable() {
		//			@Override public void run(){
		//				performCameraVideo();
		//			}
		//		} );
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
			log.trace( ex );
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
				log.trace( ex );
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
	
	static HashSet< String > acceptable_mime_types;
	
	void addAttachment( final Uri uri, final String mime_type ){
		if( attachment_list != null && attachment_list.size() >= 4 ){
			Utils.showToast( this, false, R.string.attachment_too_many );
			return;
		}
		if( account == null ){
			Utils.showToast( this, false, R.string.account_select_please );
			return;
		}
		
		if( acceptable_mime_types == null ){
			acceptable_mime_types = new HashSet<>();
			//
			acceptable_mime_types.add( "image/*" ); // Android標準のギャラリーが image/* を出してくることがあるらしい
			acceptable_mime_types.add( "video/*" ); // Android標準のギャラリーが image/* を出してくることがあるらしい
			//
			acceptable_mime_types.add( "image/jpeg" );
			acceptable_mime_types.add( "image/png" );
			acceptable_mime_types.add( "image/gif" );
			acceptable_mime_types.add( "video/webm" );
			acceptable_mime_types.add( "video/mp4" );
		}
		
		if( TextUtils.isEmpty( mime_type ) ){
			Utils.showToast( this, false, R.string.mime_type_missing );
			return;
		}else if( ! acceptable_mime_types.contains( mime_type ) ){
			Utils.showToast( this, true, R.string.mime_type_not_acceptable, mime_type );
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
								public void writeTo( @NonNull BufferedSink sink ) throws IOException{
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
					
					if( result != null && result.object != null ){
						pa.attachment = TootAttachment.parse( result.object );
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
				
				// 投稿欄の末尾に追記する
				int selStart = etContent.getSelectionStart();
				int selEnd = etContent.getSelectionEnd();
				Editable e = etContent.getEditableText();
				int len = e.length();
				char last_char = ( len <= 0 ? ' ' : e.charAt( len - 1 ) );
				if( ! EmojiDecoder.isWhitespaceBeforeEmoji( last_char ) ){
					e.append( " " + pa.attachment.text_url );
				}else{
					e.append( pa.attachment.text_url );
				}
				etContent.setSelection( selStart, selEnd );
				
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
			log.trace( ex );
			Utils.showToast( this, ex, "opening camera app failed." );
		}
	}
	
	private void performCameraVideo(){
		
		try{
			Intent takeVideoIntent = new Intent( MediaStore.ACTION_VIDEO_CAPTURE );
			startActivityForResult( takeVideoIntent, REQUEST_CODE_VIDEO );
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "opening video app failed." );
		}
	}
	
	private static final int PERMISSION_REQUEST_CODE = 1;
	
	private void preparePermission(){
		if( Build.VERSION.SDK_INT >= 23 ){
			// No explanation needed, we can request the permission.
			
			ActivityCompat.requestPermissions( this
				, new String[]{
					Manifest.permission.WRITE_EXTERNAL_STORAGE,
					//		Manifest.permission.CAMERA,
				}
				, PERMISSION_REQUEST_CODE
			);
		}else{
			Utils.showToast( this, true, R.string.missing_permission_to_access_media );
		}
	}
	
	@Override public void onRequestPermissionsResult(
		int requestCode
		, @NonNull String permissions[]
		, @NonNull int[] grantResults
	){
		switch( requestCode ){
		case PERMISSION_REQUEST_CODE:
			boolean bNotGranted = false;
			for( int i = 0, ie = permissions.length ; i < ie ; ++ i ){
				if( grantResults[ i ] != PackageManager.PERMISSION_GRANTED ){
					bNotGranted = true;
				}
			}
			if( bNotGranted ){
				Utils.showToast( this, true, R.string.missing_permission_to_access_media );
			}else{
				openAttachment();
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
		final CharSequence[] caption_list = new CharSequence[]{
			Styler.getVisibilityCaption( this, TootStatus.VISIBILITY_WEB_SETTING ),
			Styler.getVisibilityCaption( this, TootStatus.VISIBILITY_PUBLIC ),
			Styler.getVisibilityCaption( this, TootStatus.VISIBILITY_UNLISTED ),
			Styler.getVisibilityCaption( this, TootStatus.VISIBILITY_PRIVATE ),
			Styler.getVisibilityCaption( this, TootStatus.VISIBILITY_DIRECT ),
		};
		
		new AlertDialog.Builder( this )
			.setTitle( R.string.choose_visibility )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					switch( which ){
					case 0:
						visibility = TootStatus.VISIBILITY_WEB_SETTING;
						break;
					case 1:
						visibility = TootStatus.VISIBILITY_PUBLIC;
						break;
					case 2:
						visibility = TootStatus.VISIBILITY_UNLISTED;
						break;
					case 3:
						visibility = TootStatus.VISIBILITY_PRIVATE;
						break;
					case 4:
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
		
		if( PostDraft.hasDraft() ){
			dialog.addAction(
				getString( R.string.restore_draft )
				, new Runnable() {
					@Override public void run(){
						openDraftPicker();
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
		
		dialog.addAction(
			getString( R.string.recommended_plugin )
			, new Runnable() {
				@Override public void run(){
					showRecommendedPlugin( null );
				}
			}
		);
		dialog.show( this, null );
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	// post
	
	private void performPost(){
		post_helper.content = etContent.getText().toString().trim();
		
		if( ! cbEnquete.isChecked() ){
			post_helper.enquete_items = null;
		}else{
			post_helper.enquete_items = new ArrayList<>();
			for( MyEditText et : list_etChoice ){
				post_helper.enquete_items.add( et.getText().toString().trim() );
			}
		}
		
		if( ! cbContentWarning.isChecked() ){
			post_helper.spoiler_text = null;
		}else{
			post_helper.spoiler_text = etContentWarning.getText().toString().trim();
		}
		
		post_helper.visibility = this.visibility;
		post_helper.bNSFW = cbNSFW.isChecked();
		
		post_helper.in_reply_to_id = this.in_reply_to_id;
		
		post_helper.attachment_list = this.attachment_list;
		
		post_helper.post( account, false, false, new PostHelper.Callback() {
			
			@Override public void onPostComplete( SavedAccount target_account, TootStatus status ){
				Intent data = new Intent();
				data.putExtra( EXTRA_POSTED_ACCT, target_account.acct );
				data.putExtra( EXTRA_POSTED_STATUS_ID, status.id );
				
				setResult( RESULT_OK, data );
				isPostComplete = true;
				ActPost.this.finish();
			}
		} );
	}
	
	/////////////////////////////////////////////////
	
	long in_reply_to_id = - 1L;
	String in_reply_to_text;
	String in_reply_to_image;
	String in_reply_to_url;
	
	void showReplyTo(){
		if( in_reply_to_id == - 1L ){
			llReply.setVisibility( View.GONE );
		}else{
			llReply.setVisibility( View.VISIBLE );
			tvReplyTo.setText( new DecodeOptions()
				.setShort( true )
				.setDecodeEmoji( true )
				.decodeHTML( ActPost.this, account, in_reply_to_text ) );
			ivReply.setImageUrl( pref, 16f, in_reply_to_image );
		}
	}
	
	private void removeReply(){
		in_reply_to_id = - 1L;
		in_reply_to_text = null;
		in_reply_to_image = null;
		in_reply_to_url = null;
		showReplyTo();
	}
	
	/////////////////////////////////////////////////
	
	public static final String DRAFT_CONTENT = "content";
	public static final String DRAFT_CONTENT_WARNING = "content_warning";
	static final String DRAFT_CONTENT_WARNING_CHECK = "content_warning_check";
	static final String DRAFT_NSFW_CHECK = "nsfw_check";
	static final String DRAFT_VISIBILITY = "visibility";
	static final String DRAFT_ACCOUNT_DB_ID = "account_db_id";
	static final String DRAFT_ATTACHMENT_LIST = "attachment_list";
	static final String DRAFT_REPLY_ID = "reply_id";
	static final String DRAFT_REPLY_TEXT = "reply_text";
	static final String DRAFT_REPLY_IMAGE = "reply_image";
	static final String DRAFT_REPLY_URL = "reply_url";
	static final String DRAFT_IS_ENQUETE = "is_enquete";
	static final String DRAFT_ENQUETE_ITEMS = "enquete_items";
	
	private void saveDraft(){
		String content = etContent.getText().toString();
		String content_warning = cbContentWarning.isChecked() ? etContentWarning.getText().toString() : "";
		boolean isEnquete = cbEnquete.isChecked();
		String[] str_choice = new String[]{
			isEnquete ? list_etChoice[ 0 ].getText().toString() : "",
			isEnquete ? list_etChoice[ 1 ].getText().toString() : "",
			isEnquete ? list_etChoice[ 2 ].getText().toString() : "",
			isEnquete ? list_etChoice[ 3 ].getText().toString() : "",
		};
		boolean hasContent = false;
		if( ! TextUtils.isEmpty( content.trim() ) ) hasContent = true;
		if( ! TextUtils.isEmpty( content_warning.trim() ) ) hasContent = true;
		for( String s : str_choice ){
			if( ! TextUtils.isEmpty( s.trim() ) ) hasContent = true;
		}
		if( ! hasContent ){
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
			json.put( DRAFT_REPLY_URL, in_reply_to_url );
			
			json.put( DRAFT_IS_ENQUETE, isEnquete );
			JSONArray array = new JSONArray();
			for( String s : str_choice ){
				array.put( s );
			}
			json.put( DRAFT_ENQUETE_ITEMS, array );
			
			PostDraft.save( System.currentTimeMillis(), json );
			
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	private void openDraftPicker(){
		
		new DlgDraftPicker().open( this, new DlgDraftPicker.Callback() {
			@Override public void onDraftSelected( JSONObject draft ){
				restoreDraft( draft );
			}
		} );
		
	}
	
	static boolean check_exist( String url ){
		try{
			Request request = new Request.Builder().url( url ).build();
			Call call = App1.ok_http_client.newCall( request );
			Response response = call.execute();
			if( response.isSuccessful() ){
				return true;
			}
			log.e( Utils.formatResponse( response, "check_exist failed." ) );
		}catch( Throwable ex ){
			log.trace( ex );
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
				long account_db_id = Utils.optLongX( draft, DRAFT_ACCOUNT_DB_ID, - 1L );
				JSONArray tmp_attachment_list = draft.optJSONArray( DRAFT_ATTACHMENT_LIST );
				
				account = SavedAccount.loadAccount( ActPost.this, log, account_db_id );
				if( account == null ){
					list_warning.add( getString( R.string.account_in_draft_is_lost ) );
					try{
						for( int i = 0, ie = tmp_attachment_list.length() ; i < ie ; ++ i ){
							TootAttachment ta = TootAttachment.parse( tmp_attachment_list.optJSONObject( i ) );
							if( ta != null && ! TextUtils.isEmpty( ta.text_url ) ){
								content = content.replace( ta.text_url, "" );
							}
						}
						tmp_attachment_list = new JSONArray();
						draft.put( DRAFT_ATTACHMENT_LIST, tmp_attachment_list );
						draft.put( DRAFT_CONTENT, content );
						draft.remove( DRAFT_REPLY_ID );
						draft.remove( DRAFT_REPLY_TEXT );
						draft.remove( DRAFT_REPLY_IMAGE );
						draft.remove( DRAFT_REPLY_URL );
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
						TootAttachment ta = TootAttachment.parse( tmp_attachment_list.optJSONObject( i ) );
						if( ta == null ){
							isSomeAttachmentRemoved = true;
							tmp_attachment_list.remove( i );
						}else if( ! check_exist( ta.url ) ){
							isSomeAttachmentRemoved = true;
							tmp_attachment_list.remove( i );
							if( ! TextUtils.isEmpty( ta.text_url ) ){
								content = content.replace( ta.text_url, "" );
							}
						}
					}
					if( isSomeAttachmentRemoved ){
						list_warning.add( getString( R.string.attachment_in_draft_is_lost ) );
						draft.put( DRAFT_ATTACHMENT_LIST, tmp_attachment_list );
						draft.put( DRAFT_CONTENT, content );
					}
				}catch( JSONException ex ){
					log.trace( ex );
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
				long reply_id = Utils.optLongX( draft, DRAFT_REPLY_ID, - 1L );
				String reply_text = draft.optString( DRAFT_REPLY_TEXT, null );
				String reply_image = draft.optString( DRAFT_REPLY_IMAGE, null );
				String reply_url = draft.optString( DRAFT_REPLY_URL, null );
				
				etContent.setText( content );
				etContent.setSelection( content.length() );
				etContentWarning.setText( content_warning );
				etContentWarning.setSelection( content_warning.length() );
				cbContentWarning.setChecked( content_warning_checked );
				cbNSFW.setChecked( nsfw_checked );
				ActPost.this.visibility = visibility;
				
				cbEnquete.setChecked( draft.optBoolean( DRAFT_IS_ENQUETE, false ) );
				JSONArray array = draft.optJSONArray( DRAFT_ENQUETE_ITEMS );
				if( array != null ){
					int src_index = 0;
					for( MyEditText et : list_etChoice ){
						if( src_index < array.length() ){
							et.setText( array.optString( src_index ) );
							++ src_index;
						}else{
							et.setText( "" );
						}
					}
				}
				
				if( account != null ) setAccount( account );
				
				if( tmp_attachment_list.length() > 0 ){
					if( attachment_list != null ){
						attachment_list.clear();
					}else{
						attachment_list = new ArrayList<>();
					}
					for( int i = 0, ie = tmp_attachment_list.length() ; i < ie ; ++ i ){
						TootAttachment ta = TootAttachment.parse( tmp_attachment_list.optJSONObject( i ) );
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
					in_reply_to_url = reply_url;
				}
				
				updateContentWarning();
				showMediaAttachment();
				showVisibility();
				updateTextCount();
				showReplyTo();
				showEnquete();
				
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
	
	//////////////////////////////////////////////////////////////////////////
	// Mushroom plugin
	
	private static final String STATE_MUSHROOM_INPUT = "mushroom_input";
	private static final String STATE_MUSHROOM_START = "mushroom_start";
	private static final String STATE_MUSHROOM_END = "mushroom_end";
	int mushroom_input;
	int mushroom_start;
	int mushroom_end;
	
	@NonNull String prepareMushroomText( @NonNull EditText et ){
		mushroom_start = et.getSelectionStart();
		mushroom_end = et.getSelectionEnd();
		if( mushroom_end > mushroom_start ){
			return et.getText().toString().substring( mushroom_start, mushroom_end );
		}else{
			return "";
		}
	}
	
	void applyMushroomText( @NonNull EditText et, @NonNull String text ){
		String src = et.getText().toString();
		if( mushroom_start > src.length() ) mushroom_start = src.length();
		if( mushroom_end > src.length() ) mushroom_end = src.length();
		
		StringBuilder sb = new StringBuilder();
		sb.append( src.substring( 0, mushroom_start ) );
		// int new_sel_start = sb.length();
		sb.append( text );
		int new_sel_end = sb.length();
		sb.append( src.substring( mushroom_end ) );
		et.setText( sb );
		et.setSelection( new_sel_end, new_sel_end );
	}
	
	void openMushroom(){
		try{
			String text = null;
			if( etContentWarning.hasFocus() ){
				mushroom_input = 1;
				text = prepareMushroomText( etContentWarning );
			}else if( etContent.hasFocus() ){
				mushroom_input = 0;
				text = prepareMushroomText( etContent );
			}else{
				for( int i = 0 ; i < 4 ; ++ i ){
					if( list_etChoice[ i ].hasFocus() ){
						mushroom_input = i + 2;
						text = prepareMushroomText( list_etChoice[ i ] );
					}
				}
			}
			if( text == null ){
				mushroom_input = 0;
				text = prepareMushroomText( etContent );
			}
			
			Intent intent = new Intent( "com.adamrocker.android.simeji.ACTION_INTERCEPT" );
			intent.addCategory( "com.adamrocker.android.simeji.REPLACE" );
			intent.putExtra( "replace_key", text );
			
			// Create intent to show chooser
			Intent chooser = Intent.createChooser( intent, getString( R.string.select_plugin ) );
			
			// Verify the intent will resolve to at least one activity
			if( intent.resolveActivity( getPackageManager() ) == null ){
				showRecommendedPlugin( getString( R.string.plugin_not_installed ) );
				return;
			}
			startActivityForResult( chooser, REQUEST_CODE_MUSHROOM );
			
		}catch( Throwable ex ){
			log.trace( ex );
			showRecommendedPlugin( getString( R.string.plugin_not_installed ) );
		}
	}
	
	private void applyMushroomResult( String text ){
		if( mushroom_input == 0 ){
			applyMushroomText( etContent, text );
		}else if( mushroom_input == 1 ){
			applyMushroomText( etContentWarning, text );
		}else{
			for( int i = 0 ; i < 4 ; ++ i ){
				if( mushroom_input == i + 2 ){
					applyMushroomText( list_etChoice[ i ], text );
				}
			}
		}
	}
	
	private void showRecommendedPlugin( String title ){
		String language_code = getString( R.string.language_code );
		int res_id;
		if( "ja".equals( language_code ) ){
			res_id = R.raw.recommended_plugin_ja;
		}else if( "fr".equals( language_code ) ){
			res_id = R.raw.recommended_plugin_fr;
		}else{
			res_id = R.raw.recommended_plugin_en;
		}
		byte[] data = Utils.loadRawResource( this, res_id );
		if( data != null ){
			String text = Utils.decodeUTF8( data );
			@SuppressLint("InflateParams")
			View viewRoot = getLayoutInflater().inflate( R.layout.dlg_plugin_missing, null, false );
			
			TextView tvText = viewRoot.findViewById( R.id.tvText );
			LinkClickContext lcc = new LinkClickContext() {
				@Override public AcctColor findAcctColor( String url ){
					return null;
				}
			};
			CharSequence sv = new DecodeOptions().decodeHTML( ActPost.this, lcc, text );
			tvText.setText( sv );
			tvText.setMovementMethod( LinkMovementMethod.getInstance() );
			
			TextView tvTitle = viewRoot.findViewById( R.id.tvTitle );
			if( TextUtils.isEmpty( title ) ){
				tvTitle.setVisibility( View.GONE );
			}else{
				tvTitle.setText( title );
				
			}
			
			new AlertDialog.Builder( this )
				.setView( viewRoot )
				.setCancelable( true )
				.setNeutralButton( R.string.close, null )
				.show();
		}
		
	}
	
	final MyClickableSpan.LinkClickCallback link_click_listener = new MyClickableSpan.LinkClickCallback() {
		@Override public void onClickLink( View view, @NonNull MyClickableSpan span ){
			// ブラウザで開く
			try{
				Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( span.url ) );
				startActivity( intent );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
	};
	
	private void showEnquete(){
		llEnquete.setVisibility( cbEnquete.isChecked() ? View.VISIBLE : View.GONE );
	}
	
}
