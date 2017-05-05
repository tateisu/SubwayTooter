package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
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

import com.astuetz.PagerSlidingTabStrip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootApplication;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.dialog.ReportForm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
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
	

//	@Override
//	protected void attachBaseContext(Context newBase) {
//		super.attachBaseContext( CalligraphyContextWrapper.wrap(newBase));
//	}
	
	float density;
	
	SharedPreferences pref;
	Handler handler;
	String posted_acct;
	long posted_status_id;
	
	
	@Override
	protected void onCreate( Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, true );
		this.density = getResources().getDisplayMetrics().density;
		
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		pref = Pref.pref( this );
		handler = new Handler();
		
		initUI();
		
		AlarmService.startCheck( this );
		
		loadColumnList();
	}
	
	@Override protected void onDestroy(){
		super.onDestroy();
	}
	
	@Override protected void onResume(){
		log.d("onResume");
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
		
		if( ! TextUtils.isEmpty( posted_acct ) ){
			int refresh_after_toot = pref.getInt( Pref.KEY_REFRESH_AFTER_TOOT,0);
			if( refresh_after_toot != Pref.RAT_DONT_REFRESH ){
				for( Column column : pager_adapter.column_list ){
					SavedAccount a = column.access_info;
					if( ! Utils.equalsNullable( a.acct,posted_acct )) continue;
					column.startRefreshForPost(posted_status_id,refresh_after_toot);
				}
			}
			posted_acct = null;
		}
		
		if( pager_adapter.getCount() == 0 ){
			llEmpty.setVisibility( View.VISIBLE );
		}
		
		Uri uri = ActOAuthCallback.last_uri.getAndSet( null );
		if( uri != null ){
			handleIntentUri( uri );
		}
	}
	
	StatusButtonsPopup list_item_popup;
	
	void closeListItemPopup(){
		if( list_item_popup != null ){
			list_item_popup.dismiss();
			list_item_popup = null;
		}
	}
	
	@Override protected void onPause(){
		closeListItemPopup();
		
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
	static final int REQUEST_APP_ABOUT = 3;
	static final int REQUEST_CODE_NICKNAME = 4;
	static final int REQUEST_CODE_POST = 5;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		log.d("onActivityResult");
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
			}else if( requestCode == REQUEST_APP_ABOUT ){
				if( data != null ){
					String search = data.getStringExtra( ActAbout.EXTRA_SEARCH );
					if( ! TextUtils.isEmpty( search ) ){
						performAddTimeline( true, Column.TYPE_SEARCH, search, true );
					}
					return;
				}
			}else if( requestCode == REQUEST_CODE_NICKNAME ){
				for( Column column : pager_adapter.column_list ){
					column.onNicknameUpdated();
				}
			}else if( requestCode == REQUEST_CODE_POST ){
				if( data != null ){
					posted_acct = data.getStringExtra( ActPost.EXTRA_POSTED_ACCT );
					posted_status_id = data.getLongExtra( ActPost.EXTRA_POSTED_STATUS_ID,0L );
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override
	public void onBackPressed(){
		
		// メニューが開いていたら閉じる
		DrawerLayout drawer = (DrawerLayout) findViewById( R.id.drawer_layout );
		if( drawer.isDrawerOpen( GravityCompat.START ) ){
			drawer.closeDrawer( GravityCompat.START );
			return;
		}
		
		// カラムが0個ならアプリを終了する
		if( pager_adapter.getCount() == 0 ){
			ActMain.this.finish();
			return;
		}
		
		// カラム設定が開いているならカラム設定を閉じる
		ColumnViewHolder vh = pager_adapter.getColumnViewHolder( pager.getCurrentItem() );
		if( vh.isColumnSettingShown() ){
			vh.closeColumnSetting();
			return;
		}
		
		// カラムが1個以上ある場合は設定に合わせて挙動を変える
		switch( pref.getInt( Pref.KEY_BACK_BUTTON_ACTION, 0 ) ){
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
			dialog.show( this, null );
			break;
		
		case ActAppSetting.BACK_CLOSE_COLUMN:
			Column column = pager_adapter.getColumn( pager.getCurrentItem() );
			if( column != null ){
				if( column.dont_close
					&& pref.getBoolean( Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, false )
					&& pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false )
					){
					ActMain.this.finish();
					return;
				}
				performColumnClose( false, column );
			}
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
	public boolean onNavigationItemSelected( @NonNull MenuItem item ){
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		
		if( id == R.id.nav_account_add ){
			performAccountAdd();
		}else if( id == R.id.nav_add_tl_home ){
			
			performAddTimeline( false, Column.TYPE_HOME );
		}else if( id == R.id.nav_add_tl_local ){
			performAddTimeline( true, Column.TYPE_LOCAL );
		}else if( id == R.id.nav_add_tl_federate ){
			performAddTimeline( true, Column.TYPE_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			performAddTimeline( false, Column.TYPE_FAVOURITES );

//		}else if( id == R.id.nav_add_reports ){
//			performAddTimeline(Column.TYPE_REPORTS );
		}else if( id == R.id.nav_add_statuses ){
			performAddTimeline( false, Column.TYPE_PROFILE );
		}else if( id == R.id.nav_add_notifications ){
			performAddTimeline( false, Column.TYPE_NOTIFICATIONS );
			
		}else if( id == R.id.nav_app_setting ){
			performAppSetting();
		}else if( id == R.id.nav_account_setting ){
			performAccountSetting();
		}else if( id == R.id.nav_column_list ){
			performColumnList();
			
		}else if( id == R.id.nav_add_tl_search ){
			performAddTimeline( true, Column.TYPE_SEARCH, "", false );
			
		}else if( id == R.id.nav_app_about ){
			openAppAbout();
			
		}else if( id == R.id.nav_oss_license ){
			openOSSLicense();
			
		}else if( id == R.id.nav_app_exit ){
			finish();
			
		}else if( id == R.id.nav_add_mutes ){
			performAddTimeline( false, Column.TYPE_MUTES );
			
		}else if( id == R.id.nav_add_blocks ){
			performAddTimeline( false, Column.TYPE_BLOCKS );
			
		}else if( id == R.id.nav_follow_requests ){
			performAddTimeline( false, Column.TYPE_FOLLOW_REQUESTS );
			
		}else if( id == R.id.nav_muted_app ){
			startActivity( new Intent( this, ActMutedApp.class ) );
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
			@Override public void onClick( View view ){
				performTootButton();
			}
		} );
		// floating action button
		FloatingActionButton fabMenu = (FloatingActionButton) findViewById( R.id.fabMenu );
		fabMenu.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View view ){
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
			public void startLogin( final Dialog dialog, final String instance, final boolean bPseudoAccount ){
				
				final ProgressDialog progress = new ProgressDialog( ActMain.this );
				
				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
					
					@Override protected TootApiResult doInBackground( Void... params ){
						TootApiClient api_client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
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
						
						api_client.setInstance( instance );
						
						if( bPseudoAccount ){
							return api_client.checkInstance();
						}else{
							return api_client.authorize1();
							
						}
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						progress.dismiss();
						
						//noinspection StatementWithEmptyBody
						if( result == null ){
							// cancelled.
						}else if( result.error != null ){
							String sv = result.error;
							
							// エラーはブラウザ用URLかもしれない
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
							// 疑似アカウントが追加された
							try{
								String username = "?";
								String full_acct = username + "@" + instance;
								
								JSONObject account_info = new JSONObject();
								account_info.put( "username", username );
								account_info.put( "acct", username );
								
								long row_id = SavedAccount.insert( instance, full_acct, account_info, new JSONObject() );
								SavedAccount account = SavedAccount.loadAccount( log, row_id );
								if( account != null ){
									account.notification_follow = false;
									account.notification_favourite = false;
									account.notification_boost = false;
									account.notification_mention = false;
									account.saveSetting();
									
									Utils.showToast( ActMain.this, false, R.string.server_confirmed );
									addColumn( account, Column.TYPE_LOCAL );
									dialog.dismiss();
								}
							}catch( JSONException ex ){
								ex.printStackTrace();
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
	
	// ActOAuthCallbackで受け取ったUriを処理する
	private void handleIntentUri( @NonNull final Uri uri ){
		
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
						column.startLoading();
					}
				}
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			return;
		}
		
		final ProgressDialog progress = new ProgressDialog( ActMain.this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
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
				
				TootApiResult result = client.authorize2( code );
				if( result != null && result.object != null ){
					// taは使い捨てなので、生成に使うLinkClickContextはダミーで問題ない
					LinkClickContext lcc = new LinkClickContext() {
						@Override public AcctColor findAcctColor( String url ){
							return null;
						}
					};
					this.ta = TootAccount.parse( log, lcc, result.object );
				}
				return result;
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				progress.dismiss();
				
				//noinspection StatementWithEmptyBody
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
								c.startLoading();
							}
						}
					}
				}else{
					// アカウント追加時
					String user = ta.username + "@" + host;
					long row_id = SavedAccount.insert( host, user, result.object, result.token_info );
					SavedAccount account = SavedAccount.loadAccount( log, row_id );
					if( account != null ){
						if( account.locked ){
							account.visibility = TootStatus.VISIBILITY_PRIVATE;
							account.saveSetting();
						}
						Utils.showToast( ActMain.this, false, R.string.account_confirmed );
						AlarmService.startCheck( ActMain.this );
						addColumn( account, Column.TYPE_HOME );
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
			if( done_list.contains( a ) ) continue;
			done_list.add( a );
			a.reloadSetting();
			column.fireShowColumnHeader();
		}
	}
	void reloadAccountSetting(SavedAccount account){
		ArrayList< SavedAccount > done_list = new ArrayList<>();
		for( Column column : pager_adapter.column_list ){
			SavedAccount a = column.access_info;
			if( ! Utils.equalsNullable( a.acct ,account.acct ) ) continue;
			if( done_list.contains( a ) ) continue;
			done_list.add( a );
			a.reloadSetting();
			column.fireShowColumnHeader();
		}
	}
	public void performColumnClose( boolean bConfirm, final Column column ){
		if( column.dont_close ){
			Utils.showToast( this, false, R.string.column_has_dont_close_option );
			return;
		}
		
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
	
	void performOpenUser( SavedAccount access_info, TootAccount user ){
		if( access_info.isPseudo() ){
			Utils.showToast( this, false, R.string.not_available_for_pseudo_account );
		}else{
			addColumn( access_info, Column.TYPE_PROFILE, user.id );
		}
	}
	
	public void performOpenUserFromAnotherAccount( final TootAccount who, ArrayList< SavedAccount > account_list_non_pseudo_same_instance ){
		AccountPicker.pick( this, false, false, account_list_non_pseudo_same_instance, new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( SavedAccount ai ){
				addColumn( ai, Column.TYPE_PROFILE, who.id );
			}
		} );
	}
	
	public void performConversation( SavedAccount access_info, TootStatus status ){
		addColumn( access_info, Column.TYPE_CONVERSATION, status.id );
	}
	
	private void performAddTimeline( boolean bAllowPseudo, final int type, final Object... args ){
		AccountPicker.pick( this, bAllowPseudo, true, new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( SavedAccount ai ){
				switch( type ){
				default:
					addColumn( ai, type, args );
					break;
				
				case Column.TYPE_PROFILE:
					addColumn( ai, type, ai.id );
					break;
				}
			}
		} );
	}
	
	public void openHashTag( SavedAccount access_info, String tag ){
		addColumn( access_info, Column.TYPE_HASHTAG, tag );
	}
	
	public void performMuteApp( @NonNull TootApplication application ){
		MutedApp.save( application.name );
		for( Column column : pager_adapter.column_list ){
			column.removeMuteApp();
		}
		Utils.showToast( ActMain.this, false, R.string.app_was_muted );
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
			
			try{
				// 初回はChrome指定で試す
				
				CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
				builder.setToolbarColor( Styler.getAttributeColor( this, R.attr.colorPrimary ) ).setShowTitle( true );
				CustomTabsIntent customTabsIntent = builder.build();
				customTabsIntent.intent.setComponent( new ComponentName( "com.android.chrome", "com.google.android.apps.chrome.Main" ) );
				customTabsIntent.launchUrl( this, Uri.parse( url ) );
			}catch( Throwable ex2 ){
				log.e( ex2, "openChromeTab: missing chrome. retry to other application." );
				
				// chromeがないなら ResolverActivity でアプリを選択させる
				CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
				builder.setToolbarColor( Styler.getAttributeColor( this, R.attr.colorPrimary ) ).setShowTitle( true );
				CustomTabsIntent customTabsIntent = builder.build();
				customTabsIntent.launchUrl( this, Uri.parse( url ) );
			}
			
		}catch( Throwable ex ){
			// ex.printStackTrace();
			log.e( ex, "openChromeTab failed. url=%s", url );
		}
	}
	
	// 他インスタンスのハッシュタグの表示
	private void openHashTagOtherInstance( final SavedAccount access_info, final String url, String host, final String tag ){
		
		ActionsDialog dialog = new ActionsDialog();
		
		// ブラウザで表示する
		dialog.addAction( getString( R.string.open_web_on_host, host ), new Runnable() {
			@Override public void run(){
				openChromeTab( access_info, url, true );
			}
		} );
		
		// 各アカウント
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( log );
		
		// ソートする
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		
		// 各アカウントで開く選択肢
		for( SavedAccount a : account_list ){
			final SavedAccount _a = a;
			dialog.addAction( getString( R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					openHashTag( _a, tag );
				}
			} );
		}
		
		// TODO: もしカラならログイン無しアカウントで開きたいかも
		
		dialog.show( this, "#" + tag );
		
	}
	
	final HTMLDecoder.LinkClickCallback link_click_listener = new HTMLDecoder.LinkClickCallback() {
		@Override public void onClickLink( LinkClickContext lcc, String url ){
			openChromeTab( (SavedAccount) lcc, url, false );
		}
	};
	
	private void performTootButton(){
		Column c = pager_adapter.getColumn( pager.getCurrentItem() );
		if( c != null ){
			if( c.access_info.isPseudo() ){
				Utils.showToast( this, false, R.string.not_available_for_pseudo_account );
			}else{
				ActPost.open( this, REQUEST_CODE_POST,c.access_info.db_id, "" );
			}
		}
	}
	
	public void performReply( SavedAccount account, TootStatus status ){
		ActPost.open( this, REQUEST_CODE_POST,account.db_id, status );
	}
	
	public void performMention( SavedAccount account, TootAccount who ){
		ActPost.open( this, REQUEST_CODE_POST,account.db_id, "@" + account.getFullAcct( who ) + " " );
	}
	
	public void performMentionFromAnotherAccount( SavedAccount access_info, final TootAccount who, ArrayList< SavedAccount > account_list_non_pseudo ){
		final String initial_text = "@" + access_info.getFullAcct( who ) + " ";
		AccountPicker.pick( this, false, false, account_list_non_pseudo, new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( SavedAccount ai ){
				ActPost.open( ActMain.this, REQUEST_CODE_POST,ai.db_id, initial_text );
			}
		} );
	}
	
	/////////////////////////////////////////////////////////////////////////
	
	private void showColumnMatchAccount( SavedAccount account ){
		for( Column column : pager_adapter.column_list ){
			if( account.acct.equals( column.access_info.acct ) ){
				column.fireShowContent();
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
	
	public void performFavourite( final SavedAccount account, final TootStatus status, final RelationChangedCallback callback ){
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
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
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
					if( callback != null ) callback.onRelationChanged();
					
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
				// 結果に関わらず、更新中状態から復帰させる
				showColumnMatchAccount( account );
			}
			
		}.execute();
		// ファボ表示を更新中にする
		showColumnMatchAccount( account );
	}
	
	/////////////////////////////////////////////////////////////////////////
	// boost
	final HashSet< String > map_busy_boost = new HashSet<>();
	
	boolean isBusyBoost( SavedAccount account, TootStatus status ){
		String busy_key = account.host + ":" + status.id;
		return map_busy_boost.contains( busy_key );
	}
	
	public void performBoost( final SavedAccount access_info, final TootStatus status, boolean bConfirmed, final RelationChangedCallback callback ){
		//
		final String busy_key = access_info.host + ":" + status.id;
		//
		if( map_busy_boost.contains( busy_key ) ){
			Utils.showToast( this, false, R.string.wait_previous_operation );
			return;
		}
		
		if( status.reblogged ){
			// FAVがついているか、FAV操作中はBoostを外せない
			if( isBusyFav( access_info, status ) || status.favourited ){
				Utils.showToast( this, false, R.string.cant_remove_boost_while_favourited );
				return;
			}
		}else if( ! bConfirmed ){
			DlgConfirm.open( this, getString( R.string.confirm_boost_from,AcctColor.getNickname( access_info.acct ) ), new DlgConfirm.Callback() {
				@Override public boolean isConfirmEnabled(){
					return access_info.confirm_boost;
				}
				
				@Override public void setConfirmEnabled( boolean bv ){
					access_info.confirm_boost = bv;
					access_info.saveSetting();
					reloadAccountSetting(access_info);
				}
				
				@Override public void onOK(){
					performBoost( access_info, status, true, callback );
				}
			} );
			return;
		}
		
		//
		map_busy_boost.add( busy_key );
		//
		new AsyncTask< Void, Void, TootApiResult >() {
			final boolean new_state = ! status.reblogged;
			TootStatus new_status;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( access_info );
				
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
						new_status = TootStatus.parse( log, access_info, result.object );
					}
				}
				
				return result;
				
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				map_busy_boost.remove( busy_key );
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
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
						column.findStatus( access_info, new_status.id, new Column.StatusEntryCallback() {
							@Override public void onIterate( TootStatus status ){
								status.reblogged = new_status.reblogged;
								status.reblogs_count = new_status.reblogs_count;
							}
						} );
					}
					if( callback != null ) callback.onRelationChanged();
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
				showColumnMatchAccount( access_info );
			}
			
		}.execute();
		
		showColumnMatchAccount( access_info );
	}
	
	////////////////////////////////////////
	
	private void performAccountSetting(){
		AccountPicker.pick( this, true, true, new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( SavedAccount ai ){
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
		JSONArray array = encodeColumnList();
		App1.saveColumnList(this,ActColumnList.TMP_FILE_COLUMN_LIST,array);
		Intent intent = new Intent( this, ActColumnList.class );
		intent.putExtra( ActColumnList.EXTRA_SELECTION, pager.getCurrentItem() );
		startActivityForResult( intent, REQUEST_CODE_COLUMN_LIST );
	}

//	private void dumpColumnList(){
//		for( int i = 0, ie = pager_adapter.column_list.size() ; i < ie ; ++ i ){
//			Column column = pager_adapter.column_list.get( i );
//			log.d( "dumpColumnList [%s]%s %s", i, column.access_info.acct, column.getColumnName( true ) );
//		}
//	}
	
	static final String FILE_COLUMN_LIST = "column_list";
	
	void saveColumnList(){
		JSONArray array = encodeColumnList();
		App1.saveColumnList(this,FILE_COLUMN_LIST,array);
		
	}
	
	private void loadColumnList(){
		JSONArray array = App1.loadColumnList(this,FILE_COLUMN_LIST);
		if( array != null ){
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				try{
					JSONObject src = array.optJSONObject( i );
					Column col = new Column( ActMain.this, src );
					pager_adapter.addColumn( pager, col, pager_adapter.getCount() );
				}catch( Throwable ex ){
					ex.printStackTrace();
				}
			}
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
	
	private static class RelationResult {
		TootApiResult result;
		TootRelationShip relation;
	}
	
	// relationshipを取得
	@NonNull
	RelationResult loadRelation1( TootApiClient client, SavedAccount access_info, long who_id ){
		RelationResult rr = new RelationResult();
		TootApiResult r2 = rr.result = client.request( "/api/v1/accounts/relationships?id=" + who_id );
		if( r2 != null && r2.array != null ){
			TootRelationShip.List list = TootRelationShip.parseList( log, r2.array );
			if( ! list.isEmpty() ){
				TootRelationShip item = rr.relation = list.get( 0 );
				long now = System.currentTimeMillis();
				UserRelation.save1( now, access_info.db_id, item );
			}
		}
		return rr;
	}
	
	void callFollow(
		final SavedAccount access_info
		, final TootAccount who
		, final boolean bFollow
		, boolean bConfirmed
		, final RelationChangedCallback callback
	){
		if( access_info.isMe( who )){
			Utils.showToast( this,false,R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmed ){
			if( bFollow && who.locked ){
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_request_who_from,who.display_name ,AcctColor.getNickname( access_info.acct) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							reloadAccountSetting(access_info);
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callFollow( access_info, who, bFollow, true, callback );
						}
					}
				);
				return;
			}else if( bFollow ){
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_who_from,who.display_name ,AcctColor.getNickname( access_info.acct) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow = bv;
							access_info.saveSetting();
							reloadAccountSetting(access_info);
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callFollow( access_info, who, bFollow, true, callback );
						}
					}
				);
				return;
			}else{
				DlgConfirm.open( this
					, getString( R.string.confirm_unfollow_who_from, who.display_name ,AcctColor.getNickname( access_info.acct))
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_unfollow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_unfollow = bv;
							access_info.saveSetting();
							reloadAccountSetting(access_info);
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callFollow( access_info, who, bFollow, true, callback );
						}
					}
				);
				return;
			}
		}
		
		new AsyncTask< Void, Void, TootApiResult >() {
			@Override protected TootApiResult doInBackground( Void... params ){
				
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
					}
				} );
				
				client.setAccount( access_info );
				
				TootApiResult result;
				
				if( bFollow & who.acct.contains( "@" ) ){
					
					// リモートフォローする
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "uri=" + Uri.encode( who.acct )
						) );
					
					result = client.request( "/api/v1/follows", request_builder );
					if( result != null ){
						if( result.object != null ){
							TootAccount remote_who = TootAccount.parse( log, access_info, result.object );
							if( remote_who != null ){
								RelationResult rr = loadRelation1( client, access_info, remote_who.id );
								result = rr.result;
								relation = rr.relation;
							}
						}
					}
					
				}else{
					
					// ローカルでフォロー/アンフォローする
					
					Request.Builder request_builder = new Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "" // 空データ
						) );
					result = client.request( "/api/v1/accounts/" + who.id
							+ ( bFollow ? "/follow" : "/unfollow" )
						, request_builder );
					if( result != null ){
						if( result.object != null ){
							relation = TootRelationShip.parse( log, result.object );
							if( relation != null ){
								long now = System.currentTimeMillis();
								UserRelation.save1( now, access_info.db_id, relation );
							}
						}
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
//				if( relation != null ){
//			     	App1.relationship_map.put( access_info, relation );
//					if( callback != null ) callback.onRelationChanged( relation );
//				}else if( remote_who != null ){
//					App1.relationship_map.addFollowing( access_info, remote_who.id );
//					if( callback != null )
//						callback.onRelationChanged( App1.relationship_map.get( access_info, remote_who.id ) );
//				}
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( relation != null ){
					
					showColumnMatchAccount( access_info );
					
					if( bFollow && relation.requested ){
						// 鍵付きアカウントにフォローリクエストを申請した状態
						Utils.showToast( ActMain.this, false, R.string.follow_requested );
					}else if( !bFollow && relation.requested ){
						Utils.showToast( ActMain.this, false, R.string.follow_request_cant_remove_by_sender );
					}else{
						// ローカル操作成功、もしくはリモートフォロー成功
						if( callback != null ) callback.onRelationChanged();
					}
					
				}else if( bFollow && who.locked && result.response != null && result.response.code() == 422 ){
					Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
					
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.execute();
	}
	
	// acct で指定したユーザをリモートフォローする
	void callRemoteFollow( final SavedAccount access_info
		, final String acct, final boolean locked, boolean bConfirmed, final RelationChangedCallback callback
	){
		if( access_info.isMe( acct )){
			Utils.showToast( this,false,R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmed ){
			if( locked ){
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_request_who_from, AcctColor.getNickname( acct ) , AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							reloadAccountSetting(access_info);
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callRemoteFollow( access_info, acct, locked, true, callback );
						}
					}
				);
				return;
			}else{
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_who_from, AcctColor.getNickname( acct ) , AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow = bv;
							access_info.saveSetting();
							reloadAccountSetting();
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callRemoteFollow( access_info, acct, locked, true, callback );
						}
					}
				);
				return;
			}
		}
		
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
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
						TootAccount remote_who = TootAccount.parse( log, access_info, result.object );
						if( remote_who != null ){
							RelationResult rr = loadRelation1( client, access_info, remote_who.id );
							result = rr.result;
							relation = rr.relation;
						}
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
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( relation != null ){
					
					showColumnMatchAccount( access_info );
					
					if( callback != null ) callback.onRelationChanged();
					
				}else if( locked && result.response.code() == 422 ){
					Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.execute();
	}
	
	////////////////////////////////////////
	
	void callMute( final SavedAccount access_info, final TootAccount who, final boolean bMute, final RelationChangedCallback callback ){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
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
						if( relation != null ){
							long now = System.currentTimeMillis();
							UserRelation.save1( now, access_info.db_id, relation );
						}
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
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( relation != null ){
					Utils.showToast( ActMain.this, false, bMute ? R.string.mute_succeeded : R.string.unmute_succeeded );
					
					if( bMute ){
						for( Column column : pager_adapter.column_list ){
							column.removeStatusByAccount( access_info, who.id );
						}
					}else{
						for( Column column : pager_adapter.column_list ){
							column.removeFromMuteList( access_info, who.id );
						}
					}
					
					if( callback != null ) callback.onRelationChanged();
					
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
			
		}.execute();
	}
	
	void callBlock( final SavedAccount access_info, final TootAccount who, final boolean bBlock, final RelationChangedCallback callback ){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
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
						if( relation != null ){
							long now = System.currentTimeMillis();
							UserRelation.save1( now, access_info.db_id, relation );
						}
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
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( relation != null ){
					
					for( Column column : pager_adapter.column_list ){
						if( bBlock ){
							column.removeStatusByAccount( access_info, who.id );
						}else{
							column.removeFromBlockList( access_info, who.id );
						}
					}
					
					Utils.showToast( ActMain.this, false, bBlock ? R.string.block_succeeded : R.string.unblock_succeeded );
					
					if( callback != null ) callback.onRelationChanged();
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.execute();
	}
	
	void callFollowRequestAuthorize( final SavedAccount access_info
		, final TootAccount who, final boolean bAllow
	){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
					}
				} );
				client.setAccount( access_info );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				
				return client.request(
					"/api/v1/follow_requests/" + who.id + ( bAllow ? "/authorize" : "/reject" )
					, request_builder );
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( result.object != null ){
					
					for( Column column : pager_adapter.column_list ){
						column.removeFollowRequest( access_info, who.id );
					}
					
					Utils.showToast( ActMain.this, false, ( bAllow ? R.string.follow_request_authorized : R.string.follow_request_rejected ), who.display_name );
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.execute();
	}
	
	void deleteStatus( final SavedAccount access_info, final long status_id ){
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
				
				Request.Builder request_builder = new Request.Builder().delete(); // method is delete
				
				return client.request( "/api/v1/statuses/" + status_id, request_builder );
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				//noinspection StatementWithEmptyBody
				if( result == null ){
					//cancelled.
				}else if( result.object != null ){
					Utils.showToast( ActMain.this, false, R.string.delete_succeeded );
					for( Column column : pager_adapter.column_list ){
						column.removeStatus( access_info, status_id );
					}
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.execute();
	}
	
	interface ReportCompleteCallback {
		void onReportComplete( TootApiResult result );
		
	}
	
	private void callReport(
		@NonNull final SavedAccount access_info
		
		, @NonNull final TootAccount who
		, @NonNull final TootStatus status
		, @NonNull final String comment
		, final ReportCompleteCallback callback
	){
		new AsyncTask< Void, Void, TootApiResult >() {
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
					}
				} );
				
				client.setAccount( access_info );
				String sb = "account_id=" + Long.toString( who.id )
					+ "&comment=" + Uri.encode( comment )
					+ "&status_ids[]=" + Long.toString( status.id );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, sb
					) );
				
				return client.request( "/api/v1/reports", request_builder );
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( result.object != null ){
					callback.onReportComplete( result );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
			}
			
		}.execute();
	}
	
	void openReportForm( @NonNull final SavedAccount account, @NonNull final TootAccount who, @NonNull final TootStatus status ){
		ReportForm.showReportForm( this, who, status, new ReportForm.ReportFormCallback() {
			
			@Override public void startReport( final Dialog dialog, String comment ){
				
				// レポートの送信を開始する
				callReport( account, who, status, comment, new ReportCompleteCallback() {
					@Override public void onReportComplete( TootApiResult result ){
						
						// 成功したらダイアログを閉じる
						dialog.dismiss();
						Utils.showToast( ActMain.this, false, R.string.report_completed );
					}
				} );
			}
		} );
	}
	
	////////////////////////////////////////////////
	
	void sendStatus( SavedAccount access_info, TootStatus status ){
		try{
			StringBuilder sb = new StringBuilder();
			sb.append( getString( R.string.send_header_url ) );
			sb.append( ": " );
			sb.append( status.url );
			sb.append( "\n" );
			sb.append( getString( R.string.send_header_date ) );
			sb.append( ": " );
			sb.append( TootStatus.formatTime( status.time_created_at ) );
			sb.append( "\n" );
			sb.append( getString( R.string.send_header_from_acct ) );
			sb.append( ": " );
			sb.append( access_info.getFullAcct( status.account ) );
			sb.append( "\n" );
			sb.append( getString( R.string.send_header_from_name ) );
			sb.append( ": " );
			sb.append( status.account.display_name );
			sb.append( "\n" );
			if( ! TextUtils.isEmpty( status.spoiler_text ) ){
				sb.append( getString( R.string.send_header_content_warning ) );
				sb.append( ": " );
				sb.append( HTMLDecoder.decodeHTMLForClipboard( access_info, status.spoiler_text ) );
				sb.append( "\n" );
			}
			sb.append( "\n" );
			sb.append( HTMLDecoder.decodeHTMLForClipboard( access_info, status.content ) );
			
			Intent intent = new Intent();
			intent.setAction( Intent.ACTION_SEND );
			intent.setType( "text/plain" );
			intent.putExtra( Intent.EXTRA_TEXT, sb.toString() );
			startActivity( intent );
			
		}catch( Throwable ex ){
			log.e( ex, "sendStatus failed." );
			ex.printStackTrace();
			Utils.showToast( this, ex, "sendStatus failed." );
		}
	}
	
	////////////////////////////////////////////////
	
	final RelationChangedCallback follow_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.follow_succeeded );
		}
	};
	final RelationChangedCallback unfollow_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.unfollow_succeeded );
		}
	};
	final ActMain.RelationChangedCallback favourite_complete_callback = new ActMain.RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.favourite_succeeded );
		}
	};
	final ActMain.RelationChangedCallback boost_complete_callback = new ActMain.RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.boost_succeeded );
		}
	};

	
	private void openOSSLicense(){
		startActivity( new Intent( this, ActOSSLicense.class ) );
	}
	
	private void openAppAbout(){
		startActivityForResult( new Intent( this, ActAbout.class ), REQUEST_APP_ABOUT );
	}
	
	public void deleteNotification( boolean bConfirmed, final SavedAccount target_account ){
		if( ! bConfirmed ){
			new AlertDialog.Builder( this )
				.setMessage( R.string.confirm_delete_notification )
				.setNegativeButton( R.string.cancel, null )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick( DialogInterface dialog, int which ){
						deleteNotification( true, target_account );
					}
				} )
				.show();
			return;
		}
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
				client.setAccount( target_account );
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "" // 空データ
					) );
				return client.request( "/api/v1/notifications/clear", request_builder );
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				//noinspection StatementWithEmptyBody
				if( result == null ){
					//cancelled.
				}else if( result.object != null ){
					// ok. empty object will be returned.
					for( Column column : pager_adapter.column_list ){
						if( column.column_type == Column.TYPE_NOTIFICATIONS
							&& column.access_info.acct.equals( target_account.acct )
							){
							column.removeNotifications();
						}
					}
					Utils.showToast( ActMain.this, false, R.string.delete_succeeded );
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
			
		}.execute();
	}
	
}
