package jp.juggler.subwaytooter;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.view.MyListView;
import jp.juggler.subwaytooter.util.ScrollPosition;
import jp.juggler.subwaytooter.util.Utils;

class ColumnViewHolder
	implements View.OnClickListener
	, SwipyRefreshLayout.OnRefreshListener
	, CompoundButton.OnCheckedChangeListener
{
	private static final LogCategory log = new LogCategory( "ColumnViewHolder" );
	
	final ActMain activity;
	final Column column;
	final AtomicBoolean is_destroyed = new AtomicBoolean( false );
	private final ItemListAdapter status_adapter;
	
	ColumnViewHolder( ActMain activity, Column column ){
		this.activity = activity;
		this.column = column;
		this.status_adapter = new ItemListAdapter( activity, column );
	}
	
	private boolean isPageDestroyed(){
		return is_destroyed.get() || activity.isFinishing();
	}
	
	void onPageDestroy( @SuppressWarnings("UnusedParameters") View root ){
		log.d( "onPageDestroy:%s", column.getColumnName( true ) );
		
		saveScrollPosition();
		
		column.setColumnViewHolder( null );
		
		closeBitmaps();
		
		activity.closeListItemPopup();
		
	}
	
	private TextView tvLoading;
	private MyListView listView;
	private TextView tvColumnContext;
	private TextView tvColumnName;
	private HeaderViewHolder vh_header;
	private SwipyRefreshLayout swipyRefreshLayout;
	private View btnSearch;
	private EditText etSearch;
	private CheckBox cbResolve;
	private View llColumnSetting;
	private EditText etRegexFilter;
	private TextView tvRegexFilterError;
	private ImageView ivColumnIcon;
	
	private View llColumnHeader;
	private TextView tvColumnIndex;
	private ImageButton btnColumnSetting;
	private ImageButton btnColumnReload;
	private ImageButton btnColumnClose;
	
	private View flColumnBackground;
	private ImageView ivColumnBackgroundImage;
	
	void onPageCreate( View root, int page_idx, int page_count ){
		log.d( "onPageCreate:%s", column.getColumnName( true ) );
		
		flColumnBackground = root.findViewById( R.id.flColumnBackground );
		ivColumnBackgroundImage = (ImageView) root.findViewById( R.id.ivColumnBackgroundImage );
		llColumnHeader = root.findViewById( R.id.llColumnHeader );
		
		tvColumnIndex = (TextView) root.findViewById( R.id.tvColumnIndex );
		tvColumnIndex.setText( activity.getString( R.string.column_index, page_idx + 1, page_count ) );
		
		tvColumnName = (TextView) root.findViewById( R.id.tvColumnName );
		tvColumnContext = (TextView) root.findViewById( R.id.tvColumnContext );
		ivColumnIcon = (ImageView) root.findViewById( R.id.ivColumnIcon );
		
		btnColumnSetting = (ImageButton) root.findViewById( R.id.btnColumnSetting );
		btnColumnReload = (ImageButton) root.findViewById( R.id.btnColumnReload );
		btnColumnClose = (ImageButton) root.findViewById( R.id.btnColumnClose );
		
		btnColumnSetting.setOnClickListener( this );
		btnColumnReload.setOnClickListener( this );
		btnColumnClose.setOnClickListener( this );
		
		llColumnHeader.setOnClickListener( this );
		
		root.findViewById( R.id.btnColor ).setOnClickListener( this );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (MyListView) root.findViewById( R.id.listView );
		listView.setAdapter( status_adapter );
		
		this.swipyRefreshLayout = (SwipyRefreshLayout) root.findViewById( R.id.swipyRefreshLayout );
		swipyRefreshLayout.setOnRefreshListener( this );
		swipyRefreshLayout.setDistanceToTriggerSync( (int) ( 0.5f + 20f * activity.density ) );
		
		View llSearch = root.findViewById( R.id.llSearch );
		btnSearch = root.findViewById( R.id.btnSearch );
		etSearch = (EditText) root.findViewById( R.id.etSearch );
		cbResolve = (CheckBox) root.findViewById( R.id.cbResolve );
		
		listView.setFastScrollEnabled( ! Pref.pref( activity ).getBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, true ) );
		
		boolean bAllowFilter;
		switch( column.column_type ){
		default:
			bAllowFilter = true;
			break;
		case Column.TYPE_SEARCH:
		case Column.TYPE_CONVERSATION:
		case Column.TYPE_REPORTS:
		case Column.TYPE_BLOCKS:
		case Column.TYPE_MUTES:
		case Column.TYPE_FOLLOW_REQUESTS:
		case Column.TYPE_NOTIFICATIONS:
			bAllowFilter = false;
			break;
		}
		
		boolean bAllowFilterBoost;
		switch( column.column_type ){
		default:
			bAllowFilterBoost = false;
			break;
		case Column.TYPE_HOME:
		case Column.TYPE_PROFILE:
			bAllowFilterBoost = true;
			break;
		}
		
		llColumnSetting = root.findViewById( R.id.llColumnSetting );
		llColumnSetting.setVisibility( View.GONE );
		
		CheckBox cb;
		cb = (CheckBox) root.findViewById( R.id.cbDontCloseColumn );
		cb.setChecked( column.dont_close );
		cb.setOnCheckedChangeListener( this );
		
		cb = (CheckBox) root.findViewById( R.id.cbWithAttachment );
		cb.setChecked( column.with_attachment );
		cb.setOnCheckedChangeListener( this );
		cb.setEnabled( bAllowFilter );
		cb.setVisibility( bAllowFilter ? View.VISIBLE : View.GONE );
		
		cb = (CheckBox) root.findViewById( R.id.cbDontShowBoost );
		cb.setChecked( column.dont_show_boost );
		cb.setOnCheckedChangeListener( this );
		cb.setEnabled( bAllowFilter );
		cb.setVisibility( bAllowFilterBoost ? View.VISIBLE : View.GONE );
		
		cb = (CheckBox) root.findViewById( R.id.cbDontShowReply );
		cb.setChecked( column.dont_show_reply );
		cb.setOnCheckedChangeListener( this );
		cb.setEnabled( bAllowFilter );
		cb.setVisibility( bAllowFilterBoost ? View.VISIBLE : View.GONE );
		
		cb = (CheckBox) root.findViewById( R.id.cbDontStreaming );
		if( ! column.canStreaming() ){
			cb.setVisibility(  View.GONE );
		}else{
			cb.setVisibility( View.VISIBLE  );
			cb.setChecked( column.dont_streaming );
			cb.setOnCheckedChangeListener( this );
		}
		
		cb = (CheckBox) root.findViewById( R.id.cbDontAutoRefresh );
		if( ! column.canAutoRefresh() ){
			cb.setVisibility(  View.GONE );
		}else{
			cb.setVisibility(View.VISIBLE  );
			cb.setChecked( column.dont_auto_refresh );
			cb.setOnCheckedChangeListener( this );
		}
		
		cb = (CheckBox) root.findViewById( R.id.cbHideMediaDefault );
		if( ! column.canShowMedia() ){
			cb.setVisibility(  View.GONE );
		}else{
			cb.setVisibility(View.VISIBLE  );
			cb.setChecked( column.hide_media_default );
			cb.setOnCheckedChangeListener( this );
		}
		
		etRegexFilter = (EditText) root.findViewById( R.id.etRegexFilter );
		if( ! bAllowFilter ){
			etRegexFilter.setVisibility( View.GONE );
			root.findViewById( R.id.llRegexFilter ).setVisibility( View.GONE );
		}else{
			etRegexFilter.setText( column.regex_text );
			// tvRegexFilterErrorの表示を更新
			tvRegexFilterError = (TextView) root.findViewById( R.id.tvRegexFilterError );
			isRegexValid();
			// 入力の追跡
			etRegexFilter.addTextChangedListener( new TextWatcher() {
				@Override
				public void beforeTextChanged( CharSequence s, int start, int count, int after ){
				}
				
				@Override
				public void onTextChanged( CharSequence s, int start, int before, int count ){
				}
				
				@Override public void afterTextChanged( Editable s ){
					activity.handler.removeCallbacks( proc_start_filter );
					if( isRegexValid() ){
						activity.handler.postDelayed( proc_start_filter, 1500L );
					}
				}
			} );
		}
		Button button = (Button) root.findViewById( R.id.btnDeleteNotification );
		if( column.column_type != Column.TYPE_NOTIFICATIONS ){
			button.setVisibility( View.GONE );
		}else{
			button.setVisibility( View.VISIBLE );
			button.setOnClickListener( this );
			
		}
		
		if( column.column_type != Column.TYPE_SEARCH ){
			llSearch.setVisibility( View.GONE );
		}else{
			etSearch.setText( column.search_query );
			cbResolve.setChecked( column.search_resolve );
			btnSearch.setOnClickListener( this );
			etSearch.setOnEditorActionListener( new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction( TextView v, int actionId, KeyEvent event ){
					if( actionId == EditorInfo.IME_ACTION_SEARCH ){
						btnSearch.performClick();
						return true;
					}
					return false;
				}
			} );
		}
		
		if( column.column_type == Column.TYPE_PROFILE ){
			vh_header = new HeaderViewHolder( activity, column, listView );
			listView.addHeaderView( vh_header.viewRoot );
		}
		
		switch( column.column_type ){
		case Column.TYPE_CONVERSATION:
		case Column.TYPE_SEARCH:
			swipyRefreshLayout.setEnabled( false );
		}
		
		if( column.bSimpleList ){
			listView.setOnItemClickListener( status_adapter );
		}
		
		//
		
		column.setColumnViewHolder( this );
		
		showColumnColor();
		
		showContent();
	}
	
	void showColumnColor(){
		int c = column.header_bg_color;
		if( c == 0 ){
			llColumnHeader.setBackgroundResource( R.drawable.btn_bg_ddd );
		}else{
			ViewCompat.setBackground( llColumnHeader,Styler.getAdaptiveRippleDrawable(
				c,
				(column.header_fg_color != 0 ? column.header_fg_color :
					Styler.getAttributeColor( activity,R.attr.colorRippleEffect ))
			) );
		}
		
		c = column.header_fg_color;
		if( c == 0 ){
			tvColumnIndex.setTextColor( Styler.getAttributeColor( activity, R.attr.colorColumnHeaderPageNumber ) );
			tvColumnName.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			Styler.setIconDefaultColor( activity, ivColumnIcon, Column.getIconAttrId( column.column_type ) );
			Styler.setIconDefaultColor( activity, btnColumnSetting, R.attr.ic_tune );
			Styler.setIconDefaultColor( activity, btnColumnReload, R.attr.btn_refresh );
			Styler.setIconDefaultColor( activity, btnColumnClose, R.attr.btn_close );
		}else{
			tvColumnIndex.setTextColor( c );
			tvColumnName.setTextColor( c );
			Styler.setIconCustomColor( activity, ivColumnIcon, c, Column.getIconAttrId( column.column_type ) );
			Styler.setIconCustomColor( activity, btnColumnSetting, c, R.attr.ic_tune );
			Styler.setIconCustomColor( activity, btnColumnReload, c, R.attr.btn_refresh );
			Styler.setIconCustomColor( activity, btnColumnClose, c, R.attr.btn_close );
		}
		
		c = column.column_bg_color;
		if( c == 0 ){
			ViewCompat.setBackground( flColumnBackground, null );
		}else{
			flColumnBackground.setBackgroundColor( c );
		}
		
		ivColumnBackgroundImage.setAlpha( column.column_bg_image_alpha );
		
		loadBackgroundImage( ivColumnBackgroundImage, column.column_bg_image );
		
	}
	
	private String last_image_uri;
	private Bitmap last_image_bitmap;
	
	private void closeBitmaps(){
		try{
			if( last_image_bitmap != null ){
				ivColumnBackgroundImage.setVisibility( View.GONE );
				ivColumnBackgroundImage.setImageDrawable( null );
				last_image_uri = null;
				last_image_bitmap.recycle();
				last_image_bitmap = null;
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			
		}
	}
	
	private void loadBackgroundImage( ImageView iv, String url ){
		try{
			if( TextUtils.isEmpty( url ) ){
				closeBitmaps();
				return;
			}else if( url.equals( last_image_uri ) ){
				// 今表示してるのと同じ
				return;
			}
			
			// 直前のBitmapを掃除する
			closeBitmaps();
			
			iv.setVisibility( View.VISIBLE );
			
			int w = iv.getResources().getDisplayMetrics().widthPixels;
			int h = iv.getResources().getDisplayMetrics().heightPixels;
			int resize_max = ( w > h ? w : h );
			
			Uri uri = Uri.parse( url );
			last_image_bitmap = Utils.createResizedBitmap( log, activity, uri, false, resize_max );
			if( last_image_bitmap != null ){
				iv.setImageBitmap( last_image_bitmap );
				last_image_uri = url;
			}
			
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	private final Runnable proc_start_filter = new Runnable() {
		@Override public void run(){
			if( isPageDestroyed() ) return;
			if( isRegexValid() ){
				column.regex_text = etRegexFilter.getText().toString();
				activity.app_state.saveColumnList();
				column.startLoading();
			}
		}
	};
	
	private boolean isRegexValid(){
		String s = etRegexFilter.getText().toString();
		if( s.length() == 0 ){
			tvRegexFilterError.setText( "" );
			return true;
		}
		try{
			//noinspection ResultOfMethodCallIgnored
			Pattern.compile( s ).matcher( "" );
			tvRegexFilterError.setText( "" );
			return true;
		}catch( Throwable ex ){
			String message = ex.getMessage();
			if( TextUtils.isEmpty( message ) )
				message = Utils.formatError( ex, activity.getResources(), R.string.regex_error );
			tvRegexFilterError.setText( message );
			return false;
		}
	}
	
	boolean isColumnSettingShown(){
		return llColumnSetting.getVisibility() == View.VISIBLE;
	}
	
	void closeColumnSetting(){
		llColumnSetting.setVisibility( View.GONE );
	}
	
	@Override public void onRefresh( SwipyRefreshLayoutDirection direction ){
		column.startRefresh( false, direction == SwipyRefreshLayoutDirection.BOTTOM, - 1L, - 1 );
	}
	
	@Override public void onCheckedChanged( CompoundButton view, boolean isChecked ){
		switch( view.getId() ){
		
		case R.id.cbDontCloseColumn:
			column.dont_close = isChecked;
			showColumnCloseButton();
			activity.app_state.saveColumnList();
			break;
		
		case R.id.cbWithAttachment:
			column.with_attachment = isChecked;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.cbDontShowBoost:
			column.dont_show_boost = isChecked;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		
		case R.id.cbDontShowReply:
			column.dont_show_reply = isChecked;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.cbDontStreaming:
			column.dont_streaming = isChecked;
			activity.app_state.saveColumnList();
			if( isChecked ){
				column.onResume( activity );
			}else{
				column.stopStreaming();
			}
			break;

		case R.id.cbDontAutoRefresh:
			column.dont_auto_refresh = isChecked;
			activity.app_state.saveColumnList();
			break;
		
		case R.id.cbHideMediaDefault:
			column.hide_media_default = isChecked;
			activity.app_state.saveColumnList();
			column.fireShowContent();
			break;
			
		}
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnColumnClose:
			activity.closeColumn( false, column );
			break;
		
		case R.id.btnColumnReload:
			if( column.column_type == Column.TYPE_SEARCH ){
				Utils.hideKeyboard( activity, etSearch );
				etSearch.setText( column.search_query );
				cbResolve.setChecked( column.search_resolve );
			}
			column.startLoading();
			break;
		
		case R.id.btnSearch:
			Utils.hideKeyboard( activity, etSearch );
			column.search_query = etSearch.getText().toString().trim();
			column.search_resolve = cbResolve.isChecked();
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.llColumnHeader:
			if( status_adapter.getCount() > 0 ) listView.setSelectionFromTop( 0, 0 );
			break;
		
		case R.id.btnColumnSetting:
			llColumnSetting.setVisibility( llColumnSetting.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE );
			break;
		
		case R.id.btnDeleteNotification:
			activity.deleteNotification( false, column.access_info );
			break;
		
		case R.id.btnColor:
			int idx = activity.app_state.column_list.indexOf( column );
			ActColumnCustomize.open( activity, idx, ActMain.REQUEST_COLUMN_COLOR );
			break;
		}
		
	}
	
	private void showError( String message ){
		tvLoading.setVisibility( View.VISIBLE );
		tvLoading.setText( message );
		
		swipyRefreshLayout.setVisibility( View.GONE );
		
		// ロード完了後に先頭から表示させる
		if( status_adapter.getCount() > 0 ){
			listView.setSelectionFromTop( 0, 0 );
		}
	}
	
	private void showColumnCloseButton(){
		// カラム保護の状態
		btnColumnClose.setEnabled( ! column.dont_close );
		btnColumnClose.setAlpha( column.dont_close ? 0.3f : 1f );
	}
	
	/////////////////////////////////////////////////////////////////
	// Column から呼ばれる
	
	boolean hasHeaderView(){
		return vh_header != null;
	}
	
	SwipyRefreshLayout getRefreshLayout(){
		return swipyRefreshLayout;
	}
	
	MyListView getListView(){
		return listView;
	}
	
	// カラムヘッダなど、負荷が低い部分の表示更新
	void showColumnHeader(){
		
		String acct = column.access_info.acct;
		AcctColor ac = AcctColor.load( acct );
		int c;
		
		tvColumnContext.setText( ac != null && ! TextUtils.isEmpty( ac.nickname ) ? ac.nickname : acct );
		
		c = ( ac != null ? ac.color_fg : 0 );
		tvColumnContext.setTextColor( c != 0 ? c : Styler.getAttributeColor( activity, R.attr.colorAcctSmall ) );
		
		c = ( ac != null ? ac.color_bg : 0 );
		if( c == 0 ){
			ViewCompat.setBackground( tvColumnContext, null );
		}else{
			tvColumnContext.setBackgroundColor( c );
		}
		tvColumnContext.setPaddingRelative( activity.acct_pad_lr, 0, activity.acct_pad_lr, 0 );
		
		tvColumnName.setText( column.getColumnName( false ) );
		
		showColumnCloseButton();
		
	}
	
	void showContent(){
		
		showColumnHeader();
		
		if( column.is_dispose.get() ){
			showError( "column was disposed." );
			return;
		}
		
		if( vh_header != null ){
			vh_header.bind( column.who_account );
		}
		
		if( ! column.bFirstInitialized ){
			showError( "initializing" );
			return;
		}
		
		if( column.bInitialLoading ){
			String message = column.task_progress;
			if( message == null ) message = "loading?";
			showError( message );
			return;
		}
		
		if( ! TextUtils.isEmpty( column.mInitialLoadingError ) ){
			showError( column.mInitialLoadingError );
			return;
		}
		
		if( ! column.bRefreshLoading ){
			swipyRefreshLayout.setRefreshing( false );
			if( column.mRefreshLoadingError != null ){
				Utils.showToast( activity, true, column.mRefreshLoadingError );
				column.mRefreshLoadingError = null;
			}
		}
		
		if( column.list_data.isEmpty() && vh_header == null ){
			showError( activity.getString( R.string.list_empty ) );
		}else{
			tvLoading.setVisibility( View.GONE );
			swipyRefreshLayout.setVisibility( View.VISIBLE );
			status_adapter.notifyDataSetChanged();
		}
		restoreScrollPosition();
	}
	
	private void restoreScrollPosition(){
		ScrollPosition sp = column.scroll_save;
		if( sp == null ) return;
		column.scroll_save = null;
		
		if( listView.getVisibility() == View.VISIBLE ){
			sp.restore( listView );
		}
		
	}
	
	private void saveScrollPosition(){
		
		if( listView.getVisibility() == View.VISIBLE ){
			column.scroll_save = new ScrollPosition( listView );
		}else{
			column.scroll_save = new ScrollPosition( 0, 0 );
		}
	}
	
	@NonNull ScrollPosition getScrollPosition(){
		return new ScrollPosition( listView );
	}
	
	void setScrollPosition( @NonNull ScrollPosition sp, final float delta ){
		sp.restore( listView );
		listView.postDelayed( new Runnable() {
			@Override public void run(){
				if( isPageDestroyed() ) return;
				listView.scrollListBy( (int) ( delta * activity.density ) );
			}
		}, 20L );
		
	}
	
}
