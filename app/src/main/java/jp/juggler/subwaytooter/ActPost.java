package jp.juggler.subwaytooter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootMention;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ActPost extends AppCompatActivity implements View.OnClickListener {
	static final LogCategory log = new LogCategory( "ActPost" );
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	static final String KEY_REPLY_STATUS = "reply_status";
	static final String KEY_INITIAL_TEXT = "initial_text";
	
	static final String KEY_ATTACHMENT_LIST = "attachment_list";
	static final String KEY_VISIBILITY = "visibility";
	static final String KEY_IN_REPLY_TO_ID = "in_reply_to_id";
	static final String KEY_IN_REPLY_TO_TEXT = "in_reply_to_text";
	static final String KEY_IN_REPLY_TO_IMAGE = "in_reply_to_image";
	
	
	public static void open( Context context, long account_db_id, TootStatus reply_status ){
		Intent intent = new Intent( context, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		if( reply_status != null ){
			intent.putExtra( KEY_REPLY_STATUS, reply_status.json.toString() );
		}
		context.startActivity( intent );
	}
	public static void open( Context context, long account_db_id, String  initial_text ){
		Intent intent = new Intent( context, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		if( initial_text != null ){
			intent.putExtra( KEY_INITIAL_TEXT, initial_text );
		}
		context.startActivity( intent );
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAccount:
			performAccountChooser();
			break;
		
		case R.id.btnVisibility:
			performVisibility();
			break;
		
		case R.id.btnAttachment:
			performAttachment();
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
			performPost();
			break;
		
		case R.id.btnRemoveReply:
			removeReply();
			break;
		}
	}
	
	static final int REQUEST_CODE_ATTACHMENT = 1;
	
	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( resultCode == RESULT_OK ){
			if( requestCode == REQUEST_CODE_ATTACHMENT ){
				if( data != null ){
					Uri uri = data.getData();
					if( uri != null ){
						String type = data.getType();
						if( TextUtils.isEmpty( type ) ){
							type = getContentResolver().getType( uri );
						}
						addAttachment( uri, type );
					}
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		
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
			if( ! TextUtils.isEmpty( sv ) ){
				try{
					attachment_list.clear();
					JSONArray array = new JSONArray( sv );
					for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
						try{
							TootAttachment a = TootAttachment.parse( log, array.optJSONObject( i ) );
							if( a != null ){
								PostAttachment pa = new PostAttachment();
								pa.status = ATTACHMENT_UPLOADED;
								pa.attachment = a;
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
			
			String sv = intent.getStringExtra( KEY_INITIAL_TEXT );
			if( sv != null){
				etContent.setText(sv);
				etContent.setSelection( sv.length() );
			}
			
			sv = intent.getStringExtra( KEY_REPLY_STATUS );
			if( sv != null ){
				try{
					TootStatus repley_status = TootStatus.parse( log, account,new JSONObject( sv ) );
					
					// CW をリプライ元に合わせる
					if( ! TextUtils.isEmpty( repley_status.spoiler_text ) ){
						cbContentWarning.setChecked( true );
						etContentWarning.setText( repley_status.spoiler_text );
					}
					
					// mention を自動設定する
					String acct_me = account.getFullAcct( account );
					ArrayList< String > mention_list = new ArrayList<>();
					mention_list.add( account.getFullAcct( repley_status.account ) );
					if( repley_status.mentions != null ){
						for( TootMention mention : repley_status.mentions ){
							sv = account.getFullAcct( mention.acct );
							if( !sv.equals( acct_me ) && ! mention_list.contains( sv ) ){
								mention_list.add( sv );
							}
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
					in_reply_to_id = repley_status.id;
					in_reply_to_text = repley_status.content;
					in_reply_to_image = repley_status.account.avatar_static;
					
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
						int i = TootStatus.compareVisibility( this.visibility, repley_status.visibility );
						if( i > 0 ){ // より大きい=>より公開範囲が広い
							this.visibility = repley_status.visibility;
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
			setAccount( null );
		}
		
		updateContentWarning();
		showMediaAttachment();
		updateVisibility();
		updateTextCount();
		showReplyTo();
	}
	
	@Override
	protected void onSaveInstanceState( Bundle outState ){
		if( account != null ){
			outState.putLong( KEY_ACCOUNT_DB_ID, account.db_id );
		}
		if( visibility != null ){
			outState.putString( KEY_VISIBILITY, visibility );
		}
		if( ! attachment_list.isEmpty() ){
			JSONArray array = new JSONArray();
			for( PostAttachment pa : attachment_list ){
				if( pa.status == ATTACHMENT_UPLOADED ){
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
	
	Button btnAccount;
	ImageButton btnVisibility;
	View btnAttachment;
	View btnPost;
	View llAttachment;
	NetworkImageView ivMedia1;
	NetworkImageView ivMedia2;
	NetworkImageView ivMedia3;
	NetworkImageView ivMedia4;
	CheckBox cbNSFW;
	CheckBox cbContentWarning;
	EditText etContentWarning;
	EditText etContent;
	TextView tvCharCount;
	
	ArrayList< SavedAccount > account_list;
	
	View llReply;
	TextView tvReplyTo;
	View btnRemoveReply;
	NetworkImageView ivReply;
	
	private void initUI(){
		setContentView( R.layout.act_post );
		
		btnAccount = (Button) findViewById( R.id.btnAccount );
		btnVisibility = (ImageButton) findViewById( R.id.btnVisibility );
		btnAttachment = findViewById( R.id.btnAttachment );
		btnPost = findViewById( R.id.btnPost );
		llAttachment = findViewById( R.id.llAttachment );
		ivMedia1 = (NetworkImageView) findViewById( R.id.ivMedia1 );
		ivMedia2 = (NetworkImageView) findViewById( R.id.ivMedia2 );
		ivMedia3 = (NetworkImageView) findViewById( R.id.ivMedia3 );
		ivMedia4 = (NetworkImageView) findViewById( R.id.ivMedia4 );
		cbNSFW = (CheckBox) findViewById( R.id.cbNSFW );
		cbContentWarning = (CheckBox) findViewById( R.id.cbContentWarning );
		etContentWarning = (EditText) findViewById( R.id.etContentWarning );
		etContent = (EditText) findViewById( R.id.etContent );
		tvCharCount = (TextView) findViewById( R.id.tvCharCount );
		
		llReply = findViewById( R.id.llReply );
		tvReplyTo = (TextView) findViewById( R.id.tvReplyTo );
		btnRemoveReply = findViewById( R.id.btnRemoveReply );
		ivReply= (NetworkImageView) findViewById( R.id.ivReply );
		
		account_list = SavedAccount.loadAccountList( log );
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override
			public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( a.getFullAcct( a ), b.getFullAcct( b ) );
			}
		} );
		
		btnAccount.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		btnAttachment.setOnClickListener( this );
		btnPost.setOnClickListener( this );
		btnRemoveReply.setOnClickListener( this );
		ivMedia1.setOnClickListener( this );
		ivMedia2.setOnClickListener( this );
		ivMedia3.setOnClickListener( this );
		ivMedia4.setOnClickListener( this );
		
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
				
			}
			
			@Override
			public void afterTextChanged( Editable s ){
				updateTextCount();
			}
		} );
	}
	
	private void updateTextCount(){
		tvCharCount.setText( Integer.toString( 500 - etContent.getText().length() ) );
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
		
		if( ! attachment_list.isEmpty() ){
			// 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
			Utils.showToast( this,false,R.string.cant_change_account_when_attachiment_specified );
		}
		
		final ArrayList< SavedAccount > tmp_account_list = new ArrayList<>();
		if( in_reply_to_id != -1L ){
			// リプライは数値IDなのでサーバが同じじゃないと選択できない
			for( SavedAccount a : account_list ){
				if( !a.host.equals( account.host ) ) continue;
				tmp_account_list.add(a);
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
						setAccount( tmp_account_list.get( which ) );
					}
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
	}
	
	//////////////////////////////////////////////////////////
	// Attachment
	
	static final int ATTACHMENT_UPLOADING = 1;
	static final int ATTACHMENT_UPLOADED = 2;
	
	static class PostAttachment {
		int status;
		TootAttachment attachment;
	}
	
	final ArrayList< PostAttachment > attachment_list = new ArrayList<>();
	
	private void showMediaAttachment(){
		if( attachment_list.isEmpty() ){
			llAttachment.setVisibility( View.GONE );
			cbNSFW.setVisibility( View.GONE );
		}else{
			llAttachment.setVisibility( View.VISIBLE );
			cbNSFW.setVisibility( View.VISIBLE );
			showAttachment_sub( ivMedia1, 0 );
			showAttachment_sub( ivMedia2, 1 );
			showAttachment_sub( ivMedia3, 2 );
			showAttachment_sub( ivMedia4, 3 );
		}
	}
	
	private void showAttachment_sub( NetworkImageView iv, int idx ){
		if( idx >= attachment_list.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			PostAttachment a = attachment_list.get( idx );
			if( a.status == ATTACHMENT_UPLOADING ){
				iv.setImageDrawable( ContextCompat.getDrawable( this, R.drawable.ic_loading ) );
			}else if( a.attachment != null ){
				iv.setImageUrl( a.attachment.preview_url, App1.getImageLoader() );
			}else{
				iv.setImageDrawable( ContextCompat.getDrawable( this, R.drawable.ic_unknown ) );
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
	
	private void performAttachment(){
		if( attachment_list.size() >= 4 ){
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
			intent.putExtra( Intent.EXTRA_MIME_TYPES, new String[]{ "image/*", "video/*" } );
			startActivityForResult( intent, REQUEST_CODE_ATTACHMENT );
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "ACTION_OPEN_DOCUMENT failed." );
		}
	}
	
	static final byte[] hex = Utils.encodeUTF8( "0123456789abcdef" );
	
	void addAttachment( final Uri uri, final String mime_type ){
		if( attachment_list.size() >= 4 ){
			Utils.showToast( this, false, R.string.attachment_too_many );
			return;
		}
		if( account == null ){
			Utils.showToast( this, false, R.string.account_select_please );
			return;
		}
		
		final PostAttachment pa = new PostAttachment();
		pa.status = ATTACHMENT_UPLOADING;
		attachment_list.add( pa );
		showMediaAttachment();
		
		new AsyncTask< Void, Void, TootApiResult >() {
			final SavedAccount target_account = account;
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
					}
				} );
				client.setAccount( target_account );
				
				if( TextUtils.isEmpty( mime_type ) ){
					return new TootApiResult( "mime_type is null." );
				}
				
				try{
					final long content_length = getStreamSize( true, getContentResolver().openInputStream( uri ) );
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
									return MediaType.parse( mime_type );
								}
								
								@Override
								public long contentLength() throws IOException{
									return content_length;
								}
								
								@Override
								public void writeTo( BufferedSink sink ) throws IOException{
									InputStream is = getContentResolver().openInputStream( uri );
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
				pa.status = ATTACHMENT_UPLOADED;
				
				if( pa.attachment == null ){
					if( result != null ){
						Utils.showToast( ActPost.this, true, result.error );
					}
					attachment_list.remove( pa );
				}else{
					String sv = etContent.getText().toString();
					sv = sv + pa.attachment.text_url + " ";
					etContent.setText( sv );
				}
				
				showMediaAttachment();
			}
			
		}.execute();
	}
	
	public String getDocumentName( Uri uri ){
		
		Cursor cursor = getContentResolver().query( uri, null, null, null, null, null );
		try{
			if( cursor != null && cursor.moveToFirst() ){
				return cursor.getString( cursor.getColumnIndex( OpenableColumns.DISPLAY_NAME ) );
			}
		}finally{
			cursor.close();
		}
		return null;
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
	
	private void updateVisibility(){
		btnVisibility.setImageResource( Styler.getVisibilityIcon( visibility ) );
	}
	
	private void performVisibility(){
		final String[] caption_list = new String[]{
			getString( R.string.visibility_public ),
			getString( R.string.visibility_unlisted ),
			getString( R.string.visibility_private ),
			getString( R.string.visibility_direct ),
		};

//		public static final String VISIBILITY_PUBLIC ="public";
//		public static final String VISIBILITY_UNLISTED ="unlisted";
//		public static final String VISIBILITY_PRIVATE ="private";
//		public static final String VISIBILITY_DIRECT ="direct";
		
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
					updateVisibility();
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	// post
	
	private void performPost(){
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
		
		for( PostAttachment pa : attachment_list ){
			if( pa.attachment != null ){
				sb.append( "&media_ids[]=" + pa.attachment.id );
			}
		}
		
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			final SavedAccount target_account = account;
			
			TootStatus status;
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActPost.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override
							public void run(){
								progress.setMessage( s );
							}
						} );
					}
				} );
				
				client.setAccount( target_account );
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, sb.toString()
					) );
				
				TootApiResult result = client.request( "/api/v1/statuses", request_builder );
				if( result.object != null ){
					status = TootStatus.parse( log,  account,result.object );
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
				
				if( status != null ){
					ActMain.update_at_resume = true;
					ActPost.this.finish();
				}else{
					if( result != null ){
						Utils.showToast( ActPost.this, true, result.error );
					}
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
		AsyncTaskCompat.executeParallel( task );
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
			tvReplyTo.setText( HTMLDecoder.decodeHTML(  account,in_reply_to_text ) );
			ivReply.setImageUrl( in_reply_to_image,App1.getImageLoader() );
		}
	}
	
	private void removeReply(){
		in_reply_to_id = - 1L;
		in_reply_to_text = null;
		in_reply_to_image = null;
		showReplyTo();
	}
}
