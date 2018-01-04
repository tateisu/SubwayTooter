package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.woxthebox.draglistview.DragItem
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import com.woxthebox.draglistview.swipe.ListSwipeHelper
import com.woxthebox.draglistview.swipe.ListSwipeItem

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class ActColumnList : AppCompatActivity() {
	
	private lateinit var listView : DragListView
	private lateinit var listAdapter : MyListAdapter
	private var old_selection : Int = 0
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		initUI()
		
		if(savedInstanceState != null) {
			restoreData(savedInstanceState.getInt(EXTRA_SELECTION))
		} else {
			val intent = intent
			restoreData(intent.getIntExtra(EXTRA_SELECTION, - 1))
		}
	}
	
	override fun onSaveInstanceState(outState : Bundle?) {
		super.onSaveInstanceState(outState)
		outState !!.putInt(EXTRA_SELECTION, old_selection)
		
		//
		val array = JSONArray()
		val item_list = listAdapter.itemList
		var i = 0
		val ie = item_list.size
		while(i < ie) {
			array.put(item_list[i].json)
			++ i
		}
		AppState.saveColumnList(this, TMP_FILE_COLUMN_LIST, array)
	}
	
	override fun onBackPressed() {
		makeResult(- 1)
		super.onBackPressed()
	}
	
	private fun initUI() {
		setContentView(R.layout.act_column_list)
		
		Styler.fixHorizontalPadding2(findViewById(R.id.llContent))
		
		// リストのアダプター
		listAdapter = MyListAdapter()
		
		// ハンドル部分をドラッグで並べ替えできるRecyclerView
		listView = findViewById(R.id.drag_list_view)
		listView.setLayoutManager(LinearLayoutManager(this))
		listView.setAdapter(listAdapter, true)
		listView.setCanDragHorizontally(false)
		listView.setCustomDragItem(MyDragItem(this, R.layout.lv_column_list))
		
		listView.recyclerView.isVerticalScrollBarEnabled = true
		listView.setDragListListener(object : DragListView.DragListListenerAdapter() {
			override fun onItemDragStarted(position : Int) {
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			override fun onItemDragEnded(fromPosition : Int, toPosition : Int) {
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
				
				//				if( fromPosition != toPosition ){
				//					// 並べ替えが発生した
				//				}
			}
		})
		
		// リストを左右スワイプした
		listView.setSwipeListener(object : ListSwipeHelper.OnSwipeListenerAdapter() {
			
			override fun onItemSwipeStarted(item : ListSwipeItem?) {
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			override fun onItemSwipeEnded(item : ListSwipeItem?, swipedDirection : ListSwipeItem.SwipeDirection?) {
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
				
				// 左にスワイプした(右端に青が見えた) なら要素を削除する
				if(swipedDirection == ListSwipeItem.SwipeDirection.LEFT) {
					val adapterItem = item !!.tag as MyItem
					if(adapterItem.json.optBoolean(Column.KEY_DONT_CLOSE, false)) {
						Utils.showToast(this@ActColumnList, false, R.string.column_has_dont_close_option)
						listView.resetSwipedViews(null)
						return
					}
					listAdapter.removeItem(listAdapter.getPositionForItem(adapterItem))
				}
			}
		})
	}
	
	private fun restoreData(ivSelection : Int) {
		
		this.old_selection = ivSelection
		
		val tmp_list = ArrayList<MyItem>()
		try {
			val array = AppState.loadColumnList(this, TMP_FILE_COLUMN_LIST)
			if(array != null) {
				var i = 0
				val ie = array.length()
				while(i < ie) {
					try {
						val src = array.optJSONObject(i)
						val item = MyItem(src, i.toLong(), this)
						if(src != null) {
							tmp_list.add(item)
							if(old_selection == item.old_index) {
								item.setOldSelection(true)
							}
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					++ i
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		listAdapter.itemList = tmp_list
	}
	
	private fun makeResult(new_selection : Int) {
		val intent = Intent()
		
		val item_list = listAdapter.itemList
		// どの要素を選択するか
		if(new_selection >= 0 && new_selection < listAdapter.itemCount) {
			intent.putExtra(EXTRA_SELECTION, new_selection)
		} else {
			var i = 0
			val ie = item_list.size
			while(i < ie) {
				if(item_list[i].bOldSelection) {
					intent.putExtra(EXTRA_SELECTION, i)
					break
				}
				++ i
			}
		}
		// 並べ替え用データ
		val order_list = ArrayList<Int>()
		for(item in item_list) {
			order_list.add(item.old_index)
		}
		intent.putExtra(EXTRA_ORDER, order_list)
		
		setResult(Activity.RESULT_OK, intent)
	}
	
	private fun performItemSelected(item : MyItem) {
		val idx = listAdapter.getPositionForItem(item)
		makeResult(idx)
		finish()
	}
	
	// リスト要素のデータ
	internal class MyItem(val json : JSONObject, val id : Long, context : Context) {
		val name : String = json.optString(Column.KEY_COLUMN_NAME)
		val acct : String = json.optString(Column.KEY_COLUMN_ACCESS)
		val old_index = json.optInt(Column.KEY_OLD_INDEX)
		val type = json.optInt(Column.KEY_TYPE)
		val acct_color_fg : Int
		val acct_color_bg : Int
		var bOldSelection : Boolean = false
		
		init {
			var c = json.optInt(Column.KEY_COLUMN_ACCESS_COLOR, 0)
			this.acct_color_fg = if(c != 0) c else Styler.getAttributeColor(context, R.attr.colorColumnListItemText)
			
			c = json.optInt(Column.KEY_COLUMN_ACCESS_COLOR_BG, 0)
			this.acct_color_bg = c
		}
		
		fun setOldSelection(b : Boolean) {
			bOldSelection = b
		}
	}
	
	// リスト要素のViewHolder
	internal inner class MyViewHolder(viewRoot : View) : DragItemAdapter.ViewHolder(
		viewRoot, R.id.ivDragHandle // View ID。 ここを押すとドラッグ操作をすぐに開始する
		, true // 長押しでドラッグ開始するなら真
	) {
		
		private val ivBookmark = viewRoot.findViewById(R.id.ivBookmark) as View
		private val tvAccess = viewRoot.findViewById(R.id.tvAccess) as TextView
		private val tvName = viewRoot.findViewById(R.id.tvName) as TextView
		private val ivColumnIcon = viewRoot.findViewById(R.id.ivColumnIcon) as ImageView
		private val acct_pad_lr = (0.5f + 4f * viewRoot.resources.displayMetrics.density).toInt()
		
		init {
			// リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
			if(viewRoot is ListSwipeItem) {
				viewRoot.setSwipeInStyle(ListSwipeItem.SwipeInStyle.SLIDE)
				viewRoot.supportedSwipeDirection = ListSwipeItem.SwipeDirection.LEFT
			}
		}
		
		fun bind(item : MyItem) {
			itemView.tag = item // itemView は親クラスのメンバ変数
			ivBookmark.visibility = if(item.bOldSelection) View.VISIBLE else View.INVISIBLE
			tvAccess.text = item.acct
			tvAccess.setTextColor(item.acct_color_fg)
			tvAccess.setBackgroundColor(item.acct_color_bg)
			tvAccess.setPaddingRelative(acct_pad_lr, 0, acct_pad_lr, 0)
			tvName.text = item.name
			ivColumnIcon.setImageResource(Styler.getAttributeResourceId(
				this@ActColumnList, Column.getIconAttrId(item.acct, item.type)
			))
		}
		
		//		@Override
		//		public boolean onItemLongClicked( View view ){
		//			return false;
		//		}
		
		override fun onItemClicked(view : View?) {
			val item = itemView.tag as MyItem // itemView は親クラスのメンバ変数
			val activity = Utils.getActivityFromView(view)
			(activity as? ActColumnList)?.performItemSelected(item)
		}
	}
	
	// ドラッグ操作中のデータ
	private inner class MyDragItem internal constructor(context : Context, layoutId : Int) : DragItem(context, layoutId) {
		
		override fun onBindDragView(clickedView : View, dragView : View) {
			val item = clickedView.tag as MyItem
			
			var tv = dragView.findViewById<TextView>(R.id.tvAccess)
			tv.text = item.acct
			tv.setTextColor(item.acct_color_fg)
			tv.setBackgroundColor(item.acct_color_bg)
			
			tv = dragView.findViewById(R.id.tvName)
			tv.text = item.name
			
			val ivColumnIcon = dragView.findViewById<ImageView>(R.id.ivColumnIcon)
			ivColumnIcon.setImageResource(Styler.getAttributeResourceId(
				this@ActColumnList, Column.getIconAttrId(item.acct, item.type)))
			
			dragView.findViewById<View>(R.id.ivBookmark).visibility = clickedView.findViewById<View>(R.id.ivBookmark).visibility
			
			dragView.findViewById<View>(R.id.item_layout).setBackgroundColor(
				Styler.getAttributeColor(this@ActColumnList, R.attr.list_item_bg_pressed_dragged)
			)
		}
	}
	
	private inner class MyListAdapter internal constructor() : DragItemAdapter<MyItem, MyViewHolder>() {
		
		
		init {
			setHasStableIds(true)
			itemList = ArrayList()
		}
		
		override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : MyViewHolder {
			val view = layoutInflater.inflate(R.layout.lv_column_list, parent, false)
			return MyViewHolder(view)
		}
		
		override fun onBindViewHolder(holder : MyViewHolder, position : Int) {
			super.onBindViewHolder(holder, position)
			holder.bind(itemList[position])
		}
		
		override fun getUniqueItemId(position : Int) : Long {
			val item = mItemList[position] // mItemList は親クラスのメンバ変数
			return item.id
		}
		
	}
	
	companion object {
		private val log = LogCategory("ActColumnList")
		internal val TMP_FILE_COLUMN_LIST = "tmp_column_list"
		
		val EXTRA_ORDER = "order"
		val EXTRA_SELECTION = "selection"
		
		fun open(activity : ActMain, currentItem : Int, requestCode : Int) {
			val array = activity.app_state.encodeColumnList()
			AppState.saveColumnList(activity, ActColumnList.TMP_FILE_COLUMN_LIST, array)
			val intent = Intent(activity, ActColumnList::class.java)
			intent.putExtra(ActColumnList.EXTRA_SELECTION, currentItem)
			activity.startActivityForResult(intent, requestCode)
		}
	}
}
