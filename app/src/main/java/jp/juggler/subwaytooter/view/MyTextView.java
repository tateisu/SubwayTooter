package jp.juggler.subwaytooter.view;

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
	
	@Override public boolean onTouchEvent(MotionEvent event) {
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
	
	@Override protected void onSizeChanged( int w, int h, int oldw, int oldh ){
		super.onSizeChanged( w, h, oldw, oldh );
		if( w>0 && h > 0 && size_callback != null ) size_callback.onSizeChanged( w,h );
	}
}
