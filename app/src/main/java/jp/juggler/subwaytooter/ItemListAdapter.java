package jp.juggler.subwaytooter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import java.util.List;

import jp.juggler.subwaytooter.view.MyListView;

class ItemListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
	private final Column column;
	private final ActMain activity;
	private final List< Object > list;
	
	HeaderViewHolderBase header;

	private final boolean bSimpleList;
	
	ItemListAdapter( ActMain activity, Column column ,boolean bSimpleList ){
		this.activity = activity;
		this.column = column;
		this.list = column.list_data;
		this.bSimpleList = bSimpleList;
	}
	
	
	
	@Override
	public int getCount(){
		return ( header != null ? 1 : 0 ) + column.list_data.size();
	}
	
	@Override public int getViewTypeCount(){
		return ( header != null ? 2 : 1 );
	}
	
	@Override public int getItemViewType( int position ){
		if( header != null ){
			if( position == 0 ) return 1;
		}
		return 0;
	}
	
	@Override
	public Object getItem( int position ){
		if( header != null ){
			if( position == 0 ) return header;
			-- position;
		}
		if( position >= 0 && position < column.list_data.size() ) return list.get( position );
		return null;
	}
	
	@Override
	public long getItemId( int position ){
		return 0;
	}
	
	@Override
	public View getView( int position, View view, ViewGroup parent ){
		if( header != null){
			if( position == 0) return header.viewRoot;
			--position;
		}

		Object o = ( position >= 0 && position < list.size() ? list.get( position ) : null );
		
		ItemViewHolder holder;
		if( view == null ){
			view = activity.getLayoutInflater().inflate( bSimpleList ? R.layout.lv_status_simple : R.layout.lv_status, parent, false );
			holder = new ItemViewHolder( activity, column, this, view ,bSimpleList );
			view.setTag( holder );
		}else{
			holder = (ItemViewHolder) view.getTag();
		}
		holder.bind( o ,column.getViewHolder() );
		return view;
	}
	
	@Override
	public void onItemClick( AdapterView< ? > parent, View view, int position, long id ){
		if( bSimpleList ){
			Object tag = view.getTag();
			if( tag instanceof ItemViewHolder ){
				( (ItemViewHolder) tag ).onItemClick( (MyListView) parent, view );
			}
		}
	}
}
	