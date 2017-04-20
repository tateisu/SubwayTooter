package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActMain extends AppCompatActivity
	implements NavigationView.OnNavigationItemSelectedListener {
	public static final LogCategory log = new LogCategory( "ActMain" );
	
	@Override
	protected void onCreate( Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		initUI();
		
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
			performAddTimeline(Column.TYPE_TL_HOME );
		}else if( id == R.id.nav_add_tl_local ){
			performAddTimeline(Column.TYPE_TL_LOCAL );
		}else if( id == R.id.nav_add_tl_federate ){
			performAddTimeline(Column.TYPE_TL_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			performAddTimeline(Column.TYPE_TL_FAVOURITES );
//		}else if( id == R.id.nav_add_reports ){
//			performAddTimeline(Column.TYPE_TL_REPORTS );
		}else if( id == R.id.nav_add_statuses ){
			performAddTimeline(Column.TYPE_TL_STATUSES );
		}else if( id == R.id.nav_add_notifications ){
			performAddTimeline(Column.TYPE_TL_NOTIFICATIONS );
			
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
		
		// toolbar
		Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
		setSupportActionBar( toolbar );
		
		// navigation drawer
		DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
			this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close );
		drawer.addDrawerListener( toggle );
		toggle.syncState();
		
		NavigationView navigationView = (NavigationView) findViewById( R.id.nav_view );
		navigationView.setNavigationItemSelectedListener( this );
		
		// floating action button
		FloatingActionButton fab = (FloatingActionButton) findViewById( R.id.fab );
		fab.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ){
				Snackbar.make( view, "Replace with your own action", Snackbar.LENGTH_LONG )
					.setAction( "Action", null ).show();
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
			public void startLogin( final Dialog dialog,final String instance, final String user_mail, final String password ){

				final ProgressDialog progress = new ProgressDialog( ActMain.this );

				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {

					boolean __isCancelled(){
						return isCancelled();
					}
					
					boolean is_added = false;
					
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
							is_added = ! SavedAccount.hasAccount(log,instance, user_mail);
							SavedAccount.save( log,instance, user_mail, result.object );
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
							SavedAccount account = SavedAccount.loadAccount(log,instance,user_mail);
							if( account != null ){
								ActMain.this.onAccountUpdated(account,is_added);
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
	
	public void performColumnClose( Column column ){
		pager_adapter.removeColumn( pager,column );
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}
	}
	
	private void onAccountUpdated( SavedAccount data, boolean is_added){
		Utils.showToast(this,false,R.string.accout_confirmed);
		if( is_added ){
			Column col = new Column( this, data, Column.TYPE_TL_HOME );
			pager_adapter.addColumn( pager, col );
			llEmpty.setVisibility( View.GONE );
		}
	}
	
	
	private void performAddTimeline( final int type,final Object... params){
		AccountPicker.pick( this, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				Column col = new Column( ActMain.this, ai, type ,ai.id,params);
				pager_adapter.addColumn( pager, col );
				pager.setCurrentItem( pager_adapter.getCount() -1 );
				llEmpty.setVisibility( View.GONE );
			}
		} );
	}
}
