package jp.juggler.subwaytooter.view;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.Touch;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

public class MyLinkMovementMethod extends LinkMovementMethod {
	static MyLinkMovementMethod sInstance;
	
	public static MyLinkMovementMethod getInstance(){
		if( sInstance == null )
			sInstance = new MyLinkMovementMethod();
		
		return sInstance;
	}
	
	@Override
	public boolean onTouchEvent( TextView widget, Spannable buffer, MotionEvent event ){
		
		int action = event.getAction();
		
		if( action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN ){
			return Touch.onTouchEvent( widget, buffer, event );
		}
		
		int x = (int) event.getX();
		int y = (int) event.getY();
		
		x -= widget.getTotalPaddingLeft();
		y -= widget.getTotalPaddingTop();
		
		x += widget.getScrollX();
		y += widget.getScrollY();
		
		Layout layout = widget.getLayout();
		
		int line = layout.getLineForVertical( y );
		if( 0 <= line && line < layout.getLineCount() ){
			
			float line_left = layout.getLineLeft( line );
			float line_right = layout.getLineRight( line );
			if( line_left <= x && x <= line_right ){
				
				int offset = layout.getOffsetForHorizontal( line, x );
				
				ClickableSpan[] link = buffer.getSpans( offset, offset, ClickableSpan.class );
				
				if( link != null && link.length > 0 ){
					//noinspection StatementWithEmptyBody
					if( action == MotionEvent.ACTION_UP ){
						link[ 0 ].onClick( widget );
					}
					if( widget instanceof MyTextView ){
						( (MyTextView) widget ).linkHit = true;
					}
					return true;
				}
			}
		}

		Touch.onTouchEvent( widget, buffer, event );
		return false;
	}
}
