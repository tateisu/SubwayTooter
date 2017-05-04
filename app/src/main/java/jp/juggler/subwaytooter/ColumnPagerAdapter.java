package jp.juggler.subwaytooter;

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;

class ColumnPagerAdapter extends PagerAdapter {
	
	private final ActMain activity;
	private final LayoutInflater inflater;
	final ArrayList< Column > column_list = new ArrayList<>();
	private final SparseArray< ColumnViewHolder > holder_list = new SparseArray<>();
	
	ColumnPagerAdapter( ActMain activity ){
		this.activity = activity;
		this.inflater = activity.getLayoutInflater();
	}
	
	@Override public int getCount(){
		return column_list.size();
	}
	
	Column getColumn( int idx ){
		if( idx >= 0 && idx < column_list.size() ) return column_list.get( idx );
		return null;
	}
	
	ColumnViewHolder getColumnViewHolder( int idx ){
		return holder_list.get( idx );
	}
	
	int addColumn( ViewPager pager, Column column ){
		return addColumn( pager, column, pager.getCurrentItem() + 1 );
	}
	
	int addColumn( ViewPager pager, Column column, int index ){
		int size = column_list.size();
		if( index > size ) index = size;
		
		pager.setAdapter( null );
		column_list.add( index, column );
		pager.setAdapter( this );
		notifyDataSetChanged();
		return index;
	}
	
	void removeColumn( ViewPager pager, Column column ){
		int idx_column = column_list.indexOf( column );
		if( idx_column == - 1 ) return;
		pager.setAdapter( null );
		column_list.remove( idx_column );
		pager.setAdapter( this );
	}
	
	void setOrder( ViewPager pager, ArrayList< Integer > order ){
		pager.setAdapter( null );
		
		ArrayList< Column > tmp_list = new ArrayList<>();
		HashSet< Integer > used_set = new HashSet<>();
		
		for( Integer i : order ){
			used_set.add( i );
			tmp_list.add( column_list.get( i ) );
		}
		for( int i = 0, ie = column_list.size() ; i < ie ; ++ i ){
			if( used_set.contains( i ) ) continue;
			column_list.get( i ).dispose();
		}
		column_list.clear();
		column_list.addAll( tmp_list );
		
		pager.setAdapter( this );
	}
	
	@Override public CharSequence getPageTitle( int page_idx ){
		return "page" + page_idx;
	}
	
	@Override public boolean isViewFromObject( View view, Object object ){
		return view == object;
	}
	
	@Override public Object instantiateItem( ViewGroup container, int page_idx ){
		View root = inflater.inflate( R.layout.page_column, container, false );
		container.addView( root, 0 );
		
		Column column = column_list.get( page_idx );
		ColumnViewHolder holder = new ColumnViewHolder( activity, column );
		//
		holder_list.put( page_idx, holder );
		//
		holder.onPageCreate( root, page_idx, column_list.size() );
		
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