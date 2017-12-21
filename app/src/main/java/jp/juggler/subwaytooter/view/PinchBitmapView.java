package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class PinchBitmapView extends View {
	
	static final LogCategory log = new LogCategory( "PinchImageView" );
	
	public interface Callback {
		void onSwipe( int delta );
	}
	
	public PinchBitmapView( Context context ){
		this( context, null );
		init( context );
	}
	
	public PinchBitmapView( Context context, AttributeSet attrs ){
		this( context, attrs, 0 );
		init( context );
	}
	
	public PinchBitmapView( Context context, AttributeSet attrs, int defStyle ){
		super( context, attrs, defStyle );
		init( context );
	}
	
	@Override public boolean onTouchEvent( MotionEvent ev ){
		return handleTouchEvent( ev );
	}
	
	private Bitmap bitmap;
	
	private final Matrix matrix = new Matrix();
	private final Paint paint = new Paint();
	
	float swipe_velocity;
	
	Callback callback;
	
	public void setCallback( Callback callback ){
		this.callback = callback;
	}
	
	private void init( Context context ){
		paint.setFilterBitmap( true );
		swipe_velocity = 100f * context.getResources().getDisplayMetrics().density;
	}
	
	@Override protected void onSizeChanged( int w, int h, int oldw, int oldh ){
		super.onSizeChanged( w, h, oldw, oldh );
		initializeScale();
	}
	
	public void setBitmap( Bitmap b ){
		if( bitmap != null ){
			bitmap.recycle();
		}
		this.bitmap = b;
		initializeScale();
	}
	
	void initializeScale(){
		if( bitmap != null && ! bitmap.isRecycled() ){
			this.bitmap_w = bitmap.getWidth();
			this.bitmap_h = bitmap.getHeight();
			if( bitmap_w < 1f ) bitmap_w = 1f;
			if( bitmap_h < 1f ) bitmap_h = 1f;
			view_w = this.getWidth();
			view_h = this.getHeight();
			if( view_w < 1f ) view_w = 1f;
			if( view_h < 1f ) view_h = 1f;
			
			this.bitmap_aspect = bitmap_w / bitmap_h;
			this.view_aspect = view_w / view_h;
			if( view_aspect > bitmap_aspect ){
				current_scale = view_h / bitmap_h;
			}else{
				current_scale = view_w / bitmap_w;
			}
			
			float draw_w = bitmap_w * current_scale;
			float draw_h = bitmap_h * current_scale;
			
			current_trans_x = ( view_w - draw_w ) / 2f;
			current_trans_y = ( view_h - draw_h ) / 2f;
		}
		invalidate();
	}
	
	@Override protected void onDraw( Canvas canvas ){
		super.onDraw( canvas );
		
		if( bitmap != null && ! bitmap.isRecycled() ){
			matrix.reset();
			matrix.postScale( current_scale, current_scale );
			matrix.postTranslate( current_trans_x, current_trans_y );
			canvas.drawBitmap( bitmap, matrix, paint );
		}
	}
	
	boolean handleTouchEvent( MotionEvent ev ){
		if( bitmap == null || bitmap.isRecycled() ) return false;
		
		if( velocityTracker != null ){
			velocityTracker.addMovement( ev );
		}
		
		int action = ev.getAction();
		
		switch( action ){
		
		case MotionEvent.ACTION_DOWN:
			
			if( velocityTracker != null ){
				velocityTracker.recycle();
				velocityTracker = null;
			}
			
			velocityTracker = VelocityTracker.obtain();
			
			bDrag = false;
			bImageMoved = false;
			startTracking( ev );
			break;
		
		case MotionEvent.ACTION_POINTER_DOWN:
		case MotionEvent.ACTION_POINTER_UP:
			bDrag = true;
			startTracking( ev );
			break;
		
		case MotionEvent.ACTION_MOVE:
			nextTracking( ev );
			break;
		
		case MotionEvent.ACTION_UP:
			nextTracking( ev );
			if( ! bDrag ){
				
				performClick();
			}else if( ! bImageMoved ){
				
				velocityTracker.computeCurrentVelocity( 1000 );
				float xv = velocityTracker.getXVelocity();
				log.d( "velocity %f", xv );
				if( xv >= swipe_velocity ){
					Utils.runOnMainThread( new Runnable() {
						@Override public void run(){
							if( callback != null ) callback.onSwipe( - 1 );
						}
					} );
				}else if( xv <= - swipe_velocity ){
					Utils.runOnMainThread( new Runnable() {
						@Override public void run(){
							if( callback != null ) callback.onSwipe( 1 );
						}
					} );
				}
				
			}
			
			if( velocityTracker != null ){
				velocityTracker.recycle();
				velocityTracker = null;
			}
			break;
		}
		return true;
	}
	
	float touch_start_x;
	float touch_start_y;
	float touch_start_radius;
	float touch_start_trans_x;
	float touch_start_trans_y;
	float touch_start_scale;
	float view_aspect;
	float bitmap_aspect;
	float scale_min;
	float scale_max;
	float view_w;
	float view_h;
	float bitmap_w;
	float bitmap_h;
	
	float current_scale;
	float current_trans_x;
	float current_trans_y;
	boolean bDrag;
	boolean bImageMoved = false;
	
	float drag_width;
	
	int last_pointer_count;
	
	final PointerAvg pos = new PointerAvg();
	
	VelocityTracker velocityTracker;
	
	static class PointerAvg {
		float avg_x;
		float avg_y;
		float max_radius;
		int count;
		
		void update( MotionEvent ev ){
			count = ev.getPointerCount();
			if( count <= 1 ){
				avg_x = ev.getX();
				avg_y = ev.getY();
				max_radius = 0f;
			}else{
				avg_x = 0f;
				avg_y = 0f;
				for( int i = 0 ; i < count ; ++ i ){
					avg_x += ev.getX( i );
					avg_y += ev.getY( i );
				}
				avg_x /= count;
				avg_y /= count;
				max_radius = 0f;
				for( int i = 0 ; i < count ; ++ i ){
					float dx = ev.getX( i ) - avg_x;
					float dy = ev.getY( i ) - avg_y;
					float delta = dx * dx + dy * dy;
					if( delta > max_radius ) max_radius = delta;
				}
				max_radius = (float) Math.sqrt( max_radius );
				if( max_radius < 0.5f ) max_radius = 0.5f;
			}
		}
	}
	
	void startTracking( MotionEvent ev ){
		pos.update( ev );
		last_pointer_count = pos.count;
		touch_start_x = pos.avg_x;
		touch_start_y = pos.avg_y;
		touch_start_radius = pos.max_radius;
		touch_start_trans_x = current_trans_x;
		touch_start_trans_y = current_trans_y;
		touch_start_scale = current_scale;
		
		view_w = this.getWidth();
		view_h = this.getHeight();
		if( view_w < 1f ) view_w = 1f;
		if( view_h < 1f ) view_h = 1f;
		view_aspect = view_w / view_h;
		
		if( view_aspect > bitmap_aspect ){
			// ビューの方が横長、画像の方が縦長
			// 縦方向のサイズを使って最小スケールを決める
			scale_min = view_h / bitmap_h / 2;
			// ビューの方が横長、画像の方が縦長
			// 横方向のサイズを使って最大スケールを決める
			scale_max = view_w / bitmap_w * 8;
		}else{
			scale_min = view_w / bitmap_w / 2;
			scale_max = view_h / bitmap_h * 8;
		}
		if( scale_max < scale_min ) scale_max = scale_min * 4;
		
		drag_width = getResources().getDisplayMetrics().density * 8f;
	}
	
	final Matrix tracking_matrix = new Matrix();
	final Matrix tracking_matrix_inv = new Matrix();
	final float[] points_dst = new float[ 2 ];
	final float[] points_src = new float[ 2 ];
	
	void nextTracking( MotionEvent ev ){
		pos.update( ev );
		
		if( pos.count != last_pointer_count ){
			log.d( "nextTracking: pointer count changed" );
			startTracking( ev );
			return;
		}
		
		if( pos.count > 1 ){
			// pos.avg_x,y が画像の座標空間でどこに位置するか調べる
			tracking_matrix.reset();
			tracking_matrix.postScale( current_scale, current_scale );
			tracking_matrix.postTranslate( current_trans_x, current_trans_y );
			tracking_matrix.invert( tracking_matrix_inv );
			points_src[ 0 ] = pos.avg_x;
			points_src[ 1 ] = pos.avg_y;
			tracking_matrix_inv.mapPoints( points_dst, points_src );
			float avg_on_image_x = points_dst[ 0 ];
			float avg_on_image_y = points_dst[ 1 ];
			
			// update scale
			float new_scale = touch_start_scale * pos.max_radius / touch_start_radius;
			new_scale = new_scale < scale_min ? scale_min : new_scale > scale_max ? scale_max : new_scale;
			current_scale = new_scale;
			
			// pos.avg_x,y が画像の座標空間でどこに位置するか再び調べる
			tracking_matrix.reset();
			tracking_matrix.postScale( current_scale, current_scale );
			tracking_matrix.postTranslate( current_trans_x, current_trans_y );
			tracking_matrix.invert( tracking_matrix_inv );
			points_src[ 0 ] = pos.avg_x;
			points_src[ 1 ] = pos.avg_y;
			tracking_matrix_inv.mapPoints( points_dst, points_src );
			float avg_on_image_x2 = points_dst[ 0 ];
			float avg_on_image_y2 = points_dst[ 1 ];
			
			// ズレた分 * scaleだけ移動させるとスケール変更時にタッチ中心がスクロールしないのではないか
			float delta_x = avg_on_image_x2 - avg_on_image_x;
			float delta_y = avg_on_image_y2 - avg_on_image_y;
			touch_start_trans_x += current_scale * delta_x;
			touch_start_trans_y += current_scale * delta_y;
			
			invalidate();
		}
		
		// 平行移動
		{
			// start時から指を動かした量
			float move_x = pos.avg_x - touch_start_x;
			float move_y = pos.avg_y - touch_start_y;
			
			if( Math.abs( move_x ) >= drag_width || Math.abs( move_y ) >= drag_width ){
				bDrag = true;
			}
			
			// 画像の移動量
			float trans_x = touch_start_trans_x + move_x;
			float trans_y = touch_start_trans_y + move_y;
			
			// 画像サイズとビューサイズを使って移動可能範囲をクリッピング
			float draw_w = bitmap_w * current_scale;
			float draw_h = bitmap_h * current_scale;
			if( draw_w <= view_w ){
				float remain = view_w - draw_w;
				trans_x = remain / 2f;
			}else{
				float remain = draw_w - view_w;
				trans_x = trans_x >= 0f ? 0f : trans_x < - remain ? - remain : trans_x;
			}
			if( draw_h <= view_h ){
				float remain = view_h - draw_h;
				trans_y = remain / 2f;
			}else{
				float remain = draw_h - view_h;
				trans_y = trans_y >= 0f ? 0f : trans_y < - remain ? - remain : trans_y;
			}
			
			if( current_trans_x != trans_x || current_trans_y != trans_y ){
				bImageMoved = true;
			}
			
			// TODO trans_x,trans_y を画像の移動量に反映させる
			current_trans_x = trans_x;
			current_trans_y = trans_y;
			invalidate();
		}
	}
	
	@Override public boolean performClick(){
		initializeScale();
		return true;
	}
	
}
