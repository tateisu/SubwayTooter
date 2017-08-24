package jp.juggler.subwaytooter.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class EnqueteTimerView extends View {
	public EnqueteTimerView( Context context ){
		super( context );
	}
	
	public EnqueteTimerView( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
	}
	
	public EnqueteTimerView( Context context, @Nullable AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}
	
	@TargetApi(21)
	public EnqueteTimerView( Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes ){
		super( context, attrs, defStyleAttr, defStyleRes );
	}
	
	long time_start;
	long duration;
	Paint paint = new Paint();
	
	public void setParams( long time_start, long duration ){
		this.time_start = time_start;
		this.duration = duration;
		invalidate();
	}
	
	static final int bg_color = 0x40808080;
	static final int fg_color = 0xffffffff;

	@Override protected void onDraw( Canvas canvas ){
		super.onDraw( canvas );
		
		int view_w = getWidth();
		int view_h = getHeight();
		
		paint.setColor( bg_color );
		canvas.drawRect( 0,0,view_w,view_h,paint );
		
		long progress = System.currentTimeMillis() - time_start;
		float ratio = duration <= 0L ? 1f : progress <= 0L ? 0f : progress >= duration ? 1f : progress / (float)duration;
		
		paint.setColor( fg_color );
		canvas.drawRect( 0,0,view_w * ratio,view_h,paint );
		
		if( ratio < 1f) postInvalidateOnAnimation();
	}
}
