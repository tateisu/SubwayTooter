package jp.juggler.subwaytooter;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import jp.juggler.subwaytooter.util.LogCategory;

class TabletColumnViewHolder extends RecyclerView.ViewHolder{
	static final LogCategory log = new LogCategory( "TabletColumnViewHolder" );
	
	
	ColumnViewHolder vh;
	private int old_position;
	
	TabletColumnViewHolder( View v ){
		super( v );
		
		v.findViewById( R.id.vTabletDivider ).setVisibility( View.VISIBLE );
	}
	
	void bind( ActMain activity, Column column,int position,int column_count ){
		if( vh != null ){
			log.d("destroy #%s",old_position);
			vh.onPageDestroy( itemView );
			vh = null;
		}
		
		old_position = position;
		log.d("create #%s",position);
		vh =new ColumnViewHolder( activity, column);
		vh.onPageCreate( itemView,position,column_count );
		
		if( ! column.bFirstInitialized ){
			column.startLoading();
		}
	}
}
