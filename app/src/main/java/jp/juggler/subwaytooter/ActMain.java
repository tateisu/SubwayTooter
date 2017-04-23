package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.HashSet;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ActMain extends AppCompatActivity
	implements NavigationView.OnNavigationItemSelectedListener {
	public static final LogCategory log = new LogCategory( "ActMain" );
	
	static boolean update_at_resume = false;
	
//	@Override
//	protected void attachBaseContext(Context newBase) {
//		super.attachBaseContext( CalligraphyContextWrapper.wrap(newBase));
//	}
	
	SharedPreferences pref;
	
	@Override
	protected void onCreate( Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		pref = Pref.pref(this);
		
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
		
		// アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
		int size =pager_adapter.getCount();
		for(int i=size-1;i>=0;--i){
			Column column = pager_adapter.getColumn( i );
			SavedAccount sa = SavedAccount.loadAccount( log, column.access_info.db_id );
			if( sa == null ){
				pager_adapter.removeColumn( pager,column );
			}
		}
		
		
		if(update_at_resume){
			update_at_resume = false;
			// TODO: 各カラムを更新する
		}
		
		if( pager_adapter.getCount() == 0){
			llEmpty.setVisibility( View.VISIBLE );
		}
	}
	
	@Override
	protected void onPause(){
		HTMLDecoder.link_callback = null;
		saveColumnList();
		super.onPause();
	}
	
	static final int REQUEST_CODE_COLUMN_LIST =1;
	
	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( resultCode == RESULT_OK && requestCode == REQUEST_CODE_COLUMN_LIST ){
			if( data != null ){
				ArrayList<Integer> order = data.getIntegerArrayListExtra( ActColumnList.EXTRA_ORDER );
				if( order != null ){
					pager_adapter.setOrder( pager, order );
				}
				if( pager_adapter.column_list.isEmpty() ){
					llEmpty.setVisibility( View.VISIBLE );
				}
				
				int select = data.getIntExtra( ActColumnList.EXTRA_SELECTION ,-1);
				if( select != -1 ){
					pager.setCurrentItem( select,true );
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override
	public void onBackPressed(){
		DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
		if( drawer.isDrawerOpen( GravityCompat.START ) ){
			drawer.closeDrawer( GravityCompat.START );
		}else if( pref.getBoolean( Pref.KEY_BACK_TO_COLUMN_LIST ,false) ){
			performColumnList();
		}else if( ! pager_adapter.column_list.isEmpty() ){
			performColumnClose( false,pager_adapter.getColumn( pager.getCurrentItem() ) );
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
			
		}else if( id == R.id.nav_app_setting ){
			performAppSetting(  );
		}else if( id == R.id.nav_account_setting ){
			performAccountSetting();
		}else if( id == R.id.nav_column_list ){
			performColumnList();
			
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
				if( ! drawer.isDrawerOpen( Gravity.START ) ){
					drawer.openDrawer( Gravity.START );
				}
			}
		} );
		
		// ViewPager
		pager = (ViewPager) findViewById( R.id.viewPager );
		pager_adapter = new ColumnPagerAdapter( this );
		pager.setAdapter( pager_adapter );
	}
	
	public void performAccountAdd(){
		LoginForm.showLoginForm( this, null,new LoginForm.LoginFormCallback() {
			
			@Override
			public void startLogin( final Dialog dialog, final String instance, final String user_mail, final String password ){
				
				final ProgressDialog progress = new ProgressDialog( ActMain.this );
				
				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
					
					long row_id;
					
					@Override
					protected TootApiResult doInBackground( Void... params ){
						TootApiClient api_client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
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
						
						api_client.setUserInfo( instance, user_mail, password );
						
						TootApiResult result = api_client.request( "/api/v1/accounts/verify_credentials" );
						if( result != null && result.object != null ){
							TootAccount ta = TootAccount.parse( log, result.object );
							String user = ta.username +"@" + instance;
							this.row_id = SavedAccount.insert( instance, user, result.object ,result.token_info );
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
	

	public void performColumnClose( boolean bConfirm,final Column column ){
		if(! bConfirm && ! pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN,false ) ){
			new AlertDialog.Builder( this )
				.setTitle( R.string.confirm )
				.setMessage( R.string.close_column )
				.setNegativeButton( R.string.cancel, null )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick( DialogInterface dialog, int which ){
						performColumnClose( true,column );
					}
				} )
				.show();
			return;
		}
		int page_showing = pager.getCurrentItem();
		int page_delete = pager_adapter.column_list.indexOf( column );
		pager_adapter.removeColumn( pager, column );
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}else if( page_showing > 0 && page_showing == page_delete ){
			pager.setCurrentItem( page_showing-1 ,true);
		}
	}
	
	//////////////////////////////////////////////////////////////
	// カラム追加系
	
	public void addColumn(SavedAccount ai,int type,long who,long status_id ){
		// 既に同じカラムがあればそこに移動する
		for( Column column : pager_adapter.column_list ){
			if( ai.user.equals( column.access_info.user )
				&& column.type == type
				&& column.who_id == who
				&& column.status_id == status_id
			){
				pager.setCurrentItem( pager_adapter.column_list.indexOf( column ) ,true);
				return;
			}
		}
		
		llEmpty.setVisibility( View.GONE );
		//
		Column col = new Column( ActMain.this, ai, type, who,status_id );
		int idx = pager_adapter.addColumn( pager, col );
		pager.setCurrentItem( idx ,true);
	}
	
	private void onAccountUpdated( SavedAccount data ){
		Utils.showToast( this, false, R.string.account_confirmed );
		addColumn(data, Column.TYPE_TL_HOME,data.id ,0L );
	}
	
	void performOpenUser(SavedAccount access_info,TootAccount user){
		addColumn( access_info,Column.TYPE_TL_STATUSES, user.id ,0L);
	}
	
	
	public void performConversation( SavedAccount access_info, TootStatus status ){
		addColumn( access_info,Column.TYPE_TL_CONVERSATION, access_info.id, status.id );
	}
	
	private void performAddTimeline( final int type ){
		AccountPicker.pick( this, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				addColumn( ai, type,ai.id ,0L);
			}
		} );
	}
	
	//////////////////////////////////////////////////////////////

	
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
			// ex.printStackTrace();
			log.e( ex, "openChromeTab failed. url=%s",url );
		}
	}
	
	final HTMLDecoder.LinkClickCallback link_click_listener = new HTMLDecoder.LinkClickCallback() {
		@Override
		public void onClickLink( String url ){
			openChromeTab( url );
		}
	};

	private void performTootButton(){
		Column c = pager_adapter.getColumn( pager.getCurrentItem() );
		if( c != null && c.access_info != null ){
			ActPost.open( this, c.access_info.db_id ,null );
		}
	}
	public void performReply( SavedAccount account, TootStatus status ){
		ActPost.open( this, account.db_id ,status );
	}
	
	/////////////////////////////////////////////////////////////////////////
	
	private void showColumnMatchAccount( SavedAccount account ){
		for( Column column : pager_adapter.column_list ){
			if( account.user.equals( column.access_info.user ) ){
				column.fireVisualCallback();
			}
		}
	}
	
	
	/////////////////////////////////////////////////////////////////////////
	// favourite
	
	final HashSet<String> map_busy_fav = new HashSet<>(  );
	
	boolean isBusyFav(SavedAccount account,TootStatus status){
		String busy_key = account.host+":"+ status.id;
		return map_busy_fav.contains(busy_key);
	}
	
	public void performFavourite( final SavedAccount account, final TootStatus status ){
		//
		final String busy_key = account.host+":"+ status.id;
		//
		if( map_busy_fav.contains(busy_key) ){
			Utils.showToast( this,false,R.string.wait_previous_operation );
			return;
		}
		//
		map_busy_fav.add( busy_key );
		//
		new AsyncTask<Void,Void,TootApiResult>(){
			final boolean new_state = ! status.favourited;
			TootStatus new_status;
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( account );
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						,""
					));
				
				TootApiResult result = client.request(
					(new_state
						? "/api/v1/statuses/"+status.id+"/favourite"
						: "/api/v1/statuses/"+status.id+"/unfavourite"
					)
					, request_builder );
				if( result.object != null ){
					new_status = TootStatus.parse( log,result.object );
				}

				return result;
				
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				map_busy_fav.remove( busy_key);
				if( new_status  != null ){
					// カウント数は遅延があるみたい
					if( new_state && new_status.favourites_count <= status.favourites_count ){
						// 星つけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = status.favourites_count +1;
					}else if( !new_state && new_status.favourites_count >= status.favourites_count ){
						// 星外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = status.favourites_count -1;
						if( new_status.favourites_count < 0 ){
							new_status.favourites_count = 0;
						}
					}
					for( Column column : pager_adapter.column_list ){
						column.findStatus( account, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public void onIterate( TootStatus status ){
								status.favourited = new_status.favourited;
								status.favourites_count = new_status.favourites_count;
							}
						});
					}
				}else{
					if( result != null) Utils.showToast( ActMain.this,true,result.error );
				}
				showColumnMatchAccount(account);
			}
			
		}.execute();
		showColumnMatchAccount(account);
	}
	

	
	/////////////////////////////////////////////////////////////////////////
	// boost
	final HashSet<String> map_busy_boost = new HashSet<>(  );
	
	boolean isBusyBoost(SavedAccount account,TootStatus status){
		String busy_key = account.host+":"+ status.id;
		return map_busy_boost.contains( busy_key);
	}
	
	public void performBoost( final SavedAccount account, final TootStatus status ,boolean bConfirmed){
		//
		final String busy_key = account.host + ":" + status.id;
		//
		if(map_busy_boost.contains( busy_key ) ){
			Utils.showToast( this, false, R.string.wait_previous_operation );
			return;
		}
		
		if( status.reblogged ){
			// FAVがついているか、FAV操作中はBoostを外せない
			if( isBusyFav( account, status ) || status.favourited ){
				Utils.showToast( this, false, R.string.cant_remove_boost_while_favourited );
				return;
			}
		}else{
			if(!bConfirmed && account.confirm_boost ){
				// TODO: アカウント設定でスキップさせたい
				new AlertDialog.Builder(this)
					.setTitle(R.string.confirm)
					.setMessage(R.string.confirm_boost)
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick( DialogInterface dialog, int which ){
							performBoost(  account,  status ,true);
						}
					} )
					.setNegativeButton( R.string.cancel,null )
					.show();
				return;
			}
		}

		//
		map_busy_boost.add( busy_key);
		//
		new AsyncTask<Void,Void,TootApiResult>(){
			final boolean new_state = ! status.reblogged;
			TootStatus new_status;
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( account );
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						,""
					));
				
				TootApiResult result = client.request(
					"/api/v1/statuses/"+status.id+(new_state ? "/reblog" : "/unreblog")
					, request_builder );

				if( result.object != null ){
					// reblog,unreblog のレスポンスは信用ならんのでステータスを再取得する
					result = client.request("/api/v1/statuses/"+status.id);
					if( result.object != null ){
						new_status = TootStatus.parse( log, result.object );
					}
				}
				
				return result;
				
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				map_busy_boost.remove( busy_key);
				if( new_status  != null ){
					// カウント数は遅延があるみたい
					if( new_status.reblogged && new_status.reblogs_count <= status.reblogs_count ){
						// 星つけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = status.reblogs_count +1;
					}else if( !new_status.reblogged && new_status.reblogs_count >= status.reblogs_count ){
						// 星外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = status.reblogs_count -1;
						if( new_status.reblogs_count < 0 ){
							new_status.reblogs_count = 0;
						}
					}
					for( Column column : pager_adapter.column_list ){
						column.findStatus( account, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public void onIterate( TootStatus status ){
								status.reblogged = new_status.reblogged;
								status.reblogs_count = new_status.reblogs_count;
							}
						});
					}
				}else{
					if( result != null) Utils.showToast( ActMain.this,true,result.error );
				}
				showColumnMatchAccount(account);
			}
			
		}.execute();
		
		showColumnMatchAccount(account);
	}
	
	////////////////////////////////////////
	
	public void performMore( SavedAccount account, TootStatus status ){
		// open menu
		// Expand this status
		// Mute user
		// Block user
		// report user
		Utils.showToast( this,false,"not implemented. toot="+status.decoded_content );
	}


	////////////////////////////////////////
	

	
	private void performAccountSetting(){
		AccountPicker.pick( this, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				ActAccountSetting.open( ActMain.this, ai);
			}
		} );
	}
	
	private void performAppSetting(){
		ActAppSetting.open( ActMain.this);
	}
	
	

	////////////////////////////////////////////////////////
	// column list
	
	
	JSONArray encodeColumnList(){
		JSONArray array = new JSONArray();
		for(int i=0,ie = pager_adapter.column_list.size(); i<ie;++i){
			Column column = pager_adapter.column_list.get(i);
			try{
				JSONObject dst = new JSONObject();
				column.encodeJSON( dst ,i);
				array.put( dst );
			}catch( JSONException ex ){
				ex.printStackTrace();
			}
		}
		return array;
	}
	
	private void performColumnList(){
		Intent intent = new Intent(this,ActColumnList.class);
		intent.putExtra(ActColumnList.EXTRA_ORDER,encodeColumnList().toString() );
		intent.putExtra(ActColumnList.EXTRA_SELECTION,pager.getCurrentItem() );
		startActivityForResult( intent ,REQUEST_CODE_COLUMN_LIST );
	}
	
	
	static final String FILE_COLUMN_LIST = "column_list";
	
	
	private void saveColumnList(){
		JSONArray array = encodeColumnList();
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

}
