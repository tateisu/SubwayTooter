package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

public class ActAppSetting extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
	
	public static void open( Context context ){
		context.startActivity( new Intent( context, ActAppSetting.class ) );
	}
	
	SharedPreferences pref;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		initUI();
		pref = Pref.pref( this );
		
		loadUIFromData();
		
	}
	
	Switch swDontConfirmBeforeCloseColumn;
	Switch swPriorLocalURL;
	Spinner spBackButtonAction;
	
	static final int BACK_ASK_ALWAYS =0;
	static final int BACK_CLOSE_COLUMN =1;
	static final int BACK_OPEN_COLUMN_LIST =2;
	static final int BACK_EXIT_APP =3;
	
	
	
	private void initUI(){
		setContentView( R.layout.act_app_setting );
		swDontConfirmBeforeCloseColumn = (Switch) findViewById( R.id.swDontConfirmBeforeCloseColumn );
		swDontConfirmBeforeCloseColumn.setOnCheckedChangeListener( this );
		
		swPriorLocalURL = (Switch) findViewById( R.id.swPriorLocalURL );
		swPriorLocalURL.setOnCheckedChangeListener( this );
		
		spBackButtonAction =  (Spinner) findViewById( R.id.spBackButtonAction );
		spBackButtonAction.setOnItemSelectedListener( this );
		
		String[] caption_list = new String[4];
		caption_list[0] = getString(R.string.ask_always);
		caption_list[1] = getString(R.string.close_column);
		caption_list[2] = getString(R.string.open_column_list);
		caption_list[3] = getString(R.string.app_exit);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, caption_list);
		adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
		spBackButtonAction.setAdapter( adapter );
	}
	
	boolean load_busy;
	private void loadUIFromData(){
		load_busy =true;

		swDontConfirmBeforeCloseColumn.setChecked( pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false ) );
		swPriorLocalURL.setChecked( pref.getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) );
		
		spBackButtonAction.setSelection( pref.getInt(Pref.KEY_BACK_BUTTON_ACTION,0) );

		load_busy = false;
	}
	
	private void saveUIToData(){
		if(load_busy) return;
		pref.edit()
			.putBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, swDontConfirmBeforeCloseColumn.isChecked() )
			.putBoolean( Pref.KEY_PRIOR_LOCAL_URL, swPriorLocalURL.isChecked() )
			.putInt( Pref.KEY_BACK_BUTTON_ACTION, spBackButtonAction.getSelectedItemPosition() )
			.apply();
	}
	
	@Override public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
	@Override public void onItemSelected( AdapterView< ? > parent, View view, int position, long id ){
		saveUIToData();
	}
	
	@Override public void onNothingSelected( AdapterView< ? > parent ){
	}
}
