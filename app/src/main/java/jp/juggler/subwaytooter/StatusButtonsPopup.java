package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.view.MyListView;

class StatusButtonsPopup {
	
	private final ActMain activity;
	private final View viewRoot;
	private final StatusButtons buttons_for_status;
	
	@SuppressLint("InflateParams")
	StatusButtonsPopup( ActMain activity, Column column, boolean bSimpleList ){
		this.activity = activity;
		this.viewRoot = activity.getLayoutInflater().inflate( R.layout.list_item_popup, null, false );
		this.buttons_for_status = new StatusButtons( activity, column, viewRoot, bSimpleList );
	}
	
	private PopupWindow window;
	
	void dismiss(){
		if( window != null && window.isShowing() ){
			window.dismiss();
		}
	}
	
	@SuppressLint("RtlHardcoded")
	void show(
		@NonNull final MyListView listView
		, @NonNull View anchor
		, @NonNull TootStatusLike status
		, @Nullable TootNotification notification
	){
		
		//
		window = new PopupWindow( activity );
		window.setWidth( WindowManager.LayoutParams.WRAP_CONTENT );
		window.setHeight( WindowManager.LayoutParams.WRAP_CONTENT );
		window.setContentView( viewRoot );
		window.setBackgroundDrawable( new ColorDrawable( 0x00000000 ) );
		window.setTouchable( true );
		window.setOutsideTouchable( true );
		window.setTouchInterceptor( new View.OnTouchListener() {
			@Override public boolean onTouch( View v, MotionEvent event ){
				if( MotionEventCompat.getActionMasked( event ) == MotionEvent.ACTION_OUTSIDE ){
					window.dismiss();
					listView.last_popup_close = SystemClock.elapsedRealtime();
					return true;
				}
				return false;
			}
		} );
		
		buttons_for_status.bind( status, notification );
		buttons_for_status.close_window = window;
		
		int[] location = new int[ 2 ];
		
		anchor.getLocationOnScreen( location );
		int anchor_left = location[ 0 ];
		int anchor_top = location[ 1 ];
		
		listView.getLocationOnScreen( location );
		int listView_top = location[ 1 ];
		
		float density = activity.density;
		
		int clip_top = listView_top + (int) ( 0.5f + 8f * density );
		int clip_bottom = listView_top + listView.getHeight() - (int) ( 0.5f + 8f * density );
		
		int popup_height = (int) ( 0.5f + ( 56f + 24f ) * density );
		int popup_y = anchor_top + anchor.getHeight() / 2;
		
		if( popup_y < clip_top ){
			// 画面外のは画面内にする
			popup_y = clip_top;
		}else if( clip_bottom - popup_y < popup_height ){
			// 画面外のは画面内にする
			if( popup_y > clip_bottom ) popup_y = clip_bottom;
			
			// 画面の下側にあるならポップアップの吹き出しが下から出ているように見せる
			viewRoot.findViewById( R.id.ivTriangleTop ).setVisibility( View.GONE );
			viewRoot.findViewById( R.id.ivTriangleBottom ).setVisibility( View.VISIBLE );
			popup_y -= popup_height;
		}
		
		int anchor_width = anchor.getWidth();
		int popup_width = getViewWidth( viewRoot );
		int popup_x = anchor_left + anchor_width / 2 - popup_width / 2;
		if( popup_x < 0 ) popup_x = 0;
		int popup_x_max = activity.getResources().getDisplayMetrics().widthPixels - popup_width;
		if( popup_x > popup_x_max ) popup_x = popup_x_max;
		
		window.showAtLocation(
			listView
			, Gravity.LEFT | Gravity.TOP
			, popup_x
			, popup_y
		);
	}
	
	private static int getViewWidth( View v ){
		int spec = View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED );
		v.measure( spec, spec );
		return v.getMeasuredWidth();
	}
}