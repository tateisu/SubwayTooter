package jp.juggler.subwaytooter

import android.support.v7.widget.RecyclerView
import android.view.View

import jp.juggler.util.LogCategory

internal class TabletColumnViewHolder(
	activity : ActMain,
	viewRoot : View
) : RecyclerView.ViewHolder(viewRoot) {
	
	companion object {
		val log = LogCategory("TabletColumnViewHolder")
	}
	
	val columnViewHolder : ColumnViewHolder
	
	private var pageIndex = - 1
	
	init {
		columnViewHolder = ColumnViewHolder(activity, viewRoot)
		// viewRoot.findViewById<View>(R.id.vTabletDivider).visibility = View.VISIBLE
	}
	
	fun bind(column : Column, pageIndex : Int, column_count : Int) {
		log.d("bind. %d => %d ", this.pageIndex, pageIndex)
		
		columnViewHolder.onPageDestroy(this.pageIndex)
		
		this.pageIndex = pageIndex
		
		columnViewHolder.onPageCreate(column, pageIndex, column_count)
		
		if(! column.bFirstInitialized) {
			column.startLoading()
		}
	}
	
	fun onViewRecycled() {
		log.d("onViewRecycled %d", pageIndex)
		columnViewHolder.onPageDestroy(pageIndex)
	}
}
