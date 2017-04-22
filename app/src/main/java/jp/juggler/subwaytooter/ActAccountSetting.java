package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;

public class ActAccountSetting extends AppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	static final LogCategory log = new LogCategory( "ActAccountSetting" );
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	
	public static void open( Context context, SavedAccount ai ){
		Intent intent = new Intent( context, ActAccountSetting.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, ai.db_id );
		context.startActivity( intent );
	}
	
	SavedAccount account;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		initUI();
		account = SavedAccount.loadAccount( log, getIntent().getLongExtra( KEY_ACCOUNT_DB_ID, - 1L ) );
		if( account == null ) finish();
		loadUIFromData( account );
		
	}
	
	TextView tvInstance;
	TextView tvUser;
	View btnAccessToken;
	View btnAccountRemove;
	Button btnVisibility;
	Switch swConfirmBeforeBoost;
	Switch swNSFWOpen;
	
	private void initUI(){
		setContentView( R.layout.act_account_setting );
		tvInstance = (TextView) findViewById( R.id.tvInstance );
		tvUser = (TextView) findViewById( R.id.tvUser );
		btnAccessToken = findViewById( R.id.btnAccessToken );
		btnAccountRemove = findViewById( R.id.btnAccountRemove );
		btnVisibility = (Button) findViewById( R.id.btnVisibility );
		swConfirmBeforeBoost = (Switch) findViewById( R.id.swConfirmBeforeBoost );
		swNSFWOpen = (Switch) findViewById( R.id.swNSFWOpen );
		
		btnAccessToken.setOnClickListener( this );
		btnAccountRemove.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		
		swNSFWOpen.setOnCheckedChangeListener( this );
		swConfirmBeforeBoost.setOnCheckedChangeListener( this );
	}
	
	private void loadUIFromData( SavedAccount a ){
		tvInstance.setText( a.host );
		tvUser.setText( a.user );
		
		String sv = a.visibility;
		if( sv != null ){
			visibility = sv;
		}
		swConfirmBeforeBoost.setChecked( a.confirm_boost );
		swNSFWOpen.setChecked( a.dont_hide_nsfw );
		
		
		updateVisibility();
	}
	
	private void saveUIToData(){
		account.visibility = visibility;
		account.confirm_boost = swConfirmBeforeBoost.isChecked();
		account.dont_hide_nsfw = swNSFWOpen.isChecked();
		account.saveSetting();
	}
	
	@Override
	public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAccessToken:
			performAccessToken();
			break;
		case R.id.btnAccountRemove:
			performAccountRemove();
			break;
		case R.id.btnVisibility:
			performVisibility();
			break;
		}
	}
	
	///////////////////////////////////////////////////
	
	String visibility = TootStatus.VISIBILITY_PUBLIC;
	
	private void updateVisibility(){
		btnVisibility.setText( Styler.getVisibilityString( this, visibility ) );
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
					saveUIToData();
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
		
	}
	
	///////////////////////////////////////////////////
	private void performAccountRemove(){
		new AlertDialog.Builder( this )
			.setTitle( R.string.confirm )
			.setMessage( R.string.confirm_account_remove )
			.setNegativeButton( R.string.cancel, null )
			.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					account.delete();
					finish();
				}
			} )
			.show();
		
	}
	
	///////////////////////////////////////////////////
	private void performAccessToken(){
		
	}
	
	///////////////////////////////////////////////////
	
}
