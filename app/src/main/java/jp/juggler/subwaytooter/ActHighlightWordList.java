package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.woxthebox.draglistview.DragItem;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;
import com.woxthebox.draglistview.swipe.ListSwipeHelper;
import com.woxthebox.draglistview.swipe.ListSwipeItem;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import jp.juggler.subwaytooter.dialog.DlgTextInput;
import jp.juggler.subwaytooter.table.HighlightWord;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActHighlightWordList extends AppCompatActivity implements View.OnClickListener {
	
	private static final LogCategory log = new LogCategory( "ActHighlightWordList" );
	
	DragListView listView;
	MyListAdapter listAdapter;
	
	//	@Override public void onBackPressed(){
	//		setResult( RESULT_OK );
	//		super.onBackPressed();
	//	}
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		loadData();
	}
	
	@Override protected void onDestroy(){
		super.onDestroy();
		stopLastRingtone();
	}
	
	private void initUI(){
		setContentView( R.layout.act_highlight_list );
		
		Styler.fixHorizontalPadding2( findViewById( R.id.llContent ) );
		
		// リストのアダプター
		listAdapter = new MyListAdapter();
		
		// ハンドル部分をドラッグで並べ替えできるRecyclerView
		listView = findViewById( R.id.drag_list_view );
		listView.setLayoutManager( new LinearLayoutManager( this ) );
		listView.setAdapter( listAdapter, false );
		
		listView.setCanDragHorizontally( true );
		listView.setDragEnabled( false );
		listView.setCustomDragItem( new MyDragItem( this, R.layout.lv_highlight_word ) );
		
		listView.getRecyclerView().setVerticalScrollBarEnabled( true );
		
		// リストを左右スワイプした
		listView.setSwipeListener( new ListSwipeHelper.OnSwipeListenerAdapter() {
			
			@Override public void onItemSwipeStarted( ListSwipeItem item ){
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			@Override public void onItemSwipeEnded(
				ListSwipeItem item
				, ListSwipeItem.SwipeDirection swipedDirection
			){
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
				
				// 左にスワイプした(右端にBGが見えた) なら要素を削除する
				if( swipedDirection == ListSwipeItem.SwipeDirection.LEFT ){
					Object o = item.getTag();
					if( o instanceof HighlightWord ){
						HighlightWord adapterItem = (HighlightWord) o;
						adapterItem.delete();
						listAdapter.removeItem( listAdapter.getPositionForItem( adapterItem ) );
					}
				}
			}
		} );
		
		findViewById( R.id.btnAdd ).setOnClickListener( this );
	}
	
	private void loadData(){
		
		ArrayList< HighlightWord > tmp_list = new ArrayList<>();
		try{
			Cursor cursor = HighlightWord.createCursor();
			if( cursor != null ){
				try{
					while( cursor.moveToNext() ){
						HighlightWord item = new HighlightWord( cursor );
						tmp_list.add( item );
					}
					
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		listAdapter.setItemList( tmp_list );
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAdd:
			create();
			break;
		}
	}
	
	// リスト要素のViewHolder
	class MyViewHolder extends DragItemAdapter.ViewHolder implements View.OnClickListener {
		
		final TextView tvName;
		final View btnSound;
		
		MyViewHolder( final View viewRoot ){
			super( viewRoot
				, R.id.ivDragHandle // View ID。 ここを押すとドラッグ操作をすぐに開始する
				, false // 長押しでドラッグ開始するなら真
			);
			
			tvName = viewRoot.findViewById( R.id.tvName );
			btnSound = viewRoot.findViewById( R.id.btnSound );
			
			// リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
			if( viewRoot instanceof ListSwipeItem ){
				ListSwipeItem lsi = (ListSwipeItem) viewRoot;
				lsi.setSwipeInStyle( ListSwipeItem.SwipeInStyle.SLIDE );
				lsi.setSupportedSwipeDirection( ListSwipeItem.SwipeDirection.LEFT );
			}
			
		}
		
		void bind( HighlightWord item ){
			itemView.setTag( item ); // itemView は親クラスのメンバ変数
			tvName.setText( item.name );
			
			int c = item.color_bg;
			if( c == 0 ){
				tvName.setBackgroundColor( 0 );
			}else{
				tvName.setBackgroundColor( c );
			}
			
			c = item.color_fg;
			if( c == 0 ){
				tvName.setTextColor( Styler.getAttributeColor( ActHighlightWordList.this, android.R.attr.textColorPrimary ) );
			}else{
				tvName.setTextColor( c );
			}
			
			btnSound.setVisibility( item.sound_type == HighlightWord.SOUND_TYPE_NONE ? View.GONE :View.VISIBLE);
			btnSound.setOnClickListener( this );
			btnSound.setTag( item );
		}
		
		//		@Override
		//		public boolean onItemLongClicked( View view ){
		//			return false;
		//		}
		
		@Override
		public void onItemClicked( View view ){
			Object o = view.getTag();
			if( o instanceof HighlightWord ){
				HighlightWord adapterItem = (HighlightWord) o;
				edit( adapterItem );
			}
		}
		
		@Override public void onClick( View v ){
			Object o = v.getTag();
			if( o instanceof HighlightWord ){
				sound( (HighlightWord) o );
			}
			
		}
	}
	
	// ドラッグ操作中のデータ
	private class MyDragItem extends DragItem {
		MyDragItem( Context context, int layoutId ){
			super( context, layoutId );
		}
		
		@Override
		public void onBindDragView( View clickedView, View dragView ){
			( (TextView) dragView.findViewById( R.id.tvName ) ).setText(
				( (TextView) clickedView.findViewById( R.id.tvName ) ).getText()
			);
			(  dragView.findViewById( R.id.btnSound ) ).setVisibility(
				(  clickedView.findViewById( R.id.btnSound ) ).getVisibility()
			);
			dragView.findViewById( R.id.item_layout ).setBackgroundColor(
				Styler.getAttributeColor( ActHighlightWordList.this, R.attr.list_item_bg_pressed_dragged )
			);
		}
	}
	
	private class MyListAdapter extends DragItemAdapter< HighlightWord, MyViewHolder > {
		
		MyListAdapter(){
			super();
			setHasStableIds( true );
			setItemList( new ArrayList< HighlightWord >() );
		}
		
		@Override
		public MyViewHolder onCreateViewHolder( ViewGroup parent, int viewType ){
			View view = getLayoutInflater().inflate( R.layout.lv_highlight_word, parent, false );
			return new MyViewHolder( view );
		}
		
		@Override
		public void onBindViewHolder( MyViewHolder holder, int position ){
			super.onBindViewHolder( holder, position );
			holder.bind( getItemList().get( position ) );
		}
		
		@Override
		public long getUniqueItemId( int position ){
			HighlightWord item = mItemList.get( position ); // mItemList は親クラスのメンバ変数
			return item.id;
		}
	}
	
	private void create(){
		DlgTextInput.show( this, getString( R.string.new_item ), "", new DlgTextInput.Callback() {
			@Override public void onEmptyError(){
				Utils.showToast( ActHighlightWordList.this, true, R.string.word_empty );
			}
			
			@Override public void onOK( Dialog dialog, String text ){
				HighlightWord item = HighlightWord.load( text );
				if( item == null ){
					item = new HighlightWord( text );
					item.save();
					loadData();
				}
				edit( item );
				try{
					dialog.dismiss();
				}catch( Throwable ignored ){
				
				}
			}
		} );
	}
	
	private void edit( @NonNull HighlightWord item ){
		ActHighlightWordEdit.open( this, REQUEST_CODE_EDIT, item );
	}
	
	private static final int REQUEST_CODE_EDIT = 1;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK && data != null ){
			try{
				HighlightWord item = new HighlightWord( new JSONObject( data.getStringExtra( ActHighlightWordEdit.EXTRA_ITEM ) ) );
				item.save();
				loadData();
				return;
			}catch( Throwable ex ){
				throw new RuntimeException( "can't loading data", ex );
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	WeakReference< Ringtone > last_ringtone;
	
	private void stopLastRingtone(){
		Ringtone r = last_ringtone == null ? null : last_ringtone.get();
		if( r != null ){
			try{
				r.stop();
			}catch( Throwable ex ){
				log.trace( ex );
			}finally{
				last_ringtone = null;
			}
		}
	}
	
	private void sound( @NonNull HighlightWord item ){
		
		stopLastRingtone();
		
		if( item.sound_type == HighlightWord.SOUND_TYPE_NONE ) return;
		
		if( item.sound_type == HighlightWord.SOUND_TYPE_CUSTOM
			&& ! TextUtils.isEmpty( item.sound_uri )
			){
			try{
				Ringtone ringtone = RingtoneManager.getRingtone( this, Uri.parse( item.sound_uri ) );
				if( ringtone != null ){
					last_ringtone = new WeakReference<>( ringtone );
					ringtone.play();
					return;
				}
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		
		Uri uri = RingtoneManager.getDefaultUri( RingtoneManager.TYPE_NOTIFICATION );
		try{
			Ringtone ringtone = RingtoneManager.getRingtone( this, uri );
			if( ringtone != null ){
				last_ringtone = new WeakReference<>( ringtone );
				ringtone.play();
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
}
