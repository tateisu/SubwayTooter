package jp.juggler.apng.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import java.lang.ref.WeakReference

class ActList : AppCompatActivity() {
	
	companion object {
		const val TAG = "ActList"
	}
	
	class WeakRef<T : Any>(t : T) : WeakReference<T>(t) {
		operator fun invoke() : T? = get()
	}
	
	fun <T : Any> ref(t : T) = WeakRef(t)
	
	class ListItem(val id : Int, val caption : String)
	
	private lateinit var listView : ListView
	private lateinit var listAdapter : MyAdapter
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.act_list)
		this.listView = findViewById(R.id.listView)
		listAdapter = MyAdapter()
		listView.adapter = listAdapter
		listView.onItemClickListener = listAdapter
		
		launch(UI) {
			val list = async(CommonPool) {
				// RawリソースのIDと名前の一覧
				R.raw::class.java.fields
					.mapNotNull {
						val id = it.get(null) as? Int
						if(id == null) {
							null
						} else {
							ListItem(
								id,
								resources.getResourceName(id).replaceFirst(""".+/""".toRegex(), "")
							)
						}
					}
					.toMutableList()
					.let { list ->
						list.sortBy { item -> item.caption }
						list
					}
				
			}.await()
			
			if(isDestroyed) return@launch
			
			listAdapter.list.addAll(list)
			listAdapter.notifyDataSetChanged()
		}
	}
	
	inner class MyAdapter : BaseAdapter(), AdapterView.OnItemClickListener {
		
		val list = ArrayList<ListItem>()
		
		override fun getCount() : Int {
			return list.size
		}
		
		override fun getItem(position : Int) : Any {
			return list[position]
		}
		
		override fun getItemId(position : Int) : Long {
			return list[position].id.toLong()
		}
		
		override fun getView(
			position : Int,
			viewArg : View?,
			parent : ViewGroup?
		) : View {
			val view : View
			val holder : MyViewHolder
			if(viewArg == null) {
				view = layoutInflater.inflate(R.layout.lv_item, parent, false)
				holder = MyViewHolder(view, ref(this@ActList))
				view.tag = holder
			} else {
				view = viewArg
				holder = view.tag as MyViewHolder
			}
			holder.bind(list[position])
			return view
		}
		
		override fun onItemClick(
			parent : AdapterView<*>?,
			view : View?,
			position : Int,
			id : Long
		) {
			val item = list[position]
			ActViewer.open(this@ActList, item.id, item.caption)
		}
		
	}
	
	class MyViewHolder(
		viewRoot : View,
		private val activity : WeakRef<ActList>
	) {
		
		private val tvCaption : TextView = viewRoot.findViewById(R.id.tvCaption)
		private val apngView : ApngView = viewRoot.findViewById(R.id.apngView)
		
		private var lastId : Int = 0
		private var lastJob : Job? = null
		
		fun bind(listItem : ListItem) {
			tvCaption.text = listItem.caption
			
			val resId = listItem.id
			if(lastId != resId) {
				lastId = resId
				apngView.apngFrames?.dispose()
				apngView.apngFrames = null
				Log.d(TAG, "loading start: id=$resId")
				launch(UI) {
					try {
						if(activity()?.isDestroyed != false) return@launch
						
						lastJob?.cancelAndJoin()
						
						val job = async(CommonPool) {
							activity()?.resources?.openRawResource(resId)?.use { inStream ->
								ApngFrames.parseApng(inStream, 128)
							}
						}
						lastJob = job
						val apngFrames = job.await()

						if(activity()?.isDestroyed == false
							&& lastId == resId
							&& apngFrames != null
						) {
							Log.d(TAG, "loading complete: resId=$resId")
							apngView.apngFrames = apngFrames
						} else {
							apngFrames?.dispose()
						}
					} catch(ex : Throwable) {
						ex.printStackTrace()
						Log.e(TAG, "load error: ${ex.javaClass.simpleName} ${ex.message}")
					}
				}
			}
		}
	}
}
