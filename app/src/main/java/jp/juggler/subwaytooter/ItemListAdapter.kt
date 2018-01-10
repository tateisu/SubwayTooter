package jp.juggler.subwaytooter

import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter

import jp.juggler.subwaytooter.view.MyListView

internal class ItemListAdapter(private val activity : ActMain, private val column : Column, private val bSimpleList : Boolean)
	: BaseAdapter()
	, AdapterView.OnItemClickListener {
	private val list : List<Any>
	
	var header : HeaderViewHolderBase? = null
	
	init {
		this.list = column.list_data
	}
	
	
	override fun getCount() : Int {
		return (if(header != null) 1 else 0) + column.list_data.size
	}
	
	override fun getViewTypeCount() : Int {
		return if(header != null) 2 else 1
	}
	
	override fun getItemViewType(position : Int) : Int {
		if(header != null) {
			if(position == 0) return 1
		}
		return 0
	}
	
	override fun getItem(positionArg : Int) : Any? {
		var position = positionArg
		if(header != null) {
			if(position == 0) return header
			-- position
		}
		return if(position >= 0 && position < column.list_data.size) list[position] else null
	}
	
	override fun getItemId(position : Int) : Long {
		return 0
	}
	
	override fun getView(positionArg : Int, viewOld : View?, parent : ViewGroup) : View {
		var position = positionArg
		val header = this.header
		if(header != null) {
			if(position == 0) return header.viewRoot
			-- position
		}
		
		val o = if(position >= 0 && position < list.size) list[position] else null
		
		val holder : ItemViewHolder
		val view : View
		if(viewOld == null) {
			view = activity.layoutInflater.inflate(if(bSimpleList) R.layout.lv_status_simple else R.layout.lv_status, parent, false)
			holder = ItemViewHolder(activity, column, this, view, bSimpleList)
			view.tag = holder
		} else {
			view = viewOld
			holder = view.tag as ItemViewHolder
		}
		holder.bind(o)
		return view
	}
	
	override fun onItemClick(parent : AdapterView<*>, view : View, position : Int, id : Long) {
		if(bSimpleList) {
			(view.tag as? ItemViewHolder)?.onItemClick(parent as MyListView, view)
		}
	}
}
	