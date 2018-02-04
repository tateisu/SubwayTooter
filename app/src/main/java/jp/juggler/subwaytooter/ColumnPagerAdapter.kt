package jp.juggler.subwaytooter

import android.support.v4.view.PagerAdapter
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import java.util.ArrayList

internal class ColumnPagerAdapter(private val activity : ActMain) : PagerAdapter() {
	private val inflater : LayoutInflater
	private val column_list : ArrayList<Column>
	private val holder_list = SparseArray<ColumnViewHolder>()
	
	init {
		this.inflater = activity.layoutInflater
		this.column_list = activity.app_state.column_list
	}
	
	override fun getCount() : Int {
		return column_list.size
	}
	
	fun getColumn(idx : Int) : Column? {
		return if(idx >= 0 && idx < column_list.size) column_list[idx] else null
	}
	
	fun getColumnViewHolder(idx : Int) : ColumnViewHolder? {
		return holder_list.get(idx)
	}
	
	
	override fun getPageTitle(page_idx : Int) : CharSequence? {
		return getColumn(page_idx) ?.getColumnName(false)
	}
	
	override fun isViewFromObject(view : View, obj : Any) : Boolean {
		return view === obj
	}
	
	override fun instantiateItem(container : ViewGroup, page_idx : Int) : Any {
		val root = inflater.inflate(R.layout.page_column, container, false)
		container.addView(root, 0)
		
		val column = column_list[page_idx]
		val holder = ColumnViewHolder(activity, root)
		//
		holder_list.put(page_idx, holder)
		//
		holder.onPageCreate(column, page_idx, column_list.size)
		
		return root
	}
	
	override fun destroyItem(container : ViewGroup, page_idx : Int, obj : Any) {
		if( obj is View ){
			container.removeView(obj)
		}
		//
		val holder = holder_list.get(page_idx)
		holder_list.remove(page_idx)
		holder?.onPageDestroy(page_idx)
	}
}