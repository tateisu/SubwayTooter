package jp.juggler.subwaytooter;


import android.app.Activity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;

public class ColumnPagerAdapter extends PagerAdapter{
	
	final ActMain activity;
	final LayoutInflater inflater;

	boolean loop_mode = false;
	
	ColumnPagerAdapter( ActMain activity ){
		this.activity = activity;
		this.inflater = activity.getLayoutInflater();
	}
	
	final ArrayList<Column> column_list = new ArrayList<>();
	final SparseArray<ColumnViewHolder> holder_list = new SparseArray<>();
	
	int addColumn( ViewPager pager, Column column ){
		return addColumn( pager,column,pager.getCurrentItem()+1 );
	}
	
	int addColumn( ViewPager pager, Column column,int index ){
		int size = column_list.size();
		if( index > size ) index = size;
	
		pager.setAdapter( null );
		column_list.add( index,column );
		pager.setAdapter( this );
		notifyDataSetChanged();
		return index;
	}
	
	private void saveScrollPosition(){
		for( int i=0,ie=holder_list.size();i<ie;++i){
			holder_list.valueAt( i ).saveScrollPosition();
		}
	}
	
	public void removeColumn( ViewPager pager,Column column ){
		int idx_column = column_list.indexOf( column );
		if( idx_column == - 1 ) return;
		int idx_showing = pager.getCurrentItem();
		pager.setAdapter( null );
		column_list.remove( idx_column );
		pager.setAdapter( this );
	}
	
	public void setOrder(  ViewPager pager,ArrayList< Integer > order ){
		pager.setAdapter( null );

		ArrayList<Column> tmp_list = new ArrayList<>();
		HashSet<Integer> used_set = new HashSet<>(  );

		for(Integer i : order){
			used_set.add( i );
			tmp_list.add( column_list.get(i));
		}
		for(int i=0,ie=column_list.size();i<ie;++i){
			if( used_set.contains( i ) ) continue;
			column_list.get(i).dispose();
		}
		column_list.clear();
		column_list.addAll( tmp_list );
		
		pager.setAdapter( this );
	}
	
	
	
	public Column getColumn( int idx ){
		if( idx >= 0 && idx < column_list.size() ) return column_list.get( idx );
		return null;
	}
	
	public ColumnViewHolder getColumnViewHolder( int idx ){
		return holder_list.get( idx );
	}
	
	
	@Override public int getCount(){
		return column_list.size();
	}
	
	@Override public CharSequence getPageTitle( int page_idx ){
		return "page"+ page_idx;
	}
	
	@Override
	public boolean isViewFromObject( View view, Object object ){
		return view == object;
	}
	
	@Override public Object instantiateItem( ViewGroup container, int page_idx ){
		View root = inflater.inflate( R.layout.page_column, container, false );
		container.addView( root, 0 );
		
		Column column = column_list.get( page_idx  );
		ColumnViewHolder holder = new ColumnViewHolder( activity,column, page_idx  );
		//
		holder_list.put( page_idx, holder );
		//
		holder.onPageCreate( root ,page_idx,column_list.size());

		return root;
	}
	
	@Override public void destroyItem( ViewGroup container, int page_idx, Object object ){
		View view = (View) object;
		//
		container.removeView( view );
		//
		ColumnViewHolder holder = holder_list.get( page_idx );
		holder_list.remove( page_idx );
		if( holder != null ){
			holder.is_destroyed.set( true );
			holder.onPageDestroy( view );
		}
	}
	
}