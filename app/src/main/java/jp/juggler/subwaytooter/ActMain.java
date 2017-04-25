package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
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
import android.text.TextUtils;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.dialog.ReportForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.ActionsDialog;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LinkClickContext;
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
		
		pref = Pref.pref( this );
		
		initUI();
		
		AlarmService.startCheck( this );
		
		loadColumnList();
	}
	
	@Override protected void onDestroy(){
		super.onDestroy();
	}
	
	@Override protected void onResume(){
		super.onResume();
		HTMLDecoder.link_callback = link_click_listener;
		
		// アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
		{
			
			ArrayList< Integer > new_order = new ArrayList<>();
			boolean bRemoved = false;
			for( int i = 0, ie = pager_adapter.getCount() ; i < ie ; ++ i ){
				Column column = pager_adapter.getColumn( i );
				SavedAccount sa = SavedAccount.loadAccount( log, column.access_info.db_id );
				if( sa == null ){
					bRemoved = true;
				}else{
					new_order.add( i );
				}
			}
			if( bRemoved ){
				pager_adapter.setOrder( pager, new_order );
				saveColumnList();
			}
		}
		
		// 各カラムのアカウント設定を読み直す
		reloadAccountSetting();
		
		if( update_at_resume ){
			update_at_resume = false;
			// TODO: 各カラムを更新する
		}
		
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}
		
		Uri uri = ActOAuthCallback.last_uri.get();
		if( uri != null ){
			ActOAuthCallback.last_uri.set( null );
			updateAccessToken( uri );
		}
	}
	
	@Override protected void onPause(){
		HTMLDecoder.link_callback = null;
		super.onPause();
	}
	
	boolean isOrderChanged( ArrayList< Integer > new_order ){
		if( new_order.size() != pager_adapter.getCount() ) return true;
		for( int i = 0, ie = new_order.size() ; i < ie ; ++ i ){
			if( new_order.get( i ) != i ) return true;
		}
		return false;
	}
	
	static final int REQUEST_CODE_COLUMN_LIST = 1;
	static final int REQUEST_CODE_ACCOUNT_SETTING = 2;
	
	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( resultCode == RESULT_OK ){
			if( requestCode == REQUEST_CODE_COLUMN_LIST ){
				if( data != null ){
					ArrayList< Integer > order = data.getIntegerArrayListExtra( ActColumnList.EXTRA_ORDER );
					if( order != null && isOrderChanged( order ) ){
						pager_adapter.setOrder( pager, order );
						saveColumnList();
					}
					
					if( pager_adapter.column_list.isEmpty() ){
						llEmpty.setVisibility( View.VISIBLE );
					}else{
						int select = data.getIntExtra( ActColumnList.EXTRA_SELECTION, - 1 );
						if( select != - 1 ){
							pager.setCurrentItem( select, true );
						}
					}
				}
			}else if( requestCode == REQUEST_CODE_ACCOUNT_SETTING ){
				if( data != null ){
					startAccessTokenUpdate( data );
					return;
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
			return;
		}

		switch( pref.getInt(Pref.KEY_BACK_BUTTON_ACTION,0)){
		default:
		case ActAppSetting.BACK_ASK_ALWAYS:
			ActionsDialog dialog = new ActionsDialog();
			dialog.addAction( getString( R.string.close_column ), new Runnable() {
				@Override public void run(){
					performColumnClose( true, pager_adapter.getColumn( pager.getCurrentItem() ) );
				}
			} );
			dialog.addAction( getString( R.string.open_column_list ), new Runnable() {
				@Override public void run(){
					performColumnList();
				}
			} );
			dialog.addAction( getString( R.string.app_exit ), new Runnable() {
				@Override public void run(){
					ActMain.this.finish();
				}
			} );
			dialog.show( this,null );
			break;

		case ActAppSetting.BACK_CLOSE_COLUMN:
			performColumnClose( false, pager_adapter.getColumn( pager.getCurrentItem() ) );
			break;

		case ActAppSetting.BACK_EXIT_APP:
			ActMain.this.finish();
			break;

		case ActAppSetting.BACK_OPEN_COLUMN_LIST:
			performColumnList();
			break;
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
			performAddTimeline( Column.TYPE_HOME );
		}else if( id == R.id.nav_add_tl_local ){
			performAddTimeline( Column.TYPE_LOCAL );
		}else if( id == R.id.nav_add_tl_federate ){
			performAddTimeline( Column.TYPE_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			performAddTimeline( Column.TYPE_FAVOURITES );
//		}else if( id == R.id.nav_add_reports ){
//			performAddTimeline(Column.TYPE_REPORTS );
		}else if( id == R.id.nav_add_statuses ){
			performAddTimeline( Column.TYPE_PROFILE );
		}else if( id == R.id.nav_add_notifications ){
			performAddTimeline( Column.TYPE_NOTIFICATIONS );
			
		}else if( id == R.id.nav_app_setting ){
			performAppSetting();
		}else if( id == R.id.nav_account_setting ){
			performAccountSetting();
		}else if( id == R.id.nav_column_list ){
			performColumnList();
			
		}else if( id == R.id.nav_add_tl_search ){
			performAddTimeline( Column.TYPE_SEARCH );
			
		}else if( id == R.id.nav_app_about ){
			openAppAbout();
			
		}else if( id == R.id.nav_oss_license ){
			openOSSLicense();
			
		}else if( id == R.id.nav_app_exit ){
			finish();
			
			
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
		LoginForm.showLoginForm( this, null, new LoginForm.LoginFormCallback() {
			@Override
			public void startLogin( final Dialog dialog, final String instance ){
				
				final ProgressDialog progress = new ProgressDialog( ActMain.this );
				
				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
					
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
						
						api_client.setInstance( instance );
						return api_client.authorize1();
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						progress.dismiss();
						
						if( result == null ){
							// cancelled.
						}else if( result.error != null ){
							// エラー？
							String sv = result.error;
							if( sv.startsWith( "https" ) ){
								// OAuth認証が必要
								Intent data = new Intent();
								data.setData( Uri.parse( sv ) );
								startAccessTokenUpdate( data );
								dialog.dismiss();
								return;
							}
							// 他のエラー
							Utils.showToast( ActMain.this, true, sv );
							log.e( result.error );
						}else{
							// 多分ここは通らないはず
							Utils.showToast( ActMain.this, false, R.string.access_token_updated_for );
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
	
	private void startAccessTokenUpdate( Intent data ){
		Uri uri = data.getData();
		if( uri == null ) return;
		// ブラウザで開く
		try{
			Intent intent = new Intent( Intent.ACTION_VIEW, uri );
			startActivity( intent );
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	private void updateAccessToken( final Uri uri ){
		
		// 通知タップ
		// subwaytooter://notification_click?db_id=(db_id)
		String sv = uri.getQueryParameter( "db_id" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				long db_id = Long.parseLong( sv, 10 );
				SavedAccount account = SavedAccount.loadAccount( log, db_id );
				if( account != null ){
					Column column = addColumn( account, Column.TYPE_NOTIFICATIONS );
					if( ! column.bInitialLoading ){
						column.reload();
					}
				}
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			return;
		}
		
		final ProgressDialog progress = new ProgressDialog( ActMain.this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			long row_id;
			TootAccount ta;
			SavedAccount sa;
			String host;
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
						Utils.runOnMainThread( new Runnable() {
							@Override
							public void run(){
								progress.setMessage( s );
							}
						} );
					}
				} );
				
				// エラー時
				// subwaytooter://oauth
				// ?error=access_denied
				// &error_description=%E3%83%AA%E3%82%BD%E3%83%BC%E3%82%B9%E3%81%AE%E6%89%80%E6%9C%89%E8%80%85%E3%81%BE%E3%81%9F%E3%81%AF%E8%AA%8D%E8%A8%BC%E3%82%B5%E3%83%BC%E3%83%90%E3%83%BC%E3%81%8C%E8%A6%81%E6%B1%82%E3%82%92%E6%8B%92%E5%90%A6%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F%E3%80%82
				// &state=db%3A3
				String error = uri.getQueryParameter( "error_description" );
				if( ! TextUtils.isEmpty( error ) ){
					return new TootApiResult( error );
				}
				
				// subwaytooter://oauth
				//    ?code=113cc036e078ac500d3d0d3ad345cd8181456ab087abc67270d40f40a4e9e3c2
				//    &state=host%3Amastodon.juggler.jp
				
				String code = uri.getQueryParameter( "code" );
				if( TextUtils.isEmpty( code ) ){
					return new TootApiResult( "missing code in callback url." );
				}
				
				String sv = uri.getQueryParameter( "state" );
				if( TextUtils.isEmpty( sv ) ){
					return new TootApiResult( "missing state in callback url." );
				}
				
				if( sv.startsWith( "db:" ) ){
					try{
						long db_id = Long.parseLong( sv.substring( 3 ), 10 );
						this.sa = SavedAccount.loadAccount( log, db_id );
						if( sa == null ){
							return new TootApiResult( "missing account db_id=" + db_id );
						}
						client.setAccount( sa );
					}catch( Throwable ex ){
						ex.printStackTrace();
						return new TootApiResult( Utils.formatError( ex, "invalid state" ) );
					}
				}else if( sv.startsWith( "host:" ) ){
					String host = sv.substring( 5 );
					client.setInstance( host );
				}
				
				if( client.instance == null ){
					return new TootApiResult( "missing instance  in callback url." );
				}
				
				this.host = client.instance;
				
				TootApiResult result = client.authorize2( uri.toString(), code );
				if( result != null && result.object != null ){
					// taは使い捨てなので、生成に使うLinkClickContextはダミーで問題ない
					LinkClickContext lcc = new LinkClickContext() {
					};
					this.ta = TootAccount.parse( log, lcc, result.object );
				}
				return result;
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				progress.dismiss();
				
				// /noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( result.error != null ){
					Utils.showToast( ActMain.this, true, result.error );
				}else if( ta == null ){
					// 自分のユーザネームを取れなかった
					// …普通はエラーメッセージが設定されてるはずだが
					Utils.showToast( ActMain.this, true, "missing TootAccount" );
				}else if( this.sa != null ){
					// アクセストークン更新時
					
					// インスタンスは同じだと思うが、ユーザ名が異なる可能性がある
					if( ! sa.username.equals( ta.username ) ){
						Utils.showToast( ActMain.this, true, R.string.user_name_not_match );
					}else{
						Utils.showToast( ActMain.this, false, R.string.access_token_updated_for, sa.acct );
						
						// DBの情報を更新する
						sa.updateTokenInfo( result.token_info );
						// 各カラムの持つアカウント情報をリロードする
						reloadAccountSetting();
						
						// 自動でリロードする
						for( Column c : pager_adapter.column_list ){
							if( c.access_info.acct.equals( sa.acct ) ){
								c.reload();
							}
						}
					}
				}else{
					// アカウント追加時
					String user = ta.username + "@" + host;
					this.row_id = SavedAccount.insert( host, user, result.object, result.token_info );
					SavedAccount account = SavedAccount.loadAccount( log, row_id );
					if( account != null ){
						AlarmService.startCheck( ActMain.this );
						onAccountUpdated( account );
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
	
	void reloadAccountSetting(){
		ArrayList< SavedAccount > done_list = new ArrayList<>();
		for( Column column : pager_adapter.column_list ){
			SavedAccount a = column.access_info;
			if( a == null || done_list.contains( a ) ) continue;
			done_list.add( a );
			a.reloadSetting();
		}
	}
	
	public void performColumnClose( boolean bConfirm, final Column column ){
		if( ! bConfirm && ! pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false ) ){
			new AlertDialog.Builder( this )
				.setMessage( R.string.confirm_close_column )
				.setNegativeButton( R.string.cancel, null )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick( DialogInterface dialog, int which ){
						performColumnClose( true, column );
					}
				} )
				.show();
			return;
		}
		int page_showing = pager.getCurrentItem();
		int page_delete = pager_adapter.column_list.indexOf( column );
		pager_adapter.removeColumn( pager, column );
		saveColumnList();
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}else if( page_showing > 0 && page_showing == page_delete ){
			pager.setCurrentItem( page_showing - 1, true );
		}
	}
	
	//////////////////////////////////////////////////////////////
	// カラム追加系
	
	public Column addColumn( SavedAccount ai, int type, Object... params ){
		// 既に同じカラムがあればそこに移動する
		for( Column column : pager_adapter.column_list ){
			if( column.isSameSpec( ai, type, params ) ){
				pager.setCurrentItem( pager_adapter.column_list.indexOf( column ), true );
				return column;
			}
		}
		//
		llEmpty.setVisibility( View.GONE );
		//
		Column col = new Column( ActMain.this, ai, type, params );
		int idx = pager_adapter.addColumn( pager, col );
		saveColumnList();
		pager.setCurrentItem( idx, true );
		return col;
	}
	
	private void onAccountUpdated( SavedAccount data ){
		Utils.showToast( this, false, R.string.account_confirmed );
		addColumn( data, Column.TYPE_HOME );
	}
	
	void performOpenUser( SavedAccount access_info, TootAccount user ){
		addColumn( access_info, Column.TYPE_PROFILE, user.id );
	}
	
	public void performConversation( SavedAccount access_info, TootStatus status ){
		addColumn( access_info, Column.TYPE_CONVERSATION, status.id );
	}
	
	private void performAddTimeline( final int type ){
		AccountPicker.pick( this, true, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				switch( type ){
				default:
					addColumn( ai, type, ai.id );
					break;
				case Column.TYPE_SEARCH:
					addColumn( ai, type, "", false );
					break;
				}
			}
		} );
	}
	
	public void openHashTag( SavedAccount access_info, String tag ){
		addColumn( access_info, Column.TYPE_HASHTAG, tag );
	}
	
	//////////////////////////////////////////////////////////////
	
	interface GetAccountCallback {
		// return account information
		// if failed, account is null.
		void onGetAccount( TootAccount account );
	}
	
	void startGetAccount( final SavedAccount access_info, final String host, final String user, final GetAccountCallback callback ){
		
		final ProgressDialog progress = new ProgressDialog( this );
		final AsyncTask< Void, Void, TootAccount > task = new AsyncTask< Void, Void, TootAccount >() {
			@Override
			protected TootAccount doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					
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
				client.setAccount( access_info );
				String path = "/api/v1/accounts/search" + "?q=" + Uri.encode( user );
				
				TootApiResult result = client.request( path );
				
				if( result.array != null ){
					for( int i = 0, ie = result.array.length() ; i < ie ; ++ i ){
						
						TootAccount item = TootAccount.parse( log, access_info, result.array.optJSONObject( i ) );
						
						if( ! item.username.equals( user ) ) continue;
						
						if( ! item.acct.contains( "@" )
							|| item.acct.equalsIgnoreCase( user + "@" + host ) )
							return item;
					}
				}
				
				return null;
				
			}
			
			@Override
			protected void onCancelled( TootAccount result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootAccount result ){
				progress.dismiss();
				callback.onGetAccount( result );
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
	
	public void openBrowser( SavedAccount account, String url ){
		openChromeTab( account, url, false );
	}
	
	Pattern reHashTag = Pattern.compile( "\\Ahttps://([^/]+)/tags/([^?#]+)\\z" );
	Pattern reUserPage = Pattern.compile( "\\Ahttps://([^/]+)/@([^?#]+)\\z" );
	
	public void openChromeTab( final SavedAccount access_info, final String url, boolean noIntercept ){
		try{
			log.d( "openChromeTab url=%s", url );
			
			if( ! noIntercept ){
				// ハッシュタグをアプリ内で開く
				Matcher m = reHashTag.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					String host = m.group( 1 );
					String tag = Uri.decode( m.group( 2 ) );
					if( host.equalsIgnoreCase( access_info.host ) ){
						openHashTag( access_info, tag );
						return;
					}else{
						openHashTagOtherInstance( access_info, url, host, tag );
						return;
					}
				}
				
				m = reUserPage.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/@SubwayTooter
					final String host = m.group( 1 );
					final String user = Uri.decode( m.group( 2 ) );
					startGetAccount( access_info, host, user, new GetAccountCallback() {
						@Override
						public void onGetAccount( TootAccount who ){
							if( who != null ){
								performOpenUser( access_info, who );
								return;
							}
							openChromeTab( access_info, url, true );
						}
					} );
					return;
				}
			}
			
			// ビルダーを使って表示方法を指定する
			CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
			builder.setToolbarColor( ContextCompat.getColor( this, R.color.colorPrimary ) ).setShowTitle( true );
			
			// CustomTabsでURLをひらくIntentを発行
			CustomTabsIntent customTabsIntent = builder.build();
			customTabsIntent.launchUrl( this, Uri.parse( url ) );
			
		}catch( Throwable ex ){
			// ex.printStackTrace();
			log.e( ex, "openChromeTab failed. url=%s", url );
		}
	}
	
	// 他インスタンスのハッシュタグの表示
	private void openHashTagOtherInstance( final SavedAccount access_info, final String url, String host, final String tag ){
		
		ActionsDialog dialog = new ActionsDialog();
		
		ArrayList< SavedAccount > account_list = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( log ) ){
			if( a.host.equalsIgnoreCase( host ) ){
				account_list.add( a );
			}
		}
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override
			public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( a.getFullAcct( a ), b.getFullAcct( b ) );
			}
		} );
		for( SavedAccount a : account_list ){
			final SavedAccount _a = a;
			dialog.addAction(
				getString( R.string.open_in_account, a.acct )
				, new Runnable() {
					@Override
					public void run(){
						openHashTag( _a, tag );
					}
				}
			);
			
		}
		if( account_list.isEmpty() ){
			// TODO ログインなしアカウントで開く選択肢
		}
		// カラムのアカウントで開く
		{
			final SavedAccount _a = access_info;
			dialog.addAction(
				getString( R.string.open_in_account, access_info.acct )
				, new Runnable() {
					@Override
					public void run(){
						openHashTag( _a, tag );
					}
				}
			
			);
		}
		
		// ブラウザで表示する
		{
			dialog.addAction(
				getString( R.string.open_web_on_host, host )
				, new Runnable() {
					@Override
					public void run(){
						openChromeTab( access_info, url, true );
					}
				}
			);
			
		}
		
		dialog.show( this, "#" + tag );
		
	}
	
	final HTMLDecoder.LinkClickCallback link_click_listener = new HTMLDecoder.LinkClickCallback() {
		@Override
		public void onClickLink( LinkClickContext lcc, String url ){
			openChromeTab( (SavedAccount) lcc, url, false );
		}
	};
	
	private void performTootButton(){
		Column c = pager_adapter.getColumn( pager.getCurrentItem() );
		if( c != null && c.access_info != null ){
			ActPost.open( this, c.access_info.db_id, "" );
		}
	}
	
	public void performReply( SavedAccount account, TootStatus status ){
		ActPost.open( this, account.db_id, status );
	}
	
	public void performMention( SavedAccount account, TootAccount who ){
		ActPost.open( this, account.db_id, account.getFullAcct( who ) + " " );
	}
	
	/////////////////////////////////////////////////////////////////////////
	
	private void showColumnMatchAccount( SavedAccount account ){
		for( Column column : pager_adapter.column_list ){
			if( account.acct.equals( column.access_info.acct ) ){
				column.fireVisualCallback();
			}
		}
	}
	
	/////////////////////////////////////////////////////////////////////////
	// favourite
	
	final HashSet< String > map_busy_fav = new HashSet<>();
	
	boolean isBusyFav( SavedAccount account, TootStatus status ){
		String busy_key = account.host + ":" + status.id;
		return map_busy_fav.contains( busy_key );
	}
	
	public void performFavourite( final SavedAccount account, final TootStatus status ){
		//
		final String busy_key = account.host + ":" + status.id;
		//
		if( map_busy_fav.contains( busy_key ) ){
			Utils.showToast( this, false, R.string.wait_previous_operation );
			return;
		}
		//
		map_busy_fav.add( busy_key );
		//
		new AsyncTask< Void, Void, TootApiResult >() {
			final boolean bSet = ! status.favourited;
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
						, ""
					) );
				
				TootApiResult result = client.request(
					( bSet
						? "/api/v1/statuses/" + status.id + "/favourite"
						: "/api/v1/statuses/" + status.id + "/unfavourite"
					)
					, request_builder );
				if( result.object != null ){
					new_status = TootStatus.parse( log, account, result.object );
				}
				
				return result;
				
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				map_busy_fav.remove( busy_key );
				if( new_status != null ){
					// カウント数は遅延があるみたい
					if( bSet && new_status.favourites_count <= status.favourites_count ){
						// 星つけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = status.favourites_count + 1;
					}else if( ! bSet && new_status.favourites_count >= status.favourites_count ){
						// 星外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = status.favourites_count - 1;
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
						} );
					}
				}else{
					if( result != null ) Utils.showToast( ActMain.this, true, result.error );
				}
				showColumnMatchAccount( account );
			}
			
		}.execute();
		showColumnMatchAccount( account );
	}
	
	/////////////////////////////////////////////////////////////////////////
	// boost
	final HashSet< String > map_busy_boost = new HashSet<>();
	
	boolean isBusyBoost( SavedAccount account, TootStatus status ){
		String busy_key = account.host + ":" + status.id;
		return map_busy_boost.contains( busy_key );
	}
	
	public void performBoost( final SavedAccount account, final TootStatus status, boolean bConfirmed ){
		//
		final String busy_key = account.host + ":" + status.id;
		//
		if( map_busy_boost.contains( busy_key ) ){
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
			if( ! bConfirmed && account.confirm_boost ){
				// TODO: アカウント設定でスキップさせたい
				new AlertDialog.Builder( this )
					.setTitle( R.string.confirm )
					.setMessage( R.string.confirm_boost )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick( DialogInterface dialog, int which ){
							performBoost( account, status, true );
						}
					} )
					.setNegativeButton( R.string.cancel, null )
					.show();
				return;
			}
		}
		
		//
		map_busy_boost.add( busy_key );
		//
		new AsyncTask< Void, Void, TootApiResult >() {
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
						, ""
					) );
				
				TootApiResult result = client.request(
					"/api/v1/statuses/" + status.id + ( new_state ? "/reblog" : "/unreblog" )
					, request_builder );
				
				if( result.object != null ){
					// reblog,unreblog のレスポンスは信用ならんのでステータスを再取得する
					result = client.request( "/api/v1/statuses/" + status.id );
					if( result.object != null ){
						new_status = TootStatus.parse( log, account, result.object );
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
				map_busy_boost.remove( busy_key );
				if( new_status != null ){
					// カウント数は遅延があるみたい
					if( new_status.reblogged && new_status.reblogs_count <= status.reblogs_count ){
						// 星つけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = status.reblogs_count + 1;
					}else if( ! new_status.reblogged && new_status.reblogs_count >= status.reblogs_count ){
						// 星外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = status.reblogs_count - 1;
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
						} );
					}
				}else{
					if( result != null ) Utils.showToast( ActMain.this, true, result.error );
				}
				showColumnMatchAccount( account );
			}
			
		}.execute();
		
		showColumnMatchAccount( account );
	}
	
	////////////////////////////////////////
	
	private void performAccountSetting(){
		AccountPicker.pick( this, true, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				ActAccountSetting.open( ActMain.this, ai, REQUEST_CODE_ACCOUNT_SETTING );
			}
		} );
	}
	
	private void performAppSetting(){
		ActAppSetting.open( ActMain.this );
	}
	
	////////////////////////////////////////////////////////
	// column list
	
	JSONArray encodeColumnList(){
		JSONArray array = new JSONArray();
		for( int i = 0, ie = pager_adapter.column_list.size() ; i < ie ; ++ i ){
			Column column = pager_adapter.column_list.get( i );
			try{
				JSONObject dst = new JSONObject();
				column.encodeJSON( dst, i );
				array.put( dst );
			}catch( JSONException ex ){
				ex.printStackTrace();
			}
		}
		return array;
	}
	
	private void performColumnList(){
		Intent intent = new Intent( this, ActColumnList.class );
		intent.putExtra( ActColumnList.EXTRA_ORDER, encodeColumnList().toString() );
		intent.putExtra( ActColumnList.EXTRA_SELECTION, pager.getCurrentItem() );
		startActivityForResult( intent, REQUEST_CODE_COLUMN_LIST );
	}
	
	private void dumpColumnList(){
		for( int i = 0, ie = pager_adapter.column_list.size() ; i < ie ; ++ i ){
			Column column = pager_adapter.column_list.get( i );
			log.d( "dumpColumnList [%s]%s %s", i, column.access_info.acct, column.getColumnName( true ) );
		}
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
						pager_adapter.addColumn( pager, col, pager_adapter.getCount() );
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
	
	////////////////////////////////////////////////////////////////////////////
	
	interface RelationChangedCallback {
		// void onRelationChanged( TootRelationShip relationship );
		void onRelationChanged();
	}
	
	private void callFollow( final SavedAccount access_info, final TootAccount who, final boolean bFollow, final RelationChangedCallback callback ){
		
		new AsyncTask< Void, Void, TootApiResult >() {
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
						
					}
				} );
				client.setAccount( access_info );
				
				TootApiResult result;
				if( bFollow & who.acct.contains( "@" ) ){
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "uri=" + Uri.encode( who.acct )
						) );
					result = client.request( "/api/v1/follows", request_builder );
					if( result != null ){
						if( result.object != null ){
							remote_who = TootAccount.parse( log, access_info, result.object );
							
							Utils.showToast( ActMain.this, false, R.string.follow_succeeded );
						}else if( who.locked && result.response.code() == 422 ){
							Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
						}else{
							Utils.showToast( ActMain.this, false, result.error );
						}
					}
				}else{
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "" // 空データ
						) );
					result = client.request( "/api/v1/accounts/" + who.id + ( bFollow ? "/follow" : "/unfollow" )
						, request_builder );
					if( result != null ){
						if( result.object != null ){
							relation = TootRelationShip.parse( log, result.object );
							
							Utils.showToast( ActMain.this, false, bFollow ? R.string.follow_succeeded : R.string.unfollow_succeeded );
						}else if( bFollow && who.locked && result.response.code() == 422 ){
							Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
						}else{
							Utils.showToast( ActMain.this, false, result.error );
						}
					}
				}
				
				return result;
			}
			
			TootRelationShip relation;
			TootAccount remote_who;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
