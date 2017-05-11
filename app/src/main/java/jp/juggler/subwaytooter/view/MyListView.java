package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.os.SystemClock;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ListView;


public class MyListView extends ListView {
	public MyListView( Context context ){
		super( context );
	}
	
	public MyListView( Context context, AttributeSet attrs ){
		super( context, attrs );
	}
	
	public MyListView( Context context, AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}
	
	public MyListView( Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes ){
		super( context, attrs, defStyleAttr, defStyleRes );
	}
	
	public long last_popup_close = 0L;
	static final String TAG="SubwayTooter";
	
//	@Override public boolean onInterceptTouchEvent( MotionEvent ev ){
//		boolean rv = super.onInterceptTouchEvent( ev );
//		long now = SystemClock.elapsedRealtime();
//		if( now - last_popup_close < 100L ){
//			Log.d(TAG,"MyListView onInterceptTouchEvent action="
//				+ MotionEventCompat.getActionMasked( ev )
//				+" rv="+rv
//			);
//		}
//		return rv;
//	}
//
	@Override public boolean onTouchEvent( MotionEvent ev ){

		long now = SystemClock.elapsedRealtime();
		if( now - last_popup_close < 30L ){
			int action = MotionEventCompat.getActionMasked( ev );

			if( action == MotionEvent.ACTION_DOWN ){
				return false;
			}
			boolean rv = super.onTouchEvent( ev );
			Log.d(TAG,"MyListView onTouchEvent action=" + action +" rv="+rv );
			return rv;
		}

		return super.onTouchEvent( ev );
	}
}
