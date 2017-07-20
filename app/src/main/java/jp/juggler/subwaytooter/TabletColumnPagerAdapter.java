package jp.juggler.subwaytooter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

class TabletColumnPagerAdapter extends RecyclerView.Adapter< TabletColumnViewHolder > {
	
	private final ActMain activity;
	private final LayoutInflater mLayoutInflater;
	private final List< Column > column_list;
	
	TabletColumnPagerAdapter( ActMain activity ){
		super();
		this.activity = activity;
		this.column_list = activity.app_state.column_list;
		mLayoutInflater = LayoutInflater.from( activity );
	}
	
	@Override public int getItemCount(){
		return column_list.size();
	}
	
	private int mColumnWidth = 0;
	
	void setColumnWidth( int width ){
		mColumnWidth = width;
	}
	
	@Override public TabletColumnViewHolder onCreateViewHolder( ViewGroup parent, int viewType ){
		View v = mLayoutInflater.inflate( R.layout.page_column, parent, false );
		
		return new TabletColumnViewHolder( activity, v );
	}
	
	@Override public void onBindViewHolder( TabletColumnViewHolder holder, int position ){
		
		if( mColumnWidth > 0 ){
			ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
			lp.width = mColumnWidth;
			holder.itemView.setLayoutParams( lp );
		}
		
		holder.bind( column_list.get( position ), position, column_list.size() );
	}
	
	@Override public void onViewRecycled( TabletColumnViewHolder holder ){
		super.onViewRecycled( holder );
		holder.onViewRecycled();
	}
}
