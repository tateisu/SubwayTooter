package jp.juggler.subwaytooter;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.TextViewCompat;
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
import android.widget.ListAdapter;
import android.widget.TextView;

import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;

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
	
	@Nullable Column column;
	@Nullable private ItemListAdapter status_adapter;
	
	private final TextView tvLoading;
	private final MyListView listView;
	private final SwipyRefreshLayout swipyRefreshLayout;
	
	private final View llColumnHeader;
	private final TextView tvColumnIndex;
	private final TextView tvColumnContext;
	private final ImageView ivColumnIcon;
	private final TextView tvColumnName;
	
	private final View llColumnSetting;

	private final View btnSearch;
	private final EditText etSearch;
	private final CheckBox cbResolve;
	private final EditText etRegexFilter;
	private final TextView tvRegexFilterError;
	
	private final ImageButton btnColumnSetting;
	private final ImageButton btnColumnReload;
	private final ImageButton btnColumnClose;
	
	private final View flColumnBackground;
	private final ImageView ivColumnBackgroundImage;
	private final View llSearch;
	private final CheckBox cbDontCloseColumn;
	private final CheckBox cbWithAttachment;
	private final CheckBox cbDontShowBoost;
	private final CheckBox cbDontShowReply;
	private final CheckBox cbDontStreaming;
	private final CheckBox cbDontAutoRefresh;
	private final CheckBox cbHideMediaDefault;
	private final View llRegexFilter;
	private final Button btnDeleteNotification;
	
	ColumnViewHolder( ActMain arg_activity, View root ){
		this.activity = arg_activity;
		
		if( activity.timeline_font != null ){
			Utils.scanView( root, new Utils.ScanViewCallback() {
				@Override public void onScanView( View v ){
					try{
						if( v instanceof TextView ){
							( (TextView) v ).setTypeface( activity.timeline_font );
						}
					}catch(Throwable ex){
						ex.printStackTrace();
					}
				}
			} );
		}
		
		
		flColumnBackground = root.findViewById( R.id.flColumnBackground );
		ivColumnBackgroundImage = (ImageView) root.findViewById( R.id.ivColumnBackgroundImage );
		llColumnHeader = root.findViewById( R.id.llColumnHeader );
		
		tvColumnIndex = (TextView) root.findViewById( R.id.tvColumnIndex );
		
		tvColumnName = (TextView) root.findViewById( R.id.tvColumnName );
		tvColumnContext = (TextView) root.findViewById( R.id.tvColumnContext );
		ivColumnIcon = (ImageView) root.findViewById( R.id.ivColumnIcon );
		
		btnColumnSetting = (ImageButton) root.findViewById( R.id.btnColumnSetting );
		btnColumnReload = (ImageButton) root.findViewById( R.id.btnColumnReload );
		btnColumnClose = (ImageButton) root.findViewById( R.id.btnColumnClose );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (MyListView) root.findViewById( R.id.listView );
		
		btnSearch = root.findViewById( R.id.btnSearch );
		etSearch = (EditText) root.findViewById( R.id.etSearch );
		cbResolve = (CheckBox) root.findViewById( R.id.cbResolve );
		
		llSearch = root.findViewById( R.id.llSearch );
		
		llColumnSetting = root.findViewById( R.id.llColumnSetting );
		
		cbDontCloseColumn = (CheckBox) root.findViewById( R.id.cbDontCloseColumn );
		cbWithAttachment = (CheckBox) root.findViewById( R.id.cbWithAttachment );
		cbDontShowBoost = (CheckBox) root.findViewById( R.id.cbDontShowBoost );
		cbDontShowReply = (CheckBox) root.findViewById( R.id.cbDontShowReply );
		cbDontStreaming = (CheckBox) root.findViewById( R.id.cbDontStreaming );
		cbDontAutoRefresh = (CheckBox) root.findViewById( R.id.cbDontAutoRefresh );
		cbHideMediaDefault = (CheckBox) root.findViewById( R.id.cbHideMediaDefault );
		etRegexFilter = (EditText) root.findViewById( R.id.etRegexFilter );
		llRegexFilter = root.findViewById( R.id.llRegexFilter );
		tvRegexFilterError = (TextView) root.findViewById( R.id.tvRegexFilterError );

		
		
		btnDeleteNotification = (Button) root.findViewById( R.id.btnDeleteNotification );
		
		llColumnHeader.setOnClickListener( this );
		btnColumnSetting.setOnClickListener( this );
		btnColumnReload.setOnClickListener( this );
		btnColumnClose.setOnClickListener( this );
		btnDeleteNotification.setOnClickListener( this );
		
		root.findViewById( R.id.btnColor ).setOnClickListener( this );
		
		this.swipyRefreshLayout = (SwipyRefreshLayout) root.findViewById( R.id.swipyRefreshLayout );
		swipyRefreshLayout.setOnRefreshListener( this );
		swipyRefreshLayout.setDistanceToTriggerSync( (int) ( 0.5f + 20f * activity.density ) );
		
		cbDontCloseColumn.setOnCheckedChangeListener( this );
		cbWithAttachment.setOnCheckedChangeListener( this );
		cbDontShowBoost.setOnCheckedChangeListener( this );
		cbDontShowReply.setOnCheckedChangeListener( this );
		cbDontStreaming.setOnCheckedChangeListener( this );
		cbDontAutoRefresh.setOnCheckedChangeListener( this );
		cbHideMediaDefault.setOnCheckedChangeListener( this );
		
		// 入力の追跡
		etRegexFilter.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence s, int start, int count, int after ){
			}
			
			@Override
			public void onTextChanged( CharSequence s, int start, int before, int count ){
			}
			
			@Override public void afterTextChanged( Editable s ){
				if( loading_busy ) return;
				activity.handler.removeCallbacks( proc_start_filter );
				if( isRegexValid() ){
					activity.handler.postDelayed( proc_start_filter, 1500L );
				}
			}
		} );
		
		btnSearch.setOnClickListener( this );
		etSearch.setOnEditorActionListener( new TextView.OnEditorActionListener() {
			@Override public boolean onEditorAction( TextView v, int actionId, KeyEvent event ){
				if( !loading_busy ){
					if( actionId == EditorInfo.IME_ACTION_SEARCH ){
						btnSearch.performClick();
						return true;
					}
				}
				return false;
			}
		} );
		
	}
	
	private boolean isPageDestroyed(){
		return column ==null || activity.isFinishing();
	}
	
	void onPageDestroy(){
		// タブレットモードの場合、onPageCreateより前に呼ばれる
		
		if( column != null ){
			log.d( "onPageDestroy #%s", tvColumnName.getText() );
			saveScrollPosition();
			listView.setAdapter( null );
			column.setColumnViewHolder( null );
			column = null;
		}

		closeBitmaps();
		
		activity.closeListItemPopup();
	}
	
	private static void vg( View v, boolean visible ){
		v.setVisibility( visible ? View.VISIBLE : View.GONE );
	}
	
	private boolean loading_busy;
	
	void onPageCreate( Column column, int page_idx, int page_count ){
		loading_busy = true;
		try{
			this.column = column;
			
			log.d( "onPageCreate:%s", column.getColumnName( true ) );
			
			boolean bSimpleList = ( column.column_type != Column.TYPE_CONVERSATION && activity.pref.getBoolean( Pref.KEY_SIMPLE_LIST, false ) );
			
			tvColumnIndex.setText( activity.getString( R.string.column_index, page_idx + 1, page_count ) );
			
			listView.setAdapter( null );
			
			this.status_adapter = new ItemListAdapter( activity, column , bSimpleList );
			if( column.column_type == Column.TYPE_PROFILE ){
				status_adapter.header =new HeaderViewHolder( activity, column, listView );
			}else{
				status_adapter.header = null;
			}
			
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
			
			llColumnSetting.setVisibility( View.GONE );
			
			cbDontCloseColumn.setChecked( column.dont_close );
			cbWithAttachment.setChecked( column.with_attachment );
			cbDontShowBoost.setChecked( column.dont_show_boost );
			cbDontShowReply.setChecked( column.dont_show_reply );
			cbDontStreaming.setChecked( column.dont_streaming );
			cbDontAutoRefresh.setChecked( column.dont_auto_refresh );
			cbHideMediaDefault.setChecked( column.hide_media_default );
			
			etRegexFilter.setText( column.regex_text );
			etSearch.setText( column.search_query );
			cbResolve.setChecked( column.search_resolve );
			
			vg( cbWithAttachment, bAllowFilter );
			vg( cbDontShowBoost, bAllowFilterBoost );
			vg( cbDontShowReply, bAllowFilterBoost );
			vg( cbDontStreaming, column.canStreaming() );
			vg( cbDontAutoRefresh, column.canAutoRefresh() );
			vg( cbHideMediaDefault, column.canShowMedia() );
			
			vg( etRegexFilter, bAllowFilter );
			vg( llRegexFilter, bAllowFilter );
			
			vg( btnDeleteNotification, column.column_type == Column.TYPE_NOTIFICATIONS );
			vg( llSearch, column.column_type == Column.TYPE_SEARCH );
			
			// tvRegexFilterErrorの表示を更新
			if( bAllowFilter ){
				isRegexValid();
			}
			
			switch( column.column_type ){
			case Column.TYPE_CONVERSATION:
			case Column.TYPE_SEARCH:
				swipyRefreshLayout.setEnabled( false );
				break;
			default:
				swipyRefreshLayout.setEnabled( true );
				break;
			}
			
			//
			listView.setAdapter( status_adapter );
			listView.setFastScrollEnabled( ! Pref.pref( activity ).getBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, true ) );
			listView.setOnItemClickListener( status_adapter );
			
			column.setColumnViewHolder( this );
			
			showColumnColor();
			
			showContent();
		}finally{
			loading_busy = false;
		}
	}
	
	void showColumnColor(){
		if( column == null ) return;
		
		int c = column.header_bg_color;
		if( c == 0 ){
			llColumnHeader.setBackgroundResource( R.drawable.btn_bg_ddd );
		}else{
			ViewCompat.setBackground( llColumnHeader, Styler.getAdaptiveRippleDrawable(
				c,
				( column.header_fg_color != 0 ? column.header_fg_color :
					Styler.getAttributeColor( activity, R.attr.colorRippleEffect ) )
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
	private AsyncTask<Void,Void,Bitmap> last_image_task;
	
	private void closeBitmaps(){
		try{
			ivColumnBackgroundImage.setVisibility( View.GONE );
			ivColumnBackgroundImage.setImageDrawable( null );

			if( last_image_bitmap != null ){
				last_image_bitmap.recycle();
				last_image_bitmap = null;
			}

			if( last_image_task != null ){
				last_image_task.cancel( true );
				last_image_task = null;
			}

			last_image_uri = null;

		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	private void loadBackgroundImage( final ImageView iv, final String url ){
		try{
			if( TextUtils.isEmpty( url ) ){
				// 指定がないなら閉じる
				closeBitmaps();
				return;
			}
			
			if( url.equals( last_image_uri ) ){
				// 今表示してるのと同じ
				return;
			}
			
			// 直前の処理をキャンセルする。Bitmapも破棄する
			closeBitmaps();

			if( TextUtils.isEmpty( url ) ){
				// この状態でOK
				return;
			}
			last_image_uri = url;
			
			final int screen_w = iv.getResources().getDisplayMetrics().widthPixels;
			final int screen_h = iv.getResources().getDisplayMetrics().heightPixels;
			
			// 非同期処理を開始
			last_image_task = new AsyncTask< Void, Void, Bitmap >() {
				@Override protected Bitmap doInBackground( Void... params ){
					try{
						int resize_max = ( screen_w > screen_h ? screen_w : screen_h );
						Uri uri = Uri.parse( url );
						return Utils.createResizedBitmap( log, activity, uri, false, resize_max );
						
					}catch(Throwable ex){
						ex.printStackTrace();
					}
					return null;
				}
				
				@Override protected void onCancelled( Bitmap bitmap ){
					onPostExecute( bitmap );
				}
				@Override protected void onPostExecute( Bitmap bitmap ){
					if( bitmap !=null ){
						if( isCancelled() || ! url.equals( last_image_uri ) ){
							bitmap.recycle();
						}else{
							last_image_bitmap = bitmap;
							iv.setImageBitmap( last_image_bitmap );
							iv.setVisibility( View.VISIBLE );
						}
					}
				}
			};
			last_image_task.executeOnExecutor( App1.task_executor );
			
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	private final Runnable proc_start_filter = new Runnable() {
		@Override public void run(){
			if( isPageDestroyed() ) return;
			if( column == null ) return;

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
		if( column == null ) return;
		column.startRefresh( false, direction == SwipyRefreshLayoutDirection.BOTTOM, - 1L, - 1 );
	}
	
	@Override public void onCheckedChanged( CompoundButton view, boolean isChecked ){
		if( loading_busy || column ==null || status_adapter ==null ) return;

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
		if( loading_busy || column ==null || status_adapter ==null ) return;
		
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
		
	}
	
	private void showColumnCloseButton(){
		if( column == null ) return;
		// カラム保護の状態
		btnColumnClose.setEnabled( ! column.dont_close );
		btnColumnClose.setAlpha( column.dont_close ? 0.3f : 1f );
	}
	
	/////////////////////////////////////////////////////////////////
	// Column から呼ばれる
	
	boolean hasHeaderView(){
		return status_adapter != null && status_adapter.header != null;
	}
	
	SwipyRefreshLayout getRefreshLayout(){
		return swipyRefreshLayout;
	}
	
	MyListView getListView(){
		return listView;
	}
	
	// カラムヘッダなど、負荷が低い部分の表示更新
	void showColumnHeader(){
		if( column == null ) return;
		
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
		// クラッシュレポートにadapterとリストデータの状態不整合が多かったので、
		// とりあえずリストデータ変更の通知だけは最優先で行っておく
		try{
			if( status_adapter != null ) status_adapter.notifyDataSetChanged();
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		
		showColumnHeader();
		
		if( column == null || column.is_dispose.get() ){
			showError( "column was disposed." );
			return;
		}
		
		if( status_adapter.header != null ){
			status_adapter.header.bind( column.who_account );
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
		
		if( status_adapter.getCount() == 0 ){
			showError( activity.getString( R.string.list_empty ) );
		}else{
			tvLoading.setVisibility( View.GONE );
			swipyRefreshLayout.setVisibility( View.VISIBLE );
			// 表示状態が変わった後にもう一度呼び出す必要があるらしい。。。
			status_adapter.notifyDataSetChanged();
		}
		restoreScrollPosition();
	}
	
	private void restoreScrollPosition(){
		if( column == null ) return;
		
		ScrollPosition sp = column.scroll_save;
		if( sp == null ) return;
		
		column.scroll_save = null;
		
		if( listView.getVisibility() == View.VISIBLE ){
			sp.restore( listView );
		}
		
	}
	
	private void saveScrollPosition(){
		if( column != null && ! column.is_dispose.get() ){
			if( listView.getVisibility() == View.VISIBLE ){
				column.scroll_save = new ScrollPosition( listView );
			}else{
				column.scroll_save = new ScrollPosition( 0, 0 );
			}
		}
	}
	
	@NonNull ScrollPosition getScrollPosition(){
		return new ScrollPosition( listView );
	}
	
	void setScrollPosition( @NonNull ScrollPosition sp, final float delta ){
		final ListAdapter last_adapter = listView.getAdapter();
		if( column == null || last_adapter == null ) return;
		
		sp.restore( listView );

		listView.postDelayed( new Runnable() {
			@Override public void run(){
				if( column == null || listView.getAdapter() != last_adapter ) return;
				listView.scrollListBy( (int) ( delta * activity.density ) );
			}
		}, 20L );
	}
}
