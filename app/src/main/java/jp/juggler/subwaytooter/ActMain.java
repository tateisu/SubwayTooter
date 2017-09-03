package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.JsonReader;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootApplication;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.api_msp.entity.MSPToot;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgAccessToken;
import jp.juggler.subwaytooter.dialog.DlgConfirm;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.dialog.ReportForm;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyClickableSpan;
import jp.juggler.subwaytooter.util.PostHelper;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.ColumnStripLinearLayout;
import jp.juggler.subwaytooter.view.GravitySnapHelper;
import jp.juggler.subwaytooter.view.MyEditText;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ActMain extends AppCompatActivity
	implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener, ViewPager.OnPageChangeListener, Column.Callback, DrawerLayout.DrawerListener
{
	public static final LogCategory log = new LogCategory( "ActMain" );
	
	//	@Override
	//	protected void attachBaseContext(Context newBase) {
	//		super.attachBaseContext( CalligraphyContextWrapper.wrap(newBase));
	//	}
	
	public float density;
	int acct_pad_lr;
	
	SharedPreferences pref;
	Handler handler;
	AppState app_state;
	
	// onActivityResultで設定されてonResumeで消化される
	// 状態保存の必要なし
	String posted_acct;
	long posted_status_id;
	
	float timeline_font_size_sp = Float.NaN;
	float acct_font_size_sp = Float.NaN;
	
	float validateFloat( float fv ){
		if( Float.isNaN( fv ) ) return fv;
		if( fv < 1f ) fv = 1f;
		return fv;
	}
	
	@Override protected void onCreate( Bundle savedInstanceState ){
		log.d( "onCreate" );
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, true );
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		handler = new Handler();
		app_state = App1.getAppState( this );
		pref = App1.pref;
		
		this.density = app_state.density;
		this.acct_pad_lr = (int) ( 0.5f + 4f * density );
		
		timeline_font_size_sp = validateFloat( pref.getFloat( Pref.KEY_TIMELINE_FONT_SIZE, Float.NaN ) );
		acct_font_size_sp = validateFloat( pref.getFloat( Pref.KEY_ACCT_FONT_SIZE, Float.NaN ) );
		
		initUI();
		
		updateColumnStrip();
		
		if( ! app_state.column_list.isEmpty() ){
			
			// 前回最後に表示していたカラムの位置にスクロールする
			int column_pos = pref.getInt( Pref.KEY_LAST_COLUMN_POS, - 1 );
			if( column_pos >= 0 && column_pos < app_state.column_list.size() ){
				scrollToColumn( column_pos, true );
			}
			
			// 表示位置に合わせたイベントを発行
			if( pager_adapter != null ){
				onPageSelected( pager.getCurrentItem() );
			}else{
				resizeColumnWidth();
			}
		}
		
		PollingService.queueUpdateNotification( this );
		
