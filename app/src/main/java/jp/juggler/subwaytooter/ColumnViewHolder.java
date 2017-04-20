package jp.juggler.subwaytooter;

import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;

public class ColumnViewHolder implements View.OnClickListener, Column.VisualCallback {
	static final LogCategory log = new LogCategory( "ColumnViewHolder" );
	
	public final AtomicBoolean is_destroyed = new AtomicBoolean( false );
	public final ActMain activity;
	public final Column column;
	public final int column_index;
	
	public ColumnViewHolder( ActMain activity, Column column, int column_index ){
		log.d("ctor");
		this.activity = activity;
		this.column = column;
		this.column_index = column_index;
	}
	
	public boolean isPageDestroyed(){
		return is_destroyed.get() || activity.isFinishing();
	}
	
	TextView tvLoading;
	ListView listView;
	TextView tvColumnName;
	StatusListAdapter status_adapter;
	
	void onPageCreate( View root ){
		log.d("onPageCreate:%s",column.getColumnName() );
		
		tvColumnName =  (TextView) root.findViewById( R.id.tvColumnName );
		

		root.findViewById( R.id.btnColumnClose ).setOnClickListener( this );
		root.findViewById( R.id.btnColumnReload ).setOnClickListener( this );
		
		tvLoading = (TextView) root.findViewById( R.id.tvLoading );
		listView = (ListView) root.findViewById( R.id.listView );
		status_adapter = new StatusListAdapter();
		listView.setAdapter( status_adapter );
		//
	
		column.addVisualListener( this );
		onVisualColumn();
	}
	
