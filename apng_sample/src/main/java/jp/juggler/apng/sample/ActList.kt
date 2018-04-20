package jp.juggler.apng.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import jp.juggler.apng.ApngFrames
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import java.lang.ref.WeakReference
import android.support.v4.content.ContextCompat



class ActList : AppCompatActivity() {
	
	companion object {
		const val TAG = "ActList"
		
		const val PERMISSION_REQUEST_CODE_STORAGE = 1
	}
	
	class ListItem(val id : Int, val caption : String)
	
	private lateinit var listView : ListView
	private lateinit var listAdapter : MyAdapter
	private var timeAnimationStart : Long = 0L
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.act_list)
		this.listView = findViewById(R.id.listView)
		listAdapter = MyAdapter()
		listView.adapter = listAdapter
		listView.onItemClickListener = listAdapter
		timeAnimationStart = SystemClock.elapsedRealtime()
		
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			// Assume thisActivity is the current activity
			if( PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE
			)){
				ActivityCompat.requestPermissions(
					this,
					arrayOf( Manifest.permission.WRITE_EXTERNAL_STORAGE),
					
					PERMISSION_REQUEST_CODE_STORAGE
				)
			}
		}
		
		
		launch(UI) {
			
			if(isDestroyed) return@launch
			
			val list = async(CommonPool) {
				// RawリソースのIDと名前の一覧
				R.raw::class.java.fields
					.mapNotNull { it.get(null) as? Int }
					.map { id ->
						ListItem(
							id,
							resources.getResourceName(id)
								.replaceFirst(""".+/""".toRegex(), "")
						)
					}
					.toMutableList()
					.apply { sortBy { it.caption } }
				
			}.await()
			
			if(isDestroyed) return@launch
			
			listAdapter.list.addAll(list)
			listAdapter.notifyDataSetChanged()
		}
	}
	

	override fun onRequestPermissionsResult(
		requestCode : Int,
		permissions : Array<String>,
		grantResults : IntArray
	) {
		when(requestCode) {
			PERMISSION_REQUEST_CODE_STORAGE -> {
				// If request is cancelled, the result arrays are empty.
				if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// permission was granted, yay! Do the
					// contacts-related task you need to do.
				} else {
					// permission denied, boo! Disable the
					// functionality that depends on this permission.
				}
				return
			}
		// other 'case' lines to check for other
		// permissions this app might request
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
				holder = MyViewHolder(view, this@ActList)
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
		_activity : ActList
	) {
		
		private val activity = ref(_activity)
		private val tvCaption : TextView = viewRoot.findViewById(R.id.tvCaption)
		private val apngView : ApngView = viewRoot.findViewById(R.id.apngView)
		
		init {
			apngView.timeAnimationStart = _activity.timeAnimationStart
		}
		
		private var lastId : Int = 0
		private var lastJob : Job? = null
		
		fun bind(listItem : ListItem) {
			tvCaption.text = listItem.caption
			
			val resId = listItem.id
			if(lastId != resId) {
				lastId = resId
				apngView.apngFrames?.dispose()
				apngView.apngFrames = null
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

class WeakRef<T : Any>(t : T) : WeakReference<T>(t) {
	operator fun invoke() : T? = get()
}

fun <T : Any> ref(t : T) = WeakRef(t)
