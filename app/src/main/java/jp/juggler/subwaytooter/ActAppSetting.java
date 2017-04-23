package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import jp.juggler.subwaytooter.util.LogCategory;

public class ActAppSetting extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {
	
	static final LogCategory log = new LogCategory( "ActAppSetting" );

	public static void open( Context context ){
		context.startActivity( new Intent(context,ActAppSetting.class) );
		
	}
	
	SharedPreferences pref;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		initUI();
		pref = Pref.pref(this);
		
		loadUIFromData();
		
	}
	
	TextView tvInstance;
	TextView tvUser;
	View btnAccessToken;
	View btnAccountRemove;
	Button btnVisibility;
	Switch swBackToColumnList;
	Switch swDontConfirmBeforeCloseColumn;
	
	private void initUI(){
		setContentView( R.layout.act_app_setting );
		swBackToColumnList = (Switch) findViewById( R.id.swBackToColumnList );
		swDontConfirmBeforeCloseColumn = (Switch) findViewById( R.id.swDontConfirmBeforeCloseColumn );
		
		swBackToColumnList.setOnCheckedChangeListener( this );
		swDontConfirmBeforeCloseColumn.setOnCheckedChangeListener( this );
	}
	
	private void loadUIFromData(  ){
		
		swBackToColumnList.setChecked( pref.getBoolean( Pref.KEY_BACK_TO_COLUMN_LIST,false ) );
		swDontConfirmBeforeCloseColumn.setChecked( pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN,false ) );
		
	}
	
	private void saveUIToData(){
		pref
			.edit()
			.putBoolean( Pref.KEY_BACK_TO_COLUMN_LIST,swBackToColumnList.isChecked() )
			.putBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN,swDontConfirmBeforeCloseColumn.isChecked() )
			.apply();
	}
	
	@Override
	public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
}
