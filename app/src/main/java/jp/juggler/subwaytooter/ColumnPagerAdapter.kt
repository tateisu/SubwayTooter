package jp.juggler.subwaytooter

import android.support.v4.view.PagerAdapter
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.juggler.subwaytooter.util.Benchmark
import jp.juggler.subwaytooter.util.LogCategory
import java.util.*

internal class ColumnPagerAdapter(private val activity : ActMain) : PagerAdapter() {
	
	companion object {
		val log = LogCategory("ColumnPagerAdapter")
	}
	
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
		return getColumn(page_idx)?.getColumnName(false)
	}
	
	override fun isViewFromObject(view : View, obj : Any) : Boolean {
		return view === obj
	}
	
	private val viewCache = LinkedList<ColumnViewHolder>()
	
	override fun instantiateItem(container : ViewGroup, page_idx : Int) : Any {
		val viewRoot : View
		val holder : ColumnViewHolder
		if(viewCache.isNotEmpty()) {
			val b = Benchmark(log, "instantiateItem: cached")
			holder = viewCache.removeFirst()
			viewRoot = holder.viewRoot
			b.report()
		} else {
			val b = Benchmark(log, "instantiateItem: new")
			viewRoot = inflater.inflate(R.layout.page_column, container, false)
			holder = ColumnViewHolder(activity, viewRoot)
			b.report()
		}
		container.addView(viewRoot, 0)
		holder_list.put(page_idx, holder)
		holder.onPageCreate(column_list[page_idx], page_idx, column_list.size)
		return viewRoot
	}
	
	override fun destroyItem(container : ViewGroup, page_idx : Int, obj : Any) {
		val holder = holder_list.get(page_idx)
		holder_list.remove(page_idx)
		if(holder != null) {
			holder.onPageDestroy(page_idx)
			container.removeView(holder.viewRoot)
			viewCache.addLast(holder)
		}
	}
}