//				if( relation != null ){
//			     	App1.relationship_map.put( access_info, relation );
//					if( callback != null ) callback.onRelationChanged( relation );
//				}else if( remote_who != null ){
//					App1.relationship_map.addFollowing( access_info, remote_who.id );
//					if( callback != null )
//						callback.onRelationChanged( App1.relationship_map.get( access_info, remote_who.id ) );
//				}
				
				if( relation != null ){
					if( callback != null ) callback.onRelationChanged();
				}else if( remote_who != null ){
					if( callback != null )
						callback.onRelationChanged();
				}
			}
		}.execute();
	}
	
	// acct で指定したユーザをリモートフォローする
	void callRemoteFollow( final SavedAccount access_info, final String acct, final boolean locked, final RelationChangedCallback callback ){
		
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
						
					}
				} );
				client.setAccount( access_info );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "uri=" + Uri.encode( acct )
					) );
				TootApiResult result = client.request( "/api/v1/follows", request_builder );
				
				if( result != null ){
					if( result.object != null ){
						who = TootAccount.parse( log, access_info, result.object );
						Utils.showToast( ActMain.this, false, R.string.follow_succeeded );
					}else if( locked && result.response.code() == 422 ){
						Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
					}else{
						Utils.showToast( ActMain.this, false, result.error );
					}
				}
				
				return result;
			}
			
			TootAccount who;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( who != null ){
					//	App1.relationship_map.addFollowing( access_info, who.id );
//					if( callback != null )
//						callback.onRelationChanged( App1.relationship_map.get( access_info, who.id ) );
					
					if( callback != null )
						callback.onRelationChanged();
					
				}
			}
		}.execute();
	}
	
	// アカウントを選択してからユーザをフォローする
	void followFromAnotherAccount( final SavedAccount access_info, final TootAccount who, final RelationChangedCallback callback ){
		AccountPicker.pick( ActMain.this, false, new AccountPicker.AccountPickerCallback() {
			@Override
			public void onAccountPicked( SavedAccount ai ){
				String acct = who.acct;
				if( ! acct.contains( "@" ) ){
					acct = acct + "@" + access_info.host;
				}
				callRemoteFollow( ai, acct, who.locked, callback );
			}
		} );
	}
	
	////////////////////////////////////////
	
	private void callMute( final SavedAccount access_info, final TootAccount who, final boolean bMute, final RelationChangedCallback callback ){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
						
					}
				} );
				client.setAccount( access_info );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				TootApiResult result = client.request( "/api/v1/accounts/" + who.id + ( bMute ? "/mute" : "/unmute" )
					, request_builder );
				if( result != null ){
					if( result.object != null ){
						relation = TootRelationShip.parse( log, result.object );
						Utils.showToast( ActMain.this, false, bMute ? R.string.mute_succeeded : R.string.unmute_succeeded );
						
					}else{
						Utils.showToast( ActMain.this, false, result.error );
					}
				}
				return result;
			}
			
			TootRelationShip relation;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( relation != null ){
//					App1.relationship_map.put( access_info, relation );
//					if( callback != null ) callback.onRelationChanged( relation );
					
					if( callback != null ) callback.onRelationChanged();
					
					if( bMute ){
						for( Column column : pager_adapter.column_list ){
							column.removeStatusByAccount( access_info, who.id );
						}
					}
					showColumnMatchAccount( access_info );
				}
			}
			
		}.execute();
	}
	
	private void callBlock( final SavedAccount access_info, final TootAccount who, final boolean bBlock, final RelationChangedCallback callback ){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
						
					}
				} );
				client.setAccount( access_info );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				TootApiResult result = client.request( "/api/v1/accounts/" + who.id + ( bBlock ? "/block" : "/unblock" )
					, request_builder );
				if( result != null ){
					if( result.object != null ){
						relation = TootRelationShip.parse( log, result.object );
						Utils.showToast( ActMain.this, false, bBlock ? R.string.block_succeeded : R.string.unblock_succeeded );
					}else{
						Utils.showToast( ActMain.this, false, result.error );
					}
				}
				
				return result;
			}
			
			TootRelationShip relation;
			TootAccount remote_who;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				boolean bOK = false;
				if( relation != null ){
					bOK = true;
//					App1.relationship_map.put( access_info, relation );
//					if( callback != null ) callback.onRelationChanged( relation );
					if( callback != null ) callback.onRelationChanged();
				}else if( remote_who != null ){
					bOK = true;
//					App1.relationship_map.addFollowing( access_info, remote_who.id );
//					if( callback != null )
//						callback.onRelationChanged( App1.relationship_map.get( access_info, remote_who.id ) );
					if( callback != null )
						callback.onRelationChanged();
				}
				if( bOK ){
					if( bBlock ){
						for( Column column : pager_adapter.column_list ){
							column.removeStatusByAccount( access_info, who.id );
						}
					}
					showColumnMatchAccount( access_info );
				}
			}
		}.execute();
	}
	
	interface ReportCompleteCallback {
		void onReportComplete( TootApiResult result );
	}
	
	private void callReport( final SavedAccount account, final TootAccount who, final TootStatus status
		, final String comment, final ReportCompleteCallback callback
	){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override
			protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override
					public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override
					public void publishApiProgress( String s ){
						
					}
				} );
				client.setAccount( account );
				
				StringBuilder sb = new StringBuilder();
				
				sb.append( "account_id=" )
					.append( Long.toString( status.account.id ) )
					.append( "&comment=" )
					.append( Uri.encode( comment ) )
				;
				
				if( status != null ){
					sb.append( "&status_ids[]=" )
						.append( Long.toString( status.id ) )
					;
				}
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, sb.toString()
					) );
				TootApiResult result = client.request( "/api/v1/reports", request_builder );
				if( result != null ){
					callback.onReportComplete( result );
				}
				
				return result;
			}
		}.execute();
	}
	
	private void openReportForm( final SavedAccount account, final TootAccount who, final TootStatus status ){
		ReportForm.showReportForm( this, who, status, new ReportForm.ReportFormCallback() {
			@Override
			public void startReport( final Dialog dialog, String comment ){
				callReport( account, who, status, comment, new ReportCompleteCallback() {
					@Override
					public void onReportComplete( TootApiResult result ){
						//noinspection StatementWithEmptyBody
						if( result == null ){
							// cancelled
						}else if( result.object != null ){
							Utils.showToast( ActMain.this, false, R.string.report_completed );
							dialog.dismiss();
						}else{
							// error
							Utils.showToast( ActMain.this, false, result.error );
						}
					}
				} );
			}
		} );
	}
	
	////////////////////////////////////////////////
	
	final RelationChangedCallback follow_comolete_callback = new RelationChangedCallback() {
		//		@Override public void onRelationChanged( TootRelationShip relationship ){
//			Utils.showToast( ActMain.this,false,R.string.follow_succeeded );
//		}
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.follow_succeeded );
		}
	};
	
	// ステータスのmoreメニュー
	public void openStatusMoreMenu( final SavedAccount access_info, final TootStatus status ){
		
		ActionsDialog dialog = new ActionsDialog();
		
		final ArrayList< SavedAccount > tmp_list = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( log ) ){
			if( a.host.equalsIgnoreCase( access_info.host ) ){
				// 同じホストを収集
				tmp_list.add( a );
			}
		}
		if( ! tmp_list.isEmpty() ){
			dialog.addAction( getString( R.string.favourite_from_another_account ), new Runnable() {
				@Override public void run(){
					AccountPicker.pick( ActMain.this, false, tmp_list, new AccountPicker.AccountPickerCallback() {
						@Override public void onAccountPicked( SavedAccount ai ){
							if( ai != null ) performFavourite( ai, status );
						}
					} );
				}
			} );
			dialog.addAction( getString( R.string.boost_from_another_account ), new Runnable() {
				@Override public void run(){
					AccountPicker.pick( ActMain.this, false, tmp_list, new AccountPicker.AccountPickerCallback() {
						@Override public void onAccountPicked( SavedAccount ai ){
							if( ai != null ) performBoost( ai, status, false );
						}
					} );
				}
			} );
		}
		
		dialog.addAction( getString( R.string.follow ), new Runnable() {
			@Override public void run(){
				callFollow( access_info, status.account, true, null );
			}
		} );
		
		dialog.addAction( getString( R.string.follow_from_another_account ), new Runnable() {
			@Override public void run(){
				followFromAnotherAccount( access_info, status.account, follow_comolete_callback );
			}
		} );
		dialog.addAction( getString( R.string.unfollow ), new Runnable() {
			@Override public void run(){
				callFollow( access_info, status.account, false, null );
			}
		} );
		dialog.addAction( getString( R.string.mute ), new Runnable() {
			@Override public void run(){
				callMute( access_info, status.account, true, null );
			}
		} );
		dialog.addAction( getString( R.string.unmute ), new Runnable() {
			@Override public void run(){
				callMute( access_info, status.account, false, null );
			}
		} );
		dialog.addAction( getString( R.string.block ), new Runnable() {
			@Override public void run(){
				callBlock( access_info, status.account, true, null );
			}
		} );
		dialog.addAction( getString( R.string.unblock ), new Runnable() {
			@Override public void run(){
				callBlock( access_info, status.account, false, null );
			}
		} );
		dialog.addAction( getString( R.string.report ), new Runnable() {
			@Override public void run(){
				openReportForm( access_info, status.account, status );
			}
		} );
		dialog.addAction( getString( R.string.open_web_page ), new Runnable() {
			@Override public void run(){
				// 強制的にブラウザで開く
				openChromeTab( access_info, status.url, true );
			}
		} );
		dialog.show( this, null );
		
	}
	
	public void openAccountMoreMenu( final SavedAccount access_info, final TootAccount who ){
		ActionsDialog dialog = new ActionsDialog();
		
		dialog.addAction( getString( R.string.mention ), new Runnable() {
			@Override public void run(){
				performMention( access_info, who );
			}
		} );
		dialog.addAction( getString( R.string.follow ), new Runnable() {
			@Override public void run(){
				callFollow( access_info, who, true, null );
			}
		} );
		dialog.addAction( getString( R.string.follow_from_another_account ), new Runnable() {
			@Override public void run(){
				followFromAnotherAccount( access_info, who, follow_comolete_callback );
			}
		} );
		dialog.addAction( getString( R.string.unfollow ), new Runnable() {
			@Override
			public void run(){
				callFollow( access_info, who, false, null );
			}
		} );
		dialog.addAction( getString( R.string.mute ), new Runnable() {
			@Override public void run(){
				callMute( access_info, who, true, null );
			}
		} );
		dialog.addAction( getString( R.string.unmute ), new Runnable() {
			@Override
			public void run(){
				callMute( access_info, who, false, null );
			}
		} );
		dialog.addAction( getString( R.string.block ), new Runnable() {
			@Override public void run(){
				callBlock( access_info, who, true, null );
			}
		} );
		dialog.addAction( getString( R.string.unblock ), new Runnable() {
			@Override public void run(){
				callBlock( access_info, who, false, null );
			}
		} );
		dialog.addAction( getString( R.string.report ), new Runnable() {
			@Override public void run(){
				openReportForm( access_info, who, null );
			}
		} );
		dialog.show( this, null );
	}
	
	private void openOSSLicense(){
		startActivity( new Intent( this, ActOSSLicense.class ) );
	}
	
	private void openAppAbout(){
		startActivity( new Intent( this, ActAbout.class ) );
	}
	
}
