package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.action.Action_Account;
import jp.juggler.subwaytooter.action.Action_App;
import jp.juggler.subwaytooter.action.Action_HashTag;
import jp.juggler.subwaytooter.action.Action_Toot;
import jp.juggler.subwaytooter.action.Action_User;
import jp.juggler.subwaytooter.action.RelationChangedCallback;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgTextInput;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.ChromeTabOpener;
import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyClickableSpan;
import jp.juggler.subwaytooter.util.PostHelper;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.ColumnStripLinearLayout;
import jp.juggler.subwaytooter.view.GravitySnapHelper;
import jp.juggler.subwaytooter.view.MyEditText;

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
	public Handler handler;
	public AppState app_state;
	
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
		
		PollingWorker.queueUpdateNotification( this );
		
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
		log.d( "onSaveInstanceState" );
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
		log.d( "onRestoreInstanceState" );
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
	
	boolean bStart;
	
	@Override public boolean isActivityStart(){
		return bStart;
	}
	
	@Override protected void onStart(){
		super.onStart();
		
		bStart = true;
		log.d( "onStart" );
		
		// アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
		{
			ArrayList< Integer > new_order = new ArrayList<>();
			for( int i = 0, ie = app_state.column_list.size() ; i < ie ; ++ i ){
				Column column = app_state.column_list.get( i );
				
				if( ! column.access_info.isNA() ){
					SavedAccount sa = SavedAccount.loadAccount( ActMain.this, column.access_info.db_id );
					if( sa == null ) continue;
				}
				
				new_order.add( i );
			}
			
			if( new_order.size() != app_state.column_list.size() ){
				setOrder( new_order );
			}
		}
		
		// 各カラムのアカウント設定を読み直す
		reloadAccountSetting();
		
		// 投稿直後ならカラムの再取得を行う
		refreshAfterPost();
		
		// 画面復帰時に再取得やストリーミング開始を行う
		for( Column column : app_state.column_list ){
			column.onStart( this );
		}
		
		// カラムの表示範囲インジケータを更新
		updateColumnStripSelection( - 1, - 1f );
		
		// 相対時刻表示
		proc_updateRelativeTime.run();
		
	}
	
	@Override protected void onStop(){
		
		log.d( "onStop" );
		
		bStart = false;
		
		handler.removeCallbacks( proc_updateRelativeTime );
		
		post_helper.closeAcctPopup();
		
		closeListItemPopup();
		
		app_state.stream_reader.stopAll();
		
		super.onStop();
		
	}
	
	@Override protected void onResume(){
		super.onResume();
		log.d( "onResume" );
		
		MyClickableSpan.link_callback = new WeakReference<>( link_click_listener );
		
		if( pref.getBoolean( Pref.KEY_DONT_SCREEN_OFF, false ) ){
			getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		}else{
			getWindow().clearFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
		}
		
		// 外部から受け取ったUriの処理
		Uri uri = ActCallback.last_uri.getAndSet( null );
		if( uri != null ){
			handleIntentUri( uri );
		}
		
		// 外部から受け取ったUriの処理
		Intent intent = ActCallback.sent_intent.getAndSet( null );
		if( intent != null ){
			handleSentIntent( intent );
		}
		
	}
	
	@Override protected void onPause(){
		log.d( "onPause" );
		
		// 最後に表示していたカラムの位置
		int column_pos;
		if( pager_adapter != null ){
			column_pos = pager.getCurrentItem();
		}else{
			column_pos = tablet_layout_manager.findFirstVisibleItemPosition();
		}
		pref.edit().putInt( Pref.KEY_LAST_COLUMN_POS, column_pos ).apply();
		
		super.onPause();
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
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnMenu:
			if( ! drawer.isDrawerOpen( Gravity.START ) ){
				drawer.openDrawer( Gravity.START );
			}
			break;
		
		case R.id.btnToot:
			Action_Account.openPost( ActMain.this );
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
		
		post_helper.content = etQuickToot.getText().toString().trim();
		post_helper.spoiler_text = null;
		post_helper.visibility = account.visibility;
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
					if( post_helper != null ){
						post_helper.setInstance( column.access_info.isNA() ? null : column.access_info.host );
					}
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
	public static final int REQUEST_CODE_COLUMN_LIST = 1;
	public static final int REQUEST_CODE_ACCOUNT_SETTING = 2;
	public static final int REQUEST_APP_ABOUT = 3;
	static final int REQUEST_CODE_NICKNAME = 4;
	public static final int REQUEST_CODE_POST = 5;
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
						Action_Account.timeline( ActMain.this, getDefaultInsertPosition(), true, Column.TYPE_SEARCH, search, true );
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
			if( resultCode == ActText.RESULT_SEARCH_MSP ){
				String text = data.getStringExtra( Intent.EXTRA_TEXT );
				addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_MSP, text );
			}else if( resultCode == ActText.RESULT_SEARCH_TS ){
				String text = data.getStringExtra( Intent.EXTRA_TEXT );
				addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_TS, text );
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
					Action_App.columnList( ActMain.this );
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
			Action_App.columnList( ActMain.this );
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
	
	// Handle navigation view item clicks here.
	@Override public boolean onNavigationItemSelected( @NonNull MenuItem item ){
		int id = item.getItemId();
		
		//////////////////////////////////////////////
		// アカウント

		if( id == R.id.nav_account_add ){
			Action_Account.add( this );
			
		}else if( id == R.id.nav_account_setting ){
			Action_Account.setting( this );
			
		//////////////////////////////////////////////
		// カラム
		
		}else if( id == R.id.nav_column_list ){
			Action_App.columnList( this );
			
		}else if( id == R.id.nav_add_tl_home ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_HOME );
			
		}else if( id == R.id.nav_add_tl_local ){
			Action_Account.timeline( this, getDefaultInsertPosition(), true, Column.TYPE_LOCAL );
			
		}else if( id == R.id.nav_add_tl_federate ){
			Action_Account.timeline( this, getDefaultInsertPosition(), true, Column.TYPE_FEDERATE );
			
		}else if( id == R.id.nav_add_favourites ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_FAVOURITES );
			
		}else if( id == R.id.nav_add_statuses ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_PROFILE );
			
		}else if( id == R.id.nav_add_notifications ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_NOTIFICATIONS );
			
		}else if( id == R.id.nav_add_tl_search ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_SEARCH, "", false );
			
		}else if( id == R.id.nav_add_mutes ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_MUTES );
			
		}else if( id == R.id.nav_add_blocks ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_BLOCKS );
			
		}else if( id == R.id.nav_add_domain_blocks ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_DOMAIN_BLOCKS );
			
		}else if( id == R.id.nav_add_list ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_LIST_LIST );
			
		}else if( id == R.id.nav_follow_requests ){
			Action_Account.timeline( this, getDefaultInsertPosition(), false, Column.TYPE_FOLLOW_REQUESTS );
			
		//////////////////////////////////////////////
		// トゥート検索
		
		}else if( id == R.id.mastodon_search_portal ){
			addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_MSP, "" );
			
		}else if( id == R.id.tootsearch ){
			addColumn( getDefaultInsertPosition(), SavedAccount.getNA(), Column.TYPE_SEARCH_TS, "" );
			
		//////////////////////////////////////////////
		// 設定
		
		}else  if( id == R.id.nav_app_setting ){
			ActAppSetting.open( this, REQUEST_CODE_APP_SETTING );
			
		}else if( id == R.id.nav_muted_app ){
			startActivity( new Intent( this, ActMutedApp.class ) );
			
		}else if( id == R.id.nav_muted_word ){
			startActivity( new Intent( this, ActMutedWord.class ) );

		}else if( id == R.id.nav_app_about ){
			startActivityForResult( new Intent( this, ActAbout.class ), ActMain.REQUEST_APP_ABOUT );
			
		}else if( id == R.id.nav_oss_license ){
			startActivity( new Intent( this, ActOSSLicense.class ) );
			
		}else if( id == R.id.nav_app_exit ){
			finish();
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
	
	public Typeface timeline_font;
	public Typeface timeline_font_bold;
	
	boolean dont_crop_media_thumbnail;
	boolean mShortAcctLocalUser;
	int mAvatarIconSize;
	
	View llQuickTootBar;
	MyEditText etQuickToot;
	ImageButton btnQuickToot;
	public PostHelper post_helper;
	
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
				timeline_font_bold = Typeface.create( timeline_font, Typeface.BOLD );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		mShortAcctLocalUser = pref.getBoolean( Pref.KEY_SHORT_ACCT_LOCAL_USER, false );
		
		{
			float icon_size_dp = 48f;
			try{
				sv = pref.getString( Pref.KEY_AVATAR_ICON_SIZE, null );
				float fv = TextUtils.isEmpty( sv ) ? Float.NaN : Float.parseFloat( sv );
				if( Float.isNaN( fv ) || Float.isInfinite( fv ) || fv < 1f ){
					// error or bad range
				}else{
					icon_size_dp = fv;
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			mAvatarIconSize = (int) ( 0.5f + icon_size_dp * density );
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
			etQuickToot.setVerticalScrollBarEnabled( true );
			etQuickToot.setScrollbarFadingEnabled( false );
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
	
	public void startAccessTokenUpdate( Intent data ){
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
		
		if( "subwaytooter".equals( uri.getScheme() ) ){
			try{
				handleOAuth2CallbackUri( uri );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			return;
		}
		
		final String url = uri.toString();
		
		Matcher m = reStatusPage.matcher( url );
		if( m.find() ){
			try{
				// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
				final String host = m.group( 1 );
				final long status_id = Long.parseLong( m.group( 3 ), 10 );
				// ステータスをアプリ内で開く
				Action_Toot.conversationOtherInstance( ActMain.this, getDefaultInsertPosition(), uri.toString(), status_id, host, status_id );
			}catch( Throwable ex ){
				Utils.showToast( this, ex, "can't parse status id." );
			}
			return;
		}
		
		m = reUserPage.matcher( url );
		if( m.find() ){
			// https://mastodon.juggler.jp/@SubwayTooter
			// ユーザページをアプリ内で開く
			Action_User.profile(
				ActMain.this
				, getDefaultInsertPosition()
				, null
				, uri.toString()
				, m.group( 1 )
				, Uri.decode( m.group( 2 ) )
			);
			return;
		}
		
		// このアプリでは処理できないURLだった
		// 外部ブラウザを開きなおそうとすると無限ループの恐れがある
		// アプリケーションチューザーを表示する
		
		String error_message = getString( R.string.cant_handle_uri_of, url );
		
		try{
			int query_flag;
			if( Build.VERSION.SDK_INT >= 23 ){
				// Android 6.0以降
				// MATCH_DEFAULT_ONLY だと標準の設定に指定されたアプリがあるとソレしか出てこない
				// MATCH_ALL を指定すると 以前と同じ挙動になる
				query_flag = PackageManager.MATCH_ALL;
			}else{
				// Android 5.xまでは MATCH_DEFAULT_ONLY でマッチするすべてのアプリを取得できる
				query_flag = PackageManager.MATCH_DEFAULT_ONLY;
			}
			
			// queryIntentActivities に渡すURLは実在しないホストのものにする
			Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( "https://dummy.subwaytooter.club/" ) );
			intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
			List< ResolveInfo > resolveInfoList = getPackageManager().queryIntentActivities( intent, query_flag );
			if( resolveInfoList.isEmpty() ){
				throw new RuntimeException( "resolveInfoList is empty." );
			}
			
			// このアプリ以外の選択肢を集める
			String my_name = getPackageName();
			ArrayList< Intent > choice_list = new ArrayList<>();
			for( ResolveInfo ri : resolveInfoList ){
				
				// 選択肢からこのアプリを除外
				if( my_name.equals( ri.activityInfo.packageName ) ) continue;
				
				// 選択肢のIntentは目的のUriで作成する
				Intent choice = new Intent( Intent.ACTION_VIEW, uri );
				intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
				choice.setPackage( ri.activityInfo.packageName );
				choice.setClassName( ri.activityInfo.packageName, ri.activityInfo.name );
				choice_list.add( choice );
			}
			
			if( choice_list.isEmpty() ){
				throw new RuntimeException( "choice_list is empty." );
			}
			// 指定した選択肢でチューザーを作成して開く
			Intent chooser = Intent.createChooser( choice_list.remove( 0 ), error_message );
			chooser.putExtra( Intent.EXTRA_INITIAL_INTENTS, choice_list.toArray( new Intent[ choice_list.size() ] ) );
			startActivity( chooser );
			return;
		}catch( Throwable ex ){
			log.trace( ex );
		}
		
		new AlertDialog.Builder( this )
			.setCancelable( true )
			.setMessage( error_message )
			.setPositiveButton( R.string.close, null )
			.show();
		
	}
	
	private void handleOAuth2CallbackUri( @NonNull final Uri uri ){
		
		// 通知タップ
		// subwaytooter://notification_click/?db_id=(db_id)
		String sv = uri.getQueryParameter( "db_id" );
		if( ! TextUtils.isEmpty( sv ) ){
			try{
				long db_id = Long.parseLong( sv, 10 );
				SavedAccount account = SavedAccount.loadAccount( ActMain.this, db_id );
				if( account != null ){
					Column column = addColumn( getDefaultInsertPosition(), account, Column.TYPE_NOTIFICATIONS );
					// 通知を読み直す
					if( ! column.bInitialLoading ){
						column.startLoading();
					}
					
					PollingWorker.queueNotificationClicked( this, db_id );
					
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			return;
		}
		
		// OAuth2 認証コールバック
		// subwaytooter://oauth/?...
		new TootTaskRunner( ActMain.this, true ).run( new TootTask() {
			
			TootAccount ta;
			SavedAccount sa;
			String host;
			
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				
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
						this.sa = SavedAccount.loadAccount( ActMain.this, db_id );
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
				String client_name = Pref.pref( ActMain.this ).getString( Pref.KEY_CLIENT_NAME, "" );
				
				TootApiResult result = client.authorize2( client_name, code );
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
			
			@Override public void handleResult( TootApiResult result ){
				afterAccountVerify( result, ta, sa, host );
			}
			
		});
	}
	
	boolean afterAccountVerify( @Nullable TootApiResult result, @Nullable TootAccount ta, @Nullable SavedAccount sa, @Nullable String host ){
		//noinspection StatementWithEmptyBody
		if( result == null ){
			// cancelled.
			
		}else if( result.error != null ){
			Utils.showToast( ActMain.this, true, result.error );
			
		}else if( result.token_info == null ){
			Utils.showToast( ActMain.this, true, "can't get access token." );
			
		}else if( result.object == null ){
			Utils.showToast( ActMain.this, true, "can't parse json response." );
			
		}else if( ta == null ){
			// 自分のユーザネームを取れなかった
			// …普通はエラーメッセージが設定されてるはずだが
			Utils.showToast( ActMain.this, true, "can't verify user credential." );
			
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
				PollingWorker.queueUpdateNotification( ActMain.this );
				return true;
			}
		}else if( host != null ){
			// アカウント追加時
			String user = ta.username + "@" + host;
			long row_id = SavedAccount.insert( host, user, result.object, result.token_info );
			SavedAccount account = SavedAccount.loadAccount( ActMain.this, row_id );
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
					// 現在、アカウント設定にはこの項目はない( 「NSFWな添付メディアを隠さない」はあるが全く別の効果)
				}
				
				if( bModified ){
					account.saveSetting();
				}
				Utils.showToast( ActMain.this, false, R.string.account_confirmed );
				
				// 通知の更新が必要かもしれない
				PollingWorker.queueUpdateNotification( ActMain.this );
				
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
	public void checkAccessToken(
		@Nullable final Dialog dialog_host
		, @Nullable final Dialog dialog_token
		, @NonNull final String host
		, @NonNull final String access_token
		, @Nullable final SavedAccount sa
	){
		
		new TootTaskRunner( ActMain.this, true ) .run(  host,new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
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
			
			TootAccount ta;

			@Override public void handleResult( @Nullable TootApiResult result ){
				
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
		} );
	}
	
	// アクセストークンの手動入力(更新)
	void checkAccessToken2( long db_id ){
		
		final SavedAccount sa = SavedAccount.loadAccount( this, db_id );
		if( sa == null ) return;
		
		DlgTextInput.show( this, getString( R.string.access_token ), null, new DlgTextInput.Callback() {
			@Override public void onOK( Dialog dialog_token, String access_token ){
				checkAccessToken( null, dialog_token, sa.host, access_token, sa );
			}
			
			@Override public void onEmptyError(){
				Utils.showToast( ActMain.this, true, R.string.token_not_specified );
			}
		} );
	}
	
	public void reloadAccountSetting(){
		ArrayList< SavedAccount > done_list = new ArrayList<>();
		for( Column column : app_state.column_list ){
			SavedAccount a = column.access_info;
			if( done_list.contains( a ) ) continue;
			done_list.add( a );
			if( ! a.isNA() ) a.reloadSetting( ActMain.this );
			column.fireShowColumnHeader();
		}
	}
	
	public void reloadAccountSetting( SavedAccount account ){
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
	
	//////////////////////////////////////////////////////////////
	
	static final Pattern reUrlHashTag = Pattern.compile( "\\Ahttps://([^/]+)/tags/([^?#・\\s\\-+.,:;/]+)(?:\\z|[?#])" );
	static final Pattern reUserPage = Pattern.compile( "\\Ahttps://([^/]+)/@([A-Za-z0-9_]+)(?:\\z|[?#])" );
	static final Pattern reStatusPage = Pattern.compile( "\\Ahttps://([^/]+)/@([A-Za-z0-9_]+)/(\\d+)(?:\\z|[?#])" );
	
	public void openChromeTab( @NonNull final ChromeTabOpener opener ){
		
		try{
			log.d( "openChromeTab url=%s", opener.url );
			
			if( opener.bAllowIntercept && opener.access_info != null ){
				
				// ハッシュタグはいきなり開くのではなくメニューがある
				Matcher m = reUrlHashTag.matcher( opener.url );
				if( m.find() ){
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					final String host = m.group( 1 );
					final String tag_without_sharp = Uri.decode( m.group( 2 ) );
					Action_HashTag.dialog(
						ActMain.this
						, opener.pos
						, opener.url
						, host
						, tag_without_sharp
						, opener.tag_list
					);
					
					return;
				}
				
				// ステータスページをアプリから開く
				m = reStatusPage.matcher( opener.url );
				if( m.find() ){
					try{
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						final String host = m.group( 1 );
						final long status_id = Long.parseLong( m.group( 3 ), 10 );
						if( opener.access_info.isNA() || ! host.equalsIgnoreCase( opener.access_info.host ) ){
							Action_Toot.conversationOtherInstance( ActMain.this, opener.pos, opener.url, status_id, host, status_id );
						}else{
							Action_Toot.conversationLocal( ActMain.this, opener.pos, opener.access_info, status_id );
						}
					}catch( Throwable ex ){
						Utils.showToast( this, ex, "can't parse status id." );
					}
					return;
				}
				
				// https://mastodon.juggler.jp/@SubwayTooter
				m = reUserPage.matcher( opener.url );
				if( m.find() ){
					// ユーザページをアプリ内で開く
					Action_User.profile(
						ActMain.this
						, opener.pos
						, opener.access_info
						, opener.url
						, m.group( 1 )
						, Uri.decode( m.group( 2 ) )
					);
					return;
				}
			}
			
			App1.openCustomTab( this, opener.url );
			
		}catch( Throwable ex ){
			// log.trace( ex );
			log.e( ex, "openChromeTab failed. url=%s", opener.url );
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	
	final MyClickableSpan.LinkClickCallback link_click_listener = new MyClickableSpan.LinkClickCallback() {
		@Override public void onClickLink( View view, @NonNull final MyClickableSpan span ){
			
			View view_orig = view;
			
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
			final int pos = nextPosition( column );
			@Nullable SavedAccount access_info = column == null ? null : column.access_info;
			
			ArrayList< String > tag_list = null;
			
			try{
				//noinspection ConstantConditions
				CharSequence cs = ( (TextView) view_orig ).getText();
				if( cs instanceof Spannable ){
					Spannable content = (Spannable) cs;
					for( MyClickableSpan s : content.getSpans( 0, content.length(), MyClickableSpan.class ) ){
						Matcher m = reUrlHashTag.matcher( s.url );
						if( m.find() ){
							String s_tag = s.text.startsWith( "#" ) ? s.text : "#" + Uri.decode( m.group( 2 ) );
							if( tag_list == null ) tag_list = new ArrayList<>();
							tag_list.add( s_tag );
						}
					}
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
			
			new ChromeTabOpener( ActMain.this, pos, span.url )
				.accessInfo( access_info )
				.tagList( tag_list )
				.open();
			
		}
	};
	
	/////////////////////////////////////////////////////////////////////////
	
	public void showColumnMatchAccount( SavedAccount account ){
		for( Column column : app_state.column_list ){
			if( account.acct.equals( column.access_info.acct ) ){
				column.fireShowContent();
			}
		}
	}
	
	////////////////////////////////////////
	
	////////////////////////////////////////////////////////
	// column list
	
	////////////////////////////////////////////////////////////////////////////
	
	public final RelationChangedCallback follow_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.follow_succeeded );
		}
	};
	
	final RelationChangedCallback unfollow_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.unfollow_succeeded );
		}
	};
	public final RelationChangedCallback favourite_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.favourite_succeeded );
		}
	};
	final RelationChangedCallback unfavourite_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.unfavourite_succeeded );
		}
	};
	public final RelationChangedCallback boost_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.boost_succeeded );
		}
	};
	final RelationChangedCallback unboost_complete_callback = new RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( ActMain.this, false, R.string.unboost_succeeded );
		}
	};
	
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
	
	public int getDefaultInsertPosition(){
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
			if( column_count > 0 && column_count < nScreenColumn ){
				nScreenColumn = column_count;
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
		
		@SuppressLint("StaticFieldLeak")
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
					PollingWorker.queueAppDataImportBefore( ActMain.this );
					while( PollingWorker.mBusyAppDataImportBefore.get() ){
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
				onPostExecute( result );
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
				PollingWorker.queueAppDataImportAfter( ActMain.this );
				
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
	
	// 相対時刻の表記を定期的に更新する
	private final Runnable proc_updateRelativeTime = new Runnable() {
		@Override public void run(){
			handler.removeCallbacks( proc_updateRelativeTime );
			if( ! bStart ) return;
			for( Column c : app_state.column_list ){
				c.fireShowContent();
			}
			if( pref.getBoolean( Pref.KEY_RELATIVE_TIMESTAMP, false ) ){
				handler.postDelayed( proc_updateRelativeTime, 10000L );
			}
		}
	};
	
	int nAutoCwCellWidth = 0;
	int nAutoCwLines = 0;
	
	private void resizeAutoCW( int column_w ){
		String sv = pref.getString( Pref.KEY_AUTO_CW_LINES, "" );
		nAutoCwLines = Utils.parse_int( sv, - 1 );
		if( nAutoCwLines > 0 ){
			int lv_pad = (int) ( 0.5f + 12 * density );
			int icon_width = mAvatarIconSize;
			int icon_end = (int) ( 0.5f + 4 * density );
			nAutoCwCellWidth = column_w - lv_pad * 2 - icon_width - icon_end;
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
		a.refActivity = new WeakReference< Object >( ActMain.this );
		a.cell_width = nAutoCwCellWidth;
		a.decoded_spoiler_text = null;
		
		// テキストをレイアウトして行数を測定
		
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( nAutoCwCellWidth, LinearLayout.LayoutParams.WRAP_CONTENT );
		TextView tv = new TextView( this );
		tv.setLayoutParams( lp );
		if( ! Float.isNaN( timeline_font_size_sp ) ){
			tv.setTextSize( timeline_font_size_sp );
		}
		if( timeline_font != null ){
			tv.setTypeface( timeline_font );
		}
		tv.setText( text );
		tv.measure(
			View.MeasureSpec.makeMeasureSpec( nAutoCwCellWidth, View.MeasureSpec.EXACTLY )
			, View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED )
		);
		Layout l = tv.getLayout();
		if( l != null ){
			int line_count = a.originalLineCount = l.getLineCount();
			
			if( nAutoCwLines > 0
				&& line_count > nAutoCwLines
				&& TextUtils.isEmpty( status.spoiler_text )
				){
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
	
	// 簡易投稿入力のテキストを取得
	@NonNull public String getQuickTootText(){
		return etQuickToot.getText().toString();
	}
	
	// デフォルトの投稿先アカウントのdb_idを返す
	public long getCurrentPostTargetId(){
		
		if( pager_adapter != null ){
			Column c = pager_adapter.getColumn( pager.getCurrentItem() );
			if( c != null && ! c.access_info.isPseudo() ){
				return c.access_info.db_id;
			}
		}else{
			long db_id = App1.pref.getLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L );
			SavedAccount a = SavedAccount.loadAccount( ActMain.this, db_id );
			if( a != null ){
				return a.db_id;
			}
		}
		return - 1L;
	}
	
	// スマホモードなら現在のカラムを、タブレットモードなら-1Lを返す
	// (カラム一覧画面のデフォルト選択位置に使われる)
	public int getCurrentColumn(){
		if( pager_adapter != null ){
			return pager.getCurrentItem();
		}else{
			return - 1;
		}
	}
}
