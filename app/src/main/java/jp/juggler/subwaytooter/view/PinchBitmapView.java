package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class PinchBitmapView extends View {
	
	static final LogCategory log = new LogCategory( "PinchImageView" );
	
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
	
	void init( Context context ){
		
		paint.setFilterBitmap( true );
		
		// 定数をdpからpxに変換
		float density = context.getResources().getDisplayMetrics().density;
		swipe_velocity = 1000f * density;
		drag_width = 4f * density; // 誤反応しがちなのでやや厳しめ
	}
	
	// ページめくり操作のコールバック
	public interface Callback {
		void onSwipe( int delta );
	}
	
	@Nullable Callback callback;
	
	public void setCallback( @Nullable Callback callback ){
		this.callback = callback;
	}
	
	@Nullable Bitmap bitmap;
	float bitmap_w;
	float bitmap_h;
	float bitmap_aspect;
	
	public void setBitmap( @Nullable Bitmap b ){
		if( bitmap != null ){
			bitmap.recycle();
		}
		
		this.bitmap = b;
		initializeScale();
	}
	
	// 画像を表示する位置と拡大率
	float current_trans_x;
	float current_trans_y;
	float current_scale;
	
	// 画像表示に使う構造体
	final Matrix matrix = new Matrix();
	final Paint paint = new Paint();
	
	@Override protected void onDraw( Canvas canvas ){
		super.onDraw( canvas );
		
		if( bitmap != null && ! bitmap.isRecycled() ){
			matrix.reset();
			matrix.postScale( current_scale, current_scale );
			matrix.postTranslate( current_trans_x, current_trans_y );
			canvas.drawBitmap( bitmap, matrix, paint );
		}
	}
	
	@Override protected void onSizeChanged( int w, int h, int oldw, int oldh ){
		super.onSizeChanged( w, h, oldw, oldh );
		initializeScale();
	}
	
	@Override public boolean performClick(){
		super.performClick();
		initializeScale();
		return true;
	}
	
	// ビットマップを変更した時とビューのサイズが変わった時と画像をクリックした時に呼ばれる
	// 表示位置を再計算して再描画
	void initializeScale(){
		if( bitmap != null && ! bitmap.isRecycled() ){
			
			bitmap_w = Math.max( 1f, bitmap.getWidth() );
			bitmap_h = Math.max( 1f, bitmap.getHeight() );
			bitmap_aspect = bitmap_w / bitmap_h;
			
			view_w = Math.max( 1f, this.getWidth() );
			view_h = Math.max( 1f, this.getHeight() );
			view_aspect = view_w / view_h;
			
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
	
	// タッチ操作中に指を動かした
	boolean bDrag;
	
	// タッチ操作中に指の数を変えた
	boolean bPointerCountChanged;
	
	// ページめくりに必要なスワイプ強度
	float swipe_velocity;
	
	// 指を動かしたと判断する距離
	float drag_width;
	
	// フリック操作の検出に使う
	@Nullable VelocityTracker velocityTracker;
	
	@Override public boolean onTouchEvent( MotionEvent ev ){
		
		if( bitmap == null || bitmap.isRecycled() ) return false;
		
		int action = ev.getAction();
		
		if( action == MotionEvent.ACTION_DOWN ){
			if( velocityTracker != null ){
				velocityTracker.recycle();
				velocityTracker = null;
			}
			
			velocityTracker = VelocityTracker.obtain();
			velocityTracker.addMovement( ev );
			
			bDrag = bPointerCountChanged = false;
			startTracking( ev );
			return true;
		}
		
		if( velocityTracker != null ){
			velocityTracker.addMovement( ev );
		}
		
		switch( action ){
		case MotionEvent.ACTION_POINTER_DOWN:
		case MotionEvent.ACTION_POINTER_UP:
			// タッチ操作中に指の数を変えた
			bDrag = true;
			bPointerCountChanged = true;
			startTracking( ev );
			break;
		
		case MotionEvent.ACTION_MOVE:
			nextTracking( ev );
			break;
		
		case MotionEvent.ACTION_UP:
			nextTracking( ev );
			
			if( ! bDrag ){
				// 指を動かしていないならクリック操作だったのだろう
				
				performClick();
				
			}else if( ! bPointerCountChanged ){
				// 指の数を変えていないならページめくり操作かもしれない
				
				velocityTracker.computeCurrentVelocity( 1000 );
				final float xv = velocityTracker.getXVelocity();
				float yv = velocityTracker.getYVelocity();
				
				float image_move_x = Math.abs( current_trans_x - start_image_trans_x );
				float image_move_y = Math.abs( current_trans_y - start_image_trans_y );
				
				if( Math.abs( xv ) < Math.abs( yv ) / 8 ){
					// 指を動かした方向の角度が左右ではなかった
					log.d( "flick is vertical." );
					
				}else if( Math.abs( xv ) < swipe_velocity ){
					// 左右方向の強さが足りなかった
					log.d( "velocity %f not enough to paging", xv );
					
				}else if( image_move_x >= drag_width
					|| image_move_y >= drag_width * 5f
					){
					// 「画像を動かした」かどうかの最終チェック
					log.d( "image was moved. not paging action. %f %f "
						, image_move_x / drag_width
						, image_move_y / drag_width
					);
				}else{
					log.d( "paging! %f %f %f"
						, image_move_x / drag_width
						, image_move_y / drag_width
						,xv
					);
					
					Utils.runOnMainThread( new Runnable() {
						@Override public void run(){
							if( callback != null ) callback.onSwipe( xv >= 0f ? - 1 : 1 );
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
	
	// マルチタッチの中心位置の計算
	static class PointerAvg {
		
		// タッチ位置の数
		int count;
		
		// タッチ位置の平均
		final float[] avg = new float[ 2 ];
		
		// 中心と、中心から最も離れたタッチ位置の間の距離
		float max_radius;
		
		void update( MotionEvent ev ){
			
			count = ev.getPointerCount();
			if( count <= 1 ){
				avg[ 0 ] = ev.getX();
				avg[ 1 ] = ev.getY();
				max_radius = 0f;
				
			}else{
				avg[ 0 ] = 0f;
				avg[ 1 ] = 0f;
				for( int i = 0 ; i < count ; ++ i ){
					avg[ 0 ] += ev.getX( i );
					avg[ 1 ] += ev.getY( i );
				}
				avg[ 0 ] /= count;
				avg[ 1 ] /= count;
				max_radius = 0f;
				for( int i = 0 ; i < count ; ++ i ){
					float dx = ev.getX( i ) - avg[ 0 ];
					float dy = ev.getY( i ) - avg[ 1 ];
					float radius = dx * dx + dy * dy;
					if( radius > max_radius ) max_radius = radius;
				}
				max_radius = (float) Math.sqrt( max_radius );
				if( max_radius < 1f ) max_radius = 1f;
			}
		}
	}
	
	// 移動後の指の位置
	final PointerAvg pos = new PointerAvg();
	
	// 移動開始時の指の位置
	final PointerAvg start_pos = new PointerAvg();
	
	// 移動開始時の画像の位置
	float start_image_trans_x;
	float start_image_trans_y;
	float start_image_scale;
	
	float scale_min;
	float scale_max;
	
	float view_w;
	float view_h;
	float view_aspect;
	
	void startTracking( MotionEvent ev ){
		start_pos.update( ev );
		pos.update( ev );
		start_image_trans_x = current_trans_x;
		start_image_trans_y = current_trans_y;
		start_image_scale = current_scale;
		
		view_w = Math.max( 1f, this.getWidth() );
		view_h = Math.max( 1f, this.getHeight() );
		view_aspect = view_w / view_h;
		
		if( view_aspect > bitmap_aspect ){
			scale_min = view_h / bitmap_h / 2f;
			scale_max = view_w / bitmap_w * 8f;
		}else{
			scale_min = view_w / bitmap_w / 2f;
			scale_max = view_h / bitmap_h * 8f;
		}
		if( scale_max < scale_min ) scale_max = scale_min * 16f;
	}
	
	final Matrix tracking_matrix = new Matrix();
	final Matrix tracking_matrix_inv = new Matrix();
	final float[] avg_on_image1 = new float[ 2 ];
	final float[] avg_on_image2 = new float[ 2 ];
	
	// 画面上の指の位置から画像中の指の位置を調べる
	void getCoordinateOnImage( @NonNull float[] dst, @NonNull float[] src ){
		tracking_matrix.reset();
		tracking_matrix.postScale( current_scale, current_scale );
		tracking_matrix.postTranslate( current_trans_x, current_trans_y );
		tracking_matrix.invert( tracking_matrix_inv );
		tracking_matrix_inv.mapPoints( dst, src );
	}
	
	void nextTracking( MotionEvent ev ){
		pos.update( ev );
		
		if( pos.count != start_pos.count ){
			// タッチ操作中に指の数が変わった
			log.d( "nextTracking: pointer count changed" );
			bDrag = bPointerCountChanged = true;
			startTracking( ev );
			return;
		}
		
		// ズーム操作
		if( pos.count > 1 ){
			
			// タッチ位置にある絵柄の座標を調べる
			getCoordinateOnImage( avg_on_image1, pos.avg );
			
			// ズーム率を変更する
			float new_scale = start_image_scale * pos.max_radius / start_pos.max_radius;
			new_scale = new_scale < scale_min ? scale_min : new_scale > scale_max ? scale_max : new_scale;
			current_scale = new_scale;
			
			// 再び調べる
			getCoordinateOnImage( avg_on_image2, pos.avg );
			
			// ズーム変更の前後で位置がズレた分だけ移動させると、タッチ位置にある絵柄がズレない
			start_image_trans_x += current_scale * ( avg_on_image2[ 0 ] - avg_on_image1[ 0 ] );
			start_image_trans_y += current_scale * ( avg_on_image2[ 1 ] - avg_on_image1[ 1 ] );
			
			invalidate();
		}
		
		// 平行移動
		{
			// start時から指を動かした量
			float move_x = pos.avg[ 0 ] - start_pos.avg[ 0 ];
			float move_y = pos.avg[ 1 ] - start_pos.avg[ 1 ];
			
			// 「指を動かした」と判断したらフラグを立てる
			if( Math.abs( move_x ) >= drag_width
				|| Math.abs( move_y ) >= drag_width
				){
				bDrag = true;
			}
			
			// 画像の移動量
			float trans_x = start_image_trans_x + move_x;
			float trans_y = start_image_trans_y + move_y;
			
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
			
			// 画像の表示位置を変更して再描画
			current_trans_x = trans_x;
			current_trans_y = trans_y;
			invalidate();
		}
	}
	
}
