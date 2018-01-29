package jp.juggler.apng.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import jp.juggler.apng.ApngFrames
import kotlin.concurrent.thread

class ActMain : AppCompatActivity() {
	
	companion object {
		const val TAG = "ActMain"
	}

	private lateinit var listView : ListView
	private lateinit var listAdapter: MyAdapter
	private lateinit var handler: Handler
	override fun onCreate(savedInstanceState : Bundle?) {
		handler = Handler()
		super.onCreate(savedInstanceState)
		setContentView(R.layout.act_main)
		this.listView = findViewById(R.id.listView)
		listAdapter = MyAdapter()
		listView.adapter = listAdapter
		listView.onItemClickListener = listAdapter
		
		thread(start=true){

			// RawリソースのIDと名前の一覧
			val list = ArrayList<ListItem>()
			R.raw::class.java.fields
				.mapNotNull { it.get(null) as? Int}
				.forEach { list.add(
					ListItem(
						it,
						resources.getResourceName(it).replaceFirst(""".+/""".toRegex(), "")
					)
				)}

			list.sortBy { it.caption }
			
			handler.post{
				if(isDestroyed) return@post
				listAdapter.list.addAll(list)
				listAdapter.notifyDataSetChanged()
			}
			
		}
	}
	
	class ListItem(val id:Int,val caption:String)
	
	inner class MyAdapter :BaseAdapter(), AdapterView.OnItemClickListener {
		
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
			val view:View
			val holder: MyViewHolder
			if( viewArg == null ){
				view = layoutInflater.inflate(R.layout.lv_main,parent,false)
				holder = MyViewHolder(view)
				view.tag = holder
			}else{
				view = viewArg
				holder = view.tag as MyViewHolder
			}
			holder.bind( list[position])
			return view
		}
		
		override fun onItemClick(
			parent : AdapterView<*>?,
			view : View?,
			position : Int,
			id : Long
		) {
			val item = list[position]
			ActApngView.open(this@ActMain, item.id, item.caption)
		}
		
	}
	
	inner class MyViewHolder(viewRoot:View){
		private val tvCaption : TextView = viewRoot.findViewById(R.id.tvCaption)
		private val apngView: ApngView = viewRoot.findViewById(R.id.apngView)
		private var lastId : Int = -1
		fun bind(listItem : ListItem) {
			tvCaption.text = listItem.caption
			
			val resId = listItem.id
			if( lastId != resId){
				Log.d(TAG,"loading start: resId=$resId lastId=$lastId")
				lastId =resId
				apngView.apngFrames?.dispose()
				apngView.apngFrames=null
				thread (start=true){
					try {
						resources.openRawResource(resId).use { inStream->
							val apngFrames = ApngFrames.parseApng(inStream, 128)
							handler.post( {
								if(isDestroyed) return@post
								if( lastId != resId) {
									Log.d(TAG,"loading cancelled: resId=$resId,lastId=$lastId")
								}else{
									Log.d(TAG,"loading complete: resId=$resId")
									apngView.apngFrames = apngFrames
								}
							})
						}
					}catch(ex:Throwable){
						ex.printStackTrace()
						Log.e(TAG,"load error: ${ex.javaClass.simpleName} ${ex.message}")
					}
				}
				
			}
		}
		
	}
}
