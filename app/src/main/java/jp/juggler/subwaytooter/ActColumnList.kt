package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.woxthebox.draglistview.DragItem
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import com.woxthebox.draglistview.swipe.ListSwipeHelper
import com.woxthebox.draglistview.swipe.ListSwipeItem
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.column.ColumnEncoder
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.util.*

class ActColumnList : AppCompatActivity() {

    companion object {

        private val log = LogCategory("ActColumnList")
        internal const val TMP_FILE_COLUMN_LIST = "tmp_column_list"

        const val EXTRA_ORDER = "order"
        const val EXTRA_SELECTION = "selection"

        fun createIntent(activity: ActMain, currentItem: Int) =
            Intent(activity, ActColumnList::class.java).apply {
                val array = activity.appState.encodeColumnList()
                AppState.saveColumnList(activity, TMP_FILE_COLUMN_LIST, array)
                putExtra(EXTRA_SELECTION, currentItem)
            }
    }

    private lateinit var listView: DragListView
    private lateinit var listAdapter: MyListAdapter
    private var oldSelection: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed {
            makeResult(-1)
            finish()
        }
        App1.setActivityTheme(this)
        initUI()

        if (savedInstanceState != null) {
            restoreData(savedInstanceState.getInt(EXTRA_SELECTION))
        } else {
            val intent = intent
            restoreData(intent.getIntExtra(EXTRA_SELECTION, -1))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(EXTRA_SELECTION, oldSelection)

        val array = listAdapter.itemList.map { it.json }.toJsonArray()
        AppState.saveColumnList(this, TMP_FILE_COLUMN_LIST, array)
    }

    private fun initUI() {
        setContentView(R.layout.act_column_list)
        App1.initEdgeToEdge(this)

        Styler.fixHorizontalPadding0(findViewById(R.id.llContent))

        // リストのアダプター
        listAdapter = MyListAdapter()

        // ハンドル部分をドラッグで並べ替えできるRecyclerView
        listView = findViewById(R.id.drag_list_view)
        listView.setLayoutManager(androidx.recyclerview.widget.LinearLayoutManager(this))
        listView.setAdapter(listAdapter, true)
        listView.setCanDragHorizontally(false)
        listView.setCustomDragItem(MyDragItem(this, R.layout.lv_column_list))

        listView.recyclerView.isVerticalScrollBarEnabled = true
        listView.setDragListListener(object : DragListView.DragListListenerAdapter() {
            override fun onItemDragStarted(position: Int) {
                // 操作中はリフレッシュ禁止
                // mRefreshLayout.setEnabled( false );
            }

            override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                // 操作完了でリフレッシュ許可
                // mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );

                //				if( fromPosition != toPosition ){
                //					// 並べ替えが発生した
                //				}
            }
        })

