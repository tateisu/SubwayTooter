package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootGap;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.Emojione;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.MyLinkMovementMethod;
import jp.juggler.subwaytooter.util.MyListView;
import jp.juggler.subwaytooter.util.MyTextView;
import jp.juggler.subwaytooter.util.Utils;

class ColumnViewHolder implements View.OnClickListener, Column.VisualCallback, SwipyRefreshLayout.OnRefreshListener, CompoundButton.OnCheckedChangeListener {
	private static final LogCategory log = new LogCategory( "ColumnViewHolder" );
	
	public final ActMain activity;
	final AtomicBoolean is_destroyed = new AtomicBoolean( false );
	
	private final Column column;
	private final SavedAccount access_info;
	
	ColumnViewHolder( ActMain activity, Column column ){
		this.activity = activity;
		this.column = column;
		this.access_info = column.access_info;
	}
	
	private boolean isPageDestroyed(){
		return is_destroyed.get() || activity.isFinishing();
	}
	
	void onPageDestroy( @SuppressWarnings("UnusedParameters") View root ){
		saveScrollPosition();
		log.d( "onPageDestroy:%s", column.getColumnName( true ) );
		column.removeVisualListener( this );
		
		activity.closeListItemPopup();
	}
	
	private TextView tvLoading;
	private MyListView listView;
	private TextView tvColumnContext;
	private TextView tvColumnName;
	private StatusListAdapter status_adapter;
	private HeaderViewHolder vh_header;
	private SwipyRefreshLayout swipyRefreshLayout;
	private View btnSearch;
	private EditText etSearch;
	private CheckBox cbResolve;
	private View llColumnSetting;
	private EditText etRegexFilter;
	private TextView tvRegexFilterError;
	private View btnColumnClose;
	private ImageView ivColumnIcon;
	
	private boolean bSimpleList;
	
	void onPageCreate( View root, int page_idx, int page_count ){
		log.d( "onPageCreate:%s", column.getColumnName( true ) );
		
		acct_pad_lr = (int) ( 0.5f + 4f * activity.density );
		
		( (TextView) root.findViewById( R.id.tvColumnIndex ) )
			.setText( activity.getString( R.string.column_index, page_idx + 1, page_count ) );
		
		tvColumnName = (TextView) root.findViewById( R.id.tvColumnName );
		tvColumnContext = (TextView) root.findViewById( R.id.tvColumnContext );
		ivColumnIcon = (ImageView) root.findViewById( R.id.ivColumnIcon );
		btnColumnClose = root.findViewById( R.id.btnColumnClose );
		
		btnColumnClose.setOnClickListener( this );
		
		root.findViewById( R.id.btnColumnReload ).setOnClickListener( this );
		root.findViewById( R.id.llColumnHeader ).setOnClickListener( this );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (MyListView) root.findViewById( R.id.listView );
		status_adapter = new StatusListAdapter();
		listView.setAdapter( status_adapter );
		
		this.swipyRefreshLayout = (SwipyRefreshLayout) root.findViewById( R.id.swipyRefreshLayout );
		swipyRefreshLayout.setOnRefreshListener( this );
		swipyRefreshLayout.setDistanceToTriggerSync( (int) ( 0.5f + 20f * activity.density ) );
		
		View llSearch = root.findViewById( R.id.llSearch );
		btnSearch = root.findViewById( R.id.btnSearch );
		etSearch = (EditText) root.findViewById( R.id.etSearch );
		cbResolve = (CheckBox) root.findViewById( R.id.cbResolve );
		
		listView.setFastScrollEnabled( ! Pref.pref( activity ).getBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, false ) );
		
