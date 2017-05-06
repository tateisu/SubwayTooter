package jp.juggler.subwaytooter.util;

public class ScrollPosition {
	public int pos;
	public int top;
	
	public ScrollPosition( MyListView listView ){
		pos = listView.getFirstVisiblePosition();
		top = listView.getChildAt( 0 ).getTop();
	}
	
	public void restore( MyListView listView ){
		if( 0 <= pos && pos < listView.getAdapter().getCount() ){
			listView.setSelectionFromTop( pos, top );
		}
	}
}
