package jp.juggler.subwaytooter;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.woxthebox.draglistview.DragItem;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;
import com.woxthebox.draglistview.swipe.ListSwipeHelper;
import com.woxthebox.draglistview.swipe.ListSwipeItem;

import java.util.ArrayList;

import jp.juggler.subwaytooter.table.MutedApp;

public class ActMutedApp extends AppCompatActivity {
	
	DragListView listView;
	MyListAdapter listAdapter;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme(this,false);
		initUI();
		loadData();
	}
	
	@Override public void onBackPressed(){
		setResult( RESULT_OK );
		super.onBackPressed();
	}
	
	private void initUI(){
		setContentView( R.layout.act_mute_app );
		
		// リストのアダプター
		listAdapter = new MyListAdapter();
		
		// ハンドル部分をドラッグで並べ替えできるRecyclerView
		listView = (DragListView) findViewById( R.id.drag_list_view );
		listView.setLayoutManager( new LinearLayoutManager( this ) );
		listView.setAdapter( listAdapter, false );

		listView.setCanDragHorizontally( true );
		listView.setDragEnabled( false );
		listView.setCustomDragItem( new MyDragItem( this, R.layout.lv_mute_app ) );
		
		listView.getRecyclerView().setVerticalScrollBarEnabled( true );
//		listView.setDragListListener( new DragListView.DragListListenerAdapter() {
//			@Override
//			public void onItemDragStarted( int position ){
//				// 操作中はリフレッシュ禁止
//				// mRefreshLayout.setEnabled( false );
//			}
//
//			@Override
//			public void onItemDragEnded( int fromPosition, int toPosition ){
//				// 操作完了でリフレッシュ許可
//				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
//
////				if( fromPosition != toPosition ){
////					// 並べ替えが発生した
////				}
//			}
//		} );
		
		// リストを左右スワイプした
		listView.setSwipeListener( new ListSwipeHelper.OnSwipeListenerAdapter() {
			
			@Override
			public void onItemSwipeStarted( ListSwipeItem item ){
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			@Override
			public void onItemSwipeEnded( ListSwipeItem item, ListSwipeItem.SwipeDirection swipedDirection ){
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
				
				// 左にスワイプした(右端に青が見えた) なら要素を削除する
				if( swipedDirection == ListSwipeItem.SwipeDirection.LEFT ){
					Object o = item.getTag();
					if( o instanceof  MyItem){
						MyItem adapterItem = ( MyItem ) o;
						MutedApp.delete( adapterItem.name );
						listAdapter.removeItem( listAdapter.getPositionForItem( adapterItem ) );
					}
				}
			}
		} );
	}

	private void loadData(){
		
		ArrayList< MyItem > tmp_list = new ArrayList<>();
		try{
			Cursor cursor = MutedApp.createCursor();
			if( cursor != null ){
				try{
					int idx_name = cursor.getColumnIndex( MutedApp.COL_NAME );
					while( cursor.moveToNext() ){
						String name = cursor.getString( idx_name);
						MyItem item = new MyItem( name );
						tmp_list.add( item );
					}
					
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		listAdapter.setItemList( tmp_list );
	}


	// リスト要素のデータ
	static class MyItem {
		String name;
		MyItem(String name ){
			this.name = name;
		}
	}
	
	
	// リスト要素のViewHolder
	static class MyViewHolder extends DragItemAdapter.ViewHolder {

		final TextView tvName;
			
		MyViewHolder( final View viewRoot ){
			super( viewRoot
				, R.id.ivDragHandle // View ID。 ここを押すとドラッグ操作をすぐに開始する
				, false // 長押しでドラッグ開始するなら真
			);
			
			tvName = (TextView) viewRoot.findViewById( R.id.tvName );
			
			// リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
			if( viewRoot instanceof ListSwipeItem ){
				ListSwipeItem lsi = (ListSwipeItem) viewRoot;
				lsi.setSwipeInStyle( ListSwipeItem.SwipeInStyle.SLIDE );
				lsi.setSupportedSwipeDirection( ListSwipeItem.SwipeDirection.LEFT );
			}
			
		}
		
		void bind( MyItem item  ){
			tvName.setText( item.name );
		}

//		@Override
//		public boolean onItemLongClicked( View view ){
//			return false;
//		}
		
//		@Override
//		public void onItemClicked( View view ){
//		}
	}
	
	// ドラッグ操作中のデータ
	private class MyDragItem extends DragItem {
		MyDragItem( Context context, int layoutId ){
			super( context, layoutId );
		}
		
		@Override
		public void onBindDragView( View clickedView, View dragView ){
			((TextView)dragView.findViewById( R.id.tvName )).setText(
				((TextView)clickedView.findViewById( R.id.tvName )).getText()
			);
			
			dragView.findViewById(R.id.item_layout).setBackgroundColor(
				Styler.getAttributeColor( ActMutedApp.this, R.attr.list_item_bg_pressed_dragged)
			);
		}
	}
	
	private class MyListAdapter extends DragItemAdapter< MyItem, MyViewHolder > {
		
		MyListAdapter(){
			super();
			setHasStableIds( true );
			setItemList( new ArrayList< MyItem >() );
		}
		
		@Override
		public MyViewHolder onCreateViewHolder( ViewGroup parent, int viewType ){
			View view = getLayoutInflater().inflate( R.layout.lv_mute_app, parent, false );
			return new MyViewHolder( view );
		}
		
		@Override
		public void onBindViewHolder( MyViewHolder holder, int position ){
			super.onBindViewHolder( holder, position );
			holder.bind( getItemList().get( position ) );
		}
		
		@Override
		public long getItemId( int position ){
			MyItem item = mItemList.get( position ); // mItemList は親クラスのメンバ変数
			return item.name.hashCode();
		}
	}
}