		boolean bAllowFilter;
		switch( column.type ){
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
		switch( column.type ){
		default:
			bAllowFilterBoost = false;
			break;
		case Column.TYPE_HOME:
		case Column.TYPE_PROFILE:
			bAllowFilterBoost = true;
			break;
		}
		
		View btnColumnSetting = root.findViewById( R.id.btnColumnSetting );
		llColumnSetting = root.findViewById( R.id.llColumnSetting );
		btnColumnSetting.setVisibility( View.VISIBLE );
		btnColumnSetting.setOnClickListener( this );
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
		if( column.type != Column.TYPE_NOTIFICATIONS ){
			button.setVisibility( View.GONE );
		}else{
			button.setVisibility( View.VISIBLE );
			button.setOnClickListener( this );
			
		}
		
		if( column.type != Column.TYPE_SEARCH ){
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
		
		if( column.type == Column.TYPE_PROFILE ){
			vh_header = new HeaderViewHolder( activity, listView );
			listView.addHeaderView( vh_header.viewRoot );
		}
		
		switch( column.type ){
		case Column.TYPE_CONVERSATION:
		case Column.TYPE_SEARCH:
			swipyRefreshLayout.setEnabled( false );
		}
		
		bSimpleList = activity.pref.getBoolean( Pref.KEY_SIMPLE_LIST, false );
		if( column.type == Column.TYPE_CONVERSATION ){
			bSimpleList = false;
		}
		if( bSimpleList ){
			listView.setOnItemClickListener( status_adapter );
		}
		
		//
		
		column.addVisualListener( this );
		onVisualColumn();
	}
	
	private final Runnable proc_start_filter = new Runnable() {
		@Override public void run(){
			if( isPageDestroyed() ) return;
			if( isRegexValid() ){
				column.regex_text = etRegexFilter.getText().toString();
				activity.saveColumnList();
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
			@SuppressWarnings("unused") Matcher m = Pattern.compile( s ).matcher( "" );
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
	
	@Override public void onCheckedChanged( CompoundButton view, boolean isChecked ){
		switch( view.getId() ){
		
		case R.id.cbDontCloseColumn:
			column.dont_close = isChecked;
			showColumnCloseButton();
			activity.saveColumnList();
			break;
		
		case R.id.cbWithAttachment:
			column.with_attachment = isChecked;
			activity.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.cbDontShowBoost:
			column.dont_show_boost = isChecked;
			activity.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.cbDontShowReply:
			column.dont_show_reply = isChecked;
			activity.saveColumnList();
			column.startLoading();
			break;
			
		}
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnColumnClose:
			activity.performColumnClose( false, column );
			break;
		
		case R.id.btnColumnReload:
			if( column.type == Column.TYPE_SEARCH ){
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

//	final RelationshipMap.UpdateCallback callback_relation = new RelationshipMap.UpdateCallback() {
//		@Override public void onRelationShipUpdate(){
//			onVisualColumn();
//		}
//	};
	
	private int acct_pad_lr;
	
	@Override public void onVisualColumn2(){
		
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
		tvColumnContext.setPaddingRelative( acct_pad_lr, 0, acct_pad_lr, 0 );
		
		tvColumnName.setText( column.getColumnName( false ) );
		
		showColumnCloseButton();
		
		ivColumnIcon.setImageResource( Styler.getAttributeResourceId( activity, Column.getIconAttrId(column.type) ) );
		
	}
	
	private void showColumnCloseButton(){
		// カラム保護の状態
		btnColumnClose.setEnabled( ! column.dont_close );
		btnColumnClose.setAlpha( column.dont_close ? 0.3f : 1f );
	}
	
	@Override
	public void onVisualColumn(){
		
		onVisualColumn2();
		
		if( column.is_dispose.get() ){
			showError( "column was disposed." );
			return;
		}
		
		if( vh_header != null ){
			vh_header.bind( activity, column.access_info, column.who_account );
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
			status_adapter.set( column.list_data );
			if( column.scroll_hack > 0 ){
				listView.setSelectionFromTop( column.scroll_hack - 1, - (int) ( 0.5f + 80f * activity.density ) );
				column.scroll_hack = 0;
			}
		}
		
		proc_restore_scroll.run();
		
	}
	
	@Override public void onRefresh( SwipyRefreshLayoutDirection direction ){
		String error = column.startRefresh( direction == SwipyRefreshLayoutDirection.BOTTOM );
		if( ! TextUtils.isEmpty( error ) ){
			swipyRefreshLayout.setRefreshing( false );
			Utils.showToast( activity, true, error );
		}
	}
	
	private final Runnable proc_restore_scroll = new Runnable() {
		@Override public void run(){
			if( column.scroll_pos == 0 && column.scroll_y == 0 ){
				return;
			}
			if( listView.getVisibility() != View.VISIBLE ){
				column.scroll_pos = 0;
				column.scroll_y = 0;
				return;
			}
			if( column.scroll_pos > status_adapter.getCount() ){
				column.scroll_pos = 0;
				column.scroll_y = 0;
				return;
			}
			listView.setSelectionFromTop( column.scroll_pos, column.scroll_y );
			column.scroll_pos = 0;
			column.scroll_y = 0;
		}
	};
	
	void saveScrollPosition(){
		column.scroll_pos = 0;
		column.scroll_y = 0;
		if( listView.getVisibility() == View.VISIBLE ){
			if( listView.getChildCount() > 0 ){
				int pos = listView.getFirstVisiblePosition();
				int y = listView.getChildAt( 0 ).getTop();
				column.scroll_pos = pos;
				column.scroll_y = y;
			}
		}
	}
	
	boolean isColumnSettingShown(){
		return llColumnSetting.getVisibility() == View.VISIBLE;
	}
	
	void closeColumnSetting(){
		llColumnSetting.setVisibility( View.GONE );
		
	}
	
	///////////////////////////////////////////////////////////////////
	
	private class HeaderViewHolder implements View.OnClickListener {
		final View viewRoot;
		final NetworkImageView ivBackground;
		final TextView tvCreated;
		final NetworkImageView ivAvatar;
		final TextView tvDisplayName;
		final TextView tvAcct;
		final Button btnFollowing;
		final Button btnFollowers;
		final Button btnStatusCount;
		final View btnMore;
		final TextView tvNote;
		final ImageButton btnFollow;
		
		TootAccount who;
		SavedAccount access_info;
		
		HeaderViewHolder( final ActMain activity, ListView parent ){
			viewRoot = activity.getLayoutInflater().inflate( R.layout.lv_list_header, parent, false );
			this.ivBackground = (NetworkImageView) viewRoot.findViewById( R.id.ivBackground );
			this.tvCreated = (TextView) viewRoot.findViewById( R.id.tvCreated );
			this.ivAvatar = (NetworkImageView) viewRoot.findViewById( R.id.ivAvatar );
			this.tvDisplayName = (TextView) viewRoot.findViewById( R.id.tvDisplayName );
			this.tvAcct = (TextView) viewRoot.findViewById( R.id.tvAcct );
			this.btnFollowing = (Button) viewRoot.findViewById( R.id.btnFollowing );
			this.btnFollowers = (Button) viewRoot.findViewById( R.id.btnFollowers );
			this.btnStatusCount = (Button) viewRoot.findViewById( R.id.btnStatusCount );
			this.tvNote = (TextView) viewRoot.findViewById( R.id.tvNote );
			this.btnMore = viewRoot.findViewById( R.id.btnMore );
			this.btnFollow = (ImageButton) viewRoot.findViewById( R.id.btnFollow );
			ivBackground.setOnClickListener( this );
			btnFollowing.setOnClickListener( this );
			btnFollowers.setOnClickListener( this );
			btnStatusCount.setOnClickListener( this );
			btnMore.setOnClickListener( this );
			btnFollow.setOnClickListener( this );
			
			tvNote.setMovementMethod( MyLinkMovementMethod.getInstance() );
		}
		
		void bind( ActMain activity, SavedAccount access_info, TootAccount who ){
			this.who = who;
			this.access_info = access_info;
			if( who == null ){
				tvCreated.setText( "" );
				ivBackground.setImageDrawable( null );
				ivAvatar.setImageDrawable( null );
				tvDisplayName.setText( "" );
				tvAcct.setText( "@" );
				tvNote.setText( "" );
				btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + "?" );
				btnFollowing.setText( activity.getString( R.string.following ) + "\n" + "?" );
				btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + "?" );
				
				btnFollow.setImageDrawable( null );
			}else{
				tvCreated.setText( TootStatus.formatTime( who.time_created_at ) );
				ivBackground.setImageUrl( access_info.supplyBaseUrl( who.header_static ), App1.getImageLoader() );
				ivAvatar.setImageUrl( access_info.supplyBaseUrl( who.avatar_static ), App1.getImageLoader() );
				tvDisplayName.setText( who.display_name );
				
				String s = "@" + access_info.getFullAcct( who );
				if( who.locked ){
					s += " " + Emojione.map_name2unicode.get( "lock" );
				}
				tvAcct.setText( Emojione.decodeEmoji( s ) );
				
				tvNote.setText( who.note );
				btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + who.statuses_count );
				btnFollowing.setText( activity.getString( R.string.following ) + "\n" + who.following_count );
				btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + who.followers_count );
				
				UserRelation relation = UserRelation.load( access_info.db_id, who.id );
				Styler.setFollowIcon( activity, btnFollow, relation );
			}
		}
		
		@Override
		public void onClick( View v ){
			switch( v.getId() ){
			
			case R.id.ivBackground:
				if( who != null ){
					// 強制的にブラウザで開く
					activity.openChromeTab( access_info, who.url, true );
				}
				break;
			
			case R.id.btnFollowing:
				column.profile_tab = Column.TAB_FOLLOWING;
				column.startLoading();
				break;
			
			case R.id.btnFollowers:
				column.profile_tab = Column.TAB_FOLLOWERS;
				column.startLoading();
				break;
			
			case R.id.btnStatusCount:
				column.profile_tab = Column.TAB_STATUS;
				column.startLoading();
				break;
			
			case R.id.btnMore:
				if( who != null ){
					new DlgContextMenu( activity, access_info, who, null ).show();
				}
				break;
			
			case R.id.btnFollow:
				if( who != null ){
					new DlgContextMenu( activity, access_info, who, null ).show();
				}
				break;
				
			}
		}
	}
	
	private class StatusListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
		final ArrayList< Object > status_list = new ArrayList<>();
		
		< T > void set( List< T > src ){
			this.status_list.clear();
			this.status_list.addAll( src );
			notifyDataSetChanged();
		}
		
		@Override
		public int getCount(){
			return status_list.size();
		}
		
		@Override
		public Object getItem( int position ){
			if( position >= 0 && position < status_list.size() ) return status_list.get( position );
			return null;
		}
		
		@Override
		public long getItemId( int position ){
			return 0;
		}
		
		@Override
		public View getView( int position, View view, ViewGroup parent ){
			Object o = ( position >= 0 && position < status_list.size() ? status_list.get( position ) : null );
			
			StatusViewHolder holder;
			if( view == null ){
				view = activity.getLayoutInflater().inflate( bSimpleList ? R.layout.lv_status_simple : R.layout.lv_status, parent, false );
				holder = new StatusViewHolder( view );
				view.setTag( holder );
			}else{
				holder = (StatusViewHolder) view.getTag();
			}
			holder.bind( o );
			return view;
		}
		
		@Override
		public void onItemClick( AdapterView< ? > parent, View view, int position, long id ){
			Object tag = view.getTag();
			if( tag instanceof StatusViewHolder ){
				( (StatusViewHolder) tag ).onItemClick( view );
			}
		}
	}
	
	private class StatusViewHolder implements View.OnClickListener, View.OnLongClickListener {
		
		final View llBoosted;
		final ImageView ivBoosted;
		final TextView tvBoosted;
		final TextView tvBoostedAcct;
		final TextView tvBoostedTime;
		
		final View llFollow;
		final NetworkImageView ivFollow;
		final TextView tvFollowerName;
		final TextView tvFollowerAcct;
		final ImageButton btnFollow;
		
		final View llStatus;
		final NetworkImageView ivThumbnail;
		final TextView tvName;
		final TextView tvTime;
		final TextView tvAcct;
		
		final View llContentWarning;
		final MyTextView tvContentWarning;
		final Button btnContentWarning;
		
		final View llContents;
		final MyTextView tvMentions;
		final MyTextView tvContent;
		
		final View flMedia;
		final View btnHideMedia;
		final View btnShowMedia;
		
		final NetworkImageView ivMedia1;
		final NetworkImageView ivMedia2;
		final NetworkImageView ivMedia3;
		final NetworkImageView ivMedia4;
		
		final ButtonsForStatus buttons_for_status;
		
		final View llSearchTag;
		final Button btnSearchTag;
		
		final TextView tvApplication;
		
		TootStatus status;
		TootAccount account_thumbnail;
		TootAccount account_boost;
		TootAccount account_follow;
		String search_tag;
		TootGap gap;
		
		StatusViewHolder( View view ){
			
			this.llBoosted = view.findViewById( R.id.llBoosted );
			this.ivBoosted = (ImageView) view.findViewById( R.id.ivBoosted );
			this.tvBoosted = (TextView) view.findViewById( R.id.tvBoosted );
			this.tvBoostedTime = (TextView) view.findViewById( R.id.tvBoostedTime );
			this.tvBoostedAcct = (TextView) view.findViewById( R.id.tvBoostedAcct );
			
			this.llFollow = view.findViewById( R.id.llFollow );
			this.ivFollow = (NetworkImageView) view.findViewById( R.id.ivFollow );
			this.tvFollowerName = (TextView) view.findViewById( R.id.tvFollowerName );
			this.tvFollowerAcct = (TextView) view.findViewById( R.id.tvFollowerAcct );
			this.btnFollow = (ImageButton) view.findViewById( R.id.btnFollow );
			
			this.llStatus = view.findViewById( R.id.llStatus );
			
			this.ivThumbnail = (NetworkImageView) view.findViewById( R.id.ivThumbnail );
			this.tvName = (TextView) view.findViewById( R.id.tvName );
			this.tvTime = (TextView) view.findViewById( R.id.tvTime );
			this.tvAcct = (TextView) view.findViewById( R.id.tvAcct );
			
			this.llContentWarning = view.findViewById( R.id.llContentWarning );
			this.tvContentWarning = (MyTextView) view.findViewById( R.id.tvContentWarning );
			this.btnContentWarning = (Button) view.findViewById( R.id.btnContentWarning );
			
			this.llContents = view.findViewById( R.id.llContents );
			this.tvContent = (MyTextView) view.findViewById( R.id.tvContent );
			this.tvMentions = (MyTextView) view.findViewById( R.id.tvMentions );
			
			this.buttons_for_status = bSimpleList ? null : new ButtonsForStatus( view );
			
			this.flMedia = view.findViewById( R.id.flMedia );
			this.btnHideMedia = view.findViewById( R.id.btnHideMedia );
			this.btnShowMedia = view.findViewById( R.id.btnShowMedia );
			this.ivMedia1 = (NetworkImageView) view.findViewById( R.id.ivMedia1 );
			this.ivMedia2 = (NetworkImageView) view.findViewById( R.id.ivMedia2 );
			this.ivMedia3 = (NetworkImageView) view.findViewById( R.id.ivMedia3 );
			this.ivMedia4 = (NetworkImageView) view.findViewById( R.id.ivMedia4 );
			
			this.llSearchTag = view.findViewById( R.id.llSearchTag );
			this.btnSearchTag = (Button) view.findViewById( R.id.btnSearchTag );
			this.tvApplication = (TextView) view.findViewById( R.id.tvApplication );
			
			btnSearchTag.setOnClickListener( this );
			btnContentWarning.setOnClickListener( this );
			btnHideMedia.setOnClickListener( this );
			btnShowMedia.setOnClickListener( this );
			ivMedia1.setOnClickListener( this );
			ivMedia2.setOnClickListener( this );
			ivMedia3.setOnClickListener( this );
			ivMedia4.setOnClickListener( this );
			btnFollow.setOnClickListener( this );
			
			ivThumbnail.setOnClickListener( this );
			// ここを個別タップにすると邪魔すぎる tvName.setOnClickListener( this );
			llBoosted.setOnClickListener( this );
			llFollow.setOnClickListener( this );
			btnFollow.setOnClickListener( this );
			
			// ロングタップ
			ivThumbnail.setOnLongClickListener( this );
			
			//
			tvContent.setMovementMethod( MyLinkMovementMethod.getInstance() );
			tvMentions.setMovementMethod( MyLinkMovementMethod.getInstance() );
			tvContentWarning.setMovementMethod( MyLinkMovementMethod.getInstance() );
			
		}
		
		void bind( Object item ){
			
			this.status = null;
			this.account_thumbnail = null;
			this.account_boost = null;
			this.account_follow = null;
			this.search_tag = null;
			this.gap = null;
			
			llBoosted.setVisibility( View.GONE );
			llFollow.setVisibility( View.GONE );
			llStatus.setVisibility( View.GONE );
			llSearchTag.setVisibility( View.GONE );
			
			if( item == null ) return;
			
			if( item instanceof String ){
				showSearchTag( (String) item );
			}else if( item instanceof TootAccount ){
				showFollow( (TootAccount) item );
			}else if( item instanceof TootNotification ){
				TootNotification n = (TootNotification) item;
				if( TootNotification.TYPE_FAVOURITE.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_favourite
						, Utils.formatSpannable1( activity, R.string.display_name_favourited_by, n.account.display_name )
					);
					if( n.status != null ) showStatus( activity, n.status );
				}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, n.account.display_name )
					);
					if( n.status != null ) showStatus( activity, n.status );
				}else if( TootNotification.TYPE_FOLLOW.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_followed_by, n.account.display_name )
					);
					//
					showFollow( n.account );
				}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
					if( ! bSimpleList ){
						showBoost(
							n.account
							, n.time_created_at
							, R.attr.btn_reply
							, Utils.formatSpannable1( activity, R.string.display_name_replied_by, n.account.display_name )
						);
					}
					if( n.status != null ) showStatus( activity, n.status );
				}
			}else if( item instanceof TootStatus ){
				TootStatus status = (TootStatus) item;
				if( status.reblog != null ){
					showBoost(
						status.account
						, status.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, status.account.display_name )
					);
					showStatus( activity, status.reblog );
				}else{
					showStatus( activity, status );
				}
			}else if( item instanceof TootGap ){
				showGap( (TootGap) item );
			}
		}
		
		private void showSearchTag( String tag ){
			this.gap = null;
			search_tag = tag;
			llSearchTag.setVisibility( View.VISIBLE );
			btnSearchTag.setText( "#" + tag );
		}
		
		private void showGap( TootGap gap ){
			this.gap = gap;
			search_tag = null;
			llSearchTag.setVisibility( View.VISIBLE );
			btnSearchTag.setText( activity.getString( R.string.read_gap ) );
		}
		
		void showBoost( TootAccount who, long time, int icon_attr_id, CharSequence text ){
			account_boost = who;
			llBoosted.setVisibility( View.VISIBLE );
			ivBoosted.setImageResource( Styler.getAttributeResourceId( activity, icon_attr_id ) );
			tvBoostedTime.setText( TootStatus.formatTime( time ) );
			tvBoosted.setText( text );
			setAcct( tvBoostedAcct, access_info.getFullAcct( who ), R.attr.colorAcctSmall );
		}
		
		private void showFollow( TootAccount who ){
			account_follow = who;
			llFollow.setVisibility( View.VISIBLE );
			ivFollow.setImageUrl( access_info.supplyBaseUrl( who.avatar_static ), App1.getImageLoader() );
			tvFollowerName.setText( who.display_name );
			setAcct( tvFollowerAcct, access_info.getFullAcct( who ), R.attr.colorAcctSmall );
			
			UserRelation relation = UserRelation.load( access_info.db_id, who.id );
			Styler.setFollowIcon( activity, btnFollow, relation );
		}
		
		private void showStatus( ActMain activity, TootStatus status ){
			this.status = status;
			account_thumbnail = status.account;
			llStatus.setVisibility( View.VISIBLE );
			
			setAcct( tvAcct, access_info.getFullAcct( status.account ), R.attr.colorAcctSmall );
			tvTime.setText( TootStatus.formatTime( status.time_created_at ) );
			
			tvName.setText( status.account.display_name );
			ivThumbnail.setImageUrl( access_info.supplyBaseUrl( status.account.avatar_static ), App1.getImageLoader() );
			tvContent.setText( status.decoded_content );

//			if( status.decoded_tags == null ){
//				tvTags.setVisibility( View.GONE );
//			}else{
//				tvTags.setVisibility( View.VISIBLE );
//				tvTags.setText( status.decoded_tags );
//			}
			
			if( status.decoded_mentions == null ){
				tvMentions.setVisibility( View.GONE );
			}else{
				tvMentions.setVisibility( View.VISIBLE );
				tvMentions.setText( status.decoded_mentions );
			}
			
			// Content warning
			if( TextUtils.isEmpty( status.spoiler_text ) ){
				llContentWarning.setVisibility( View.GONE );
				llContents.setVisibility( View.VISIBLE );
			}else{
				llContentWarning.setVisibility( View.VISIBLE );
				tvContentWarning.setText( status.decoded_spoiler_text );
				boolean cw_shown = ContentWarning.isShown( access_info.host, status.id, false );
				showContent( cw_shown );
			}
			
			if( status.media_attachments == null || status.media_attachments.isEmpty() ){
				flMedia.setVisibility( View.GONE );
			}else{
				flMedia.setVisibility( View.VISIBLE );
				setMedia( ivMedia1, status, 0 );
				setMedia( ivMedia2, status, 1 );
				setMedia( ivMedia3, status, 2 );
				setMedia( ivMedia4, status, 3 );
				
				// hide sensitive media
				boolean is_shown = MediaShown.isShown( access_info.host, status.id, access_info.dont_hide_nsfw || ! status.sensitive );
				btnShowMedia.setVisibility( ! is_shown ? View.VISIBLE : View.GONE );
			}
			
			if( buttons_for_status != null ){
				buttons_for_status.bind( status );
			}
			
			if( tvApplication != null ){
				switch( column.type ){
				default:
					tvApplication.setVisibility( View.GONE );
					break;
				
				case Column.TYPE_CONVERSATION:
					if( status.application == null ){
						tvApplication.setVisibility( View.GONE );
					}else{
						tvApplication.setVisibility( View.VISIBLE );
						tvApplication.setText( activity.getString( R.string.application_is, status.application.name ) );
					}
					break;
					
				}
			}
		}
		
		private void setAcct( TextView tv, String acct, int color_attr_id ){
			AcctColor ac = AcctColor.load( acct );
			tv.setText( AcctColor.hasNickname( ac ) ? ac.nickname : acct );
			tv.setTextColor( AcctColor.hasColorForeground( ac ) ? ac.color_fg : Styler.getAttributeColor( activity, color_attr_id ) );
			
			if( AcctColor.hasColorBackground( ac ) ){
				tv.setBackgroundColor( ac.color_bg );
			}else{
				ViewCompat.setBackground( tv, null );
			}
			tv.setPaddingRelative( acct_pad_lr, 0, acct_pad_lr, 0 );
			
		}
		
		private void showContent( boolean shown ){
			btnContentWarning.setText( shown ? R.string.hide : R.string.show );
			llContents.setVisibility( shown ? View.VISIBLE : View.GONE );
			
		}
		
		private void setMedia( NetworkImageView iv, TootStatus status, int idx ){
			if( idx >= status.media_attachments.size() ){
				iv.setVisibility( View.GONE );
			}else{
				iv.setVisibility( View.VISIBLE );
				TootAttachment ta = status.media_attachments.get( idx );
				String url = ta.preview_url;
				if( TextUtils.isEmpty( url ) ) url = ta.remote_url;
				iv.setImageUrl( access_info.supplyBaseUrl( url ), App1.getImageLoader() );
			}
		}
		
		@Override
		public void onClick( View v ){
			switch( v.getId() ){
			case R.id.btnHideMedia:
				MediaShown.save( access_info.host, status.id, false );
				btnShowMedia.setVisibility( View.VISIBLE );
				break;
			case R.id.btnShowMedia:
				MediaShown.save( access_info.host, status.id, true );
				btnShowMedia.setVisibility( View.GONE );
				break;
			case R.id.ivMedia1:
				clickMedia( 0 );
				break;
			case R.id.ivMedia2:
				clickMedia( 1 );
				break;
			case R.id.ivMedia3:
				clickMedia( 2 );
				break;
			case R.id.ivMedia4:
				clickMedia( 3 );
				break;
			case R.id.btnContentWarning:{
				boolean new_shown = ( llContents.getVisibility() == View.GONE );
				ContentWarning.save( access_info.host, status.id, new_shown );
				status_adapter.notifyDataSetChanged();
				break;
			}
			
			case R.id.ivThumbnail:
				activity.performOpenUser( access_info, account_thumbnail );
				break;
			case R.id.llBoosted:
				activity.performOpenUser( access_info, account_boost );
				break;
			case R.id.llFollow:
				activity.performOpenUser( access_info, account_follow );
				break;
			case R.id.btnFollow:
				new DlgContextMenu( activity, access_info, account_follow, null ).show();
				break;
			
			case R.id.btnSearchTag:
				if( search_tag != null ){
					activity.openHashTag( access_info, search_tag );
				}else if( gap != null ){
					String error = column.startGap( gap );
					if( ! TextUtils.isEmpty( error ) ){
						Utils.showToast( activity, true, error );
					}else{
						swipyRefreshLayout.setRefreshing( true );
					}
				}
				break;
			}
		}
		
		@Override public boolean onLongClick( View v ){
			switch( v.getId() ){
			case R.id.ivThumbnail:
				new DlgContextMenu( activity, access_info, account_thumbnail, null ).show();
				break;
			}
			return false;
		}
		
		private void clickMedia( int i ){
			try{
				TootAttachment a = status.media_attachments.get( i );
				
				String sv;
				if( Pref.pref( activity ).getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) ){
					sv = a.url;
					if( TextUtils.isEmpty( sv ) ){
						sv = a.remote_url;
					}
				}else{
					sv = a.remote_url;
					if( TextUtils.isEmpty( sv ) ){
						sv = a.url;
					}
				}
				activity.openChromeTab( access_info, sv, false );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		
		// 簡略ビューの時だけ呼ばれる
		void onItemClick( View anchor ){
			
			if( status != null ){
				activity.closeListItemPopup();
				// ポップアップを表示する
				activity.list_item_popup = new ListItemPopup();
				activity.list_item_popup.show( anchor, status );
			}
		}
		
	}
	
	private final ActMain.RelationChangedCallback favourite_complete_callback = new ActMain.RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( activity, false, R.string.favourite_succeeded );
		}
	};
	private final ActMain.RelationChangedCallback boost_complete_callback = new ActMain.RelationChangedCallback() {
		@Override public void onRelationChanged(){
			Utils.showToast( activity, false, R.string.boost_succeeded );
		}
	};
	
	private class ButtonsForStatus implements View.OnClickListener {
		final ImageButton btnConversation;
		final ImageButton btnReply;
		final Button btnBoost;
		final Button btnFavourite;
		final ImageButton btnMore;
		
		ButtonsForStatus( View viewRoot ){
			btnConversation = (ImageButton) viewRoot.findViewById( R.id.btnConversation );
			btnReply = (ImageButton) viewRoot.findViewById( R.id.btnReply );
			btnBoost = (Button) viewRoot.findViewById( R.id.btnBoost );
			btnFavourite = (Button) viewRoot.findViewById( R.id.btnFavourite );
			btnMore = (ImageButton) viewRoot.findViewById( R.id.btnMore );
			btnConversation.setOnClickListener( this );
			btnReply.setOnClickListener( this );
			btnBoost.setOnClickListener( this );
			btnFavourite.setOnClickListener( this );
			btnMore.setOnClickListener( this );
			
		}
		
		TootStatus status;
		
		void bind( TootStatus status ){
			this.status = status;
			
			int color_normal = Styler.getAttributeColor( activity, R.attr.colorImageButton );
			int color_accent = Styler.getAttributeColor( activity, R.attr.colorImageButtonAccent );
			
			if( TootStatus.VISIBILITY_DIRECT.equals( status.visibility ) ){
				setButton( btnBoost, false, color_accent, R.attr.ic_mail, "" );
			}else if( TootStatus.VISIBILITY_PRIVATE.equals( status.visibility ) ){
				setButton( btnBoost, false, color_accent, R.attr.ic_lock, "" );
			}else if( activity.isBusyBoost( access_info, status ) ){
				setButton( btnBoost, false, color_normal, R.attr.btn_refresh, "?" );
			}else{
				int color = ( status.reblogged ? color_accent : color_normal );
				setButton( btnBoost, true, color, R.attr.btn_boost, Long.toString( status.reblogs_count ) );
			}
			
			if( activity.isBusyFav( access_info, status ) ){
				setButton( btnFavourite, false, color_normal, R.attr.btn_refresh, "?" );
			}else{
				int color = ( status.favourited ? color_accent : color_normal );
				setButton( btnFavourite, true, color, R.attr.btn_favourite, Long.toString( status.favourites_count ) );
			}
			
		}
		
		private void setButton( Button b, boolean enabled, int color, int icon_attr, String text ){
			Drawable d = Styler.getAttributeDrawable( activity, icon_attr ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			b.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
			b.setText( text );
			b.setTextColor( color );
			b.setEnabled( enabled );
		}
		
		PopupWindow close_window;
		
		@Override public void onClick( View v ){
			if( close_window != null ) close_window.dismiss();
			switch( v.getId() ){
			case R.id.btnConversation:
				activity.performConversation( access_info, status );
				break;
			case R.id.btnReply:
				if( access_info.isPseudo() ){
					Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
				}else{
					activity.performReply( access_info, status );
				}
				break;
			case R.id.btnBoost:
				if( access_info.isPseudo() ){
					Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
				}else{
					activity.performBoost( access_info, status, false, bSimpleList ? boost_complete_callback : null );
				}
				break;
			case R.id.btnFavourite:
				if( access_info.isPseudo() ){
					Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
				}else{
					activity.performFavourite( access_info, status, bSimpleList ? favourite_complete_callback : null );
				}
				break;
			case R.id.btnMore:
				new DlgContextMenu( activity, access_info, status.account, status ).show();
				break;
			}
		}
	}
	
	class ListItemPopup {
		final View viewRoot;
		final ButtonsForStatus buttons_for_status;
		
		@SuppressLint("InflateParams") ListItemPopup(){
			viewRoot = activity.getLayoutInflater().inflate( R.layout.list_item_popup, null, false );
			buttons_for_status = new ButtonsForStatus( viewRoot );
		}
		
		PopupWindow window;
		
		void dismiss(){
			if( window != null && window.isShowing() ){
				window.dismiss();
			}
		}
		
		void show( View anchor, TootStatus status ){
			
			//
			window = new PopupWindow( activity );
			window.setWidth( WindowManager.LayoutParams.WRAP_CONTENT );
			window.setHeight( WindowManager.LayoutParams.WRAP_CONTENT );
			window.setContentView( viewRoot );
			window.setBackgroundDrawable( new ColorDrawable( 0x00000000 ) );
			window.setTouchable( true );
			window.setOutsideTouchable( true );
			window.setTouchInterceptor( new View.OnTouchListener() {
				@Override public boolean onTouch( View v, MotionEvent event ){
					if( MotionEventCompat.getActionMasked( event ) == MotionEvent.ACTION_OUTSIDE ){
						window.dismiss();
						listView.last_popup_close = SystemClock.elapsedRealtime();
						return true;
					}
					return false;
				}
			} );
			
			buttons_for_status.bind( status );
			buttons_for_status.close_window = window;
			
			int[] location = new int[ 2 ];
			
			anchor.getLocationOnScreen( location );
			int anchor_top = location[ 1 ];
			
			listView.getLocationOnScreen( location );
			int listView_top = location[ 1 ];
			
			int clip_top = listView_top + (int) ( 0.5f + 8f * activity.density );
			int clip_bottom = listView_top + listView.getHeight() - (int) ( 0.5f + 8f * activity.density );
			
			int popup_height = (int) ( 0.5f + ( 56f + 24f ) * activity.density );
			int popup_y = anchor_top + anchor.getHeight() / 2;
			
			if( popup_y < clip_top ){
				// 画面外のは画面内にする
				popup_y = clip_top;
			}else if( clip_bottom - popup_y < popup_height ){
				// 画面外のは画面内にする
				if( popup_y > clip_bottom ) popup_y = clip_bottom;
				
				// 画面の下側にあるならポップアップの吹き出しが下から出ているように見せる
				viewRoot.findViewById( R.id.ivTriangleTop ).setVisibility( View.GONE );
				viewRoot.findViewById( R.id.ivTriangleBottom ).setVisibility( View.VISIBLE );
				popup_y -= popup_height;
			}
			window.showAtLocation(
				listView
				, Gravity.CENTER_HORIZONTAL | Gravity.TOP
				, 0
				, popup_y
			);
		}
	}
}