        // リストを左右スワイプした
        listView.setSwipeListener(object : ListSwipeHelper.OnSwipeListenerAdapter() {

            override fun onItemSwipeStarted(item: ListSwipeItem) {
                // 操作中はリフレッシュ禁止
                // mRefreshLayout.setEnabled( false );
            }

            override fun onItemSwipeEnded(
                item: ListSwipeItem,
                swipedDirection: ListSwipeItem.SwipeDirection?,
            ) {
                // 操作完了でリフレッシュ許可
                // mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );

                // 左にスワイプした(右端に青が見えた) なら要素を削除する
                if (swipedDirection == ListSwipeItem.SwipeDirection.LEFT) {
                    val adapterItem = item.tag as MyItem
                    if (adapterItem.json.optBoolean(ColumnEncoder.KEY_DONT_CLOSE, false)) {
                        showToast(false, R.string.column_has_dont_close_option)
                        listView.resetSwipedViews(null)
                        return
                    }
                    listAdapter.removeItem(listAdapter.getPositionForItem(adapterItem))
                }
            }
        })
    }

    private fun restoreData(ivSelection: Int) {

        this.oldSelection = ivSelection

        val tmpList = ArrayList<MyItem>()
        try {
            AppState.loadColumnList(this, TMP_FILE_COLUMN_LIST)
                ?.objectList()
                ?.forEachIndexed { index, src ->
                    try {
                        val item = MyItem(src, index.toLong(), this)
                        tmpList.add(item)
                        if (oldSelection == item.oldIndex) {
                            item.setOldSelection(true)
                        }
                    } catch (ex: Throwable) {
                        log.trace(ex)
                    }
                }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        listAdapter.itemList = tmpList
    }

    private fun makeResult(newSelection: Int) {
        val intent = Intent()

        val itemList = listAdapter.itemList
        // どの要素を選択するか
        if (newSelection >= 0 && newSelection < listAdapter.itemCount) {
            intent.putExtra(EXTRA_SELECTION, newSelection)
        } else {
            var i = 0
            val ie = itemList.size
            while (i < ie) {
                if (itemList[i].bOldSelection) {
                    intent.putExtra(EXTRA_SELECTION, i)
                    break
                }
                ++i
            }
        }
        // 並べ替え用データ
        val orderList = ArrayList<Int>()
        for (item in itemList) {
            orderList.add(item.oldIndex)
        }
        intent.putExtra(EXTRA_ORDER, orderList)

        setResult(Activity.RESULT_OK, intent)
    }

    private fun performItemSelected(item: MyItem) {
        val idx = listAdapter.getPositionForItem(item)
        makeResult(idx)
        finish()
    }

    // リスト要素のデータ
    internal class MyItem(val json: JsonObject, val id: Long, context: Context) {

        val name: String = json.optString(ColumnEncoder.KEY_COLUMN_NAME)
        val acct: Acct = Acct.parse(json.optString(ColumnEncoder.KEY_COLUMN_ACCESS_ACCT))
        val acctName: String = json.optString(ColumnEncoder.KEY_COLUMN_ACCESS_STR)
        val oldIndex = json.optInt(ColumnEncoder.KEY_OLD_INDEX)
        val type = ColumnType.parse(json.optInt(ColumnEncoder.KEY_TYPE))
        val acctColorBg = json.optInt(ColumnEncoder.KEY_COLUMN_ACCESS_COLOR_BG, 0)
        val acctColorFg = json.optInt(ColumnEncoder.KEY_COLUMN_ACCESS_COLOR, 0)
            .notZero() ?: context.attrColor(R.attr.colorColumnListItemText)
        var bOldSelection: Boolean = false

        fun setOldSelection(b: Boolean) {
            bOldSelection = b
        }
    }

    // リスト要素のViewHolder
    internal inner class MyViewHolder(viewRoot: View) : DragItemAdapter.ViewHolder(
        viewRoot,
        R.id.ivDragHandle, // View ID。 ここを押すとドラッグ操作をすぐに開始する
        true, // 長押しでドラッグ開始するなら真
    ) {

        private val ivBookmark: View = viewRoot.findViewById(R.id.ivBookmark)
        private val tvAccess: TextView = viewRoot.findViewById(R.id.tvAccess)
        private val tvName: TextView = viewRoot.findViewById(R.id.tvName)
        private val ivColumnIcon: ImageView = viewRoot.findViewById(R.id.ivColumnIcon)
        private val acctPadLr = (0.5f + 4f * viewRoot.resources.displayMetrics.density).toInt()

        init {
            // リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
            if (viewRoot is ListSwipeItem) {
                viewRoot.setSwipeInStyle(ListSwipeItem.SwipeInStyle.SLIDE)
                viewRoot.supportedSwipeDirection = ListSwipeItem.SwipeDirection.LEFT
            }
        }

        fun bind(item: MyItem) {
            itemView.tag = item // itemView は親クラスのメンバ変数
            ivBookmark.visibility = if (item.bOldSelection) View.VISIBLE else View.INVISIBLE
            tvAccess.text = item.acctName
            tvAccess.setTextColor(item.acctColorFg)
            tvAccess.setBackgroundColor(item.acctColorBg)
            tvAccess.setPaddingRelative(acctPadLr, 0, acctPadLr, 0)
            tvName.text = item.name
            ivColumnIcon.setImageResource(item.type.iconId(item.acct))
            // 背景色がテーマ次第なので、カラム設定の色を反映するとアイコンが見えなくなる可能性がある
            // よってアイコンやテキストにカラム設定の色を反映しない
        }

        //		@Override
        //		public boolean onItemLongClicked( View view ){
        //			return false;
        //		}

        override fun onItemClicked(view: View?) {
            val item = itemView.tag as MyItem // itemView は親クラスのメンバ変数
            (view.activity as? ActColumnList)?.performItemSelected(item)
        }
    }

    // ドラッグ操作中のデータ
    private inner class MyDragItem(context: Context, layoutId: Int) :
        DragItem(context, layoutId) {

        override fun onBindDragView(clickedView: View, dragView: View) {
            val item = clickedView.tag as MyItem

            var tv: TextView = dragView.findViewById(R.id.tvAccess)
            tv.text = item.acctName
            tv.setTextColor(item.acctColorFg)
            tv.setBackgroundColor(item.acctColorBg)

            tv = dragView.findViewById(R.id.tvName)
            tv.text = item.name

            val ivColumnIcon: ImageView = dragView.findViewById(R.id.ivColumnIcon)
            ivColumnIcon.setImageResource(item.type.iconId(item.acct))

            dragView.findViewById<View>(R.id.ivBookmark).visibility =
                clickedView.findViewById<View>(R.id.ivBookmark).visibility

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
            val view = layoutInflater.inflate(R.layout.lv_column_list, parent, false)
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
