package jp.juggler.subwaytooter.actmain

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getColumnName
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.onPageCreate
import jp.juggler.subwaytooter.columnviewholder.onPageDestroy
import jp.juggler.util.log.Benchmark
import jp.juggler.util.log.LogCategory
import java.util.*

internal class ColumnPagerAdapter(private val activity: ActMain) : PagerAdapter() {

    companion object {
        val log = LogCategory("ColumnPagerAdapter")
    }

    private val appState = activity.appState

    private val holderList = SparseArray<ColumnViewHolder>()

    override fun getCount(): Int = appState.columnCount

    fun getColumn(idx: Int): Column? = appState.column(idx)

    fun getColumnViewHolder(idx: Int): ColumnViewHolder? {
        return holderList.get(idx)
    }

    override fun getPageTitle(page_idx: Int): CharSequence? {
        return getColumn(page_idx)?.getColumnName(false)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === obj
    }

    private val viewCache = LinkedList<ColumnViewHolder>()

    override fun instantiateItem(container: ViewGroup, page_idx: Int): Any {
        val holder: ColumnViewHolder
        if (viewCache.isNotEmpty()) {
            val b = Benchmark(log, "instantiateItem: cached")
            holder = viewCache.removeFirst()
            b.report()
        } else {
            val b = Benchmark(log, "instantiateItem: new")
            holder = ColumnViewHolder(activity, container)
            b.report()
        }
        container.addView(holder.viewRoot, 0)
        holderList.put(page_idx, holder)
        holder.onPageCreate(appState.column(page_idx)!!, page_idx, appState.columnCount)
        return holder.viewRoot
    }

    override fun destroyItem(container: ViewGroup, page_idx: Int, obj: Any) {
        val holder = holderList.get(page_idx)
        holderList.remove(page_idx)
        if (holder != null) {
            holder.onPageDestroy(page_idx)
            container.removeView(holder.viewRoot)
            viewCache.addLast(holder)
        }
    }
}
