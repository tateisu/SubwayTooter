package jp.juggler.subwaytooter.util;

public class ScrollPosition {
	public int pos;
	public int top;
	
	public ScrollPosition( int pos,int top ){
		this.pos = pos;
		this.top = top;
	}
	
	public ScrollPosition( MyListView listView ){
		if( listView.getChildCount() == 0 ){
			pos = top = 0;
		}else{
			pos = listView.getFirstVisiblePosition();
			top = listView.getChildAt( 0 ).getTop();
		}
	}
	
	public void restore( MyListView listView ){
		if( 0 <= pos && pos < listView.getAdapter().getCount() ){
			listView.setSelectionFromTop( pos, top );
		}
	}
}
