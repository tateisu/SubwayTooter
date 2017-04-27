package jp.juggler.subwaytooter;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootGap;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Emojione;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

class ColumnViewHolder implements View.OnClickListener, Column.VisualCallback, SwipyRefreshLayout.OnRefreshListener, CompoundButton.OnCheckedChangeListener {
	private static final LogCategory log = new LogCategory( "ColumnViewHolder" );
	
	public final ActMain activity;
	final AtomicBoolean is_destroyed = new AtomicBoolean( false );
	
	private final Column column;
	
	ColumnViewHolder( ActMain activity, Column column ){
		this.activity = activity;
		this.column = column;
	}
	
	public boolean isPageDestroyed(){
		return is_destroyed.get() || activity.isFinishing();
	}
	
	void onPageDestroy( View root ){
		saveScrollPosition();
		log.d( "onPageDestroy:%s", column.getColumnName( true ) );
		column.removeVisualListener( this );
	}
	
	private TextView tvLoading;
	private ListView listView;
	private TextView tvColumnContext;
	private TextView tvColumnName;
	private StatusListAdapter status_adapter;
	private HeaderViewHolder vh_header;
	private SwipyRefreshLayout swipyRefreshLayout;
	private View btnSearch;
	private EditText etSearch;
	private CheckBox cbResolve;
	private View llColumnSetting;
	
	void onPageCreate( View root, int page_idx, int page_count ){
		log.d( "onPageCreate:%s", column.getColumnName( true ) );
		
		( (TextView) root.findViewById( R.id.tvColumnIndex ) )
			.setText( activity.getString( R.string.column_index, page_idx + 1, page_count ) );
		
		tvColumnName = (TextView) root.findViewById( R.id.tvColumnName );
		tvColumnContext = (TextView) root.findViewById( R.id.tvColumnContext );
		
		root.findViewById( R.id.btnColumnClose ).setOnClickListener( this );
		root.findViewById( R.id.btnColumnReload ).setOnClickListener( this );
		root.findViewById( R.id.llColumnHeader ).setOnClickListener( this );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (ListView) root.findViewById( R.id.listView );
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
		
		boolean bAllowMediaOnly;
		switch( column.type ){
		default:
			bAllowMediaOnly = true;
			break;
		case Column.TYPE_SEARCH:
		case Column.TYPE_CONVERSATION:
		case Column.TYPE_REPORTS:
		case Column.TYPE_BLOCKS:
		case Column.TYPE_MUTES:
		case Column.TYPE_NOTIFICATIONS:
			bAllowMediaOnly = false;
			break;
		}
		View btnColumnSetting = root.findViewById( R.id.btnColumnSetting );
		llColumnSetting = root.findViewById( R.id.llColumnSetting );
		btnColumnSetting.setVisibility( View.VISIBLE );
		btnColumnSetting.setOnClickListener( this );
		llColumnSetting.setVisibility( View.GONE );
		
		CheckBox cbWithAttachment = (CheckBox) root.findViewById( R.id.cbWithAttachment );
		cbWithAttachment.setChecked( column.with_attachment );
		cbWithAttachment.setOnCheckedChangeListener( this );
		cbWithAttachment.setEnabled( bAllowMediaOnly );
		
		CheckBox cbDontCloseColumn = (CheckBox) root.findViewById( R.id.cbDontCloseColumn );
		cbDontCloseColumn.setChecked( column.dont_close );
		cbDontCloseColumn.setOnCheckedChangeListener( this );
		
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
		
		//
		
		column.addVisualListener( this );
		onVisualColumn();
	}
	
