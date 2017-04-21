package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActPost extends AppCompatActivity implements View.OnClickListener {
	static final LogCategory log = new LogCategory( "ActPost" );
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	
	public static void open( Context context, long account_db_id ){
		Intent intent = new Intent( context, ActPost.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, account_db_id );
		context.startActivity( intent );
	}
	
	@Override
	public void onClick( View v ){
		
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
			
		}else{
			Intent intent = getIntent();
			long account_db_id = intent.getLongExtra( KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_ID );
			if( account_db_id !=  SavedAccount.INVALID_ID ){
				for( int i = 0, ie = account_list.size() ; i < ie ; ++ i ){
					SavedAccount a = account_list.get( i );
					if( a.db_id == account_db_id ){
						setAccount( a );
						break;
					}
				}
			}
		}
		if( this.account == null ){
			setAccount( null );
		}
		
		updateContentWarning();
		updateMediaAttachment();
	}
	
	@Override
	protected void onRestoreInstanceState( Bundle savedInstanceState ){
		super.onRestoreInstanceState( savedInstanceState );
		
		if( savedInstanceState != null ){
			long account_db_id = savedInstanceState.getLong(KEY_ACCOUNT_DB_ID,SavedAccount.INVALID_ID);
			if( account_db_id != SavedAccount.INVALID_ID ){
				for( int i = 0, ie = account_list.size() ; i < ie ; ++ i ){
					SavedAccount a = account_list.get( i );
					if( a.db_id == account_db_id ){
						setAccount( a );
						break;
					}
				}
			}
			
		}
		if( this.account == null ){
			setAccount( null );
		}
		updateContentWarning();
		updateMediaAttachment();
	}
	
	@Override
	protected void onSaveInstanceState( Bundle outState ){
		if( account != null ){
			outState.putLong( KEY_ACCOUNT_DB_ID,account.db_id );
		}
	}
	
	Button btnAccount;
	View btnAttachment;
	View btnPost;
	View llAttachment;
	NetworkImageView ivMedia1;
	NetworkImageView ivMedia2;
	NetworkImageView ivMedia3;
	NetworkImageView ivMedia4;
	CheckBox cbContentWarning;
	EditText etContentWarning;
	EditText etContent;
	TextView tvCharCount;
	ArrayList< SavedAccount > account_list;
	SavedAccount account;
	
	private void initUI(){
		setContentView( R.layout.act_post );
		
		btnAccount = (Button) findViewById( R.id.btnAccount );
		btnAttachment = findViewById( R.id.btnAttachment );
		btnPost = findViewById( R.id.btnPost );
		llAttachment = findViewById( R.id.llAttachment );
		ivMedia1 = (NetworkImageView) findViewById( R.id.ivMedia1 );
		ivMedia2 = (NetworkImageView) findViewById( R.id.ivMedia2 );
		ivMedia3 = (NetworkImageView) findViewById( R.id.ivMedia3 );
		ivMedia4 = (NetworkImageView) findViewById( R.id.ivMedia4 );
		cbContentWarning = (CheckBox) findViewById( R.id.cbContentWarning );
		etContentWarning = (EditText) findViewById( R.id.etContentWarning );
		etContent = (EditText) findViewById( R.id.etContent );
		tvCharCount = (TextView) findViewById( R.id.tvCharCount );
		
		account_list = SavedAccount.loadAccountList( log );
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override
			public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( a.getFullAcct( a ), b.getFullAcct( b ) );
			}
		} );
		
		btnAccount.setOnClickListener( this );
		btnAttachment.setOnClickListener( this );
		btnPost.setOnClickListener( this );
		llAttachment = findViewById( R.id.llAttachment );
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
		tvCharCount.setText( 500 - etContent.getText().length() );
	}
	
	void setAccount( SavedAccount a ){
		this.account = a;
		btnAccount.setText( a == null ? getString( R.string.not_selected ) : a.getFullAcct( a ) );
	}
	
	private void updateContentWarning(){
		etContentWarning.setVisibility( cbContentWarning.isChecked() ? View.VISIBLE : View.GONE );
	}
	
	private void updateMediaAttachment(){
		if( attachment_list.isEmpty() ){
			llAttachment.setVisibility( View.GONE );
		}else{
			llAttachment.setVisibility( View.VISIBLE );
			showAttachment( ivMedia1, 0 );
			showAttachment( ivMedia2, 0 );
			showAttachment( ivMedia3, 0 );
			showAttachment( ivMedia4, 0 );
		}
	}
	
	private void showAttachment( NetworkImageView iv, int idx ){
		if( idx >= attachment_list.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			PostAttachment a = attachment_list.get( idx );
			if( a.status == ATTACHMENT_UPLOADING ){
				iv.setImageResource( R.drawable.ic_loading );
			}else{
				iv.setImageBitmap( a.bitmap );
			}
		}
	}
	
	static final int ATTACHMENT_UPLOADING = 1;
	static final int ATTACHMENT_UPLOADED = 2;
	
	static class PostAttachment {
		int status;
		Bitmap bitmap;
		String url;
	}
	
	final ArrayList< PostAttachment > attachment_list = new ArrayList<>();
	
}
