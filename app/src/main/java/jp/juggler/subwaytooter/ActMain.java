package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActMain extends AppCompatActivity
	implements NavigationView.OnNavigationItemSelectedListener {
	public static final LogCategory log = new LogCategory( "ActMain" );
	
	@Override
	protected void onCreate( Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		initUI();
		loadColumnList();
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		HTMLDecoder.link_callback = link_click_listener;
	}
	
	@Override
	protected void onPause(){
		HTMLDecoder.link_callback = null;
		saveColumnList();
		super.onPause();
	}
	
	@Override
	public void onBackPressed(){
		DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
		if( drawer.isDrawerOpen( GravityCompat.START ) ){
			drawer.closeDrawer( GravityCompat.START );
		}else{
			super.onBackPressed();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu( Menu menu ){
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate( R.menu.act_main, menu );
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected( MenuItem item ){
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		//noinspection SimplifiableIfStatement
		if( id == R.id.action_settings ){
			return true;
		}
		
		return super.onOptionsItemSelected( item );
	}
	
	@SuppressWarnings("StatementWithEmptyBody")
	@Override
	public boolean onNavigationItemSelected( MenuItem item ){
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		
		if( id == R.id.nav_account_add ){
			performAccountAdd();
		}else if( id == R.id.nav_add_tl_home ){
			performAddTimeline( Column.TYPE_TL_HOME );
		}else if( id == R.id.nav_add_tl_local ){
			performAddTimeline( Column.TYPE_TL_LOCAL );
		}else if( id == R.id.nav_add_tl_federate ){
			performAddTimeline( Column.TYPE_TL_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			performAddTimeline( Column.TYPE_TL_FAVOURITES );
//		}else if( id == R.id.nav_add_reports ){
//			performAddTimeline(Column.TYPE_TL_REPORTS );
		}else if( id == R.id.nav_add_statuses ){
			performAddTimeline( Column.TYPE_TL_STATUSES );
		}else if( id == R.id.nav_add_notifications ){
			performAddTimeline( Column.TYPE_TL_NOTIFICATIONS );
			
			// Handle the camera action
//		}else if( id == R.id.nav_gallery ){
//
//		}else if( id == R.id.nav_slideshow ){
//
//		}else if( id == R.id.nav_manage ){
//
//		}else if( id == R.id.nav_share ){
//
//		}else if( id == R.id.nav_send ){
			
		}
		
		DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
		drawer.closeDrawer( GravityCompat.START );
		return true;
	}
	
	ViewPager pager;
	ColumnPagerAdapter pager_adapter;
	View llEmpty;
	
	void initUI(){
		setContentView( R.layout.act_main );
		llEmpty = findViewById( R.id.llEmpty );

//		// toolbar
//		Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
//		setSupportActionBar( toolbar );
		
		// navigation drawer
		final DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
//		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//			this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close );
//		drawer.addDrawerListener( toggle );
//		toggle.syncState();
		
		NavigationView navigationView = (NavigationView) findViewById( R.id.nav_view );
		navigationView.setNavigationItemSelectedListener( this );
		
		// floating action button
		FloatingActionButton fabToot = (FloatingActionButton) findViewById( R.id.fabToot );
		fabToot.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ){
				performTootButton();
			}
		} );
		// floating action button
		FloatingActionButton fabMenu = (FloatingActionButton) findViewById( R.id.fabMenu );
		fabMenu.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ){
				if( ! drawer.isDrawerOpen( Gravity.LEFT ) ){
					drawer.openDrawer( Gravity.LEFT );
				}
			}
		} );
		
		// ViewPager
		pager = (ViewPager) findViewById( R.id.viewPager );
		pager_adapter = new ColumnPagerAdapter( this );
		pager.setAdapter( pager_adapter );
	}
	
	public void performAccountAdd(){
		LoginForm.showLoginForm( this, new LoginForm.LoginFormCallback() {
			
			@Override
			public void startLogin( final Dialog dialog, final String instance, final String user_mail, final String password ){
				
				final ProgressDialog progress = new ProgressDialog( ActMain.this );
				
				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
					
					boolean __isCancelled(){
						return isCancelled();
					}
					
					long row_id;
					
					@Override
					protected TootApiResult doInBackground( Void... params ){
						TootApiClient api_client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
							@Override
							public boolean isCancelled(){
								return __isCancelled();
							}
							
							@Override
							public void publishProgress( final String s ){
								Utils.runOnMainThread( new Runnable() {
									@Override
									public void run(){
										progress.setMessage( s );
									}
								} );
							}
						} );
						
						api_client.setUserInfo( instance, user_mail, password );
						
						TootApiResult result = api_client.get( "/api/v1/accounts/verify_credentials" );
						if( result != null && result.object != null ){
							TootAccount ta = TootAccount.parse( log, result.object );
							String user = ta.username +"@" + instance;
							this.row_id = SavedAccount.insert( log, instance, user, result.object ,result.token_info );
						}
						return result;
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						progress.dismiss();
						
						if( result == null ){
							// cancelled.
						}else if( result.object == null ){
							Utils.showToast( ActMain.this, true, result.error );
							log.e( result.error );
						}else{
							SavedAccount account = SavedAccount.loadAccount( log, row_id );
							if( account != null ){
								ActMain.this.onAccountUpdated( account );
								dialog.dismiss();
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
		} );
		
	}
	
	private void onAccountUpdated( SavedAccount data ){
		Utils.showToast( this, false, R.string.account_confirmed );
		//
		llEmpty.setVisibility( View.GONE );
		//
		Column col = new Column( this, data, Column.TYPE_TL_HOME );
		int idx = pager_adapter.addColumn( pager, col );
		pager.setCurrentItem( idx );
	}
	
	public void performColumnClose( Column column ){
		pager_adapter.removeColumn( pager, column );
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}
	}
	
	private void performAddTimeline( final int type, final Object... params ){
		AccountPicker.pick( this, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				llEmpty.setVisibility( View.GONE );
				//
				Column col = new Column( ActMain.this, ai, type, ai.id, params );
				int idx = pager_adapter.addColumn( pager, col );
				pager.setCurrentItem( idx );
			}
		} );
	}
	
	public void openBrowser( String url ){
		openChromeTab( url );
	}
	
	public void openChromeTab( String url ){
		try{
			// ビルダーを使って表示方法を指定する
			CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
			builder.setToolbarColor( ContextCompat.getColor( this, R.color.colorPrimary ) ).setShowTitle( true );
			
			// CustomTabsでURLをひらくIntentを発行
			CustomTabsIntent customTabsIntent = builder.build();
			customTabsIntent.launchUrl( this, Uri.parse( url ) );
			
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "openChromeTab failed." );
		}
	}
	
	final HTMLDecoder.LinkClickCallback link_click_listener = new HTMLDecoder.LinkClickCallback() {
		@Override
		public void onClickLink( String url ){
			openChromeTab( url );
		}
	};
	
	static final String FILE_COLUMN_LIST = "column_list";
	
	private void loadColumnList(){
		try{
			InputStream is = openFileInput( FILE_COLUMN_LIST );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream( is.available() );
				byte[] tmp = new byte[ 4096 ];
				for( ; ; ){
					int r = is.read( tmp, 0, tmp.length );
					if( r <= 0 ) break;
					bao.write( tmp, 0, r );
				}
				JSONArray array = new JSONArray( Utils.decodeUTF8( bao.toByteArray() ) );
				for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
					try{
						JSONObject src = array.optJSONObject( i );
						Column col = new Column( ActMain.this, src );
						pager_adapter.addColumn( pager, col );
					}catch( Throwable ex ){
						ex.printStackTrace();
					}
				}
			}finally{
				is.close();
			}
		}catch( FileNotFoundException ignored ){
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "loadColumnList failed." );
		}
		
		if( pager_adapter.column_list.size() > 0 ){
			llEmpty.setVisibility( View.GONE );
		}
	}
	
	private void saveColumnList(){
		JSONArray array = new JSONArray();
		for( Column column : pager_adapter.column_list ){
			try{
				JSONObject item = new JSONObject();
				column.encodeJSON( item );
				array.put( item );
			}catch( JSONException ex ){
				ex.printStackTrace();
			}
		}
		try{
			OutputStream os = openFileOutput( FILE_COLUMN_LIST, MODE_PRIVATE );
			try{
				os.write( Utils.encodeUTF8( array.toString() ) );
			}finally{
				os.close();
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "saveColumnList failed." );
		}
	}
	
	private void performTootButton(){

		Column c = pager_adapter.getColumn( pager.getCurrentItem() );
		if( c != null && c.access_info != null ){
			ActPost.open( this, c.access_info.db_id );
		}
	}
	
}
