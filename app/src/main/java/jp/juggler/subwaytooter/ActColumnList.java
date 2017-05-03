package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.woxthebox.draglistview.DragItem;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;
import com.woxthebox.draglistview.swipe.ListSwipeHelper;
import com.woxthebox.draglistview.swipe.ListSwipeItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import jp.juggler.subwaytooter.util.Utils;

public class ActColumnList extends AppCompatActivity {
	public static final String EXTRA_ORDER = "order";
	public static final String EXTRA_SELECTION = "selection";
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		if( savedInstanceState != null ){
			restoreData(
				savedInstanceState.getString( EXTRA_ORDER )
				, savedInstanceState.getInt( EXTRA_SELECTION )
			);
		}else{
			Intent intent = getIntent();
			restoreData(
				intent.getStringExtra( EXTRA_ORDER )
				, intent.getIntExtra( EXTRA_SELECTION, - 1 )
			);
		}
	}
	
	@Override
	protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		//
		outState.putInt( EXTRA_SELECTION, old_selection );
		//
		JSONArray array = new JSONArray();
		List< MyItem > item_list = listAdapter.getItemList();
		for( int i = 0, ie = item_list.size() ; i < ie ; ++ i ){
			array.put( item_list.get( i ).json );
		}
		outState.putString( EXTRA_ORDER, array.toString() );
	}
	
	@Override
	public void onBackPressed(){
		makeResult( - 1 );
		super.onBackPressed();
	}
	
	DragListView listView;
	MyListAdapter listAdapter;
	int old_selection;
	
	private void initUI(){
		setContentView( R.layout.act_column_list );
		
		// リストのアダプター
		listAdapter = new MyListAdapter();
		
		// ハンドル部分をドラッグで並べ替えできるRecyclerView
		listView = (DragListView) findViewById( R.id.drag_list_view );
		listView.setLayoutManager( new LinearLayoutManager( this ) );
		listView.setAdapter( listAdapter, true );
		listView.setCanDragHorizontally( false );
		listView.setCustomDragItem( new MyDragItem( this, R.layout.lv_column_list ) );
		
		listView.getRecyclerView().setVerticalScrollBarEnabled( true );
		listView.setDragListListener( new DragListView.DragListListenerAdapter() {
			@Override
			public void onItemDragStarted( int position ){
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			@Override
			public void onItemDragEnded( int fromPosition, int toPosition ){
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );

//				if( fromPosition != toPosition ){
//					// 並べ替えが発生した
//				}
			}
		} );
		
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
					MyItem adapterItem = (MyItem) item.getTag();
					if( adapterItem.json.optBoolean( Column.KEY_DONT_CLOSE, false ) ){
						Utils.showToast( ActColumnList.this, false, R.string.column_has_dont_close_option );
						listView.resetSwipedViews( null );
						return;
					}
					listAdapter.removeItem( listAdapter.getPositionForItem( adapterItem ) );
				}
			}
		} );
	}
	
	void restoreData( String svColumnList, int ivSelection ){
		
		this.old_selection = ivSelection;
		
		ArrayList< MyItem > tmp_list = new ArrayList<>();
		try{
			JSONArray array = new JSONArray( svColumnList );
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				try{
					JSONObject src = array.optJSONObject( i );
					MyItem item = new MyItem( src, i, this );
					if( src != null ){
						tmp_list.add( item );
						if( old_selection == item.old_index ){
							item.setOldSelection( true );
						}
					}
				}catch( Throwable ex2 ){
					ex2.printStackTrace();
				}
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		listAdapter.setItemList( tmp_list );
	}
	
	void makeResult( int new_selection ){
		Intent intent = new Intent();
		
		List< MyItem > item_list = listAdapter.getItemList();
		// どの要素を選択するか
		if( new_selection >= 0 && new_selection < listAdapter.getItemCount() ){
			intent.putExtra( EXTRA_SELECTION, new_selection );
		}else{
			for( int i = 0, ie = item_list.size() ; i < ie ; ++ i ){
				if( item_list.get( i ).bOldSelection ){
					intent.putExtra( EXTRA_SELECTION, i );
					break;
				}
			}
		}
		// 並べ替え用データ
		ArrayList< Integer > order_list = new ArrayList<>();
		for( MyItem item : item_list ){
			order_list.add( item.old_index );
		}
		intent.putExtra( EXTRA_ORDER, order_list );
		
		setResult( RESULT_OK, intent );
	}
	
	private void performItemSelected( MyItem item ){
		int idx = listAdapter.getPositionForItem( item );
		makeResult( idx );
		finish();
	}
	
	// リスト要素のデータ
	static class MyItem {
		long id;
		JSONObject json;
		String name;
		String acct;
		int acct_color_fg;
		int acct_color_bg;
		boolean bOldSelection;
		int old_index;
		int type;
		
		MyItem( JSONObject src, long id, Context context ){
			this.json = src;
			this.name = src.optString( Column.KEY_COLUMN_NAME );
			this.acct = src.optString( Column.KEY_COLUMN_ACCESS );
			int c = src.optInt( Column.KEY_COLUMN_ACCESS_COLOR, 0 );
			this.acct_color_fg = c != 0 ? c : Styler.getAttributeColor( context, R.attr.colorColumnListItemText );
			c = src.optInt( Column.KEY_COLUMN_ACCESS_COLOR_BG, 0 );
			this.acct_color_bg = c;
			this.old_index = src.optInt( Column.KEY_OLD_INDEX );
			this.id = id;
			this.type = src.optInt( Column.KEY_TYPE );
		}
		
		void setOldSelection( boolean b ){
			bOldSelection = b;
		}
	}
	
	// リスト要素のViewHolder
	class MyViewHolder extends DragItemAdapter.ViewHolder {
		final View ivBookmark;
		final TextView tvAccess;
		final TextView tvName;
		final ImageView ivColumnIcon;
		final int acct_pad_lr;
		
		MyViewHolder( final View viewRoot ){
			super( viewRoot
				, R.id.ivDragHandle // View ID。 ここを押すとドラッグ操作をすぐに開始する
				, true // 長押しでドラッグ開始するなら真
			);
			
			ivBookmark = viewRoot.findViewById( R.id.ivBookmark );
			tvAccess = (TextView) viewRoot.findViewById( R.id.tvAccess );
			tvName = (TextView) viewRoot.findViewById( R.id.tvName );
			ivColumnIcon = (ImageView) viewRoot.findViewById( R.id.ivColumnIcon );
			acct_pad_lr = (int) ( 0.5f + 4f * viewRoot.getResources().getDisplayMetrics().density );
			
			// リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
			if( viewRoot instanceof ListSwipeItem ){
				ListSwipeItem lsi = (ListSwipeItem) viewRoot;
				lsi.setSwipeInStyle( ListSwipeItem.SwipeInStyle.SLIDE );
				lsi.setSupportedSwipeDirection( ListSwipeItem.SwipeDirection.LEFT );
			}
			
		}
		
		void bind( MyItem item ){
			itemView.setTag( item ); // itemView は親クラスのメンバ変数
			ivBookmark.setVisibility( item.bOldSelection ? View.VISIBLE : View.INVISIBLE );
			tvAccess.setText( item.acct );
			tvAccess.setTextColor( item.acct_color_fg );
			tvAccess.setBackgroundColor( item.acct_color_bg );
			tvAccess.setPaddingRelative( acct_pad_lr, 0, acct_pad_lr, 0 );
			tvName.setText( item.name );
			ivColumnIcon.setImageResource( Styler.getAttributeResourceId(
				ActColumnList.this, Column.getIconAttrId( item.type ) ) );
		}

//		@Override
//		public boolean onItemLongClicked( View view ){
//			return false;
//		}
		
		@Override
		public void onItemClicked( View view ){
			MyItem item = (MyItem) itemView.getTag(); // itemView は親クラスのメンバ変数
			ActColumnList activity = ( (ActColumnList) Utils.getActivityFromView( view ) );
			if( activity != null ) activity.performItemSelected( item );
		}
	}
	
	// ドラッグ操作中のデータ
	private class MyDragItem extends DragItem {
		MyDragItem( Context context, int layoutId ){
			super( context, layoutId );
		}
		
		@Override
		public void onBindDragView( View clickedView, View dragView ){
			MyItem item = (MyItem) clickedView.getTag();
			
			TextView tv = (TextView) dragView.findViewById( R.id.tvAccess );
			tv.setText( item.acct );
			tv.setTextColor( item.acct_color_fg );
			tv.setBackgroundColor( item.acct_color_bg );
			
			tv = (TextView) dragView.findViewById( R.id.tvName );
			tv.setText( item.name );
			
			ImageView ivColumnIcon = (ImageView) dragView.findViewById( R.id.ivColumnIcon );
			ivColumnIcon.setImageResource( Styler.getAttributeResourceId(
				ActColumnList.this, Column.getIconAttrId( item.type ) ) );
			
			dragView.findViewById( R.id.ivBookmark ).setVisibility(
				clickedView.findViewById( R.id.ivBookmark ).getVisibility()
			);
			
			dragView.findViewById( R.id.item_layout ).setBackgroundColor(
				Styler.getAttributeColor( ActColumnList.this, R.attr.list_item_bg_pressed_dragged )
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
			View view = getLayoutInflater().inflate( R.layout.lv_column_list, parent, false );
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
			return item.id;
		}
	}
}
