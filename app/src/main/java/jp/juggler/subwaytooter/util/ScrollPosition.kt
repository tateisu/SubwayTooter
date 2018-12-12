package jp.juggler.subwaytooter.util

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import jp.juggler.subwaytooter.ColumnViewHolder

class ScrollPosition {
	
	var adapterIndex : Int
	
	// 先頭要素のピクセルオフセット。
	// scrollToPositionWithOffset 用の値である。( topMarginを考慮するため、view.top とは一致しない )
	val offset : Int
	
	val isHead : Boolean
		get() = adapterIndex == 0 && offset >= 0
	
	override fun toString() : String ="ScrlPos($adapterIndex,$offset)"
	
	constructor(adapterIndex : Int =0) {
		this.adapterIndex = adapterIndex
		this.offset = 0
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
			offset = (firstItemView?.top ?: 0) - (((firstItemView?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)?:0)
		}
	}
	
	fun restore(holder : ColumnViewHolder) {
		val adapter = holder.listView.adapter ?: return
		if(adapterIndex in 0 until adapter.itemCount) {
			holder.listLayoutManager.scrollToPositionWithOffset(adapterIndex, offset)
		}
	}
}
