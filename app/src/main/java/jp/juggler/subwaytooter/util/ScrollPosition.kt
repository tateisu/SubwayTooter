package jp.juggler.subwaytooter.util

import android.support.v7.widget.RecyclerView
import jp.juggler.subwaytooter.ColumnViewHolder

class ScrollPosition {
	
	var adapterIndex : Int
	val offset : Int
	
	constructor(adapterIndex : Int, top : Int) {
		this.adapterIndex = adapterIndex
		this.offset = top
	}
	
	constructor(holder : ColumnViewHolder) {
		val layoutManager = holder.listLayoutManager
		val findPosition = layoutManager.findFirstVisibleItemPosition()
		if(findPosition == RecyclerView.NO_POSITION) {
			adapterIndex = 0
			offset = 0
		} else {
			adapterIndex = findPosition
			val firstItemView = layoutManager.findViewByPosition(findPosition)
			offset = firstItemView?.top ?: 0
		}
	}
	
	fun restore(holder : ColumnViewHolder) {
		val adapter = holder.listView.adapter ?: return
		if(adapterIndex in 0 until adapter.itemCount) {
			holder.listLayoutManager.scrollToPositionWithOffset(adapterIndex, offset)
		}
	}
}
