package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * Created by tateisu on 2017/05/01.
 */

public class MyEditText extends AppCompatEditText {
	public MyEditText( Context context ){
		super( context );
	}
	
	public MyEditText( Context context, AttributeSet attrs ){
		super( context, attrs );
	}
	
	public MyEditText( Context context, AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}
	
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
}
