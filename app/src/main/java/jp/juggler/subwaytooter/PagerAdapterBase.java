package jp.juggler.subwaytooter;


import android.app.Activity;
import android.support.v4.view.PagerAdapter;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class PagerAdapterBase extends PagerAdapter{
	
	public static abstract class PageViewHolder{
		
		public final AtomicBoolean is_destroyed = new AtomicBoolean( false );
		public final Activity activity;
		
		@SuppressWarnings( "UnusedParameters" )
		public PageViewHolder( Activity activity, View ignored ){
			this.activity = activity;
		}
		
		public boolean isPageDestroyed(){
			return is_destroyed.get() || activity.isFinishing();
		}
		
		@SuppressWarnings( "RedundantThrows" )
		protected abstract void onPageCreate( @SuppressWarnings( "UnusedParameters" ) int page_idx, View root ) throws Throwable;
		
		@SuppressWarnings( "RedundantThrows" )
		protected abstract void onPageDestroy( @SuppressWarnings( "UnusedParameters" ) int page_idx, @SuppressWarnings( "UnusedParameters" ) View root ) throws Throwable;
	}
	
	public final Activity activity;
	public final LayoutInflater inflater;
	
	public PagerAdapterBase( Activity activity ){
		this.activity = activity;
		this.inflater = activity.getLayoutInflater();
	}
	
	protected final ArrayList<CharSequence> title_list = new ArrayList<>();
	protected final ArrayList<Integer> layout_id_list = new ArrayList<>();
	protected final ArrayList<Class<? extends PageViewHolder>> holder_class_list = new ArrayList<>();
	protected final SparseArray<PageViewHolder> holder_list = new SparseArray<>();
	
	public int addPage( CharSequence title, int layout_id, Class<? extends PageViewHolder> holder_class ){
		int idx = title_list.size();
		title_list.add( title );
		layout_id_list.add( layout_id );
		holder_class_list.add( holder_class );
		// ページのインデックスを返す
		return idx;
	}
	
	// ページが存在する場合そのViewHolderを返す
	// ページのViewが生成されていない場合はnullを返す
	public <T> T getPage( int idx ){
		PageViewHolder vh = holder_list.get( idx );
		if( vh == null ) return null;
		return (T) holder_class_list.get( idx ).cast( vh );
	}
	
	public boolean loop_mode = false;
	
	public int getCountReal(){
		return title_list.size();
	}
	
	@Override public int getCount(){
		return loop_mode ? Integer.MAX_VALUE : title_list.size();
	}
	
	@Override public CharSequence getPageTitle( int page_idx ){
		return title_list.get( page_idx % getCountReal() );
	}
	
	@Override
	public boolean isViewFromObject( View view, Object object ){
		return view == object;
	}
	
	@Override public Object instantiateItem( ViewGroup container, int page_idx ){
		View root = inflater.inflate( layout_id_list.get( page_idx % getCountReal() ), container, false );
		container.addView( root, 0 );
		
		try{
			PageViewHolder holder =
				holder_class_list.get( page_idx % getCountReal() )
					.getConstructor( Activity.class, View.class )
					.newInstance( activity, root );
			//
			holder_list.put( page_idx, holder );
			//
			holder.onPageCreate( page_idx % getCountReal(), root );
			//
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		return root;
	}
	
	@Override public void destroyItem( ViewGroup container, int page_idx, Object object ){
		View view = (View) object;
		//
		container.removeView( view );
		//
		try{
			PageViewHolder holder = holder_list.get( page_idx );
			holder_list.remove( page_idx );
			if( holder != null ){
				holder.is_destroyed.set( true );
				holder.onPageDestroy( page_idx % getCountReal(), view );
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
}