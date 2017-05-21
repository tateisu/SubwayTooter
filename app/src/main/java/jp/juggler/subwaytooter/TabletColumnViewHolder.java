package jp.juggler.subwaytooter;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import jp.juggler.subwaytooter.util.LogCategory;

class TabletColumnViewHolder extends RecyclerView.ViewHolder{
	static final LogCategory log = new LogCategory( "TabletColumnViewHolder" );
	
	
	final ColumnViewHolder vh;

	private int old_position = - 1;
	
	TabletColumnViewHolder(  ActMain activity, View v ){
		super( v );
		
		vh =new ColumnViewHolder( activity ,v);
		v.findViewById( R.id.vTabletDivider ).setVisibility( View.VISIBLE );
	}
	
	void bind(Column column,int position,int column_count ){
		log.d("bind. %d => %d ", old_position,position);
		old_position = position;

		vh.onPageDestroy( position );
		vh.onPageCreate( column, position,column_count );
		
		if( ! column.bFirstInitialized ){
			column.startLoading();
		}
	}
}
