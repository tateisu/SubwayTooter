package jp.juggler.subwaytooter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

internal class ItemListAdapter(
	private val activity : ActMain,
	private val column : Column,
	internal val columnVh : ColumnViewHolder,
	private val bSimpleList : Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private val list : List<Any>

	init {
		this.list = column.list_data
	}
	
	override fun getItemCount() : Int {
		return when(column.getHeaderType()){
			null-> column.list_data.size
			else-> column.list_data.size +1
		}
	}

	override fun getItemViewType(position : Int) : Int {
		val headerType = column.getHeaderType()
		if( headerType == null || position>0 ) return 0
		return headerType.viewType
	}
	
	override fun onCreateViewHolder(parent : ViewGroup?, viewType : Int) : RecyclerView.ViewHolder {
		when(viewType) {
			0 -> {
				val holder = ItemViewHolder(activity)
				holder.viewRoot.tag = holder
				return ViewHolderItem(holder)
			}
			Column.HeaderType.Profile.viewType -> {
				val viewRoot = activity.layoutInflater.inflate(R.layout.lv_header_profile, parent, false)
				val holder = ViewHolderHeaderProfile(activity,viewRoot)
				viewRoot.tag = holder
				return holder
			}
			Column.HeaderType.Search.viewType -> {
				val viewRoot = activity.layoutInflater.inflate(R.layout.lv_header_search_desc, parent, false)
				val holder = ViewHolderHeaderSearch(activity,viewRoot)
				viewRoot.tag = holder
				return holder
			}
			Column.HeaderType.Instance.viewType -> {
				val viewRoot = activity.layoutInflater.inflate(R.layout.lv_header_instance, parent, false)
				val holder = ViewHolderHeaderInstance(activity,viewRoot)
				viewRoot.tag = holder
				return holder
			}
			else -> throw RuntimeException("unknown viewType: $viewType")
		}
	}
	
	fun getHeaderViewHolder(listView:RecyclerView): ViewHolderHeaderBase?{
		return when(column.getHeaderType()){
			null-> null
			else-> listView.findViewHolderForAdapterPosition(0) as? ViewHolderHeaderBase
		}
	}
	
	override fun onBindViewHolder(holder : RecyclerView.ViewHolder, positionArg : Int) {
		val headerType = column.getHeaderType()
		if(holder is ViewHolderItem) {
			val position = if(headerType != null) positionArg - 1 else positionArg
			val o = if(position >= 0 && position < list.size) list[position] else null
			holder.ivh.bind(this, column, bSimpleList, o)
		} else if(holder is ViewHolderHeaderBase) {
			holder.bindData(column)
		}
	}
	
	override fun onViewRecycled(holder : RecyclerView.ViewHolder) {
		if(holder is ViewHolderItem) {
			holder.ivh.onViewRecycled()
		} else if(holder is ViewHolderHeaderBase) {
			holder.onViewRecycled()
		}
	}
	
//	override fun getViewTypeCount() : Int {
//		return if(header != null) 2 else 1
//	}
	

//	override fun getItem(positionArg : Int) : Any? {
//		var position = positionArg
//		if(header != null) {
//			if(position == 0) return header
//			-- position
//		}
//		return if(position >= 0 && position < column.list_data.size) list[position] else null
//	}
	
	override fun getItemId(position : Int) : Long {
		return 0
	}
//	override fun hasStableIds():Boolean = false
	
	
//	override fun getView(positionArg : Int, viewOld : View?, parent : ViewGroup) : View {
//		var position = positionArg
//		val header = this.header
//		if(header != null) {
//			if(position == 0) return header.viewRoot
//			-- position
//		}
//
//		val o = if(position >= 0 && position < list.size) list[position] else null
//
//		val holder : ItemViewHolder
//		val view : View
//		if(viewOld == null) {
//
//		} else {
//			view = viewOld
//			holder = view.tag as ItemViewHolder
//		}
//		holder.bind(o)
//		return view
//	}


}
	