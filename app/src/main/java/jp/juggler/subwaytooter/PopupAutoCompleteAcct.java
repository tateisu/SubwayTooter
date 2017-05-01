package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.ArrayList;

class PopupAutoCompleteAcct {
	final Activity activity;
	private final EditText etContent;
	private final PopupWindow acct_popup;
	private final LinearLayout llItems;
	private final View formRoot;
	private final float density;
	private final int popup_width;

	private int popup_rows;
	
	void dismiss(){
		acct_popup.dismiss();
	}
	
	boolean isShowing(){
		return acct_popup.isShowing();
	}
	
	PopupAutoCompleteAcct( Activity activity, EditText etContent, View formRoot ){
		this.activity = activity;
		this.etContent = etContent;
		this.formRoot = formRoot;
		this.density = activity.getResources().getDisplayMetrics().density;
		
		popup_width = (int)(0.5f +240f * density );
		
		@SuppressLint("InflateParams") View viewRoot =
			activity.getLayoutInflater().inflate( R.layout.acct_complete_popup, null, false );
		llItems = (LinearLayout) viewRoot.findViewById( R.id.llItems );
		//
		acct_popup = new PopupWindow( activity );
		acct_popup.setBackgroundDrawable( ContextCompat.getDrawable( activity, R.drawable.acct_popup_bg ) );
		acct_popup.setContentView( viewRoot );
		acct_popup.setTouchable( true );
	}
	
	void setList( ArrayList< String > acct_list, final int sel_start, final int sel_end ){
		
		llItems.removeAllViews();
		
		popup_rows = 0;
		
		{
			CheckedTextView v = (CheckedTextView) activity.getLayoutInflater()
				.inflate( R.layout.lv_spinner_dropdown, llItems, false );
			v.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			v.setText( R.string.close );
			v.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					acct_popup.dismiss();
				}
			} );
			llItems.addView( v );
			++ popup_rows;
		}
		
		for( int i = 0 ; ; ++ i ){
			if( i >= acct_list.size() ) break;
			final String acct = acct_list.get( i );
			CheckedTextView v = (CheckedTextView) activity.getLayoutInflater()
				.inflate( R.layout.lv_spinner_dropdown, llItems, false );
			v.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			v.setText( acct );
			v.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					String s = etContent.getText().toString();
					s = s.substring( 0, sel_start ) + acct + " "
						+ ( sel_end >= s.length() ? "" : s.substring( sel_end ) );
					etContent.setText( s );
					etContent.setSelection( sel_start + acct.length() + 1 );
					acct_popup.dismiss();
				}
			} );
			llItems.addView( v );
			++ popup_rows;
		}
		
		updatePosition();
	}
	
	void updatePosition(){
		if( acct_popup == null ) return;
		
		int[] location = new int[ 2 ];
		etContent.getLocationOnScreen( location );
		int text_top = location[ 1 ];
		
		formRoot.getLocationOnScreen( location );
		int form_top = location[ 1 ];
		int form_bottom = location[ 1 ] + formRoot.getHeight();
		
		Layout layout = etContent.getLayout();
		
		int popup_top = text_top
			+ etContent.getTotalPaddingTop()
			+ layout.getLineBottom( layout.getLineCount() - 1 )
			- etContent.getScrollY();
		
		if( popup_top < form_top ) popup_top = form_top;
		
		int popup_height = form_bottom - popup_top;
		
		int min = (int) ( 0.5f + 48f * 2f * density );
		if( popup_height < min ) popup_height = min;

		int max = (int) ( 0.5f + 48f * popup_rows * density );
		if( popup_height > max ) popup_height = max;
		
		if( acct_popup.isShowing() ){
			acct_popup.update( 0, popup_top, popup_width, popup_height );
		}else{
			acct_popup.setWidth( popup_width );
			acct_popup.setHeight( popup_height );
			acct_popup.showAtLocation(
				etContent
				, Gravity.CENTER_HORIZONTAL | Gravity.TOP
				, 0
				, popup_top
			);
		}
	}
}
