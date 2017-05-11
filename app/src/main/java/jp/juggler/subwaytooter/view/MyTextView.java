package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Created by tateisu on 2017/04/29.
 */

public class MyTextView extends TextView {
	public MyTextView( Context context ){
		super( context );
	}
	
	public MyTextView( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
	}
	
	public MyTextView( Context context, @Nullable AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}
	
	public MyTextView( Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes ){
		super( context, attrs, defStyleAttr, defStyleRes );
	}
	
	boolean linkHit;
	
	
	@Override public boolean onTouchEvent(MotionEvent event) {
		linkHit = false;
		super.onTouchEvent(event);
		return linkHit;
	}
}