	void onPageDestroy( View root ){
		log.d("onPageDestroy:%s",column.getColumnName() );
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
	
	@Override
	public void onVisualColumn(){
		
		tvColumnName.setText(column.getColumnName() );

		if( column.is_dispose.get() ){
			tvLoading.setVisibility( View.VISIBLE );
			listView.setVisibility( View.GONE );
			tvLoading.setText( "column was disposed." );
			return;
		}
		
		if( column.is_loading ){
			tvLoading.setVisibility( View.VISIBLE );
			listView.setVisibility( View.GONE );
			String progress = column.task_progress;
			if( progress == null ) progress = "loading?";
			tvLoading.setText( progress );
			return;
		}
		tvLoading.setVisibility( View.GONE );

		if( column.who_account != null ){
			// TODO
		}else{
			
		}

		switch( column.type ){
		default:
		case Column.TYPE_TL_HOME:
		case Column.TYPE_TL_LOCAL:
		case Column.TYPE_TL_FEDERATE:
		case Column.TYPE_TL_FAVOURITES:
		case Column.TYPE_TL_STATUSES:
			listView.setVisibility( View.VISIBLE );
			status_adapter.set( column.status_list );
			break;
		case Column.TYPE_TL_REPORTS:
			listView.setVisibility( View.VISIBLE );
			status_adapter.set( column.report_list );
			break;
		case Column.TYPE_TL_NOTIFICATIONS:
			listView.setVisibility( View.VISIBLE );
			status_adapter.set( column.notification_list );
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
	
	static class StatusViewHolder {
		
		final View llBoosted;
		final ImageView ivBoosted;
		final TextView tvBoosted;
		final TextView tvBoostedTime;
		
		final View llFollow;
		final ImageView ivFollow;
		final TextView tvFollowerName;
		final TextView tvFollowerAcct;
		
		final View llStatus;
		final ImageView ivThumbnail;
		final TextView tvName;
		final TextView tvTime;
		final TextView tvContent;
		final ImageView ivMedia;

		final ImageButton btnReply;
		final ImageButton btnBoost;
		final ImageButton btnFavourite;
		final ImageButton btnMore;
		
		Object item;
		SavedAccount account;
		
		public StatusViewHolder( View view ){
			this.llBoosted =  view.findViewById( R.id.llBoosted );
			this.ivBoosted = (ImageView) view.findViewById( R.id.ivBoosted );
			this.tvBoosted = (TextView) view.findViewById( R.id.tvBoosted );
			this.tvBoostedTime = (TextView) view.findViewById( R.id.tvBoostedTime );
			
			this.llFollow =  view.findViewById( R.id.llFollow );
			this.ivFollow = (ImageView) view.findViewById( R.id.ivFollow );
			this.tvFollowerName = (TextView) view.findViewById( R.id.tvFollowerName );
			this.tvFollowerAcct = (TextView) view.findViewById( R.id.tvFollowerAcct );
			
			this.llStatus =  view.findViewById( R.id.llStatus );
		
			this.ivThumbnail = (ImageView) view.findViewById( R.id.ivThumbnail );
			this.tvName = (TextView) view.findViewById( R.id.tvName );
			this.tvTime = (TextView) view.findViewById( R.id.tvTime );
			this.tvContent = (TextView) view.findViewById( R.id.tvContent );
			this.ivMedia = (ImageView) view.findViewById( R.id.ivMedia );
			this.btnReply = (ImageButton) view.findViewById( R.id.btnReply );
			this.btnBoost = (ImageButton) view.findViewById( R.id.btnBoost );
			this.btnFavourite = (ImageButton) view.findViewById( R.id.btnFavourite );
			this.btnMore = (ImageButton) view.findViewById( R.id.btnMore );
		}
		
		public void bind( ActMain activity, View view, Object item, SavedAccount account ){
			this.account = account;
			this.item = item;
			
			llBoosted.setVisibility( View.GONE );
			llFollow.setVisibility( View.GONE );
			llStatus.setVisibility( View.GONE );

			if( item == null ) return;
			
			if( item instanceof TootNotification ){
				TootNotification n = (TootNotification) item;
				if( TootNotification.TYPE_FAVOURITE.equals( n.type ) ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_favourite );
					tvBoostedTime.setText(TootStatus.formatTime( n.time_created_at )
						+"\n"+ account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.favourited_by, n.account.display_name ) );
					
					if( n.status != null ) bindSub( activity, view, n.status,account );
				}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText(TootStatus.formatTime( n.time_created_at )
						+"\n"+ account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, n.account.display_name ) );
					if( n.status != null ) bindSub( activity, view, n.status,account );
				}else if( TootNotification.TYPE_FOLLOW.equals( n.type )){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText(TootStatus.formatTime( n.time_created_at )
						+"\n"+ account.getFullAcct( n.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, n.account.display_name ) );
					//
					llFollow.setVisibility( View.VISIBLE );
					ivFollow.setImageResource( R.drawable.btn_follow );
					tvFollowerName.setText( n.account.display_name );
					tvFollowerAcct.setText( account.getFullAcct( n.account ));
				}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
					if( n.status != null ) bindSub( activity, view, n.status,account );
				}
				return;
			}
			
			if( item instanceof TootStatus ){
				TootStatus status = (TootStatus)item;
				if( status.reblog != null ){
					llBoosted.setVisibility( View.VISIBLE );
					ivBoosted.setImageResource( R.drawable.btn_boost );
					tvBoostedTime.setText(TootStatus.formatTime( status.time_created_at )
						+"\n"+ account.getFullAcct( status.account )
					);
					tvBoosted.setText( activity.getString( R.string.boosted_by, status.account.display_name ) );
					bindSub( activity, view, status.reblog,account );
				}else{
					bindSub( activity, view, status ,account);
				}
			}
		}
		
		private void bindSub( ActMain activity, View view, TootStatus status, SavedAccount account ){
			llStatus.setVisibility( View.VISIBLE );
			tvTime.setText( TootStatus.formatTime( status.time_created_at )
				+"\n"+ account.getFullAcct( status.account )
			);
			tvName.setText( status.account.display_name );
			tvContent.setText( status.content );
			
			// TODO media
			
			btnBoost.getDrawable().setColorFilter(
				( status.reblogged ? 0xff0088ff : 0xff000000 )
				, PorterDuff.Mode.SRC_ATOP
			);
			
			btnFavourite.getDrawable().setColorFilter(
				( status.favourited ? 0xff0088ff : 0xff000000 )
				, PorterDuff.Mode.SRC_ATOP
			);
			// todo show count of boost/fav
		}
	}
	
}
