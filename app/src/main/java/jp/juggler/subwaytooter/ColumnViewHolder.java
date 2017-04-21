package jp.juggler.subwaytooter;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ColumnViewHolder implements View.OnClickListener, Column.VisualCallback {
	static final LogCategory log = new LogCategory( "ColumnViewHolder" );
	
	public final AtomicBoolean is_destroyed = new AtomicBoolean( false );
	public final ActMain activity;
	public final Column column;
	public final int column_index;
	
	public ColumnViewHolder( ActMain activity, Column column, int column_index ){
		log.d( "ctor" );
		this.activity = activity;
		this.column = column;
		this.column_index = column_index;
	}
	
	public boolean isPageDestroyed(){
		return is_destroyed.get() || activity.isFinishing();
	}
	
	TextView tvLoading;
	ListView listView;
	TextView tvColumnContext;
	TextView tvColumnName;
	StatusListAdapter status_adapter;
	HeaderViewHolder vh_header;
	
	void onPageCreate( View root ){
		log.d( "onPageCreate:%s", column.getColumnName() );
		
		tvColumnName = (TextView) root.findViewById( R.id.tvColumnName );
		tvColumnContext = (TextView) root.findViewById( R.id.tvColumnContext );
		
		root.findViewById( R.id.btnColumnClose ).setOnClickListener( this );
		root.findViewById( R.id.btnColumnReload ).setOnClickListener( this );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (ListView) root.findViewById( R.id.listView );
		status_adapter = new StatusListAdapter();
		listView.setAdapter( status_adapter );
		
		if( column.type == Column.TYPE_TL_STATUSES ){
			vh_header = new HeaderViewHolder( activity, listView );
			listView.addHeaderView( vh_header.viewRoot );
		}
		
		//
		
		column.addVisualListener( this );
		onVisualColumn();
	}
	
	void onPageDestroy( View root ){
		log.d( "onPageDestroy:%s", column.getColumnName() );
		column.removeVisualListener( this );
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnColumnClose:
			activity.performColumnClose( column );
			break;
		case R.id.btnColumnReload:
			column.reload();
			break;
		}
		
	}
	
	void showError(String message){
		tvLoading.setVisibility( View.VISIBLE );
		listView.setVisibility( View.GONE );
		tvLoading.setText(  message);
	}
	
	@Override
	public void onVisualColumn(){
		
		tvColumnContext.setText( column.access_info.getFullAcct( column.access_info ) );
		tvColumnName.setText( column.getColumnName() );
		
		if( column.is_dispose.get() ){
			showError("column was disposed.");
			return;
		}
		
		if( vh_header != null ){
			vh_header.bind( activity, column.access_info, column.who_account );
		}

		if( column.is_loading ){
			String message = column.task_progress;
			if( message == null ) message = "loading?";
			showError( message);
			return;
		}
		
		switch( column.type ){
		default:
		case Column.TYPE_TL_HOME:
		case Column.TYPE_TL_LOCAL:
		case Column.TYPE_TL_FEDERATE:
		case Column.TYPE_TL_FAVOURITES:
		case Column.TYPE_TL_STATUSES:
			if( column.status_list.isEmpty() && vh_header == null ){
				showError(activity.getString(R.string.list_empty));
			}else{
				tvLoading.setVisibility( View.GONE );
				listView.setVisibility( View.VISIBLE );
				status_adapter.set( column.status_list );
			}
			break;
		case Column.TYPE_TL_REPORTS:
			if( column.report_list.isEmpty() ){
				showError(activity.getString(R.string.list_empty));
			}else{
				tvLoading.setVisibility( View.GONE );
				listView.setVisibility( View.VISIBLE );
				status_adapter.set( column.report_list );
			}
			break;
		case Column.TYPE_TL_NOTIFICATIONS:
			if( column.notification_list.isEmpty() ){
				showError(activity.getString(R.string.list_empty));
			}else{
				tvLoading.setVisibility( View.GONE );
				listView.setVisibility( View.VISIBLE );
				status_adapter.set( column.notification_list );
			}
			break;
		}
	}
	
	///////////////////////////////////////////////////////////////////
	
	class StatusListAdapter extends BaseAdapter {
		final ArrayList< Object > status_list = new ArrayList<>();
		
		public void set( TootStatus.List src ){
			this.status_list.clear();
			this.status_list.addAll( src );
			notifyDataSetChanged();
		}
		
		public void set( TootReport.List src ){
			this.status_list.clear();
			this.status_list.addAll( src );
			notifyDataSetChanged();
		}
		
		public void set( TootNotification.List src ){
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
			holder.bind( activity, view, o, column.access_info );
			return view;
		}
		
	}
	
	class StatusViewHolder implements View.OnClickListener {
		
		final View llBoosted;
		final ImageView ivBoosted;
		final TextView tvBoosted;
		final TextView tvBoostedTime;
		
		final View llFollow;
		final NetworkImageView ivFollow;
		final TextView tvFollowerName;
		final TextView tvFollowerAcct;
		
		final View llStatus;
		final NetworkImageView ivThumbnail;
		final TextView tvName;
		final TextView tvTime;
		
		final View llContentWarning;
		final TextView tvContentWarning;
		final Button btnContentWarning;
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
		SavedAccount account;
		
		public StatusViewHolder( View view ){
			this.llBoosted = view.findViewById( R.id.llBoosted );
			this.ivBoosted = (ImageView) view.findViewById( R.id.ivBoosted );
			this.tvBoosted = (TextView) view.findViewById( R.id.tvBoosted );
			this.tvBoostedTime = (TextView) view.findViewById( R.id.tvBoostedTime );
			
			this.llFollow = view.findViewById( R.id.llFollow );
			this.ivFollow = (NetworkImageView) view.findViewById( R.id.ivFollow );
			this.tvFollowerName = (TextView) view.findViewById( R.id.tvFollowerName );
			this.tvFollowerAcct = (TextView) view.findViewById( R.id.tvFollowerAcct );
			
			this.llStatus = view.findViewById( R.id.llStatus );
			
			this.ivThumbnail = (NetworkImageView) view.findViewById( R.id.ivThumbnail );
			this.tvName = (TextView) view.findViewById( R.id.tvName );
			this.tvTime = (TextView) view.findViewById( R.id.tvTime );
			
			this.llContentWarning = view.findViewById( R.id.llContentWarning );
			this.tvContentWarning = (TextView) view.findViewById( R.id.tvContentWarning );
			this.btnContentWarning = (Button) view.findViewById( R.id.btnContentWarning );
			this.tvContent = (TextView) view.findViewById( R.id.tvContent );
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
			
			btnContentWarning.setOnClickListener( this );
			btnHideMedia.setOnClickListener( this );
			btnShowMedia.setOnClickListener( this );
			ivMedia1.setOnClickListener( this );
			ivMedia2.setOnClickListener( this );
			ivMedia3.setOnClickListener( this );
			ivMedia4.setOnClickListener( this );
			
			tvContent.setMovementMethod( LinkMovementMethod.getInstance() );
		}
		
		public void bind( ActMain activity, View view, Object item, SavedAccount account ){
			this.account = account;
			
			llBoosted.setVisibility( View.GONE );
			llFollow.setVisibility( View.GONE );
			llStatus.setVisibility( View.GONE );
			
			if( item == null ) return;
			
			if( item instanceof TootNotification ){
				TootNotification n = (TootNotification) item;
				if( TootNotification.TYPE_FAVOURITE.equals( n.type ) ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_favourite );
					tvBoostedTime.setText( TootStatus.formatTime( n.time_created_at )
						+ "\n" + account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.favourited_by, n.account.display_name ) );
					
					if( n.status != null ) bindSub( activity, view, n.status, account );
				}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText( TootStatus.formatTime( n.time_created_at )
						+ "\n" + account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, n.account.display_name ) );
					if( n.status != null ) bindSub( activity, view, n.status, account );
				}else if( TootNotification.TYPE_FOLLOW.equals( n.type ) ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText( TootStatus.formatTime( n.time_created_at )
						+ "\n" + account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, n.account.display_name ) );
					//
					llFollow.setVisibility( View.VISIBLE );
					ivFollow.setImageUrl( n.account.avatar_static, App1.getImageLoader() );
					tvFollowerName.setText( n.account.display_name );
					tvFollowerAcct.setText( account.getFullAcct( n.account ) );
				}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
					if( n.status != null ) bindSub( activity, view, n.status, account );
				}
				return;
			}
			
			if( item instanceof TootStatus ){
				TootStatus status = (TootStatus) item;
				if( status.reblog != null ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText( TootStatus.formatTime( status.time_created_at )
						+ "\n" + account.getFullAcct( status.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, status.account.display_name ) );
					bindSub( activity, view, status.reblog, account );
				}else{
					bindSub( activity, view, status, account );
				}
			}
		}
		
		private void bindSub( ActMain activity, View view, TootStatus status, SavedAccount account ){
			this.status = status;
			
			llStatus.setVisibility( View.VISIBLE );
			tvTime.setText( TootStatus.formatTime( status.time_created_at )
				+ "\n" + account.getFullAcct( status.account )
			);
			tvName.setText( status.account.display_name );
			ivThumbnail.setImageUrl( status.account.avatar_static, App1.getImageLoader() );
			tvContent.setText( status.decoded_content );
			
			// Content warning
			if( TextUtils.isEmpty( status.spoiler_text ) ){
				llContentWarning.setVisibility( View.GONE );
				tvContent.setVisibility( View.VISIBLE );
			}else{
				llContentWarning.setVisibility( View.VISIBLE );
				tvContentWarning.setText( status.spoiler_text );
				showContent( ContentWarning.isShown( account.host, status.id, false ));
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
				boolean is_shown = MediaShown.isShown( account.host,status.id, ! status.sensitive );
				btnShowMedia.setVisibility( ! is_shown ? View.VISIBLE : View.GONE );
			}
			
			Drawable d;
			int color;
			
			color = ( status.reblogged ? 0xff0088ff : 0xff000000 );
			d = ContextCompat.getDrawable( activity, R.drawable.btn_boost ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			btnBoost.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
			btnBoost.setText( Long.toString( status.reblogs_count ) );
			btnBoost.setTextColor( color );
			
			color = ( status.favourited ? 0xff0088ff : 0xff000000 );
			d = ContextCompat.getDrawable( activity, R.drawable.btn_favourite ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			btnFavourite.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
			btnFavourite.setText( Long.toString( status.favourites_count ) );
			btnFavourite.setTextColor( color );
			
		}
		
		private void showContent( boolean shown ){
			btnContentWarning.setText( shown ? R.string.hide : R.string.show );
			tvContent.setVisibility( shown ? View.VISIBLE : View.GONE );
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
				MediaShown.save( account.host,status.id, false );
				btnShowMedia.setVisibility( View.VISIBLE );
				break;
			case R.id.btnShowMedia:
				MediaShown.save( account.host,status.id, true );
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
			case R.id.btnContentWarning:
				{
					boolean new_shown = (tvContent.getVisibility()==View.GONE);
					ContentWarning.save( account.host,status.id , new_shown);
					showContent( new_shown );
					break;
				}
			}
		}
		
		private void clickMedia( int i ){
			try{
				activity.openChromeTab( status.media_attachments.get( i ).remote_url );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
	}
	
	class HeaderViewHolder implements View.OnClickListener {
		final View viewRoot;
		final NetworkImageView ivBackground;
		final TextView tvCreated;
		final NetworkImageView ivAvatar;
		final TextView tvDisplayName;
		final TextView tvAcct;
		final Button btnFollowing;
		final Button btnFollowers;
		final Button btnStatusCount;
		final TextView tvNote;
		TootAccount who;
		
		public HeaderViewHolder( final ActMain activity, ListView parent ){
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
			
			ivBackground.setOnClickListener( this );
			btnFollowing.setOnClickListener( this );
			btnFollowers.setOnClickListener( this );
			btnStatusCount.setOnClickListener( this );
			
		}
		
		public void bind( ActMain activity, SavedAccount access_info, TootAccount who ){
			this.who = who;
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
				tvAcct.setText( access_info.getFullAcct( who ) );
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
					activity.openBrowser( who.url );
				}
				break;
			case R.id.btnFollowing:
				Utils.showToast( activity, false, "not implemented" );
				break;
			case R.id.btnFollowers:
				Utils.showToast( activity, false, "not implemented" );
				break;
			case R.id.btnStatusCount:
				Utils.showToast( activity, false, "not implemented" );
				break;
			}
		}
	}
}
