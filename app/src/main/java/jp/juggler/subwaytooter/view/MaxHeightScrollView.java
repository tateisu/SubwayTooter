package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ScrollView;

import jp.juggler.subwaytooter.R;

public class MaxHeightScrollView extends ScrollView {
	public MaxHeightScrollView( Context context ){
		super( context );
	}
	
	public MaxHeightScrollView( Context context, AttributeSet attrs ){
		super( context, attrs );
		TypedArray a = context.obtainStyledAttributes(attrs ,   R.styleable.MaxHeightScrollView);
		parseAttr(a);
		a.recycle();
		
	}
	
	public MaxHeightScrollView( Context context, AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
		
		TypedArray a = context.obtainStyledAttributes(attrs
			, R.styleable.MaxHeightScrollView, defStyleAttr, 0);
		parseAttr(a);
		a.recycle();
	}
	
	int maxHeight;

	void parseAttr( TypedArray a){
		maxHeight = a.getDimensionPixelSize( R.styleable.MaxHeightScrollView_maxHeight, 0);
	}
	
	@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (maxHeight > 0){
			int hSize = MeasureSpec.getSize(heightMeasureSpec);
			int hMode = MeasureSpec.getMode(heightMeasureSpec);
			
			switch (hMode){
			case MeasureSpec.AT_MOST:
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), MeasureSpec.AT_MOST);
				break;
			case MeasureSpec.UNSPECIFIED:
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
				break;
			case MeasureSpec.EXACTLY:
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), MeasureSpec.EXACTLY);
				break;
			}
		}
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
}
