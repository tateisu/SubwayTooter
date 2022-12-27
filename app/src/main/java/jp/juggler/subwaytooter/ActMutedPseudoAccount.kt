package jp.juggler.subwaytooter

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.woxthebox.draglistview.DragItem
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import com.woxthebox.draglistview.swipe.ListSwipeHelper
import com.woxthebox.draglistview.swipe.ListSwipeItem
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.LogCategory
import jp.juggler.util.attrColor
import jp.juggler.util.backPressed

class ActMutedPseudoAccount : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActMutedPseudoAccount")
    }

    internal lateinit var listView: DragListView
    private lateinit var listAdapter: MyListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed {
            setResult(RESULT_OK)
            finish()
        }
        App1.setActivityTheme(this)
        initUI()
        loadData()
    }

    private fun initUI() {
        setContentView(R.layout.act_word_list)
        App1.initEdgeToEdge(this)

        Styler.fixHorizontalPadding0(findViewById(R.id.llContent))

        // リストのアダプター
        listAdapter = MyListAdapter()

        // ハンドル部分をドラッグで並べ替えできるRecyclerView
        listView = findViewById(R.id.drag_list_view)
        listView.setLayoutManager(androidx.recyclerview.widget.LinearLayoutManager(this))
        listView.setAdapter(listAdapter, false)

        listView.setCanDragHorizontally(true)
        listView.isDragEnabled = false
        listView.setCustomDragItem(MyDragItem(this, R.layout.lv_mute_app))

        listView.recyclerView.isVerticalScrollBarEnabled = true
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
        listView.setSwipeListener(object : ListSwipeHelper.OnSwipeListenerAdapter() {

            override fun onItemSwipeStarted(item: ListSwipeItem?) {
                // 操作中はリフレッシュ禁止
                // mRefreshLayout.setEnabled( false );
            }

            override fun onItemSwipeEnded(
                item: ListSwipeItem?,
                swipedDirection: ListSwipeItem.SwipeDirection?,
            ) {
                // 操作完了でリフレッシュ許可
                // mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );

                // 左にスワイプした(右端に青が見えた) なら要素を削除する
                if (swipedDirection == ListSwipeItem.SwipeDirection.LEFT) {
                    val o = item?.tag
                    if (o is MyItem) {
                        UserRelation.deletePseudo(o.id)
                        listAdapter.removeItem(listAdapter.getPositionForItem(o))
                    }
                }
            }
        })
    }

    private fun loadData() {

        val tmpList = ArrayList<MyItem>()
        try {
            UserRelation.createCursorPseudo().use { cursor ->
                val idxId = UserRelation.COL_ID.getIndex(cursor)
                val idxName = UserRelation.COL_WHO_ID.getIndex(cursor)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idxId)
                    val name = cursor.getString(idxName)
                    val item = MyItem(id, name)
                    tmpList.add(item)
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "loadData failed.")
        }

        listAdapter.itemList = tmpList
    }

    // リスト要素のデータ
    internal class MyItem(val id: Long, val name: String)

    // リスト要素のViewHolder
    internal class MyViewHolder(viewRoot: View) :
        DragItemAdapter.ViewHolder(viewRoot, R.id.ivDragHandle, false) {

        val tvName: TextView

        init {

            tvName = viewRoot.findViewById(R.id.tvName)

            // リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
            if (viewRoot is ListSwipeItem) {
                viewRoot.setSwipeInStyle(ListSwipeItem.SwipeInStyle.SLIDE)
                viewRoot.supportedSwipeDirection = ListSwipeItem.SwipeDirection.LEFT
            }
        } // View ID。 ここを押すとドラッグ操作をすぐに開始する
        // 長押しでドラッグ開始するなら真

        fun bind(item: MyItem) {
            itemView.tag = item // itemView は親クラスのメンバ変数
            tvName.text = item.name
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
    private inner class MyDragItem(context: Context, layoutId: Int) :
        DragItem(context, layoutId) {

        override fun onBindDragView(clickedView: View, dragView: View) {
            dragView.findViewById<TextView>(R.id.tvName).text =
                clickedView.findViewById<TextView>(R.id.tvName).text

            dragView.findViewById<View>(R.id.item_layout)
                .setBackgroundColor(attrColor(R.attr.list_item_bg_pressed_dragged))
        }
    }

    private inner class MyListAdapter :
        DragItemAdapter<MyItem, MyViewHolder>() {

        init {
            setHasStableIds(true)
            itemList = ArrayList()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            val view = layoutInflater.inflate(R.layout.lv_mute_app, parent, false)
            return MyViewHolder(view)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.bind(itemList[position])
        }

        override fun getUniqueItemId(position: Int): Long {
            val item = mItemList[position] // mItemList は親クラスのメンバ変数
            return item.id
        }
    }
}
