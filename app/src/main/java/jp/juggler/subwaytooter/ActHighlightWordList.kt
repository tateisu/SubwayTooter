package jp.juggler.subwaytooter

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.woxthebox.draglistview.DragItem
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import com.woxthebox.draglistview.swipe.ListSwipeHelper
import com.woxthebox.draglistview.swipe.ListSwipeItem
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.*
import java.lang.ref.WeakReference
import java.util.*

class ActHighlightWordList : AppCompatActivity(), View.OnClickListener {
	
	companion object {
		
		private val log = LogCategory("ActHighlightWordList")
		
		private const val REQUEST_CODE_EDIT = 1
	}
	
	private lateinit var listView : DragListView
	private lateinit var listAdapter : MyListAdapter
	
	private var last_ringtone : WeakReference<Ringtone>? = null
	
	//	@Override public void onBackPressed(){
	//		setResult( RESULT_OK );
	//		super.onBackPressed();
	//	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this)
		initUI()
		loadData()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		stopLastRingtone()
	}
	
	private fun initUI() {
		setContentView(R.layout.act_highlight_list)
		App1.initEdgeToEdge(this)
		
		Styler.fixHorizontalPadding2(findViewById(R.id.llContent))
		
		// リストのアダプター
		listAdapter = MyListAdapter()
		
		// ハンドル部分をドラッグで並べ替えできるRecyclerView
		listView = findViewById(R.id.drag_list_view)
		listView.setLayoutManager(LinearLayoutManager(this))
		listView.setAdapter(listAdapter, false)
		
		listView.setCanDragHorizontally(true)
		listView.isDragEnabled = false
		listView.setCustomDragItem(MyDragItem(this, R.layout.lv_highlight_word))
		
		listView.recyclerView.isVerticalScrollBarEnabled = true
		
		// リストを左右スワイプした
		listView.setSwipeListener(object : ListSwipeHelper.OnSwipeListenerAdapter() {
			
			override fun onItemSwipeStarted(
				item : ListSwipeItem
			) {
				// 操作中はリフレッシュ禁止
				// mRefreshLayout.setEnabled( false );
			}
			
			override fun onItemSwipeEnded(
				item : ListSwipeItem,
				swipedDirection : ListSwipeItem.SwipeDirection?
			) {
				// 操作完了でリフレッシュ許可
				// mRefreshLayout.setEnabled( USE_SWIPE_REFRESH );
				
				// 左にスワイプした(右端にBGが見えた) なら要素を削除する
				if(swipedDirection == ListSwipeItem.SwipeDirection.LEFT) {
					val o = item.tag
					if(o is HighlightWord) {
						o.delete(this@ActHighlightWordList)
						listAdapter.removeItem(listAdapter.getPositionForItem(o))
					}
				}
			}
		})
		
		findViewById<View>(R.id.btnAdd).setOnClickListener(this)
	}
	
	private fun loadData() {
		
		val tmp_list = ArrayList<HighlightWord>()
		try {
			
			HighlightWord.createCursor().use { cursor ->
				while(cursor.moveToNext()) {
					val item = HighlightWord(cursor)
					tmp_list.add(item)
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		listAdapter.itemList = tmp_list
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnAdd -> create()
		}
	}
	
	// リスト要素のViewHolder
	internal inner class MyViewHolder(viewRoot : View) :
		DragItemAdapter.ViewHolder(viewRoot, R.id.ivDragHandle, false), View.OnClickListener {
		
		val tvName : TextView
		private val btnSound : View
		val ivSpeech: ImageButton
		
		init {
			
			tvName = viewRoot.findViewById(R.id.tvName)
			btnSound = viewRoot.findViewById(R.id.btnSound)
			ivSpeech = viewRoot.findViewById(R.id.ivSpeech)
			
			// リスト要素のビューが ListSwipeItem だった場合、Swipe操作を制御できる
			if(viewRoot is ListSwipeItem) {
				viewRoot.setSwipeInStyle(ListSwipeItem.SwipeInStyle.SLIDE)
				viewRoot.supportedSwipeDirection = ListSwipeItem.SwipeDirection.LEFT
			}
			
		} // View ID。 ここを押すとドラッグ操作をすぐに開始する
		// 長押しでドラッグ開始するなら真
		
		fun bind(item : HighlightWord) {
			itemView.tag = item // itemView は親クラスのメンバ変数
			tvName.text = item.name
			
			tvName.setBackgroundColor(item.color_bg)
			tvName.setTextColor(
				item.color_fg.notZero()
					?: getAttributeColor(this@ActHighlightWordList, android.R.attr.textColorPrimary)
			)
			
			btnSound.vg(item.sound_type != HighlightWord.SOUND_TYPE_NONE)?.apply{
				setOnClickListener(this@MyViewHolder)
				tag = item
			}
			
			ivSpeech.vg(item.speech != 0 )?.apply{
				setOnClickListener(this@MyViewHolder)
				tag = item
			}
		}
		
		//		@Override
		//		public boolean onItemLongClicked( View view ){
		//			return false;
		//		}
		
		override fun onItemClicked(view : View) {
			val o = view.tag
			if(o is HighlightWord) {
				edit(o)
			}
		}
		
		override fun onClick(v : View) {
			val o = v.tag
			if(o is HighlightWord) {
				when(v.id){
					R.id.btnSound->{
						sound(o)
					}
					R.id.ivSpeech->{
						App1.getAppState(this@ActHighlightWordList).addSpeech(o.name,allowRepeat = true)
					}
				}
			}
		}
	}
	
	// ドラッグ操作中のデータ
	private inner class MyDragItem internal constructor(context : Context, layoutId : Int) :
		DragItem(context, layoutId) {
		
		override fun onBindDragView(clickedView : View, dragView : View) {

			dragView.findViewById<TextView>(R.id.tvName).text =
				clickedView.findViewById<TextView>(R.id.tvName).text

			dragView.findViewById<View>(R.id.btnSound).visibility =
				clickedView.findViewById<View>(R.id.btnSound).visibility

			dragView.findViewById<View>(R.id.ivSpeech).visibility=
				clickedView.findViewById<View>(R.id.ivSpeech).visibility

			dragView.findViewById<View>(R.id.item_layout).setBackgroundColor(
				getAttributeColor(
					this@ActHighlightWordList,
					R.attr.list_item_bg_pressed_dragged
				)
			)
		}
	}
	
	private inner class MyListAdapter internal constructor() :
		DragItemAdapter<HighlightWord, MyViewHolder>() {
		
		init {
			setHasStableIds(true)
			itemList = ArrayList()
		}
		
		override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : MyViewHolder {
			val view = layoutInflater.inflate(R.layout.lv_highlight_word, parent, false)
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
	
	private fun create() {
		DlgTextInput.show(this, getString(R.string.new_item), "", object : DlgTextInput.Callback {
			override fun onEmptyError() {
				showToast(this@ActHighlightWordList, true, R.string.word_empty)
			}
			
			override fun onOK(dialog : Dialog, text : String) {
				var item = HighlightWord.load(text)
				if(item == null) {
					item = HighlightWord(text)
					item.save(this@ActHighlightWordList)
					loadData()
				}
				edit(item)
				
				dialog.dismissSafe()
			}
		})
	}
	
	private fun edit(item : HighlightWord) {
		ActHighlightWordEdit.open(this, REQUEST_CODE_EDIT, item)
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		when {
			requestCode == REQUEST_CODE_EDIT &&
				resultCode == RESULT_OK &&
				data != null ->
				try {
					val sv = data.getStringExtra(ActHighlightWordEdit.EXTRA_ITEM) ?: return
					val item = HighlightWord(sv.toJsonObject())
					item.save(this@ActHighlightWordList)
					loadData()
				} catch(ex : Throwable) {
					throw RuntimeException("can't load data", ex)
				}
			
			else -> super.onActivityResult(requestCode, resultCode, data)
		}
	}
	
	private fun stopLastRingtone() {
		val r = last_ringtone?.get()
		if(r != null) {
			try {
				r.stop()
			} catch(ex : Throwable) {
				log.trace(ex)
			} finally {
				last_ringtone = null
			}
		}
	}
	
	private fun sound(item : HighlightWord) {
		
		val sound_type = item.sound_type
		if(sound_type == HighlightWord.SOUND_TYPE_NONE) return
		
		fun Uri?.tryRingTone() : Boolean {
			try {
				if(this != null) {
					val ringtone = RingtoneManager.getRingtone(this@ActHighlightWordList, this)
					if(ringtone != null) {
						stopLastRingtone()
						last_ringtone = WeakReference(ringtone)
						ringtone.play()
						return true
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return false
		}
		
		if(sound_type == HighlightWord.SOUND_TYPE_CUSTOM
			&& item.sound_uri.mayUri().tryRingTone()
		) return
		
		// fall thru 失敗したら通常の音を鳴らす
		RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).tryRingTone()
		
	}
	
}
