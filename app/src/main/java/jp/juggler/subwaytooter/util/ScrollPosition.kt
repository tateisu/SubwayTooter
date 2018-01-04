package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.view.MyListView

class ScrollPosition{

	var pos : Int = 0
	val top : Int
	
	constructor(pos : Int, top : Int) {
		this.pos = pos
		this.top = top
	}
	
	constructor(listView : MyListView) {
		if(listView.childCount == 0) {
			top = 0
			pos = top
		} else {
			pos = listView.firstVisiblePosition
			top = listView.getChildAt(0).top
		}
	}
	
	fun restore(listView : MyListView) {
		if(0 <= pos && pos < listView.adapter.count) {
			listView.setSelectionFromTop(pos, top)
		}
	}
}
