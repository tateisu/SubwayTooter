package jp.juggler.subwaytooter.util

import android.support.v7.widget.RecyclerView
import jp.juggler.subwaytooter.ColumnViewHolder

class ScrollPosition{

	var pos : Int = 0
	val top : Int
	
	constructor(pos : Int, top : Int) {
		this.pos = pos
		this.top = top
	}
	
//	constructor(listView : MyListView) {
//		if(listView.childCount == 0) {
//			top = 0
//			pos = top
//		} else {
//			pos = listView.firstVisiblePosition
//			top = listView.getChildAt(0).top
//		}
//	}
//
//	fun restore(listView : MyListView) {
//		if(0 <= pos && pos < listView.adapter.count) {
//			listView.setSelectionFromTop(pos, top)
//		}
//	}
	
	constructor(holder:ColumnViewHolder) {
		val findPosition = holder.listLayoutManager.findFirstVisibleItemPosition()
		if( findPosition == RecyclerView.NO_POSITION){
			top = 0
			pos = top
		}else{
			pos = findPosition
			val firstItemView = holder.listLayoutManager.findViewByPosition(findPosition)
			top = firstItemView?.top ?: 0
		}
	}
	
	fun restore(holder:ColumnViewHolder) {
		if(0 <= pos && pos < holder.listView.adapter.itemCount) {
			holder.listLayoutManager.scrollToPositionWithOffset(pos,top)
		}
	}
}
