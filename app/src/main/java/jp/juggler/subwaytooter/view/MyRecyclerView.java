package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class MyRecyclerView extends RecyclerView {
	
	public MyRecyclerView( Context context ){
		super( context );
		init(context);
	}
	
	
	public MyRecyclerView( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
		init(context);
	}
	
	public MyRecyclerView( Context context, @Nullable AttributeSet attrs, int defStyle ){
		super( context, attrs, defStyle );
		init(context);
		
	}
	
	private void init( Context context ){
		final ViewConfiguration vc = ViewConfiguration.get(context);
		mTouchSlop = vc.getScaledTouchSlop();
	}

	
	boolean mForbidStartDragging;
	int mScrollPointerId;
	int mInitialTouchX;
	int mInitialTouchY;
	int mTouchSlop;

	@Override
	public boolean onInterceptTouchEvent( MotionEvent e ){
		final int action = MotionEventCompat.getActionMasked( e );
		// final int actionIndex = MotionEventCompat.getActionIndex( e );
		
		switch( action ){
		
		case MotionEvent.ACTION_DOWN:
			mForbidStartDragging = false;
			mScrollPointerId = e.getPointerId(0);
			mInitialTouchX = (int) (e.getX() + 0.5f);
			mInitialTouchY = (int) (e.getY() + 0.5f);
			
			break;
		
		case MotionEvent.ACTION_MOVE:
			final int index = e.findPointerIndex( mScrollPointerId );
			if( index >= 0 ){
				if( mForbidStartDragging ) return false;
				
				final int x = (int) ( e.getX( index ) + 0.5f );
				final int y = (int) ( e.getY( index ) + 0.5f );
				boolean canScrollHorizontally = getLayoutManager().canScrollHorizontally();
				boolean canScrollVertically = getLayoutManager().canScrollVertically();
				
				final int dx = x - mInitialTouchX;
				final int dy = y - mInitialTouchY;
				
				if( ( ! canScrollVertically && Math.abs( dy ) > mTouchSlop )
					|| ( ! canScrollHorizontally && Math.abs( dx ) > mTouchSlop )
					){
					mForbidStartDragging = true;
					return false;
				}
			}
			break;
		}
		return super.onInterceptTouchEvent( e );
	}
}
