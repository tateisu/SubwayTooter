package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.MotionEvent;

import jp.juggler.subwaytooter.util.LogCategory;

public class MyEditText extends AppCompatEditText {
	private static final LogCategory log = new LogCategory( "MyEditText" );
	
	public MyEditText( Context context ){
		super( context );
	}
	
	public MyEditText( Context context, AttributeSet attrs ){
		super( context, attrs );
	}
	
	public MyEditText( Context context, AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}

	////////////////////////////////////////////////////
	// 選択範囲変更イベントをコールバックに渡す
	
	public interface OnSelectionChangeListener {
		void onSelectionChanged( int selStart, int selEnd );
	}
	
	OnSelectionChangeListener mOnSelectionChangeListener;
	
	public void setOnSelectionChangeListener( OnSelectionChangeListener listener ){
		mOnSelectionChangeListener = listener;
	}
	
	@Override
	protected void onSelectionChanged( int selStart, int selEnd ){
		super.onSelectionChanged( selStart, selEnd );
		if( mOnSelectionChangeListener != null ){
			mOnSelectionChangeListener.onSelectionChanged( selStart, selEnd );
		}
	}
	
	////////////////////////////////////////////////////
	// Android 6.0 でのクラッシュ対応

	@Override public boolean onTouchEvent( MotionEvent event ){
		try{
			return super.onTouchEvent( event );
		}catch( Throwable ex ){
			log.trace( ex );
			return false;
			//		java.lang.NullPointerException:
			//		at android.widget.Editor$SelectionModifierCursorController.onTouchEvent (Editor.java:4889)
			//		at android.widget.Editor.onTouchEvent (Editor.java:1223)
			//		at android.widget.TextView.onTouchEvent (TextView.java:8304)
			//		at android.view.View.dispatchTouchEvent (View.java:9303)
		}
	}
}