		if( savedInstanceState != null && sent_intent2 != null ){
			handleSentIntent( sent_intent2 );
		}
	}
	
	@Override protected void onDestroy(){
		log.d( "onDestroy" );
		super.onDestroy();
		post_helper.onDestroy();
		
		// このアクティビティに関連する ColumnViewHolder への参照を全カラムから除去する
		for( Column c : app_state.column_list ){
			c.removeColumnViewHolderByActivity( this );
		}
	}
	
	static final String STATE_CURRENT_PAGE = "current_page";
	
	@Override protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		
		if( pager_adapter != null ){
			outState.putInt( STATE_CURRENT_PAGE, pager.getCurrentItem() );
		}else{
			int ve = tablet_layout_manager.findLastVisibleItemPosition();
			if( ve != RecyclerView.NO_POSITION ){
				outState.putInt( STATE_CURRENT_PAGE, ve );
			}
		}
	}
	
	@Override protected void onRestoreInstanceState( Bundle savedInstanceState ){
		super.onRestoreInstanceState( savedInstanceState );
		int pos = savedInstanceState.getInt( STATE_CURRENT_PAGE );
		if( pos > 0 && pos < app_state.column_list.size() ){
			if( pager_adapter != null ){
				pager.setCurrentItem( pos );
			}else{
				tablet_layout_manager.smoothScrollToPosition( tablet_pager, null, pos );
			}
		}
	}
	
	boolean bResume;
	
	@Override public boolean isActivityResume(){
		return bResume;
	}
	
	@Override protected void onResume(){
		bResume = true;
		log.d( "onResume" );
		super.onResume();
		
		MyClickableSpan.link_callback = link_click_listener;
		
		if( pref.getBoolean( Pref.KEY_DONT_SCREEN_OFF, false ) ){
			getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		}else{
			getWindow().clearFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		}
		
		// アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
		{
			
			ArrayList< Integer > new_order = new ArrayList<>();
			boolean bRemoved = false;
			for( int i = 0, ie = app_state.column_list.size() ; i < ie ; ++ i ){
				Column column = app_state.column_list.get( i );
				
				if( column.access_info.isNA() ){
					// 検索カラムはアカウント削除とか無関係
				}else{
					SavedAccount sa = SavedAccount.loadAccount( ActMain.this, log, column.access_info.db_id );
					if( sa == null ){
						bRemoved = true;
					}else{
						new_order.add( i );
					}
				}
			}
			if( bRemoved ){
				setOrder( new_order );
			}
		}
		
		// 各カラムのアカウント設定を読み直す
		reloadAccountSetting();
		
		refreshAfterPost();
		
		Uri uri = ActCallback.last_uri.getAndSet( null );
		if( uri != null ){
			handleIntentUri( uri );
		}
		
		Intent intent = ActCallback.sent_intent.getAndSet( null );
		if( intent != null ){
			handleSentIntent( intent );
		}
		
		for( Column column : app_state.column_list ){
			column.onResume( this );
		}
		
		updateColumnStripSelection( - 1, - 1f );
		
		proc_updateRelativeTime.run();
	}
	
	void refreshAfterPost(){
		if( ! TextUtils.isEmpty( posted_acct ) ){
			int refresh_after_toot = pref.getInt( Pref.KEY_REFRESH_AFTER_TOOT, 0 );
			if( refresh_after_toot != Pref.RAT_DONT_REFRESH ){
				for( Column column : app_state.column_list ){
					SavedAccount a = column.access_info;
					if( ! Utils.equalsNullable( a.acct, posted_acct ) ) continue;
					column.startRefreshForPost( posted_status_id, refresh_after_toot );
				}
			}
			posted_acct = null;
		}
	}
	
	static Intent sent_intent2;
	
	private void handleSentIntent( final Intent intent ){
		sent_intent2 = intent;
		AccountPicker.pick( this
			, false
			, true
			, getString( R.string.account_picker_toot )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					sent_intent2 = null;
					ActPost.open( ActMain.this, REQUEST_CODE_POST, ai.db_id, intent );
				}
			}
			, new DialogInterface.OnDismissListener() {
				@Override public void onDismiss( DialogInterface dialog ){
					sent_intent2 = null;
				}
			}
		);
	}
	
	// 画面上のUI操作で生成されて
	// onPause,onPageDestroy 等のタイミングで閉じられる
	// 状態保存の必要なし
	StatusButtonsPopup list_item_popup;
	
	void closeListItemPopup(){
		if( list_item_popup != null ){
			try{
				list_item_popup.dismiss();
			}catch( Throwable ignored ){
			}
			list_item_popup = null;
		}
	}
	
	@Override protected void onPause(){
		bResume = false;
		
		handler.removeCallbacks( proc_updateRelativeTime );
		
		post_helper.closeAcctPopup();
		
		// 最後に表示していたカラムの位置
		int column_pos;
		if( pager_adapter != null ){
			column_pos = pager.getCurrentItem();
		}else{
			column_pos = tablet_layout_manager.findFirstVisibleItemPosition();
		}
		pref.edit().putInt( Pref.KEY_LAST_COLUMN_POS, column_pos ).apply();
		
		closeListItemPopup();
		
		app_state.stream_reader.onPause();
		
		MyClickableSpan.link_callback = null;
		super.onPause();
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnMenu:
			if( ! drawer.isDrawerOpen( Gravity.START ) ){
				drawer.openDrawer( Gravity.START );
			}
			break;
		
		case R.id.btnToot:
			performTootButton();
			break;
		
		case R.id.btnQuickToot:
			performQuickPost( null );
			break;
		}
	}
	
	private void performQuickPost( SavedAccount account ){
		
		if( account == null ){
			if( pager_adapter != null ){
				Column c = app_state.column_list.get( pager.getCurrentItem() );
				if( ! c.access_info.isPseudo() ){
					account = c.access_info;
				}
			}
			if( account == null ){
				AccountPicker.pick( this, false, true, getString( R.string.account_picker_toot ), new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						performQuickPost( ai );
					}
				} );
				return;
			}
		}
		
		String visibility = account.visibility;
		if( TextUtils.isEmpty( visibility ) ){
			visibility = TootStatus.VISIBILITY_PUBLIC;
		}
		
		post_helper.content = etQuickToot.getText().toString().trim();
		post_helper.spoiler_text = null;
		post_helper.visibility = visibility;
		post_helper.bNSFW = false;
		post_helper.in_reply_to_id = - 1L;
		post_helper.attachment_list = null;
		
		Utils.hideKeyboard( this, etQuickToot );
		post_helper.post( account, false, false, new PostHelper.Callback() {
			@Override public void onPostComplete( SavedAccount target_account, TootStatus status ){
				etQuickToot.setText( "" );
				posted_acct = target_account.acct;
				posted_status_id = status.id;
				refreshAfterPost();
			}
		} );
	}
	
	@Override
	public void onPageScrolled( int position, float positionOffset, int positionOffsetPixels ){
		updateColumnStripSelection( position, positionOffset );
	}
	
	@Override public void onPageSelected( final int position ){
		handler.post( new Runnable() {
			@Override public void run(){
				if( position >= 0 && position < app_state.column_list.size() ){
					Column column = app_state.column_list.get( position );
					if( ! column.bFirstInitialized ){
						column.startLoading();
					}
					scrollColumnStrip( position );
				}
			}
		} );
		
	}
	
	@Override public void onPageScrollStateChanged( int state ){
		
	}
	
	boolean isOrderChanged( ArrayList< Integer > new_order ){
		if( new_order.size() != app_state.column_list.size() ) return true;
		for( int i = 0, ie = new_order.size() ; i < ie ; ++ i ){
			if( new_order.get( i ) != i ) return true;
		}
		return false;
	}
	
	// リザルト
	static final int RESULT_APP_DATA_IMPORT = RESULT_FIRST_USER;
	
	// リクエスト
	static final int REQUEST_CODE_COLUMN_LIST = 1;
	static final int REQUEST_CODE_ACCOUNT_SETTING = 2;
	static final int REQUEST_APP_ABOUT = 3;
	static final int REQUEST_CODE_NICKNAME = 4;
	static final int REQUEST_CODE_POST = 5;
	static final int REQUEST_CODE_COLUMN_COLOR = 6;
	static final int REQUEST_CODE_APP_SETTING = 7;
	static final int REQUEST_CODE_TEXT = 8;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		log.d( "onActivityResult" );
		if( resultCode == RESULT_OK ){
			if( requestCode == REQUEST_CODE_COLUMN_LIST ){
				if( data != null ){
					ArrayList< Integer > order = data.getIntegerArrayListExtra( ActColumnList.EXTRA_ORDER );
					if( order != null && isOrderChanged( order ) ){
						setOrder( order );
					}
					
					if( ! app_state.column_list.isEmpty() ){
						int select = data.getIntExtra( ActColumnList.EXTRA_SELECTION, - 1 );
						if( 0 <= select && select < app_state.column_list.size() ){
							scrollToColumn( select, false );
						}
					}
				}
				
			}else if( requestCode == REQUEST_APP_ABOUT ){
				if( data != null ){
					String search = data.getStringExtra( ActAbout.EXTRA_SEARCH );
					if( ! TextUtils.isEmpty( search ) ){
						performAddTimeline( getDefaultInsertPosition(), true, Column.TYPE_SEARCH, search, true );
					}
					return;
				}
			}else if( requestCode == REQUEST_CODE_NICKNAME ){
				
				updateColumnStrip();
				
				for( Column column : app_state.column_list ){
					column.fireShowColumnHeader();
				}
				
			}else if( requestCode == REQUEST_CODE_POST ){
				if( data != null ){
					etQuickToot.setText( "" );
					posted_acct = data.getStringExtra( ActPost.EXTRA_POSTED_ACCT );
					posted_status_id = data.getLongExtra( ActPost.EXTRA_POSTED_STATUS_ID, 0L );
				}
				
			}else if( requestCode == REQUEST_CODE_COLUMN_COLOR ){
				if( data != null ){
					app_state.saveColumnList();
					int idx = data.getIntExtra( ActColumnCustomize.EXTRA_COLUMN_INDEX, 0 );
					if( idx >= 0 && idx < app_state.column_list.size() ){
						app_state.column_list.get( idx ).fireColumnColor();
						app_state.column_list.get( idx ).fireShowContent();
					}
					updateColumnStrip();
				}
			}
		}
		
		if( requestCode == REQUEST_CODE_ACCOUNT_SETTING ){
			updateColumnStrip();
			
			for( Column column : app_state.column_list ){
				column.fireShowColumnHeader();
			}
			
			if( resultCode == RESULT_OK && data != null ){
				startAccessTokenUpdate( data );
			}else if( resultCode == ActAccountSetting.RESULT_INPUT_ACCESS_TOKEN && data != null ){
				long db_id = data.getLongExtra( ActAccountSetting.EXTRA_DB_ID, - 1L );
				checkAccessToken2( db_id );
			}
		}else if( requestCode == REQUEST_CODE_APP_SETTING ){
			showFooterColor();
			
			if( resultCode == RESULT_APP_DATA_IMPORT ){
				if( data != null ){
					importAppData( data.getData() );
				}
			}
			
		}else if( requestCode == REQUEST_CODE_TEXT ){
			if( resultCode == ActText.RESULT_TOOT_SEARCH ){
				String text = data.getStringExtra( Intent.EXTRA_TEXT );
				addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_PORTAL, text );
			}
		}
		
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	@Override
	public void onBackPressed(){
		
		// メニューが開いていたら閉じる
		DrawerLayout drawer = findViewById( R.id.drawer_layout );
		if( drawer.isDrawerOpen( GravityCompat.START ) ){
			drawer.closeDrawer( GravityCompat.START );
			return;
		}
		
		// カラムが0個ならアプリを終了する
		if( app_state.column_list.isEmpty() ){
			ActMain.this.finish();
			return;
		}
		
		// カラム設定が開いているならカラム設定を閉じる
		if( closeColumnSetting() ){
			return;
		}
		
		// カラムが1個以上ある場合は設定に合わせて挙動を変える
		switch( pref.getInt( Pref.KEY_BACK_BUTTON_ACTION, 0 ) ){
		default:
		case ActAppSetting.BACK_ASK_ALWAYS:
			ActionsDialog dialog = new ActionsDialog();
			
			Column current_column = null;
			if( pager_adapter != null ){
				current_column = app_state.column_list.get( pager.getCurrentItem() );
			}else{
				final int vs = tablet_layout_manager.findFirstVisibleItemPosition();
				final int ve = tablet_layout_manager.findLastVisibleItemPosition();
				if( vs == ve && vs != RecyclerView.NO_POSITION ){
					current_column = app_state.column_list.get( vs );
				}
			}
			if( current_column != null && ! current_column.dont_close ){
				final Column _column = current_column;
				dialog.addAction( getString( R.string.close_column ), new Runnable() {
					@Override public void run(){
						closeColumn( true, _column );
					}
				} );
			}
			
			dialog.addAction( getString( R.string.open_column_list ), new Runnable() {
				@Override public void run(){
					openColumnList();
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
			Column column = null;
			if( pager_adapter != null ){
				column = pager_adapter.getColumn( pager.getCurrentItem() );
			}else{
				final int vs = tablet_layout_manager.findFirstVisibleItemPosition();
				final int ve = tablet_layout_manager.findLastVisibleItemPosition();
				if( vs == ve && vs != RecyclerView.NO_POSITION ){
					column = app_state.column_list.get( vs );
				}else{
					Utils.showToast( this, false, getString( R.string.cant_close_column_by_back_button_when_multiple_column_shown ) );
				}
			}
			if( column != null ){
				if( column.dont_close
					&& pref.getBoolean( Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, false )
					&& pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false )
					){
					ActMain.this.finish();
					return;
				}
				closeColumn( false, column );
			}
			break;
		
		case ActAppSetting.BACK_EXIT_APP:
			ActMain.this.finish();
			break;
		
		case ActAppSetting.BACK_OPEN_COLUMN_LIST:
			openColumnList();
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
			
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_HOME );
		}else if( id == R.id.nav_add_tl_local ){
			performAddTimeline( getDefaultInsertPosition(), true, Column.TYPE_LOCAL );
		}else if( id == R.id.nav_add_tl_federate ){
			performAddTimeline( getDefaultInsertPosition(), true, Column.TYPE_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_FAVOURITES );
			
			//		}else if( id == R.id.nav_add_reports ){
			//			performAddTimeline(Column.TYPE_REPORTS );
		}else if( id == R.id.nav_add_statuses ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_PROFILE );
		}else if( id == R.id.nav_add_notifications ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_NOTIFICATIONS );
			
		}else if( id == R.id.nav_app_setting ){
			ActAppSetting.open( this, REQUEST_CODE_APP_SETTING );
			
		}else if( id == R.id.nav_account_setting ){
			performAccountSetting();
			
		}else if( id == R.id.nav_column_list ){
			openColumnList();
			
		}else if( id == R.id.nav_add_tl_search ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_SEARCH, "", false );
			
		}else if( id == R.id.nav_app_about ){
			openAppAbout();
			
		}else if( id == R.id.nav_oss_license ){
			openOSSLicense();
			
		}else if( id == R.id.nav_app_exit ){
			finish();
			
		}else if( id == R.id.nav_add_mutes ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_MUTES );
			
		}else if( id == R.id.nav_add_blocks ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_BLOCKS );
			
		}else if( id == R.id.nav_add_domain_blocks ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_DOMAIN_BLOCKS );
			
		}else if( id == R.id.nav_follow_requests ){
			performAddTimeline( getDefaultInsertPosition(), false, Column.TYPE_FOLLOW_REQUESTS );
			
		}else if( id == R.id.nav_muted_app ){
			startActivity( new Intent( this, ActMutedApp.class ) );
			
		}else if( id == R.id.nav_muted_word ){
			startActivity( new Intent( this, ActMutedWord.class ) );
			
		}else if( id == R.id.mastodon_search_portal ){
			addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_PORTAL, "" );
			
			//		}else if( id == R.id.nav_translation ){
			//			Intent intent = new Intent(this, TransCommuActivity.class);
			//			intent.putExtra(TransCommuActivity.APPLICATION_CODE_EXTRA, "FJlDoBKitg");
			//			this.startActivity(intent);
			//
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
		
		DrawerLayout drawer = findViewById( R.id.drawer_layout );
		drawer.closeDrawer( GravityCompat.START );
		return true;
	}
	
	ViewPager pager;
	ColumnPagerAdapter pager_adapter;
	View llEmpty;
	DrawerLayout drawer;
	ColumnStripLinearLayout llColumnStrip;
	HorizontalScrollView svColumnStrip;
	ImageButton btnMenu;
	ImageButton btnToot;
	View vFooterDivider1;
	View vFooterDivider2;
	
	RecyclerView tablet_pager;
	TabletColumnPagerAdapter tablet_pager_adapter;
	LinearLayoutManager tablet_layout_manager;
	GravitySnapHelper tablet_snap_helper;
	
	static final int COLUMN_WIDTH_MIN_DP = 300;
	
	Typeface timeline_font;
	Typeface timeline_font_bold;
	
	boolean dont_crop_media_thumbnail;
	
	View llQuickTootBar;
	MyEditText etQuickToot;
	ImageButton btnQuickToot;
	PostHelper post_helper;
	
	void initUI(){
		setContentView( R.layout.act_main );
		
		dont_crop_media_thumbnail = pref.getBoolean( Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL, false );

		String sv = pref.getString( Pref.KEY_TIMELINE_FONT, null );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				timeline_font = Typeface.createFromFile( sv );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		sv = pref.getString( Pref.KEY_TIMELINE_FONT_BOLD, null );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				timeline_font_bold = Typeface.createFromFile( sv );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}else if( timeline_font != null ){
			try{
				timeline_font_bold = Typeface.create( timeline_font ,Typeface.BOLD );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}

		
		llEmpty = findViewById( R.id.llEmpty );
		
		//		// toolbar
		//		Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
		//		setSupportActionBar( toolbar );
		
		// navigation drawer
		drawer = findViewById( R.id.drawer_layout );
		//		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
		//			this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close );
		drawer.addDrawerListener( this );
		//		toggle.syncState();
		
		NavigationView navigationView = findViewById( R.id.nav_view );
		navigationView.setNavigationItemSelectedListener( this );
		
		btnMenu = findViewById( R.id.btnMenu );
		btnToot = findViewById( R.id.btnToot );
		vFooterDivider1 = findViewById( R.id.vFooterDivider1 );
		vFooterDivider2 = findViewById( R.id.vFooterDivider2 );
		llColumnStrip = findViewById( R.id.llColumnStrip );
		svColumnStrip = findViewById( R.id.svColumnStrip );
		llQuickTootBar = findViewById( R.id.llQuickTootBar );
		etQuickToot = findViewById( R.id.etQuickToot );
		btnQuickToot = findViewById( R.id.btnQuickToot );
		
		if( ! pref.getBoolean( Pref.KEY_QUICK_TOOT_BAR, false ) ){
			llQuickTootBar.setVisibility( View.GONE );
		}
		
		btnToot.setOnClickListener( this );
		btnMenu.setOnClickListener( this );
		btnQuickToot.setOnClickListener( this );
		
		if( pref.getBoolean( Pref.KEY_DONT_USE_ACTION_BUTTON, false ) ){
			etQuickToot.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE );
			etQuickToot.setImeOptions( EditorInfo.IME_ACTION_NONE );
			// 最後に指定する必要がある？
			etQuickToot.setMaxLines( 5 );
			etQuickToot.setVerticalScrollBarEnabled(true);
			etQuickToot.setScrollbarFadingEnabled(false);
		}else{
			etQuickToot.setInputType( InputType.TYPE_CLASS_TEXT );
			etQuickToot.setImeOptions( EditorInfo.IME_ACTION_SEND );
			etQuickToot.setOnEditorActionListener( new TextView.OnEditorActionListener() {
				@Override public boolean onEditorAction( TextView v, int actionId, KeyEvent event ){
					if( actionId == EditorInfo.IME_ACTION_SEND ){
						btnQuickToot.performClick();
						return true;
					}
					return false;
				}
			} );
			// 最後に指定する必要がある？
			etQuickToot.setMaxLines( 1 );
		}
		
		svColumnStrip.setHorizontalFadingEdgeEnabled( true );
		
		post_helper = new PostHelper( this, pref, app_state.handler );
		
		DisplayMetrics dm = getResources().getDisplayMetrics();
		
		float density = dm.density;
		
		int media_thumb_height = 64;
		sv = pref.getString( Pref.KEY_MEDIA_THUMB_HEIGHT, "" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				int iv = Integer.parseInt( sv );
				if( iv >= 32 ){
					media_thumb_height = iv;
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		app_state.media_thumb_height = (int) ( 0.5f + media_thumb_height * density );
		
		int column_w_min_dp = COLUMN_WIDTH_MIN_DP;
		sv = pref.getString( Pref.KEY_COLUMN_WIDTH, "" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				int iv = Integer.parseInt( sv );
				if( iv >= 100 ){
					column_w_min_dp = iv;
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		int column_w_min = (int) ( 0.5f + column_w_min_dp * density );
		
		int sw = dm.widthPixels;
		
		pager = findViewById( R.id.viewPager );
		tablet_pager = findViewById( R.id.rvPager );
		
		if( pref.getBoolean( Pref.KEY_DISABLE_TABLET_MODE, false ) || sw < column_w_min * 2 ){
			tablet_pager.setVisibility( View.GONE );
			
			// SmartPhone mode
			pager_adapter = new ColumnPagerAdapter( this );
			pager.setAdapter( pager_adapter );
			pager.addOnPageChangeListener( this );
			
			resizeAutoCW( sw );
		}else{
			pager.setVisibility( View.GONE );
			
			// tablet mode
			tablet_pager_adapter = new TabletColumnPagerAdapter( this );
			tablet_layout_manager = new LinearLayoutManager( this, LinearLayoutManager.HORIZONTAL, false );
			tablet_pager.setAdapter( tablet_pager_adapter );
			tablet_pager.setLayoutManager( tablet_layout_manager );
			tablet_pager.addOnScrollListener( new RecyclerView.OnScrollListener() {
				@Override
				public void onScrollStateChanged( RecyclerView recyclerView, int newState ){
					super.onScrollStateChanged( recyclerView, newState );
					int vs = tablet_layout_manager.findFirstVisibleItemPosition();
					int ve = tablet_layout_manager.findLastVisibleItemPosition();
					// 端に近い方に合わせる
					int distance_left = Math.abs( vs );
					int distance_right = Math.abs( ( app_state.column_list.size() - 1 ) - ve );
					if( distance_left < distance_right ){
						scrollColumnStrip( vs );
					}else{
						scrollColumnStrip( ve );
					}
				}
				
				@Override public void onScrolled( RecyclerView recyclerView, int dx, int dy ){
					super.onScrolled( recyclerView, dx, dy );
					updateColumnStripSelection( - 1, - 1f );
				}
			} );
			///////tablet_pager.setHasFixedSize( true );
			// tablet_pager.addItemDecoration( new TabletColumnDivider( this ) );
			
			tablet_snap_helper = new GravitySnapHelper( Gravity.START );
			tablet_snap_helper.attachToRecyclerView( tablet_pager );
		}
		
		showFooterColor();
		
		post_helper.attachEditText( findViewById( R.id.llFormRoot ), etQuickToot, true, new PostHelper.Callback2() {
			@Override public void onTextUpdate(){
			}
			
			@Override public boolean canOpenPopup(){
				return drawer != null && ! drawer.isDrawerOpen( Gravity.START );
			}
		} );
	}
	
	void updateColumnStrip(){
		llEmpty.setVisibility( app_state.column_list.isEmpty() ? View.VISIBLE : View.GONE );
		
		llColumnStrip.removeAllViews();
		for( int i = 0, ie = app_state.column_list.size() ; i < ie ; ++ i ){
			
			final Column column = app_state.column_list.get( i );
			
			View viewRoot = getLayoutInflater().inflate( R.layout.lv_column_strip, llColumnStrip, false );
			ImageView ivIcon = viewRoot.findViewById( R.id.ivIcon );
			
			viewRoot.setTag( i );
			viewRoot.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					scrollToColumn( (Integer) v.getTag(), false );
				}
			} );
			viewRoot.setContentDescription( column.getColumnName( true ) );
			//
			
			int c = column.header_bg_color;
			if( c == 0 ){
				viewRoot.setBackgroundResource( R.drawable.btn_bg_ddd );
			}else{
				ViewCompat.setBackground( viewRoot, Styler.getAdaptiveRippleDrawable(
					c,
					( column.header_fg_color != 0 ? column.header_fg_color :
						Styler.getAttributeColor( this, R.attr.colorRippleEffect ) )
				
				) );
			}
			
			c = column.header_fg_color;
			if( c == 0 ){
				Styler.setIconDefaultColor( this, ivIcon, column.getIconAttrId( column.column_type ) );
			}else{
				Styler.setIconCustomColor( this, ivIcon, c, column.getIconAttrId( column.column_type ) );
			}
			
			//
			AcctColor ac = AcctColor.load( column.access_info.acct );
			if( AcctColor.hasColorForeground( ac ) ){
				View vAcctColor = viewRoot.findViewById( R.id.vAcctColor );
				vAcctColor.setBackgroundColor( ac.color_fg );
			}
			//
			llColumnStrip.addView( viewRoot );
			//
			
		}
		svColumnStrip.requestLayout();
		updateColumnStripSelection( - 1, - 1f );
		
	}
	
	private void updateColumnStripSelection( final int position, final float positionOffset ){
		handler.post( new Runnable() {
			@Override public void run(){
				if( isFinishing() ) return;
				
				if( app_state.column_list.isEmpty() ){
					llColumnStrip.setColumnRange( - 1, - 1, 0f );
				}else if( pager_adapter != null ){
					if( position >= 0 ){
						llColumnStrip.setColumnRange( position, position, positionOffset );
					}else{
						int c = pager.getCurrentItem();
						llColumnStrip.setColumnRange( c, c, 0f );
					}
				}else{
					int first = tablet_layout_manager.findFirstVisibleItemPosition();
					int last = tablet_layout_manager.findLastVisibleItemPosition();
					
					if( last - first > nScreenColumn - 1 ){
						last = first + nScreenColumn - 1;
					}
					float slide_ratio = 0f;
					if( first != RecyclerView.NO_POSITION && nColumnWidth > 0 ){
						View child = tablet_layout_manager.findViewByPosition( first );
						slide_ratio = Math.abs( child.getLeft() / (float) nColumnWidth );
					}
					
					llColumnStrip.setColumnRange( first, last, slide_ratio );
				}
			}
		} );
	}
	
	private void scrollColumnStrip( final int select ){
		int child_count = llColumnStrip.getChildCount();
		if( select < 0 || select >= child_count ){
			return;
		}
		
		View icon = llColumnStrip.getChildAt( select );
		
		int sv_width = ( (View) llColumnStrip.getParent() ).getWidth();
		int ll_width = llColumnStrip.getWidth();
		int icon_width = icon.getWidth();
		int icon_left = icon.getLeft();
		
		if( sv_width == 0 || ll_width == 0 || icon_width == 0 ){
			handler.postDelayed( new Runnable() {
				@Override public void run(){
					scrollColumnStrip( select );
				}
			}, 20L );
			
		}
		
		int sx = icon_left + icon_width / 2 - sv_width / 2;
		svColumnStrip.smoothScrollTo( sx, 0 );
		
	}
	
	public void performAccountAdd(){
		LoginForm.showLoginForm( this, null, new LoginForm.LoginFormCallback() {
			@Override
			public void startLogin(
				final Dialog dialog
				, final String instance
				, final boolean bPseudoAccount
				, final boolean bInputAccessToken
			){
				
				//noinspection deprecation
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
							return api_client.authorize1( Pref.pref( ActMain.this ).getString( Pref.KEY_CLIENT_NAME, "" ) );
						}
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						try{
							progress.dismiss();
						}catch( Throwable ignored ){
							// java.lang.IllegalArgumentException:
							// at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:396)
							// at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:322)
							// at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:116)
							// at android.app.Dialog.dismissDialog(Dialog.java:341)
							// at android.app.Dialog.dismiss(Dialog.java:324)
							// at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:867)
							// at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:837)
						}
						
						//noinspection StatementWithEmptyBody
						if( result == null ){
							// cancelled.
						}else if( result.error != null ){
							String sv = result.error;
							
							// エラーはブラウザ用URLかもしれない
							if( sv.startsWith( "https" ) ){
								
								if( bInputAccessToken ){
									// アクセストークンの手動入力
									DlgAccessToken.show( ActMain.this, new DlgAccessToken.Callback() {
										@Override
										public void startCheck( Dialog dialog_token, String access_token ){
											checkAccessToken( dialog, dialog_token, instance, access_token, null );
										}
									} );
								}else{
									// OAuth認証が必要
									Intent data = new Intent();
									data.setData( Uri.parse( sv ) );
									startAccessTokenUpdate( data );
									try{
										dialog.dismiss();
									}catch( Throwable ignored ){
										// IllegalArgumentException がたまに出る
									}
								}
								return;
							}
							
							log.e( result.error );
							
							if( sv.contains( "SSLHandshakeException" )
								&& ( Build.VERSION.RELEASE.startsWith( "7.0" )
								|| ( Build.VERSION.RELEASE.startsWith( "7.1" ) && ! Build.VERSION.RELEASE.startsWith( "7.1." ) ) )
								){
								new AlertDialog.Builder( ActMain.this )
									.setMessage( sv + "\n\n" + getString( R.string.ssl_bug_7_0 ) )
									.setNeutralButton( R.string.close, null )
									.show();
								return;
							}
							
							// 他のエラー
							Utils.showToast( ActMain.this, true, sv );
						}else{
							SavedAccount a = addPseudoAccount( instance );
							if( a != null ){
								// 疑似アカウントが追加された
								Utils.showToast( ActMain.this, false, R.string.server_confirmed );
								int pos = app_state.column_list.size();
								addColumn( pos, a, Column.TYPE_LOCAL );
								try{
									dialog.dismiss();
								}catch( Throwable ignored ){
									// IllegalArgumentException がたまに出る
								}
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
				task.executeOnExecutor( App1.task_executor );
			}
		} );
		
	}
	
	SavedAccount addPseudoAccount( String host ){
		try{
			String username = "?";
			String full_acct = username + "@" + host;
			
			JSONObject account_info = new JSONObject();
			account_info.put( "username", username );
			account_info.put( "acct", username );
			
			long row_id = SavedAccount.insert( host, full_acct, account_info, new JSONObject() );
			SavedAccount account = SavedAccount.loadAccount( ActMain.this, log, row_id );
			if( account == null ){
				throw new RuntimeException( "loadAccount returns null." );
			}
			account.notification_follow = false;
			account.notification_favourite = false;
			account.notification_boost = false;
			account.notification_mention = false;
			account.saveSetting();
			return account;
		}catch( JSONException ex ){
			log.trace( ex );
			log.e( ex, "addPseudoAccount failed." );
			Utils.showToast( this, ex, "addPseudoAccount failed." );
		}
		return null;
	}
	
	private void startAccessTokenUpdate( Intent data ){
		Uri uri = data.getData();
		if( uri == null ) return;
		// ブラウザで開く
		try{
			Intent intent = new Intent( Intent.ACTION_VIEW, uri );
			startActivity( intent );
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	// ActOAuthCallbackで受け取ったUriを処理する
	private void handleIntentUri( @NonNull final Uri uri ){
		
		// プロフURL
		if( "https".equals( uri.getScheme() ) ){
			if( uri.getPath().startsWith( "/@" ) ){
				
				Matcher m = reStatusPage.matcher( uri.toString() );
				if( m.find() ){
					// ステータスをアプリ内で開く
					try{
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						final String host = m.group( 1 );
						final long status_id = Long.parseLong( m.group( 3 ), 10 );
						openStatusOtherInstance( getDefaultInsertPosition(), null, uri.toString(), status_id, host, status_id );
						
						//
						//						ArrayList< SavedAccount > account_list_same_host = new ArrayList<>();
						//
						//						for( SavedAccount a : SavedAccount.loadAccountList( log ) ){
						//							if( host.equalsIgnoreCase( a.host ) ){
						//								account_list_same_host.add( a );
						//							}
						//						}
						//
						//						// ソートする
						//						Collections.sort( account_list_same_host, new Comparator< SavedAccount >() {
						//							@Override public int compare( SavedAccount a, SavedAccount b ){
						//								return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
						//							}
						//						} );
						//
						//						if( account_list_same_host.isEmpty() ){
						//							account_list_same_host.add( addPseudoAccount( host ) );
						//						}
						//
						//						AccountPicker.pick( this, true, true
						//							, getString( R.string.open_status_from )
						//							, account_list_same_host
						//							, new AccountPicker.AccountPickerCallback() {
						//								@Override
						//								public void onAccountPicked( @NonNull final SavedAccount ai ){
						//									openStatus( getDefaultInsertPosition(), ai, status_id );
						//								}
						//							} );
						
					}catch( Throwable ex ){
						Utils.showToast( this, ex, "can't parse status id." );
					}
					return;
				}
				
				m = reUserPage.matcher( uri.toString() );
				if( m.find() ){
					// ユーザページをアプリ内で開く
					
					// https://mastodon.juggler.jp/@SubwayTooter
					final String host = m.group( 1 );
					final String user = Uri.decode( m.group( 2 ) );
					
					openProfileByHostUser( getDefaultInsertPosition(), null, uri.toString(), host, user );
				}
				return;
			}
			// https なら oAuth用の導線は通さない
			return;
		}
		
		// 通知タップ
		// subwaytooter://notification_click?db_id=(db_id)
		String sv = uri.getQueryParameter( "db_id" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				long db_id = Long.parseLong( sv, 10 );
				SavedAccount account = SavedAccount.loadAccount( ActMain.this, log, db_id );
				if( account != null ){
					Column column = addColumn( getDefaultInsertPosition(), account, Column.TYPE_NOTIFICATIONS );
					// 通知を読み直す
					if( ! column.bInitialLoading ){
						column.startLoading();
					}
					
					PollingService.queueNotificationClicked( this, db_id );
					
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			return;
		}
		
		// OAuth2 認証コールバック
		
		//noinspection deprecation
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
						this.sa = SavedAccount.loadAccount( ActMain.this, log, db_id );
						if( sa == null ){
							return new TootApiResult( "missing account db_id=" + db_id );
						}
						client.setAccount( sa );
					}catch( Throwable ex ){
						log.trace( ex );
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
					this.ta = TootAccount.parse( ActMain.this, lcc, result.object );
				}
				return result;
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ex ){
					log.trace( ex );
					// java.lang.IllegalArgumentException:
					// at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:451)
					// at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:377)
					// at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:122)
					// at android.app.Dialog.dismissDialog(Dialog.java:546)
					// at android.app.Dialog.dismiss(Dialog.java:529)
				}
				
				afterAccountVerify( result, ta, sa, host );
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
		task.executeOnExecutor( App1.task_executor );
	}
	
	boolean afterAccountVerify( @Nullable TootApiResult result, @Nullable TootAccount ta, @Nullable SavedAccount sa, @Nullable String host ){
		//noinspection StatementWithEmptyBody
		if( result == null ){
			// cancelled.
		}else if( result.error != null ){
			Utils.showToast( ActMain.this, true, result.error );
		}else if( ta == null ){
			// 自分のユーザネームを取れなかった
			// …普通はエラーメッセージが設定されてるはずだが
			Utils.showToast( ActMain.this, true, "missing TootAccount" );
		}else if( sa != null ){
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
				for( Column c : app_state.column_list ){
					if( c.access_info.acct.equals( sa.acct ) ){
						c.startLoading();
					}
				}
				
				// 通知の更新が必要かもしれない
				PollingService.queueUpdateNotification( ActMain.this );
				return true;
			}
		}else if( host != null ){
			// アカウント追加時
			String user = ta.username + "@" + host;
			long row_id = SavedAccount.insert( host, user, result.object, result.token_info );
			SavedAccount account = SavedAccount.loadAccount( ActMain.this, log, row_id );
			if( account != null ){
				boolean bModified = false;
				if( account.locked ){
					bModified = true;
					account.visibility = TootStatus.VISIBILITY_PRIVATE;
				}
				if( ta.source != null ){
					if( ta.source.privacy != null ){
						bModified = true;
						account.visibility = ta.source.privacy;
					}
					// FIXME  ta.source.sensitive パラメータを読んで「添付画像をデフォルトでNSFWにする」を実現する
					if( ta.source.sensitive ){
						bModified = true;
						account.dont_hide_nsfw = true;
						
					}
				}
				
				if( bModified ){
					account.saveSetting();
				}
				Utils.showToast( ActMain.this, false, R.string.account_confirmed );
				
				// 通知の更新が必要かもしれない
				PollingService.queueUpdateNotification( ActMain.this );
				
				// 適当にカラムを追加する
				long count = SavedAccount.getCount();
				if( count > 1 ){
					addColumn( getDefaultInsertPosition(), account, Column.TYPE_HOME );
				}else{
					addColumn( getDefaultInsertPosition(), account, Column.TYPE_HOME );
					addColumn( getDefaultInsertPosition(), account, Column.TYPE_NOTIFICATIONS );
					addColumn( getDefaultInsertPosition(), account, Column.TYPE_LOCAL );
					addColumn( getDefaultInsertPosition(), account, Column.TYPE_FEDERATE );
				}
				
				return true;
			}
		}
		return false;
	}
	
	// アクセストークンを手動で入力した場合
	void checkAccessToken(
		@Nullable final Dialog dialog_host
		, @Nullable final Dialog dialog_token
		, @NonNull final String host
		, @NonNull final String access_token
		, @Nullable final SavedAccount sa
	){
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( ActMain.this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootAccount ta;
			
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
				
				client.setInstance( host );
				
				TootApiResult result = client.checkAccessToken( access_token );
				if( result != null && result.object != null ){
					// taは使い捨てなので、生成に使うLinkClickContextはダミーで問題ない
					LinkClickContext lcc = new LinkClickContext() {
						@Override public AcctColor findAcctColor( String url ){
							return null;
						}
					};
					this.ta = TootAccount.parse( ActMain.this, lcc, result.object );
				}
				return result;
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ex ){
					log.trace( ex );
					// java.lang.IllegalArgumentException:
					// at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:451)
					// at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:377)
					// at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:122)
					// at android.app.Dialog.dismissDialog(Dialog.java:546)
					// at android.app.Dialog.dismiss(Dialog.java:529)
				}
				
				if( afterAccountVerify( result, ta, sa, host ) ){
					try{
						if( dialog_host != null ) dialog_host.dismiss();
					}catch( Throwable ignored ){
						// IllegalArgumentException がたまに出る
					}
					try{
						if( dialog_token != null ) dialog_token.dismiss();
					}catch( Throwable ignored ){
						// IllegalArgumentException がたまに出る
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
		task.executeOnExecutor( App1.task_executor );
	}
	
	// アクセストークンの手動入力(更新)
	void checkAccessToken2( long db_id ){
		
		final SavedAccount sa = SavedAccount.loadAccount( this, log, db_id );
		if( sa == null ) return;
		
		DlgAccessToken.show( this, new DlgAccessToken.Callback() {
			@Override public void startCheck( Dialog dialog_token, String access_token ){
				checkAccessToken( null, dialog_token, sa.host, access_token, sa );
			}
		} );
	}
	
	void reloadAccountSetting(){
		ArrayList< SavedAccount > done_list = new ArrayList<>();
		for( Column column : app_state.column_list ){
			SavedAccount a = column.access_info;
			if( done_list.contains( a ) ) continue;
			done_list.add( a );
			if( ! a.isNA() ) a.reloadSetting( ActMain.this );
			column.fireShowColumnHeader();
		}
	}
	
	void reloadAccountSetting( SavedAccount account ){
		ArrayList< SavedAccount > done_list = new ArrayList<>();
		for( Column column : app_state.column_list ){
			SavedAccount a = column.access_info;
			if( ! Utils.equalsNullable( a.acct, account.acct ) ) continue;
			if( done_list.contains( a ) ) continue;
			done_list.add( a );
			if( ! a.isNA() ) a.reloadSetting( ActMain.this );
			column.fireShowColumnHeader();
		}
	}
	
	public void closeColumn( boolean bConfirm, final Column column ){
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
						closeColumn( true, column );
					}
				} )
				.show();
			return;
		}
		
		int page_delete = app_state.column_list.indexOf( column );
		
		if( pager_adapter != null ){
			int page_showing = pager.getCurrentItem();
			
			removeColumn( column );
			
			if( ! app_state.column_list.isEmpty() && page_delete > 0 && page_showing == page_delete ){
				int idx = page_delete - 1;
				scrollToColumn( idx, false );
				Column c = app_state.column_list.get( idx );
				if( ! c.bFirstInitialized ){
					c.startLoading();
				}
			}
			
		}else{
			removeColumn( column );
			
			if( ! app_state.column_list.isEmpty() && page_delete > 0 ){
				int idx = page_delete - 1;
				scrollToColumn( idx, false );
				Column c = app_state.column_list.get( idx );
				if( ! c.bFirstInitialized ){
					c.startLoading();
				}
			}
		}
	}
	
	//////////////////////////////////////////////////////////////
	// カラム追加系
	
	public Column addColumn( int index, SavedAccount ai, int type, Object... params ){
		// 既に同じカラムがあればそこに移動する
		for( Column column : app_state.column_list ){
			if( column.isSameSpec( ai, type, params ) ){
				index = app_state.column_list.indexOf( column );
				scrollToColumn( index, false );
				return column;
			}
		}
		
		//
		Column col = new Column( app_state, ai, this, type, params );
		index = addColumn( col, index );
		scrollToColumn( index, false );
		if( ! col.bFirstInitialized ){
			col.startLoading();
		}
		return col;
	}
	
	private void performAddTimeline( final int pos, boolean bAllowPseudo, final int type, final Object... args ){
		AccountPicker.pick( this, bAllowPseudo, true
			, getString( R.string.account_picker_add_timeline_of, Column.getColumnTypeName( this, type ) )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					switch( type ){
					default:
						addColumn( pos, ai, type, args );
						break;
					
					case Column.TYPE_PROFILE:
						addColumn( pos, ai, type, ai.id );
						break;
					}
				}
			} );
	}
	
	public void performMuteApp( @NonNull TootApplication application ){
		MutedApp.save( application.name );
		for( Column column : app_state.column_list ){
			column.removeMuteApp();
		}
		Utils.showToast( ActMain.this, false, R.string.app_was_muted );
	}
	
	
	//////////////////////////////////////////////////////////////
	
	interface FindAccountCallback {
		// return account information
		// if failed, account is null.
		void onFindAccount( @Nullable TootAccount account );
	}
	
	// ユーザ名からアカウントIDを取得するために検索APIを使う
	void startFindAccount( final SavedAccount access_info, final String host, final String user, final FindAccountCallback callback ){
		
		//noinspection deprecation
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
				if( result != null && result.array != null ){
					for( int i = 0, ie = result.array.length() ; i < ie ; ++ i ){
						
						TootAccount item = TootAccount.parse( ActMain.this, access_info, result.array.optJSONObject( i ) );
						
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
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				callback.onFindAccount( result );
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
		task.executeOnExecutor( App1.task_executor );
	}
	
	static final Pattern reHashTag = Pattern.compile( "\\Ahttps://([^/]+)/tags/([^?#]+)(?:\\z|\\?)" );
	static final Pattern reUserPage = Pattern.compile( "\\Ahttps://([^/]+)/@([^?#/]+)(?:\\z|\\?)" );
	static final Pattern reStatusPage = Pattern.compile( "\\Ahttps://([^/]+)/@([^?#/]+)/(\\d+)(?:\\z|\\?)" );
	
	public void openChromeTab( final int pos, @Nullable final SavedAccount access_info, final String url, boolean noIntercept ){
		try{
			log.d( "openChromeTab url=%s", url );
			
			if( ! noIntercept && access_info != null && access_info.isNA() ){
				// トゥート検索カラムではaccess_infoは何にも紐ついていない
				
				// ハッシュタグをアプリ内で開く
				Matcher m = reHashTag.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					String host = m.group( 1 );
					String tag = Uri.decode( m.group( 2 ) );
					openHashTagOtherInstance( pos, access_info, url, host, tag );
					return;
				}
				
				// ステータスページをアプリから開く
				m = reStatusPage.matcher( url );
				if( m.find() ){
					try{
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						final String host = m.group( 1 );
						final long status_id = Long.parseLong( m.group( 3 ), 10 );
						openStatusOtherInstance( pos, access_info, url, status_id, host, status_id );
						return;
					}catch( Throwable ex ){
						Utils.showToast( this, ex, "can't parse status id." );
					}
					return;
				}
				
				// ユーザページをアプリ内で開く
				m = reUserPage.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/@SubwayTooter
					final String host = m.group( 1 );
					final String user = Uri.decode( m.group( 2 ) );
					
					openProfileByHostUser( pos, access_info, url, host, user );
					return;
				}
				
			}
			
			if( ! noIntercept && access_info != null ){
				// ハッシュタグをアプリ内で開く
				Matcher m = reHashTag.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					String host = m.group( 1 );
					String tag = Uri.decode( m.group( 2 ) );
					if( host.equalsIgnoreCase( access_info.host ) ){
						openHashTag( pos, access_info, tag );
						return;
					}else{
						openHashTagOtherInstance( pos, access_info, url, host, tag );
						return;
					}
				}
				
				// ステータスページをアプリから開く
				m = reStatusPage.matcher( url );
				if( m.find() ){
					try{
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						final String host = m.group( 1 );
						final long status_id = Long.parseLong( m.group( 3 ), 10 );
						if( host.equalsIgnoreCase( access_info.host ) ){
							openStatusLocal( pos, access_info, status_id );
							return;
						}else{
							openStatusOtherInstance( pos, access_info, url, status_id, host, status_id );
							return;
						}
					}catch( Throwable ex ){
						Utils.showToast( this, ex, "can't parse status id." );
					}
					return;
				}
				
				// ユーザページをアプリ内で開く
				m = reUserPage.matcher( url );
				if( m.find() ){
					// https://mastodon.juggler.jp/@SubwayTooter
					final String host = m.group( 1 );
					final String user = Uri.decode( m.group( 2 ) );
					openProfileByHostUser( pos, access_info, url, host, user );
					
					return;
					
				}
			}
			
			do{
				if( pref.getBoolean( Pref.KEY_PRIOR_CHROME, true ) ){
					try{
						// 初回はChrome指定で試す
						CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
						builder.setToolbarColor( Styler.getAttributeColor( this, R.attr.colorPrimary ) ).setShowTitle( true );
						CustomTabsIntent customTabsIntent = builder.build();
						customTabsIntent.intent.setComponent( new ComponentName( "com.android.chrome", "com.google.android.apps.chrome.Main" ) );
						customTabsIntent.launchUrl( this, Uri.parse( url ) );
						break;
					}catch( Throwable ex2 ){
						log.e( ex2, "openChromeTab: missing chrome. retry to other application." );
					}
				}
				
				// chromeがないなら ResolverActivity でアプリを選択させる
				CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
				builder.setToolbarColor( Styler.getAttributeColor( this, R.attr.colorPrimary ) ).setShowTitle( true );
				CustomTabsIntent customTabsIntent = builder.build();
				customTabsIntent.launchUrl( this, Uri.parse( url ) );
				
			}while( false );
			
		}catch( Throwable ex ){
			// log.trace( ex );
			log.e( ex, "openChromeTab failed. url=%s", url );
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public void openHashTag( int pos, SavedAccount access_info, String tag ){
		addColumn( pos, access_info, Column.TYPE_HASHTAG, tag );
	}
	
	// 他インスタンスのハッシュタグの表示
	private void openHashTagOtherInstance( final int pos, final SavedAccount access_info, final String url, final String host, final String tag ){
		
		ActionsDialog dialog = new ActionsDialog();
		
		// ブラウザで表示する
		dialog.addAction( getString( R.string.open_web_on_host, host ), new Runnable() {
			@Override public void run(){
				openChromeTab( pos, access_info, url, true );
			}
		} );
		
		// 各アカウント
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( ActMain.this, log );
		
		// ソートする
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		
		// 各アカウントで開く選択肢
		boolean has_host = false;
		for( SavedAccount a : account_list ){
			
			if( host.equalsIgnoreCase( a.host ) ){
				has_host = true;
			}
			
			final SavedAccount _a = a;
			dialog.addAction( getString( R.string.open_in_account, a.acct ), new Runnable() {
				@Override public void run(){
					openHashTag( pos, _a, tag );
				}
			} );
		}
		
		if( ! has_host ){
			dialog.addAction( getString( R.string.open_in_pseudo_account, "?@" + host ), new Runnable() {
				@Override public void run(){
					SavedAccount sa = addPseudoAccount( host );
					if( sa != null ){
						openHashTag( pos, sa, tag );
					}
				}
			} );
		}
		
		dialog.show( this, "#" + tag );
		
	}
	
	final MyClickableSpan.LinkClickCallback link_click_listener = new MyClickableSpan.LinkClickCallback() {
		@Override public void onClickLink( View view, LinkClickContext lcc, String url ){
			Column column = null;
			while( view != null ){
				Object tag = view.getTag();
				if( tag instanceof ItemViewHolder ){
					column = ( (ItemViewHolder) tag ).column;
					break;
				}else if( tag instanceof HeaderViewHolderProfile ){
					column = ( (HeaderViewHolderProfile) tag ).column;
					break;
				}else if( tag instanceof TabletColumnViewHolder ){
					column = ( (TabletColumnViewHolder) tag ).vh.column;
					break;
				}else{
					ViewParent parent = view.getParent();
					if( parent instanceof View ){
						view = (View) parent;
					}else{
						break;
					}
				}
			}
			openChromeTab( nextPosition( column ), (SavedAccount) lcc, url, false );
		}
	};
	
	private void performTootButton(){
		openPost( etQuickToot.getText().toString() );
	}
	
	public void openPost( final String initial_text ){
		post_helper.closeAcctPopup();
		
		if( pager_adapter != null ){
			Column c = pager_adapter.getColumn( pager.getCurrentItem() );
			if( c != null && ! c.access_info.isPseudo() ){
				ActPost.open( this, REQUEST_CODE_POST, c.access_info.db_id, initial_text );
				return;
			}
		}else{
			long db_id = pref.getLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L );
			SavedAccount a = SavedAccount.loadAccount( ActMain.this, log, db_id );
			if( a != null ){
				ActPost.open( this, REQUEST_CODE_POST, a.db_id, initial_text );
				return;
			}
		}
		
		AccountPicker.pick( this, false, true, getString( R.string.account_picker_toot ), new AccountPicker.AccountPickerCallback() {
			@Override public void onAccountPicked( @NonNull SavedAccount ai ){
				ActPost.open( ActMain.this, REQUEST_CODE_POST, ai.db_id, initial_text );
			}
		} );
	}
	
	public void performMention( SavedAccount account, @NonNull TootAccount who ){
		ActPost.open( this, REQUEST_CODE_POST, account.db_id, "@" + account.getFullAcct( who ) + " " );
	}
	
	public void performMentionFromAnotherAccount( SavedAccount access_info, final TootAccount who, ArrayList< SavedAccount > account_list_non_pseudo ){
		final String initial_text = "@" + access_info.getFullAcct( who ) + " ";
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_toot )
			, account_list_non_pseudo, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					ActPost.open( ActMain.this, REQUEST_CODE_POST, ai.db_id, initial_text );
				}
			} );
	}
	
	/////////////////////////////////////////////////////////////////////////
	
	private void showColumnMatchAccount( SavedAccount account ){
		for( Column column : app_state.column_list ){
			if( account.acct.equals( column.access_info.acct ) ){
				column.fireShowContent();
			}
		}
	}
	
	/////////////////////////////////////////////////////////////////////////
	// open profile
	
	private void openProfileRemote( final int pos, final SavedAccount access_info, final String who_url ){
		new AsyncTask< Void, Void, TootApiResult >() {
			TootAccount who_local;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				
				client.setAccount( access_info );
				
				// 検索APIに他タンスのユーザのURLを投げると、自タンスのURLを得られる
				String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( who_url ) );
				path = path + "&resolve=1";
				
				TootApiResult result = client.request( path );
				
				if( result != null && result.object != null ){
					
					TootResults tmp = TootResults.parse( ActMain.this, access_info, result.object );
					if( tmp != null ){
						if( tmp.accounts != null && ! tmp.accounts.isEmpty() ){
							who_local = tmp.accounts.get( 0 );
						}
					}
					
					if( who_local == null ){
						return new TootApiResult( getString( R.string.user_id_conversion_failed ) );
					}
				}
				
				return result;
				
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				if( result == null ){
					// cancelled.
				}else if( who_local != null ){
					addColumn( pos, access_info, Column.TYPE_PROFILE, who_local.id );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
					
					// 仕方ないのでchrome tab で開く
					openChromeTab( pos, access_info, who_url, true );
				}
			}
			
		}.executeOnExecutor( App1.task_executor );
	}
	
	void openProfileFromAnotherAccount( final int pos, @NonNull final SavedAccount access_info, final TootAccount who ){
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_open_user_who, AcctColor.getNickname( who.acct ) )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					if( ai.host.equalsIgnoreCase( access_info.host ) ){
						addColumn( pos, ai, Column.TYPE_PROFILE, who.id );
					}else{
						openProfileRemote( pos, ai, who.url );
					}
				}
			} );
	}
	
	void openProfile( int pos, @NonNull SavedAccount access_info, @Nullable TootAccount who ){
		if( who == null ){
			Utils.showToast( this, false, "user is null" );
		}else if( access_info.isPseudo() ){
			openProfileFromAnotherAccount( pos, access_info, who );
		}else{
			addColumn( pos, access_info, Column.TYPE_PROFILE, who.id );
		}
	}
	
	// Intent-FilterからUser URL で指定されたユーザのプロフを開く
	// openChromeTabからUser URL で指定されたユーザのプロフを開く
	private void openProfileByHostUser(
		final int pos
		, @Nullable final SavedAccount access_info
		, @NonNull final String url
		, @NonNull final String host
		, @NonNull final String user
	){
		// リンクタップした文脈のアカウントが疑似でないなら
		if( access_info != null && ! access_info.isPseudo() ){
			if( access_info.host.equalsIgnoreCase( host ) ){
				// 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
				startFindAccount( access_info, host, user, new FindAccountCallback() {
					@Override public void onFindAccount( TootAccount who ){
						if( who != null ){
							openProfile( pos, access_info, who );
							return;
						}
						// ダメならchromeで開く
						openChromeTab( pos, access_info, url, true );
					}
				} );
			}else{
				// 文脈のアカウント異なるインスタンスなら、別アカウントで開く
				openProfileRemote( pos, access_info, url );
			}
			return;
		}
		
		// 文脈がない、もしくは疑似アカウントだった
		
		// 疑似アカウントではユーザ情報APIを呼べないし検索APIも使えない
		
		// 疑似ではないアカウントの一覧
		ArrayList< SavedAccount > account_list_filtered = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( ActMain.this, log ) ){
			if( a.isPseudo() ) continue;
			account_list_filtered.add( a );
		}
		
		if( account_list_filtered.isEmpty() ){
			// アカウントがないのでchrome tab で開くしかない
			openChromeTab( pos, access_info, url, true );
		}else{
			// アカウントを選択して開く
			AccountPicker.pick( this, false, false
				, getString( R.string.account_picker_open_user_who, AcctColor.getNickname( user + "@" + host ) )
				, account_list_filtered
				, new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						openProfileRemote( pos, ai, url );
					}
				} );
		}
		
	}
	
	/////////////////////////////////////////////////////////////////////////
	// favourite
	
	public void performFavourite(
		final SavedAccount access_info
		, final TootStatusLike arg_status
		, final int nCrossAccountMode
		, final boolean bSet
		, final RelationChangedCallback callback
	){
		if( app_state.isBusyFav( access_info, arg_status ) ){
			Utils.showToast( this, false, R.string.wait_previous_operation );
			return;
		}
		//
		app_state.setBusyFav( access_info, arg_status );
		
		//
		new AsyncTask< Void, Void, TootApiResult >() {
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
				TootApiResult result;
				
				TootStatusLike target_status;
				if( nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE ){
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( arg_status.url ) );
					path = path + "&resolve=1";
					
					result = client.request( path );
					if( result == null || result.object == null ){
						return result;
					}
					target_status = null;
					TootResults tmp = TootResults.parse( ActMain.this, access_info, result.object );
					if( tmp != null ){
						if( tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							target_status = tmp.statuses.get( 0 );
							
							log.d( "status id conversion %s => %s", arg_status.id, target_status.id );
						}
					}
					if( target_status == null ){
						return new TootApiResult( getString( R.string.status_id_conversion_failed ) );
					}else if( target_status.favourited ){
						return new TootApiResult( getString( R.string.already_favourited ) );
					}
				}else{
					target_status = arg_status;
				}
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, ""
					) );
				
				result = client.request(
					( bSet
						? "/api/v1/statuses/" + target_status.id + "/favourite"
						: "/api/v1/statuses/" + target_status.id + "/unfavourite"
					)
					, request_builder );
				if( result != null && result.object != null ){
					new_status = TootStatus.parse( ActMain.this, access_info, result.object );
				}
				
				return result;
				
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				app_state.resetBusyFav( access_info, arg_status );
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
					// カウント数は遅延があるみたいなので、恣意的に表示を変更する
					if( bSet && new_status.favourited && new_status.favourites_count <= arg_status.favourites_count ){
						// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = arg_status.favourites_count + 1;
					}else if( ! bSet && ! new_status.favourited && new_status.favourites_count >= arg_status.favourites_count ){
						// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.favourites_count = arg_status.favourites_count - 1;
						// 0未満にはならない
						if( new_status.favourites_count < 0 ){
							new_status.favourites_count = 0;
						}
					}
					
					for( Column column : app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								status.favourites_count = new_status.favourites_count;
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.favourited = new_status.favourited;
								}
								return true;
							}
						} );
					}
					if( callback != null ) callback.onRelationChanged();
					
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
				// 結果に関わらず、更新中状態から復帰させる
				showColumnMatchAccount( access_info );
			}
			
		}.executeOnExecutor( App1.task_executor );
		// ファボ表示を更新中にする
		showColumnMatchAccount( access_info );
	}
	
	/////////////////////////////////////////////////////////////////////////
	// boost
	
	public void performBoost(
		final SavedAccount access_info
		, final TootStatusLike arg_status
		, final int nCrossAccountMode
		, final boolean bSet
		, final boolean bConfirmed
		, final RelationChangedCallback callback
	){
		
		// アカウントからステータスにブースト操作を行っているなら、何もしない
		if( app_state.isBusyBoost( access_info, arg_status ) ){
			Utils.showToast( this, false, R.string.wait_previous_operation );
			return;
		}
		
		// クロスアカウント操作ではないならステータス内容を使ったチェックを行える
		if( nCrossAccountMode == NOT_CROSS_ACCOUNT ){
			if( arg_status.reblogged ){
				if( app_state.isBusyFav( access_info, arg_status ) || arg_status.favourited ){
					// FAVがついているか、FAV操作中はBoostを外せない
					Utils.showToast( this, false, R.string.cant_remove_boost_while_favourited );
					return;
				}
			}
		}
		
		// 操作確認が必要なら確認を出す
		if( ! bConfirmed ){
			DlgConfirm.open( this, getString( R.string.confirm_boost_from, AcctColor.getNickname( access_info.acct ) ), new DlgConfirm.Callback() {
				@Override public boolean isConfirmEnabled(){
					return access_info.confirm_boost;
				}
				
				@Override public void setConfirmEnabled( boolean bv ){
					access_info.confirm_boost = bv;
					access_info.saveSetting();
					reloadAccountSetting( access_info );
				}
				
				@Override public void onOK(){
					performBoost( access_info, arg_status, nCrossAccountMode, bSet, true, callback );
				}
			} );
			return;
		}
		
		app_state.setBusyBoost( access_info, arg_status );
		
		//
		new AsyncTask< Void, Void, TootApiResult >() {
			
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
				
				TootApiResult result;
				
				TootStatusLike target_status;
				if( nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE ){
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( arg_status.url ) );
					path = path + "&resolve=1";
					
					result = client.request( path );
					if( result == null || result.object == null ){
						return result;
					}
					target_status = null;
					TootResults tmp = TootResults.parse( ActMain.this, access_info, result.object );
					if( tmp != null ){
						if( tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							target_status = tmp.statuses.get( 0 );
						}
					}
					if( target_status == null ){
						return new TootApiResult( getString( R.string.status_id_conversion_failed ) );
					}else if( target_status.reblogged ){
						return new TootApiResult( getString( R.string.already_boosted ) );
					}
				}else{
					// 既に自タンスのステータスがある
					target_status = arg_status;
				}
				
				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, ""
					) );
				
				result = client.request(
					"/api/v1/statuses/" + target_status.id + ( bSet ? "/reblog" : "/unreblog" )
					, request_builder );
				
				if( result != null && result.object != null ){
					
					new_status = TootStatus.parse( ActMain.this, access_info, result.object );
					
					// reblogはreblogを表すStatusを返す
					// unreblogはreblogしたStatusを返す
					if( new_status != null && new_status.reblog != null )
						new_status = new_status.reblog;
					
					//					// reblog,unreblog のレスポンスは信用ならんのでステータスを再取得する
					//					result = client.request( "/api/v1/statuses/" + target_status.id );
					//					if( result != null && result.object != null ){
					//					}
				}
				
				return result;
				
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				app_state.resetBusyBoost( access_info, arg_status );
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					
					// カウント数は遅延があるみたいなので、恣意的に表示を変更する
					// ブーストカウント数を加工する
					if( bSet && new_status.reblogged && new_status.reblogs_count <= arg_status.reblogs_count ){
						// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = arg_status.reblogs_count + 1;
					}else if( ! bSet && ! new_status.reblogged && new_status.reblogs_count >= arg_status.reblogs_count ){
						// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
						new_status.reblogs_count = arg_status.reblogs_count - 1;
						// 0未満にはならない
						if( new_status.reblogs_count < 0 ){
							new_status.reblogs_count = 0;
						}
					}
					
					for( Column column : app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								status.reblogs_count = new_status.reblogs_count;
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.reblogged = new_status.reblogged;
								}
								return true;
							}
						} );
					}
					if( callback != null ) callback.onRelationChanged();
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
				
				// 結果に関わらず、更新中状態から復帰させる
				showColumnMatchAccount( access_info );
			}
			
		}.executeOnExecutor( App1.task_executor );
		
		showColumnMatchAccount( access_info );
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	// reply
	
	public void performReply(
		final SavedAccount access_info
		, final TootStatus arg_status
	){
		ActPost.open( this, REQUEST_CODE_POST, access_info.db_id, arg_status );
	}
	
	public void performReplyRemote(
		final SavedAccount access_info
		, final String remote_status_url
	){
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			TootStatus target_status;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( access_info );
				
				// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
				String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( remote_status_url ) );
				path = path + "&resolve=1";
				
				TootApiResult result = client.request( path );
				if( result != null && result.object != null ){
					TootResults tmp = TootResults.parse( ActMain.this, access_info, result.object );
					if( tmp != null && tmp.statuses != null && ! tmp.statuses.isEmpty() ){
						target_status = tmp.statuses.get( 0 );
						log.d( "status id conversion %s => %s", remote_status_url, target_status.id );
					}
					if( target_status == null ){
						return new TootApiResult( getString( R.string.status_id_conversion_failed ) );
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
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				if( result == null ){
					// cancelled.
				}else if( target_status != null ){
					ActPost.open( ActMain.this, REQUEST_CODE_POST, access_info.db_id, target_status );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
			}
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setMessage( getString( R.string.progress_synchronize_toot ) );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
	/////////////////////////////////////////////////////////////////////////////////////
	// open conversation
	
	public void openStatusLocal( int pos, @NonNull SavedAccount access_info, long status_id ){
		addColumn( pos, access_info, Column.TYPE_CONVERSATION, status_id );
	}
	
	public void openStatus( int pos, @NonNull SavedAccount access_info, @NonNull TootStatusLike status ){
		if( access_info.isNA() || ! access_info.host.equalsIgnoreCase( status.host_access ) ){
			openStatusOtherInstance( pos, access_info, status );
		}else{
			openStatusLocal( pos, access_info, status.id );
		}
	}
	
	static final Pattern reUriOStatusToot = Pattern.compile( "tag:([^,]*),[^:]*:objectId=(\\d+):objectType=Status", Pattern.CASE_INSENSITIVE );
	
	public void openStatusOtherInstance( int pos, @NonNull SavedAccount access_info, @NonNull TootStatusLike status ){
		if( status.account == null ){
			// アカウント情報がないと出来ないことがある
		}else if( status instanceof MSPToot ){
			// トゥート検索の場合
			openStatusOtherInstance( pos, access_info, status.url
				, status.id
				, null, - 1L
			);
		}else if( status instanceof TootStatus ){
			TootStatus ts = (TootStatus) status;
			if( status.host_original.equals( status.host_access ) ){
				// TLアカウントのホストとトゥートのアカウントのホストが同じ場合
				openStatusOtherInstance( pos, access_info, status.url
					, status.id
					, null, - 1L
				);
			}else{
				// TLアカウントのホストとトゥートのアカウントのホストが異なる場合
				
				long status_id_original = - 1L;
				//String host_original = null;
				
				try{
					// OStatusでフィードされたトゥートの場合、UriにステータスIDが含まれている
					Matcher m = reUriOStatusToot.matcher( ts.uri );
					if( m.find() ){
						status_id_original = Long.parseLong( m.group( 2 ), 10 );
						// host_original = m.group( 1 );
					}
				}catch( Throwable ex ){
					log.e( ex, "openStatusOtherInstance: cant parse tag: %s", ts.uri );
				}
				
				openStatusOtherInstance( pos, access_info, status.url
					, status_id_original
					, status.host_access, status.id
				);
			}
		}
	}
	
	void openStatusOtherInstance(
		final int pos
		, @Nullable final SavedAccount access_info
		, @NonNull final String url
		, final long status_id_original
		, final String host_access, final long status_id_access
	){
		ActionsDialog dialog = new ActionsDialog();
		
		final String host_original = Uri.parse( url ).getAuthority();
		
		// 選択肢：ブラウザで表示する
		dialog.addAction( getString( R.string.open_web_on_host, host_original ), new Runnable() {
			@Override public void run(){
				openChromeTab( pos, access_info, url, true );
			}
		} );
		
		ArrayList< SavedAccount > local_account_list = new ArrayList<>();
		ArrayList< SavedAccount > access_account_list = new ArrayList<>();
		ArrayList< SavedAccount > other_account_list = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( ActMain.this, log ) ){
			// 疑似アカウントは後でまとめて処理する
			if( a.isPseudo() ) continue;
			if( status_id_original >= 0L && host_original.equalsIgnoreCase( a.host ) ){
				// アクセス情報＋ステータスID でアクセスできるなら
				// 同タンスのアカウントならステータスIDの変換なしに表示できる
				local_account_list.add( a );
			}else if( status_id_access >= 0L && host_access.equalsIgnoreCase( a.host ) ){
				// 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
				access_account_list.add( a );
			}else{
				// 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
				other_account_list.add( a );
			}
		}
		
		// 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
		if( local_account_list.isEmpty() ){
			if( status_id_original >= 0L ){
				dialog.addAction( getString( R.string.open_in_pseudo_account, "?@" + host_original ), new Runnable() {
					@Override public void run(){
						SavedAccount sa = addPseudoAccount( host_original );
						if( sa != null ){
							openStatusLocal( pos, sa, status_id_original );
						}
					}
				} );
			}else{
				dialog.addAction( getString( R.string.open_in_pseudo_account, "?@" + host_original ), new Runnable() {
					@Override public void run(){
						SavedAccount sa = addPseudoAccount( host_original );
						if( sa != null ){
							openStatusRemote( pos, sa, url );
						}
					}
				} );
			}
		}
		
		// ローカルアカウント
		Collections.sort( local_account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		for( SavedAccount a : local_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( getString( R.string.open_in_account, AcctColor.getNickname( a.acct ) ), new Runnable() {
				@Override public void run(){
					openStatusLocal( pos, _a, status_id_original );
				}
			} );
		}
		
		// アクセスしたアカウント
		Collections.sort( access_account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		for( SavedAccount a : access_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( getString( R.string.open_in_account, AcctColor.getNickname( a.acct ) ), new Runnable() {
				@Override public void run(){
					openStatusLocal( pos, _a, status_id_access );
				}
			} );
		}
		
		// その他の実アカウント
		Collections.sort( other_account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		for( SavedAccount a : other_account_list ){
			final SavedAccount _a = a;
			dialog.addAction( getString( R.string.open_in_account, AcctColor.getNickname( a.acct ) ), new Runnable() {
				@Override public void run(){
					openStatusRemote( pos, _a, url );
				}
			} );
		}
		
		dialog.show( this, getString( R.string.open_status_from ) );
	}
	
	static final Pattern reDetailedStatusTime = Pattern.compile( "<a\\b[^>]*?\\bdetailed-status__datetime\\b[^>]*href=\"https://[^/]+/@[^/]+/(\\d+)\"" );
	
	public void openStatusRemote(
		final int pos
		, final SavedAccount access_info
		, final String remote_status_url
	){
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			long local_status_id = - 1L;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActMain.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( access_info );
				
				TootApiResult result;
				if( access_info.isPseudo() ){
					result = client.getHttp( remote_status_url );
					if( result != null && result.json != null ){
						try{
							Matcher m = reDetailedStatusTime.matcher( result.json );
							if( m.find() ){
								local_status_id = Long.parseLong( m.group( 1 ), 10 );
							}
						}catch( Throwable ex ){
							log.e( ex, "openStatusRemote: can't parse status id from HTML data." );
						}
						if( local_status_id == - 1L ){
							result = new TootApiResult( getString( R.string.status_id_conversion_failed ) );
						}
					}
				}else{
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					String path = String.format( Locale.JAPAN, Column.PATH_SEARCH, Uri.encode( remote_status_url ) );
					path = path + "&resolve=1";
					result = client.request( path );
					if( result != null && result.object != null ){
						TootResults tmp = TootResults.parse( ActMain.this, access_info, result.object );
						if( tmp != null && tmp.statuses != null && ! tmp.statuses.isEmpty() ){
							TootStatus status = tmp.statuses.get( 0 );
							local_status_id = status.id;
							log.d( "status id conversion %s => %s", remote_status_url, status.id );
						}
						if( local_status_id == - 1L ){
							result = new TootApiResult( getString( R.string.status_id_conversion_failed ) );
						}
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
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				if( result == null ){
					// cancelled.
				}else if( local_status_id != - 1L ){
					openStatusLocal( pos, access_info, local_status_id );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
			}
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setMessage( getString( R.string.progress_synchronize_toot ) );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
	////////////////////////////////////////
	// profile pin
	
	public void setProfilePin( @NonNull final SavedAccount access_info, final TootStatusLike status, final boolean bSet ){
		if( status == null ) return;
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		//
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
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
				TootApiResult result;

				Request.Builder request_builder = new Request.Builder()
					.post( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, ""
					) );
				
				result = client.request(
					( bSet
						? "/api/v1/statuses/" + status.id + "/pin"
						: "/api/v1/statuses/" + status.id + "/unpin"
					)
					, request_builder );
				if( result != null && result.object != null ){
					new_status = TootStatus.parse( ActMain.this, access_info, result.object );
				}
				
				return result;
				
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){

				try{
					progress.dismiss();
				}catch(Throwable ignored){
					
				}

				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){

					for( Column column : app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override public boolean onIterate( SavedAccount account, TootStatus status ){
								status.pinned = bSet;
								return true;
							}
						} );
					}
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}

				// 結果に関わらず、更新中状態から復帰させる
				showColumnMatchAccount( access_info );
			}
			
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setMessage( getString(R.string.profile_pin_progress));
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();

		task.executeOnExecutor( App1.task_executor );
	}
	
	////////////////////////////////////////
	// delete notification
	
	public void deleteNotificationOne( @NonNull final SavedAccount access_info, @NonNull final TootNotification notification ){
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
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
					.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, "id=" + Long.toString( notification.id )
						)
					);
				
				return client.request(
					"/api/v1/notifications/dismiss"
					, request_builder );
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				if( result == null ){
					// cancelled.
				}else if( result.object != null ){
					// 成功したら空オブジェクトが返される
					for( Column column : app_state.column_list ){
						column.removeNotificationOne( access_info, notification );
					}
					Utils.showToast( ActMain.this, true, R.string.delete_succeeded );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
			}
		};
		
		task.executeOnExecutor( App1.task_executor );
	}
	
	////////////////////////////////////////
	
	public void toggleConversationMute( @NonNull final SavedAccount access_info, @NonNull final TootStatusLike status ){
		final boolean bMute = ! status.muted;
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
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
					.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" ) );
				
				TootApiResult result = client.request(
					"/api/v1/statuses/" + status.id + ( bMute ? "/mute" : "/unmute" )
					, request_builder
				);
				
				if( result != null && result.object != null ){
					new_status = TootStatus.parse( ActMain.this, access_info, result.object );
				}
				
				return result;
			}
			
			@Override protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			TootStatus new_status;
			
			@Override protected void onPostExecute( TootApiResult result ){
				if( result == null ){
					// cancelled.
				}else if( new_status != null ){
					for( Column column : app_state.column_list ){
						column.findStatus( access_info.host, new_status.id, new Column.StatusEntryCallback() {
							@Override
							public boolean onIterate( SavedAccount account, TootStatus status ){
								if( access_info.acct.equalsIgnoreCase( account.acct ) ){
									status.muted = bMute;
								}
								return true;
							}
						} );
					}
					Utils.showToast( ActMain.this, true, bMute ? R.string.mute_succeeded : R.string.unmute_succeeded );
				}else{
					Utils.showToast( ActMain.this, true, result.error );
				}
			}
		};
		
		task.executeOnExecutor( App1.task_executor );
	}
	
	////////////////////////////////////////
	
	private void performAccountSetting(){
		AccountPicker.pick( this, true, true
			, getString( R.string.account_picker_open_setting )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					ActAccountSetting.open( ActMain.this, ai, REQUEST_CODE_ACCOUNT_SETTING );
				}
			} );
	}
	
	////////////////////////////////////////////////////////
	// column list
	
	private void openColumnList(){
		if( pager_adapter != null ){
			ActColumnList.open( this, pager.getCurrentItem(), REQUEST_CODE_COLUMN_LIST );
		}else{
			ActColumnList.open( this, - 1, REQUEST_CODE_COLUMN_LIST );
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
			TootRelationShip.List list = TootRelationShip.parseList( r2.array );
			if( ! list.isEmpty() ){
				TootRelationShip item = rr.relation = list.get( 0 );
				long now = System.currentTimeMillis();
				UserRelation.save1( now, access_info.db_id, item );
			}
		}
		return rr;
	}
	
	void callFollow(
		@NonNull final SavedAccount access_info
		, @NonNull final TootAccount who
		, final boolean bFollow
		, boolean bConfirmed
		, @Nullable final RelationChangedCallback callback
	){
		if( access_info.isMe( who ) ){
			Utils.showToast( this, false, R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmed ){
			if( bFollow && who.locked ){
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_request_who_from, who.decoded_display_name, AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							reloadAccountSetting( access_info );
						}
						
						@Override public void onOK(){
							//noinspection ConstantConditions
							callFollow( access_info, who, bFollow, true, callback );
						}
					}
				);
				return;
			}else if( bFollow ){
				String msg = getString( R.string.confirm_follow_who_from
					, who.decoded_display_name
					, AcctColor.getNickname( access_info.acct )
				);
				
				DlgConfirm.open( this
					, msg
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow = bv;
							access_info.saveSetting();
							reloadAccountSetting( access_info );
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
					, getString( R.string.confirm_unfollow_who_from, who.decoded_display_name, AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_unfollow;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_unfollow = bv;
							access_info.saveSetting();
							reloadAccountSetting( access_info );
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
							TootAccount remote_who = TootAccount.parse( ActMain.this, access_info, result.object );
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
							relation = TootRelationShip.parse( result.object );
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
					}else if( ! bFollow && relation.requested ){
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
		}.executeOnExecutor( App1.task_executor );
	}
	
	// acct で指定したユーザをリモートフォローする
	void callRemoteFollow( final SavedAccount access_info
		, final String acct, final boolean locked, boolean bConfirmed, final RelationChangedCallback callback
	){
		if( access_info.isMe( acct ) ){
			Utils.showToast( this, false, R.string.it_is_you );
			return;
		}
		
		if( ! bConfirmed ){
			if( locked ){
				DlgConfirm.open( this
					, getString( R.string.confirm_follow_request_who_from, AcctColor.getNickname( acct ), AcctColor.getNickname( access_info.acct ) )
					, new DlgConfirm.Callback() {
						@Override public boolean isConfirmEnabled(){
							return access_info.confirm_follow_locked;
						}
						
						@Override public void setConfirmEnabled( boolean bv ){
							access_info.confirm_follow_locked = bv;
							access_info.saveSetting();
							reloadAccountSetting( access_info );
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
					, getString( R.string.confirm_follow_who_from, AcctColor.getNickname( acct ), AcctColor.getNickname( access_info.acct ) )
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
						TootAccount remote_who = TootAccount.parse( ActMain.this, access_info, result.object );
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
					
				}else if( locked && result.response != null && result.response.code() == 422 ){
					Utils.showToast( ActMain.this, false, R.string.cant_follow_locked_user );
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
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
						relation = TootRelationShip.parse( result.object );
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
						for( Column column : app_state.column_list ){
							column.removeStatusByAccount( access_info, who.id );
						}
					}else{
						for( Column column : app_state.column_list ){
							column.removeFromMuteList( access_info, who.id );
						}
					}
					
					if( callback != null ) callback.onRelationChanged();
					
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
			
		}.executeOnExecutor( App1.task_executor );
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
						relation = TootRelationShip.parse( result.object );
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
					
					for( Column column : app_state.column_list ){
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
		}.executeOnExecutor( App1.task_executor );
	}
	
	void callDomainBlock( final SavedAccount access_info, final String domain, final boolean bBlock, final RelationChangedCallback callback ){
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
				
				Request.Builder request_builder = new Request.Builder();
				
				if( bBlock ){
					request_builder.post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "domain=" + Uri.encode( domain )
						) );
					
				}else{
					request_builder.delete(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
							, "domain=" + Uri.encode( domain )
						) );
				}
				TootApiResult result = client.request( "/api/v1/domain_blocks", request_builder );
				
				if( result != null ){
					if( result.object != null ){
						empty_object = result.object;
					}
				}
				
				return result;
			}
			
			JSONObject empty_object;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( empty_object != null ){
					
					for( Column column : app_state.column_list ){
						column.onDomainBlockChanged( access_info, domain, bBlock );
					}
					
					Utils.showToast( ActMain.this, false, bBlock ? R.string.block_succeeded : R.string.unblock_succeeded );
					
					if( callback != null ) callback.onRelationChanged();
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
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
					
					for( Column column : app_state.column_list ){
						column.removeFollowRequest( access_info, who.id );
					}
					
					Utils.showToast( ActMain.this, false, ( bAllow ? R.string.follow_request_authorized : R.string.follow_request_rejected ), who.decoded_display_name );
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
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
					for( Column column : app_state.column_list ){
						column.removeStatus( access_info, status_id );
					}
				}else{
					Utils.showToast( ActMain.this, false, result.error );
				}
			}
		}.executeOnExecutor( App1.task_executor );
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
			
		}.executeOnExecutor( App1.task_executor );
	}
	
	void openReportForm( @NonNull final SavedAccount account, @NonNull final TootAccount who, @NonNull final TootStatus status ){
		ReportForm.showReportForm( this, who, status, new ReportForm.ReportFormCallback() {
			
			@Override public void startReport( final Dialog dialog, String comment ){
				
				// レポートの送信を開始する
				callReport( account, who, status, comment, new ReportCompleteCallback() {
					@Override public void onReportComplete( TootApiResult result ){
						
						// 成功したらダイアログを閉じる
						try{
							dialog.dismiss();
						}catch( Throwable ignored ){
							// IllegalArgumentException がたまに出る
						}
						Utils.showToast( ActMain.this, false, R.string.report_completed );
					}
				} );
			}
		} );
	}
	
	////////////////////////////////////////////////
	
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
					for( Column column : app_state.column_list ){
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
			
		}.executeOnExecutor( App1.task_executor );
	}
	
	private void showFooterColor(){
		int footer_button_bg_color = pref.getInt( Pref.KEY_FOOTER_BUTTON_BG_COLOR, 0 );
		int footer_button_fg_color = pref.getInt( Pref.KEY_FOOTER_BUTTON_FG_COLOR, 0 );
		int footer_tab_bg_color = pref.getInt( Pref.KEY_FOOTER_TAB_BG_COLOR, 0 );
		int footer_tab_divider_color = pref.getInt( Pref.KEY_FOOTER_TAB_DIVIDER_COLOR, 0 );
		int footer_tab_indicator_color = pref.getInt( Pref.KEY_FOOTER_TAB_INDICATOR_COLOR, 0 );
		int c = footer_button_bg_color;
		if( c == 0 ){
			btnMenu.setBackgroundResource( R.drawable.btn_bg_ddd );
			btnToot.setBackgroundResource( R.drawable.btn_bg_ddd );
			btnQuickToot.setBackgroundResource( R.drawable.btn_bg_ddd );
		}else{
			int fg = ( footer_button_fg_color != 0
				? footer_button_fg_color
				: Styler.getAttributeColor( this, R.attr.colorRippleEffect ) );
			ViewCompat.setBackground( btnToot, Styler.getAdaptiveRippleDrawable( c, fg ) );
			ViewCompat.setBackground( btnMenu, Styler.getAdaptiveRippleDrawable( c, fg ) );
			ViewCompat.setBackground( btnQuickToot, Styler.getAdaptiveRippleDrawable( c, fg ) );
		}
		
		c = footer_button_fg_color;
		if( c == 0 ){
			Styler.setIconDefaultColor( this, btnToot, R.attr.ic_edit );
			Styler.setIconDefaultColor( this, btnMenu, R.attr.ic_hamburger );
			Styler.setIconDefaultColor( this, btnQuickToot, R.attr.btn_post );
		}else{
			Styler.setIconCustomColor( this, btnToot, c, R.attr.ic_edit );
			Styler.setIconCustomColor( this, btnMenu, c, R.attr.ic_hamburger );
			Styler.setIconCustomColor( this, btnQuickToot, c, R.attr.btn_post );
		}
		
		c = footer_tab_bg_color;
		if( c == 0 ){
			svColumnStrip.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorColumnStripBackground ) );
			llQuickTootBar.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorColumnStripBackground ) );
		}else{
			svColumnStrip.setBackgroundColor( c );
			svColumnStrip.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorColumnStripBackground ) );
		}
		
		c = footer_tab_divider_color;
		if( c == 0 ){
			vFooterDivider1.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorImageButton ) );
			vFooterDivider2.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorImageButton ) );
		}else{
			vFooterDivider1.setBackgroundColor( c );
			vFooterDivider2.setBackgroundColor( c );
		}
		
		c = footer_tab_indicator_color;
		llColumnStrip.setColor( c );
	}
	
	ArrayList< SavedAccount > makeAccountListNonPseudo( LogCategory log ){
		ArrayList< SavedAccount > dst = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( ActMain.this, log ) ){
			if( ! a.isPseudo() ){
				dst.add( a );
			}
		}
		Collections.sort( dst, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		return dst;
	}
	
	// 別アカ操作と別タンスの関係
	static final int NOT_CROSS_ACCOUNT = 1;
	static final int CROSS_ACCOUNT_SAME_INSTANCE = 2;
	static final int CROSS_ACCOUNT_REMOTE_INSTANCE = 3;
	
	int calcCrossAccountMode( @NonNull final SavedAccount timeline_account, @NonNull final SavedAccount action_account ){
		if( ! timeline_account.host.equalsIgnoreCase( action_account.host ) ){
			return CROSS_ACCOUNT_REMOTE_INSTANCE;
		}else if( ! timeline_account.acct.equalsIgnoreCase( action_account.acct ) ){
			return CROSS_ACCOUNT_SAME_INSTANCE;
		}else{
			return NOT_CROSS_ACCOUNT;
		}
	}
	
	void openBoostFromAnotherAccount( @NonNull final SavedAccount timeline_account, @Nullable final TootStatusLike status ){
		if( status == null ) return;
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_boost )
			, makeAccountListNonPseudo( log )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount action_account ){
					performBoost(
						action_account
						, status
						, calcCrossAccountMode( timeline_account, action_account )
						, true
						, false
						, boost_complete_callback
					);
				}
			} );
	}
	
	void openFavouriteFromAnotherAccount( @NonNull final SavedAccount timeline_account, final TootStatusLike status ){
		if( status == null ) return;
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_favourite )
			, makeAccountListNonPseudo( log )
			, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount action_account ){
					performFavourite(
						action_account
						, status
						, calcCrossAccountMode( timeline_account, action_account )
						, true
						, favourite_complete_callback
					);
				}
			} );
	}
	
	void openReplyFromAnotherAccount( final TootStatusLike status ){
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_reply )
			, makeAccountListNonPseudo( log ), new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					if( status instanceof MSPToot ){
						// MSPの場合、status.url は https://instance/@user/:status_id の形式になる
						performReplyRemote( ai, status.url );
					}else if( status instanceof TootStatus ){
						if( ai.host.equalsIgnoreCase( status.host_access ) ){
							performReply( ai, (TootStatus) status );
						}else{
							performReplyRemote( ai, status.url );
						}
					}
				}
			} );
	}
	
	//	void openReplyFromAnotherAccount( @NonNull final SavedAccount access_info, final String status_url,final long status_id ){
	//
	//		final String status_host = getHostFromStatusUrl(status_url);
	//		if( status_host ==null ) return;
	//
	//		AccountPicker.pick( this, false, false
	//			, getString( R.string.account_picker_reply )
	//			, makeAccountListNonPseudo( log ), new AccountPicker.AccountPickerCallback() {
	//				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
	//					performReplyRemote( ai,status_url,status_id );
	//				}
	//			} );
	//	}
	//	void openFollowFromAnotherAccount( @NonNull SavedAccount access_info, TootStatus status ){
	//		if( status == null ) return;
	//		openFollowFromAnotherAccount( access_info, status.account );
	//	}
	
	void openFollowFromAnotherAccount( @NonNull SavedAccount access_info, final TootAccount account ){
		if( account == null ) return;
		final String who_acct = access_info.getFullAcct( account );
		AccountPicker.pick( this, false, false
			, getString( R.string.account_picker_follow )
			, makeAccountListNonPseudo( log ), new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					callRemoteFollow( ai, who_acct, account.locked, false, follow_complete_callback );
				}
			} );
	}
	
	/////////////////////////////////////////////////////////////////////////
	// タブレット対応で必要になった関数など
	
	private boolean closeColumnSetting(){
		if( pager_adapter != null ){
			ColumnViewHolder vh = pager_adapter.getColumnViewHolder( pager.getCurrentItem() );
			if( vh != null && vh.isColumnSettingShown() ){
				vh.closeColumnSetting();
				return true;
			}
		}else{
			for( int i = 0, ie = tablet_layout_manager.getChildCount() ; i < ie ; ++ i ){
				View v = tablet_layout_manager.getChildAt( i );
				TabletColumnViewHolder holder = (TabletColumnViewHolder) tablet_pager.getChildViewHolder( v );
				if( holder != null && holder.vh.isColumnSettingShown() ){
					holder.vh.closeColumnSetting();
					return true;
				}
			}
		}
		return false;
	}
	
	private int getDefaultInsertPosition(){
		if( pager_adapter != null ){
			return 1 + pager.getCurrentItem();
		}else{
			return Integer.MAX_VALUE;
		}
	}
	
	int nextPosition( Column column ){
		if( column != null ){
			int pos = app_state.column_list.indexOf( column );
			if( pos != - 1 ) return pos + 1;
		}
		return getDefaultInsertPosition();
	}
	
	private int addColumn( Column column, int index ){
		int size = app_state.column_list.size();
		if( index > size ) index = size;
		
		if( pager_adapter != null ){
			pager.setAdapter( null );
			app_state.column_list.add( index, column );
			pager.setAdapter( pager_adapter );
		}else{
			app_state.column_list.add( index, column );
			resizeColumnWidth();
		}
		
		app_state.saveColumnList();
		updateColumnStrip();
		
		return index;
	}
	
	private void removeColumn( Column column ){
		int idx_column = app_state.column_list.indexOf( column );
		if( idx_column == - 1 ) return;
		
		if( pager_adapter != null ){
			pager.setAdapter( null );
			app_state.column_list.remove( idx_column ).dispose();
			pager.setAdapter( pager_adapter );
		}else{
			app_state.column_list.remove( idx_column ).dispose();
			resizeColumnWidth();
		}
		
		app_state.saveColumnList();
		updateColumnStrip();
	}
	
	private void setOrder( ArrayList< Integer > new_order ){
		if( pager_adapter != null ){
			pager.setAdapter( null );
		}
		
		ArrayList< Column > tmp_list = new ArrayList<>();
		HashSet< Integer > used_set = new HashSet<>();
		
		for( Integer i : new_order ){
			used_set.add( i );
			tmp_list.add( app_state.column_list.get( i ) );
		}
		
		for( int i = 0, ie = app_state.column_list.size() ; i < ie ; ++ i ){
			if( used_set.contains( i ) ) continue;
			app_state.column_list.get( i ).dispose();
		}
		app_state.column_list.clear();
		app_state.column_list.addAll( tmp_list );
		
		if( pager_adapter != null ){
			pager.setAdapter( pager_adapter );
		}else{
			resizeColumnWidth();
		}
		
		app_state.saveColumnList();
		updateColumnStrip();
	}
	
	int nScreenColumn;
	int nColumnWidth;
	
	private void resizeColumnWidth(){
		
		int column_w_min_dp = COLUMN_WIDTH_MIN_DP;
		String sv = pref.getString( Pref.KEY_COLUMN_WIDTH, "" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				int iv = Integer.parseInt( sv );
				if( iv >= 100 ){
					column_w_min_dp = iv;
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		DisplayMetrics dm = getResources().getDisplayMetrics();
		
		final int sw = dm.widthPixels;
		
		float density = dm.density;
		int column_w_min = (int) ( 0.5f + column_w_min_dp * density );
		if( column_w_min < 1 ) column_w_min = 1;
		
		if( sw < column_w_min * 2 ){
			// 最小幅で2つ表示できないのなら1カラム表示
			tablet_pager_adapter.setColumnWidth( sw );
			resizeAutoCW( sw );
		}else{
			
			// カラム最小幅から計算した表示カラム数
			nScreenColumn = sw / column_w_min;
			if( nScreenColumn < 1 ) nScreenColumn = 1;
			
			// データのカラム数より大きくならないようにする
			// (でも最小は1)
			int column_count = app_state.column_list.size();
			if( column_count > 0 ){
				if( nScreenColumn > column_count ){
					nScreenColumn = column_count;
				}
			}
			
			// 表示カラム数から計算したカラム幅
			int column_w = sw / nScreenColumn;
			
			// 最小カラム幅の1.5倍よりは大きくならないようにする
			int column_w_max = (int) ( 0.5f + column_w_min * 1.5f );
			if( column_w > column_w_max ){
				column_w = column_w_max;
			}
			resizeAutoCW( column_w );
			
			nColumnWidth = column_w;
			tablet_pager_adapter.setColumnWidth( column_w );
			tablet_snap_helper.setColumnWidth( column_w );
		}
		
		// 並べ直す
		tablet_pager_adapter.notifyDataSetChanged();
	}
	

	
	private void scrollToColumn( int index, boolean bAlign ){
		scrollColumnStrip( index );
		
		if( pager_adapter != null ){
			pager.setCurrentItem( index, true );
		}else if( ! bAlign ){
			// 指定したカラムが画面内に表示されるように動いてくれるようだ
			tablet_pager.smoothScrollToPosition( index );
		}else{
			// 指定位置が表示範囲の左端にくるようにスクロール
			tablet_pager.scrollToPosition( index );
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////
	
	private void importAppData( final Uri uri ){
		// remove all columns
		{
			if( pager_adapter != null ){
				pager.setAdapter( null );
			}
			for( Column c : app_state.column_list ){
				c.dispose();
			}
			app_state.column_list.clear();
			if( pager_adapter != null ){
				pager.setAdapter( pager_adapter );
			}else{
				resizeColumnWidth();
			}
			updateColumnStrip();
		}
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, String, ArrayList< Column > > task = new AsyncTask< Void, String, ArrayList< Column > >() {
			
			void setProgressMessage( final String sv ){
				Utils.runOnMainThread( new Runnable() {
					@Override public void run(){
						progress.setMessage( sv );
					}
				} );
				
			}
			
			@Override protected ArrayList< Column > doInBackground( Void... params ){
				try{
					setProgressMessage( "import data to local storage..." );
					
					File cache_dir = getCacheDir();
					//noinspection ResultOfMethodCallIgnored
					cache_dir.mkdir();
					File file = new File( cache_dir, "SubwayTooter." + android.os.Process.myPid() + "." + android.os.Process.myTid() + ".json" );
					
					// ローカルファイルにコピーする
					InputStream is = getContentResolver().openInputStream( uri );
					if( is == null ){
						Utils.showToast( ActMain.this, true, "openInputStream failed." );
						return null;
					}
					try{
						FileOutputStream os = new FileOutputStream( file );
						try{
							IOUtils.copy( is, os );
						}finally{
							IOUtils.closeQuietly( os );
							
						}
					}finally{
						IOUtils.closeQuietly( is );
					}
					
					// 通知サービスを止める
					setProgressMessage( "reset Notification..." );
					PollingService.queueAppDataImportBefore( ActMain.this );
					while( PollingService.mBusyAppDataImportBefore.get() ){
						Thread.sleep( 100L );
					}
					
					// JSONを読みだす
					setProgressMessage( "reading app data..." );
					Reader r = new InputStreamReader( new FileInputStream( file ), "UTF-8" );
					try{
						JsonReader reader = new JsonReader( r );
						return AppDataExporter.decodeAppData( ActMain.this, reader );
					}finally{
						IOUtils.closeQuietly( r );
					}
				}catch( Throwable ex ){
					log.trace( ex );
					Utils.showToast( ActMain.this, ex, "importAppData failed." );
				}
				return null;
			}
			
			@Override protected void onCancelled( ArrayList< Column > result ){
				super.onCancelled( result );
			}
			
			@Override protected void onPostExecute( ArrayList< Column > result ){
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				
				try{
					getWindow().clearFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
				}catch( Throwable ignored ){
				}
				
				if( isCancelled() || result == null ){
					// cancelled.
					return;
				}
				
				{
					if( pager_adapter != null ){
						pager.setAdapter( null );
					}
					app_state.column_list.clear();
					app_state.column_list.addAll( result );
					app_state.saveColumnList();
					
					if( pager_adapter != null ){
						pager.setAdapter( pager_adapter );
					}else{
						resizeColumnWidth();
					}
					updateColumnStrip();
				}
				
				// 通知サービスをリスタート
				PollingService.queueAppDataImportAfter( ActMain.this );
				
			}
		};
		
		try{
			getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		}catch( Throwable ignored ){
		}
		
		progress.setIndeterminate( true );
		progress.setCancelable( false );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
	public void openTimelineFor( @NonNull String host ){
		final ArrayList< SavedAccount > account_list = new ArrayList<>();
		for( SavedAccount a : SavedAccount.loadAccountList( ActMain.this, log ) ){
			if( host.equalsIgnoreCase( a.host ) ) account_list.add( a );
		}
		if( account_list.isEmpty() ){
			
			SavedAccount ai = addPseudoAccount( host );
			if( ai != null ){
				addColumn( getDefaultInsertPosition(), ai, Column.TYPE_LOCAL );
			}
			
		}else{
			AccountPicker.pick( this, true, false
				, getString( R.string.account_picker_add_timeline_of, host )
				, account_list, new AccountPicker.AccountPickerCallback() {
					@Override public void onAccountPicked( @NonNull SavedAccount ai ){
						addColumn( getDefaultInsertPosition(), ai, Column.TYPE_LOCAL );
					}
				} );
		}
	}
	
	@Override public void onDrawerSlide( View drawerView, float slideOffset ){
		if( post_helper != null ){
			post_helper.closeAcctPopup();
		}
	}
	
	@Override public void onDrawerOpened( View drawerView ){
		if( post_helper != null ){
			post_helper.closeAcctPopup();
		}
	}
	
	@Override public void onDrawerClosed( View drawerView ){
		if( post_helper != null ){
			post_helper.closeAcctPopup();
		}
	}
	
	@Override public void onDrawerStateChanged( int newState ){
		if( post_helper != null ){
			post_helper.closeAcctPopup();
		}
	}
	
	public void openInstanceInformation( int pos, String host ){
		addColumn( pos, SavedAccount.getNA(), Column.TYPE_INSTANCE_INFORMATION, host );
	}
	
	private final Runnable proc_updateRelativeTime = new Runnable() {
		@Override public void run(){
			handler.removeCallbacks( proc_updateRelativeTime );
			if( ! bResume ) return;
			for( Column c : app_state.column_list ){
				c.fireShowContent();
			}
			if( pref.getBoolean( Pref.KEY_RELATIVE_TIMESTAMP, false ) ){
				handler.postDelayed( proc_updateRelativeTime, 10000L );
			}
		}
	};
	
	int nAutoCwCellWidth = 0;
	int nAutoCwLines =0;
	
	private void resizeAutoCW( int column_w ){
		String sv = pref.getString( Pref.KEY_AUTO_CW_LINES, "" );
		nAutoCwLines = Utils.parse_int( sv, - 1 );
		if( nAutoCwLines > 0 ){
			int lv_pad = (int) ( 0.5f + 12 * density );
			int icon_width = (int) ( 0.5f + 48 * density );
			int icon_end = (int) ( 0.5f + 4 * density );
			nAutoCwCellWidth = column_w - lv_pad*2 - icon_width - icon_end;
		}
		// この後各カラムは再描画される
	}

	void checkAutoCW( @NonNull TootStatusLike status, @NonNull CharSequence text ){
		if( nAutoCwCellWidth <= 0 ){
			// 設定が無効
			status.auto_cw = null;
			return;
		}

		TootStatusLike.AutoCW a = status.auto_cw;
		if( a != null && a.refActivity.get() == ActMain.this && a.cell_width == nAutoCwCellWidth ){
			// 以前に計算した値がまだ使える
			return;
		}

		if( a == null ) a = status.auto_cw = new TootStatusLike.AutoCW();

		// 計算時の条件(文字フォント、文字サイズ、カラム幅）を覚えておいて、再利用時に同じか確認する
		a.refActivity =new WeakReference< Object >( ActMain.this );
		a.cell_width = nAutoCwCellWidth;
		a.decoded_spoiler_text = null;
		
		// テキストをレイアウトして行数を測定

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( nAutoCwCellWidth,LinearLayout.LayoutParams.WRAP_CONTENT );
		TextView tv = new TextView(this);
		tv.setLayoutParams( lp );
		if( ! Float.isNaN( timeline_font_size_sp ) ){
			tv.setTextSize( timeline_font_size_sp );
		}
		if( timeline_font != null ){
			tv.setTypeface( timeline_font );
		}
		tv.setText( text );
		tv.measure(
			View.MeasureSpec.makeMeasureSpec( nAutoCwCellWidth, View.MeasureSpec.EXACTLY)
			,View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED)
		);
		Layout l = tv.getLayout();
		if( l != null ){
			a.originalLineCount = l.getLineCount();
			
			if( nAutoCwLines > 0 ){
				int line_count = l.getLineCount();
				if( line_count > nAutoCwLines ){
					SpannableStringBuilder sb = new SpannableStringBuilder();
					sb.append( getString( R.string.auto_cw_prefix ) );
					sb.append( text, 0, l.getLineEnd( nAutoCwLines - 1 ) );
					int last = sb.length();
					while( last > 0 ){
						char c = sb.charAt( last - 1 );
						if( c == '\n' || Character.isWhitespace( c ) ){
							-- last;
							continue;
						}
						break;
					}
					if( last < sb.length() ){
						sb.delete( last, sb.length() );
					}
					sb.append( '…' );
					a.decoded_spoiler_text = sb;
				}
			}
		}
	}
}
