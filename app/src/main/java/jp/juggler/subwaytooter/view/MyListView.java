package jp.juggler.subwaytooter.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

import jp.juggler.subwaytooter.util.LogCategory;

public class MyListView extends ListView {
	private static final LogCategory log = new LogCategory( "MyListView" );
	
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
	
	@SuppressLint("ClickableViewAccessibility")
	@Override public boolean onTouchEvent( MotionEvent ev ){
		
		// ポップアップを閉じた時にクリックでリストを触ったことになってしまう不具合の回避
		long now = SystemClock.elapsedRealtime();
		if( now - last_popup_close < 30L ){
			int action = ev.getAction();
			if( action == MotionEvent.ACTION_DOWN ){
				// ポップアップを閉じた直後はタッチダウンを無視する
				return false;
			}
			
			boolean rv = super.onTouchEvent( ev );
			log.d( "onTouchEvent action=%s, rv=%s", action, rv );
			return rv;
		}
		
		return super.onTouchEvent( ev );
	}
	
	@Override protected void layoutChildren(){
		try{
			super.layoutChildren();
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
}
