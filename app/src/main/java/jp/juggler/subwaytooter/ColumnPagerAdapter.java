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
	private final ArrayList< Column > column_list;
	private final SparseArray< ColumnViewHolder > holder_list = new SparseArray<>();


	ColumnPagerAdapter(ActMain activity){
		this.activity = activity;
		this.inflater = activity.getLayoutInflater();
		this.column_list = activity.app_state.column_list;
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
	
	
	@Override public CharSequence getPageTitle( int page_idx ){
		return getColumn( page_idx).getColumnName( false );
	}
	
	@Override public boolean isViewFromObject( View view, Object object ){
		return view == object;
	}
	
	@Override public Object instantiateItem( ViewGroup container, int page_idx ){
		View root = inflater.inflate( R.layout.page_column, container, false );
		container.addView( root, 0 );
		
		Column column = column_list.get( page_idx );
		ColumnViewHolder holder = new ColumnViewHolder( activity,root );
		//
		holder_list.put( page_idx, holder );
		//
		holder.onPageCreate( column, page_idx, column_list.size() );
		
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
			holder.onPageDestroy();
		}
	}
}