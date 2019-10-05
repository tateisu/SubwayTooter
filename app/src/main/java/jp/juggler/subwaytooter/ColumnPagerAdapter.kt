package jp.juggler.subwaytooter

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import jp.juggler.subwaytooter.util.Benchmark
import jp.juggler.util.LogCategory
import java.util.*

internal class ColumnPagerAdapter(private val activity : ActMain) : androidx.viewpager.widget.PagerAdapter() {
	
	companion object {
		val log = LogCategory("ColumnPagerAdapter")
	}
	
	private val column_list : ArrayList<Column> = activity.app_state.column_list
	private val holder_list = SparseArray<ColumnViewHolder>()
	
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
		val holder : ColumnViewHolder
		if(viewCache.isNotEmpty()) {
			val b = Benchmark(log, "instantiateItem: cached")
			holder = viewCache.removeFirst()
			b.report()
		} else {
			val b = Benchmark(log, "instantiateItem: new")
			holder = ColumnViewHolder(activity,container)
			b.report()
		}
		container.addView(holder.viewRoot, 0)
		holder_list.put(page_idx, holder)
		holder.onPageCreate(column_list[page_idx], page_idx, column_list.size)
		return holder.viewRoot
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