	@Override public void onCheckedChanged( CompoundButton view, boolean isChecked ){
		switch( view.getId() ){
		
		case R.id.cbWithAttachment:
			column.with_attachment = isChecked;
			activity.saveColumnList();
			column.reload();
			break;
		
		case R.id.cbDontCloseColumn:
			column.dont_close = isChecked;
			activity.saveColumnList();
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
				hideKeyboard( etSearch );
				etSearch.setText( column.search_query );
				cbResolve.setChecked( column.search_resolve );
			}
			column.reload();
			break;
		
		case R.id.btnSearch:
			hideKeyboard( etSearch );
			column.search_query = etSearch.getText().toString().trim();
			column.search_resolve = cbResolve.isChecked();
			column.reload();
			break;
		
		case R.id.llColumnHeader:
			if( status_adapter.getCount() > 0 ) listView.setSelectionFromTop( 0, 0 );
			break;
		
		case R.id.btnColumnSetting:
			llColumnSetting.setVisibility( llColumnSetting.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE );
			break;
		}
		
	}
	
	private void hideKeyboard( View v ){
		InputMethodManager imm = (InputMethodManager) activity.getSystemService( Context.INPUT_METHOD_SERVICE );
		imm.hideSoftInputFromWindow( v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS );
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
	
	@Override
	public void onVisualColumn(){
//		App1.relationship_map.checkUpdate( activity, column.access_info,callback_relation );
		
		tvColumnContext.setText( column.access_info.getFullAcct( column.access_info ) );
		tvColumnName.setText( column.getColumnName( false ) );
		
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
			
			ivBackground.setOnClickListener( this );
			btnFollowing.setOnClickListener( this );
			btnFollowers.setOnClickListener( this );
			btnStatusCount.setOnClickListener( this );
			btnMore.setOnClickListener( this );
			
			tvNote.setMovementMethod( LinkMovementMethod.getInstance() );
		}
		
		void bind( ActMain activity, SavedAccount access_info, TootAccount who ){
			this.who = who;
			this.access_info = access_info;
			if( who == null ){
				tvCreated.setText( "" );
				ivBackground.setImageDrawable( null );
				ivAvatar.setImageDrawable( null );
				tvDisplayName.setText( "" );
				tvAcct.setText( "" );
				tvNote.setText( "" );
				btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + "?" );
				btnFollowing.setText( activity.getString( R.string.following ) + "\n" + "?" );
				btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + "?" );
			}else{
				tvCreated.setText( TootStatus.formatTime( who.time_created_at ) );
				ivBackground.setImageUrl( who.header_static, App1.getImageLoader() );
				ivAvatar.setImageUrl( who.avatar_static, App1.getImageLoader() );
				tvDisplayName.setText( who.display_name );
				
				String s = access_info.getFullAcct( who );
				if( who.locked ){
					s += " " + Emojione.map_name2unicode.get( "lock" );
				}
				tvAcct.setText( Emojione.decodeEmoji( s ) );
				
				tvNote.setText( who.note );
				btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + who.statuses_count );
				btnFollowing.setText( activity.getString( R.string.following ) + "\n" + who.following_count );
				btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + who.followers_count );
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
				column.reload();
				break;
			
			case R.id.btnFollowers:
				column.profile_tab = Column.TAB_FOLLOWERS;
				column.reload();
				break;
			
			case R.id.btnStatusCount:
				column.profile_tab = Column.TAB_STATUS;
				column.reload();
				break;
			
			case R.id.btnMore:
				activity.openAccountMoreMenu( access_info, who );
				break;
				
			}
		}
	}
	
	private class StatusListAdapter extends BaseAdapter {
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
				view = activity.getLayoutInflater().inflate( R.layout.lv_status, parent, false );
				holder = new StatusViewHolder( view );
				view.setTag( holder );
			}else{
				holder = (StatusViewHolder) view.getTag();
			}
			holder.bind( activity, o, column.access_info );
			return view;
		}
	}
	
	private class StatusViewHolder implements View.OnClickListener {
		
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
		final TextView tvContentWarning;
		final Button btnContentWarning;
		
		final View llContents;
		final TextView tvMentions;
		final TextView tvContent;
		
		final View flMedia;
		final View btnHideMedia;
		final View btnShowMedia;
		
		final NetworkImageView ivMedia1;
		final NetworkImageView ivMedia2;
		final NetworkImageView ivMedia3;
		final NetworkImageView ivMedia4;
		
		final ImageButton btnReply;
		final Button btnBoost;
		final Button btnFavourite;
		final ImageButton btnMore;
		
		TootStatus status;
		SavedAccount access_info;
		TootAccount account_thumbnail;
		TootAccount account_boost;
		TootAccount account_follow;
		View btnConversation;
		
		String search_tag;
		View llSearchTag;
		Button btnSearchTag;
		
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
			this.tvContentWarning = (TextView) view.findViewById( R.id.tvContentWarning );
			this.btnContentWarning = (Button) view.findViewById( R.id.btnContentWarning );
			
			this.llContents = view.findViewById( R.id.llContents );
			this.tvContent = (TextView) view.findViewById( R.id.tvContent );
			this.tvMentions = (TextView) view.findViewById( R.id.tvMentions );
			
			this.btnConversation = view.findViewById( R.id.btnConversation );
			this.btnReply = (ImageButton) view.findViewById( R.id.btnReply );
			this.btnBoost = (Button) view.findViewById( R.id.btnBoost );
			this.btnFavourite = (Button) view.findViewById( R.id.btnFavourite );
			this.btnMore = (ImageButton) view.findViewById( R.id.btnMore );
			
			this.flMedia = view.findViewById( R.id.flMedia );
			this.btnHideMedia = view.findViewById( R.id.btnHideMedia );
			this.btnShowMedia = view.findViewById( R.id.btnShowMedia );
			this.ivMedia1 = (NetworkImageView) view.findViewById( R.id.ivMedia1 );
			this.ivMedia2 = (NetworkImageView) view.findViewById( R.id.ivMedia2 );
			this.ivMedia3 = (NetworkImageView) view.findViewById( R.id.ivMedia3 );
			this.ivMedia4 = (NetworkImageView) view.findViewById( R.id.ivMedia4 );
			
			this.llSearchTag = view.findViewById( R.id.llSearchTag );
			this.btnSearchTag = (Button) view.findViewById( R.id.btnSearchTag );
			
			btnSearchTag.setOnClickListener( this );
			btnContentWarning.setOnClickListener( this );
			btnHideMedia.setOnClickListener( this );
			btnShowMedia.setOnClickListener( this );
			ivMedia1.setOnClickListener( this );
			ivMedia2.setOnClickListener( this );
			ivMedia3.setOnClickListener( this );
			ivMedia4.setOnClickListener( this );
			btnFollow.setOnClickListener( this );
			btnConversation.setOnClickListener( this );
			btnReply.setOnClickListener( this );
			btnBoost.setOnClickListener( this );
			btnFavourite.setOnClickListener( this );
			btnMore.setOnClickListener( this );
			
			ivThumbnail.setOnClickListener( this );
			// ここを個別タップにすると邪魔すぎる tvName.setOnClickListener( this );
			llBoosted.setOnClickListener( this );
			llFollow.setOnClickListener( this );
			btnFollow.setOnClickListener( this );
			
			tvContent.setMovementMethod( LinkMovementMethod.getInstance() );
			tvMentions.setMovementMethod( LinkMovementMethod.getInstance() );
		}
		
		void bind( ActMain activity, Object item, SavedAccount access_info ){
			this.access_info = access_info;
			
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
					if( n.status != null ) showStatus( activity, n.status, access_info );
				}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, n.account.display_name )
					);
					if( n.status != null ) showStatus( activity, n.status, access_info );
				}else if( TootNotification.TYPE_FOLLOW.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_followed_by, n.account.display_name )
					);
					//
					showFollow(
						n.account
					);
				}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_reply
						, Utils.formatSpannable1( activity, R.string.display_name_replied_by, n.account.display_name )
					);
					if( n.status != null ) showStatus( activity, n.status, access_info );
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
					showStatus( activity, status.reblog, access_info );
				}else{
					showStatus( activity, status, access_info );
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
			ivBoosted.setImageResource( Styler.getAttributeResourceId(activity,icon_attr_id) );
			tvBoostedTime.setText( TootStatus.formatTime( time ) );
			tvBoostedAcct.setText( access_info.getFullAcct( who ) );
			tvBoosted.setText( text );
		}
		
		private void showFollow( TootAccount who ){
			account_follow = who;
			llFollow.setVisibility( View.VISIBLE );
			ivFollow.setImageUrl( who.avatar_static, App1.getImageLoader() );
			tvFollowerName.setText( who.display_name );
			tvFollowerAcct.setText( access_info.getFullAcct( who ) );
			
			btnFollow.setImageResource( Styler.getAttributeResourceId( activity,R.attr.ic_account_add ));
		}
		
		private void showStatus( ActMain activity, TootStatus status, SavedAccount account ){
			this.status = status;
			account_thumbnail = status.account;
			llStatus.setVisibility( View.VISIBLE );
			
			tvAcct.setText( account.getFullAcct( status.account ) );
			tvTime.setText( TootStatus.formatTime( status.time_created_at ) );
			
			tvName.setText( status.account.display_name );
			ivThumbnail.setImageUrl( status.account.avatar_static, App1.getImageLoader() );
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
				boolean cw_shown = ContentWarning.isShown( account.host, status.id, false );
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
				boolean is_shown = MediaShown.isShown( account.host, status.id, account.dont_hide_nsfw || ! status.sensitive );
				btnShowMedia.setVisibility( ! is_shown ? View.VISIBLE : View.GONE );
			}
			
			Drawable d;
			int color_normal = Styler.getAttributeColor( activity,R.attr.colorImageButton );
			int color_accent = Styler.getAttributeColor( activity,R.attr.colorImageButtonAccent );
			
			if( activity.isBusyBoost( account, status ) ){
				d = Styler.getAttributeDrawable( activity,R.attr.btn_refresh ).mutate();
				d.setColorFilter( color_normal, PorterDuff.Mode.SRC_ATOP );
				btnBoost.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
				btnBoost.setText( "?" );
				btnBoost.setTextColor( color_normal );
				
			}else{
				int color = ( status.reblogged ? color_accent : color_normal );
				d = Styler.getAttributeDrawable( activity,R.attr.btn_boost ).mutate();
				d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
				btnBoost.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
				btnBoost.setText( Long.toString( status.reblogs_count ) );
				btnBoost.setTextColor( color );
				
			}
			
			if( activity.isBusyFav( account, status ) ){
				d = Styler.getAttributeDrawable( activity,R.attr.btn_refresh ).mutate();
				d.setColorFilter( color_normal, PorterDuff.Mode.SRC_ATOP );
				btnFavourite.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
				btnFavourite.setText( "?" );
				btnFavourite.setTextColor( color_normal );
			}else{
				int color = ( status.favourited ? color_accent : color_normal );
				d = Styler.getAttributeDrawable( activity,R.attr.btn_favourite ).mutate();
				d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
				btnFavourite.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
				btnFavourite.setText( Long.toString( status.favourites_count ) );
				btnFavourite.setTextColor( color );
			}
			
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
				iv.setImageUrl( url, App1.getImageLoader() );
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
				showContent( new_shown );
				break;
			}
			
			case R.id.btnConversation:
				activity.performConversation( access_info, status );
				break;
			case R.id.btnReply:
				activity.performReply( access_info, status );
				break;
			case R.id.btnBoost:
				activity.performBoost( access_info, status, false );
				break;
			case R.id.btnFavourite:
				activity.performFavourite( access_info, status );
				break;
			case R.id.btnMore:
				activity.openStatusMoreMenu( access_info, status );
				break;
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
				activity.openAccountMoreMenu( access_info, account_follow );
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
		
	}
	
}
