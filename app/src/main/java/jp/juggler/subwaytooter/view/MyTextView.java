package jp.juggler.subwaytooter.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class MyTextView extends AppCompatTextView {
	public MyTextView( Context context ){
		super( context );
	}
	
	public MyTextView( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
	}
	
	public MyTextView( Context context, @Nullable AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}

	boolean linkHit;
	
	@SuppressLint("ClickableViewAccessibility")
	@Override public boolean onTouchEvent( MotionEvent event) {
		// リンクをタップした時以外はタッチイベントを処理しない
		linkHit = false;
		super.onTouchEvent(event);
		return linkHit;
	}
	
	public interface SizeChangedCallback {
		void onSizeChanged(int w,int h);
	}
	
	SizeChangedCallback size_callback;
	
	public void setSizeChangedCallback(SizeChangedCallback cb){
		size_callback = cb;
	}
	
	@Override protected void onSizeChanged( int w, int h, int old_w, int old_h ){
		super.onSizeChanged( w, h, old_w, old_h );
		if( w>0 && h > 0 && size_callback != null ) size_callback.onSizeChanged( w,h );
	}
}
