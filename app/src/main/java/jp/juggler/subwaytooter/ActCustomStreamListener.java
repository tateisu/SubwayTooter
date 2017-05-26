package jp.juggler.subwaytooter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.hjson.JsonObject;
import org.hjson.JsonValue;

import java.util.regex.Pattern;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static jp.juggler.subwaytooter.R.id.large;
import static jp.juggler.subwaytooter.R.id.tvAcct;

public class ActCustomStreamListener extends AppCompatActivity implements View.OnClickListener, TextWatcher {
	
	static final LogCategory log = new LogCategory("ActCustomStreamListener");
	
	static final String EXTRA_ACCT = "acct";
	
	public static void open( Activity activity ){
		Intent intent = new Intent( activity, ActCustomStreamListener.class );
		activity.startActivity( intent );
	}
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		
		initUI();
		
		if( savedInstanceState != null ){
			stream_config_json = savedInstanceState.getString(STATE_STREAM_CONFIG_JSON);
		}else{
			load();
		}
		
		showButtonState();
	}
	
	static final String STATE_STREAM_CONFIG_JSON = "stream_config_json" ;
	@Override protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		outState.putString( STATE_STREAM_CONFIG_JSON,stream_config_json );
		
	}
	
	EditText etStreamListenerConfigurationUrl;
	EditText etStreamListenerSecret;
	TextView tvLog;
	View btnDiscard;
	View btnTest;
	View btnSave;
	
	String stream_config_json;
	
	private void initUI(){
		setContentView( R.layout.act_custom_stream_listener );
		
		Styler.fixHorizontalPadding(findViewById( R.id.llContent ));
		
		etStreamListenerConfigurationUrl= (EditText) findViewById( R.id.etStreamListenerConfigurationUrl );
		etStreamListenerSecret= (EditText) findViewById( R.id.etStreamListenerSecret );
		etStreamListenerConfigurationUrl.addTextChangedListener( this );
		etStreamListenerSecret.addTextChangedListener( this );

		tvLog= (TextView) findViewById( R.id.tvLog );
		
		btnDiscard=  findViewById( R.id.btnDiscard );
		btnTest=  findViewById( R.id.btnTest );
		btnSave=  findViewById( R.id.btnSave );
		
		btnDiscard.setOnClickListener( this );
		btnTest.setOnClickListener( this );
		btnSave.setOnClickListener( this );
	}
	
	boolean bLoading = false;
	
	private void load(){
		bLoading = true;
		
		SharedPreferences pref = Pref.pref( this );
		
		etStreamListenerConfigurationUrl.setText( pref.getString( Pref.KEY_STREAM_LISTENER_CONFIG_URL, "" ) );
		etStreamListenerSecret.setText( pref.getString( Pref.KEY_STREAM_LISTENER_SECRET, "" ) );
		stream_config_json = null;
		tvLog.setText( getString( R.string.input_url_and_secret_then_test ) );
		
		bLoading = false;
	}
	
	@Override public void beforeTextChanged( CharSequence s, int start, int count, int after ){
		
	}
	
	@Override public void onTextChanged( CharSequence s, int start, int before, int count ){
		
	}
	
	@Override public void afterTextChanged( Editable s ){
		tvLog.setText( getString( R.string.input_url_and_secret_then_test ) );
		stream_config_json = null;
		showButtonState();
	}
	
	private void showButtonState(){
		btnSave.setEnabled( stream_config_json != null );
		btnTest.setEnabled( ! isTestRunning() );
	}
	
	private boolean isTestRunning(){
		return last_task != null && ! last_task.isCancelled();
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnDiscard:
			Utils.hideKeyboard( this, etStreamListenerConfigurationUrl );
			finish();
			break;
		case R.id.btnTest:
			Utils.hideKeyboard( this, etStreamListenerConfigurationUrl );
			startTest();
			break;
		case R.id.btnSave:
			Utils.hideKeyboard( this, etStreamListenerConfigurationUrl );
			if( save() ){
				SavedAccount.clearRegistrationCache();
				AlarmService.startCheck( this,false );
				finish();
			}
			break;
		}
	}

	
	private boolean save(){
		if( stream_config_json == null ){
			Utils.showToast( this,false,"please test before save." );
			return false;
		}
		
		Pref.pref( this ).edit()
			.putString(Pref.KEY_STREAM_LISTENER_CONFIG_URL,etStreamListenerConfigurationUrl.getText().toString().trim() )
			.putString(Pref.KEY_STREAM_LISTENER_SECRET,etStreamListenerSecret.getText().toString().trim() )
			.putString(Pref.KEY_STREAM_LISTENER_CONFIG_DATA,stream_config_json)
			.apply();
		return true;
	}

	AsyncTask<Void,Void,String> last_task;
	static final Pattern reInstanceURL = Pattern.compile( "\\Ahttps://[a-z0-9.-_:]+\\z" );
	static final Pattern reUpperCase = Pattern.compile( "[A-Z]" );
	static final Pattern reUrl = Pattern.compile( "\\Ahttps?://[\\w\\-?&#%~!$'()*+,/:;=@._\\[\\]]+\\z" );
	
	
	void addLog(final String line){
		Utils.runOnMainThread( new Runnable() {
			@Override public void run(){
				String sv = tvLog.getText().toString();
				if( sv.isEmpty() ){
					sv = line;
				}else{
					sv = sv +"\n" + line;
				}
				tvLog.setText( sv);
			}
		} );
	}
	
	void startTest(){
		final String strSecret = etStreamListenerSecret.getText().toString().trim();
		final String strUrl = etStreamListenerConfigurationUrl.getText().toString().trim();
		stream_config_json = null;
		showButtonState();
		
		last_task = new AsyncTask< Void, Void, String >() {
			@Override protected String doInBackground( Void... params ){
				try{
					for(;;){
						if( TextUtils.isEmpty( strSecret ) ){
							addLog( "Secret is empty. Custom Listener is not used." );
							break;
						}else if( TextUtils.isEmpty( strUrl ) ){
							addLog( "Configuration URL is empty. Custom Listener is not used." );
							break;
						}
						
						addLog( "try to loading Configuration data from URL…" );
						Request.Builder builder = new Request.Builder()
							.url( strUrl );
						
						Call call = App1.ok_http_client.newCall( builder.build() );
						
						Response response = call.execute();
						if( ! response.isSuccessful() ){
							addLog( "Can't get configuration from URL. " + response );
							break;
						}
						
						String json;
						try{
							//noinspection ConstantConditions
							json = response.body().string();
						}catch( Throwable ex ){
							ex.printStackTrace();
							addLog( "Can't get content body" );
							break;
						}
						
						if( json == null ){
							addLog( "content body is null" );
							break;
						}
						
						JsonValue jv;
						try{
							jv = JsonValue.readHjson( json );
						}catch( Throwable ex ){
							ex.printStackTrace();
							addLog( Utils.formatError( ex, "Can't parse configuration data." ) );
							break;
						}
						
						if( ! jv.isObject() ){
							addLog( "configuration data is not JSON Object." );
							break;
						}
						JsonObject root = jv.asObject();
						
						boolean has_wildcard = false;
						boolean has_error = false;
						for( JsonObject.Member member : root ){
							String strInstance = member.getName();
							if( "*".equals( strInstance ) ){
								has_wildcard = true;
							}else if( reUpperCase.matcher( strInstance ).find() ){
								addLog( strInstance + " : instance URL must be lower case." );
								has_error = true;
								continue;
							}else if( strInstance.charAt( strInstance.length() - 1 ) == '/' ){
								addLog( strInstance + " : instance URL must not be trailed with '/'." );
								has_error = true;
								continue;
							}else if( ! reInstanceURL.matcher( strInstance ).find() ){
								addLog( strInstance + " : instance URL is not like https://....." );
								has_error = true;
								continue;
							}
							JsonValue entry_value = member.getValue();
							if( ! entry_value.isObject() ){
								addLog( strInstance + " : value for this instance is not JSON Object." );
								has_error = true;
								continue;
							}
							JsonObject entry = entry_value.asObject();
							
							String[] keys = new String[]{ "urlStreamingListenerRegister", "urlStreamingListenerUnregister", "appId" };
							for( String key : keys ){
								JsonValue v = entry.get( key );
								if( ! v.isString() ){
									addLog( strInstance + "." + key + " : missing parameter, or data type is not string." );
									has_error = true;
									continue;
								}
								String sv = v.asString();
								if( TextUtils.isEmpty( sv ) ){
									addLog( strInstance + "." + key + " : empty parameter." );
									has_error = true;
								}else if( sv.contains( " " ) ){
									addLog( strInstance + "." + key + " : contains whitespace." );
									has_error = true;
								}
								
								if( ! "appId".equals( key ) ){
									if(! reUrl.matcher( sv ).find() ){
										addLog( strInstance + "." + key + " : not like Url." );
										has_error = true;
									}
								}
							}
						}
						if( ! has_wildcard ){
							addLog( "Warning: This configuration has no wildcard entry." );
							if(! has_error){
								for( SavedAccount sa : SavedAccount.loadAccountList( log )){
									String instanceUrl = ("https://" + sa.host ).toLowerCase();
									JsonValue v = root.get( instanceUrl );
									if( ! v.isObject() ){
										addLog( instanceUrl + " : is not found in configuration data." );
										has_error = true;
									}
								}
							}
						}
						
						if( has_error ){
							addLog( "This configuration has error. " );
							break;
						}
						
						return json;
					}
						
				}catch(Throwable ex){
					ex.printStackTrace();
					addLog( Utils.formatError( ex,"Can't read configuration from URL." ));
				}
				return null;
			}
			
			@Override protected void onCancelled( String s ){
				super.onPostExecute( s );
			}
			
			@Override protected void onPostExecute( String s ){
				last_task = null;
				if( s!= null ){
					stream_config_json = s;
					addLog( "seems configuration is ok." );
				}else{
					addLog( "error detected." );
				}
				showButtonState();
			}
			
		};
		last_task.executeOnExecutor( App1.task_executor );
	}
}
