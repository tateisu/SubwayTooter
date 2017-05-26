package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.Styler;

public class ColumnStripLinearLayout extends LinearLayout {
	public ColumnStripLinearLayout( Context context ){
		super( context );
		init();
	}
	
	public ColumnStripLinearLayout( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
		init();
	}
	
	public ColumnStripLinearLayout( Context context, @Nullable AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
		init();
	}
	
	final Paint paint = new Paint();
	final Rect rect = new Rect();
	int h ;
	void init(){
		h = (int)(0.5f + 2f * getResources().getDisplayMetrics().density );
	}
	
	int first;
	int last;
	float slide_ratio;
	
	public void setColumnRange(int first,int last,float slide_ratio){
		if( this.first == first && this.last == last && this.slide_ratio == slide_ratio ) return;
		this.first = first;
		this.last = last;
		this.slide_ratio = slide_ratio;
		invalidate();
	}
	
	int color;
	public void setColor( int color ){
		if( color == 0 ) color =  Styler.getAttributeColor(getContext(), R.attr.colorAccent);
		if( this.color == color ) return;
		this.color = color;
		invalidate();
	}
	
	
	@Override protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		
		if( first < 0 || last >= getChildCount() ) return;
		
		View child = getChildAt( first );
		rect.left = child.getLeft();
		child = getChildAt( last );
		rect.right = child.getRight();
		
		if( slide_ratio != 0f){
			child = getChildAt( first );
			int w = child.getWidth();
			int slide = (int)(0.5f + slide_ratio * w);
			rect.left += slide;
			rect.right += slide;
		}
		
		rect.top = 0;
		rect.bottom = h;
		paint.setColor( color );
		canvas.drawRect( rect,paint );
	}
	
}
