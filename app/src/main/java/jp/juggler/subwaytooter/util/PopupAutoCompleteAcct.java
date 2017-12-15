package jp.juggler.subwaytooter.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.text.Spannable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.ArrayList;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.Styler;
import jp.juggler.subwaytooter.view.MyEditText;

@SuppressWarnings("WeakerAccess")
class PopupAutoCompleteAcct {
	final Activity activity;
	final EditText etContent;
	final PopupWindow acct_popup;
	final LinearLayout llItems;
	final View formRoot;
	final float density;
	final int popup_width;
	final Handler handler;
	final boolean bMainScreen;

	int popup_rows;
	
	void dismiss(){
		acct_popup.dismiss();
	}
	
	boolean isShowing(){
		return acct_popup.isShowing();
	}
	
	PopupAutoCompleteAcct( Activity activity, EditText etContent, View formRoot, boolean bMainScreen ){
		this.activity = activity;
		this.etContent = etContent;
		this.formRoot = formRoot;
		this.bMainScreen = bMainScreen;
		this.density = activity.getResources().getDisplayMetrics().density;
		this.handler = new Handler( activity.getMainLooper() );
		
		popup_width = (int) ( 0.5f + 240f * density );
		
		@SuppressLint("InflateParams") View viewRoot =
			activity.getLayoutInflater().inflate( R.layout.acct_complete_popup, null, false );
		llItems = viewRoot.findViewById( R.id.llItems );
		//
		acct_popup = new PopupWindow( activity );
		acct_popup.setBackgroundDrawable( ContextCompat.getDrawable( activity, R.drawable.acct_popup_bg ) );
		acct_popup.setContentView( viewRoot );
		acct_popup.setTouchable( true );
	}
	
	void setList(
		final MyEditText et
		, final int sel_start
		, final int sel_end
		, @Nullable ArrayList< CharSequence > acct_list
		, @Nullable String picker_caption
	    , @Nullable final Runnable picker_callback
	){
		
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
		
		if( picker_caption != null && picker_callback != null ){
			CheckedTextView v = (CheckedTextView) activity.getLayoutInflater()
				.inflate( R.layout.lv_spinner_dropdown, llItems, false );
			v.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			v.setText( picker_caption );
			v.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					acct_popup.dismiss();
					picker_callback.run();
				}
			} );
			llItems.addView( v );
			++ popup_rows;
		}
		
		
		if( acct_list != null ){
			for( int i = 0 ; ; ++ i ){
				if( i >= acct_list.size() ) break;
				final CharSequence acct = acct_list.get( i );
				CheckedTextView v = (CheckedTextView) activity.getLayoutInflater()
					.inflate( R.layout.lv_spinner_dropdown, llItems, false );
				v.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
				v.setText( acct );
				if( acct instanceof Spannable ){
					new NetworkEmojiInvalidator( handler, v ).register( (Spannable) acct );
				}
				v.setOnClickListener( new View.OnClickListener() {
					@Override public void onClick( View v ){
						String s = et.getText().toString();
						CharSequence svInsert = ( acct.charAt( 0 ) == ' ' ? acct.subSequence( 2, acct.length() ) : acct );
						s = s.substring( 0, sel_start ) + svInsert + " " + ( sel_end >= s.length() ? "" : s.substring( sel_end ) );
						et.setText( s );
						et.setSelection( sel_start + svInsert.length() + 1 );
						acct_popup.dismiss();
					}
				} );
				
				llItems.addView( v );
				++ popup_rows;
			}
		}
		
		updatePosition();
	}
	
	void updatePosition(){
		if( acct_popup == null ) return;
		
		int[] location = new int[ 2 ];
		etContent.getLocationOnScreen( location );
		int text_top = location[ 1 ];
		
		int popup_top;
		int popup_height;
		
		if( bMainScreen ){
			int popup_bottom = text_top + etContent.getTotalPaddingTop() - etContent.getScrollY();
			int max = popup_bottom - (int) ( 0.5f + 48f * 1f * density );
			int min = (int) ( 0.5f + 48f * 2f * density );
			popup_height = (int) ( 0.5f + 48f * popup_rows * density );
			if( popup_height < min ) popup_height = min;
			if( popup_height > max ) popup_height = max;
			popup_top = popup_bottom - popup_height;
			
		}else{
			formRoot.getLocationOnScreen( location );
			int form_top = location[ 1 ];
			int form_bottom = location[ 1 ] + formRoot.getHeight();
			
			Layout layout = etContent.getLayout();
			
			popup_top = text_top
				+ etContent.getTotalPaddingTop()
				+ layout.getLineBottom( layout.getLineCount() - 1 )
				- etContent.getScrollY();
			
			if( popup_top < form_top ) popup_top = form_top;
			
			popup_height = form_bottom - popup_top;
			
			int min = (int) ( 0.5f + 48f * 2f * density );
			int max = (int) ( 0.5f + 48f * popup_rows * density );
			
			if( popup_height < min ) popup_height = min;
			if( popup_height > max ) popup_height = max;
		}
		
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
