package jp.juggler.subwaytooter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.MyListView;

class ItemListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
	private final Column column;
	private final ActMain activity;
	private final ArrayList< Object > list;
	
	ItemListAdapter( Column column ){
		this.column = column;
		this.activity = column.activity;
		this.list = column.list_data;
	}
	
	@Override
	public int getCount(){
		return column.list_data.size();
	}
	
	@Override
	public Object getItem( int position ){
		if( position >= 0 && position < column.list_data.size() ) return list.get( position );
		return null;
	}
	
	@Override
	public long getItemId( int position ){
		return 0;
	}
	
	@Override
	public View getView( int position, View view, ViewGroup parent ){
		Object o = ( position >= 0 && position < list.size() ? list.get( position ) : null );
		
		ItemViewHolder holder;
		if( view == null ){
			view = activity.getLayoutInflater().inflate( column.bSimpleList ? R.layout.lv_status_simple : R.layout.lv_status, parent, false );
			holder = new ItemViewHolder( column, view );
			view.setTag( holder );
		}else{
			holder = (ItemViewHolder) view.getTag();
		}
		holder.bind( o ,position);
		return view;
	}
	
	@Override
	public void onItemClick( AdapterView< ? > parent, View view, int position, long id ){
		Object tag = view.getTag();
		if( tag instanceof ItemViewHolder ){
			( (ItemViewHolder) tag ).onItemClick( (MyListView) parent, view );
		}
	}
}
	