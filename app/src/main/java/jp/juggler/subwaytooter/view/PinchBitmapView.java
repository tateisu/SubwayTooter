package jp.juggler.subwaytooter.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.SystemClock;
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
		
		// 定数をdpからpxに変換
		float density = context.getResources().getDisplayMetrics().density;
		swipe_velocity = 1000f * density;
		swipe_velocity2 = 250f * density;
		drag_width = 4f * density; // 誤反応しがちなのでやや厳しめ
	}
	
	// ページめくり操作のコールバック
	public interface Callback {
		void onSwipe( int delta );
		
		void onMove( float bitmap_w, float bitmap_h, float tx, float ty, float scale );
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
			
			paint.setFilterBitmap( current_scale < 4f );
			canvas.drawBitmap( bitmap, matrix, paint );
		}
	}
	
	@Override protected void onSizeChanged( int w, int h, int oldw, int oldh ){
		super.onSizeChanged( w, h, oldw, oldh );
		
		view_w = Math.max( 1f, w );
		view_h = Math.max( 1f, h );
		view_aspect = view_w / view_h;
		
		initializeScale();
	}
	
	@Override public boolean performClick(){
		super.performClick();
		initializeScale();
		return true;
	}
	
	// 表示位置の初期化
	// 呼ばれるのは、ビットマップを変更した時、ビューのサイズが変わった時、画像をクリックした時
	void initializeScale(){
		if( bitmap != null && ! bitmap.isRecycled() && view_w >= 1f ){
			
			bitmap_w = Math.max( 1f, bitmap.getWidth() );
			bitmap_h = Math.max( 1f, bitmap.getHeight() );
			bitmap_aspect = bitmap_w / bitmap_h;
			
			if( view_aspect > bitmap_aspect ){
				current_scale = view_h / bitmap_h;
			}else{
				current_scale = view_w / bitmap_w;
			}
			
			float draw_w = bitmap_w * current_scale;
			float draw_h = bitmap_h * current_scale;
			
			current_trans_x = ( view_w - draw_w ) / 2f;
			current_trans_y = ( view_h - draw_h ) / 2f;
			
			if( callback != null )
				callback.onMove( bitmap_w, bitmap_h, current_trans_x, current_trans_y, current_scale );
		}else{
			current_trans_x = current_trans_y = 0f;
			current_scale = 1f;
			
			if( callback != null )
				callback.onMove( 0f, 0f, current_trans_x, current_trans_y, current_scale );
		}
		
		// 画像がnullに変化した時も再描画が必要
		invalidate();
	}
	
	// タッチ操作中に指を動かした
	boolean bDrag;
	
	// タッチ操作中に指の数を変えた
	boolean bPointerCountChanged;
	
	// ページめくりに必要なスワイプ強度
	float swipe_velocity;
	float swipe_velocity2;
	
	// 指を動かしたと判断する距離
	float drag_width;
	
	long time_touch_start;
	
	// フリック操作の検出に使う
	@Nullable VelocityTracker velocityTracker;
	
	@SuppressLint("ClickableViewAccessibility")
	@Override public boolean onTouchEvent( MotionEvent ev ){
		
		if( bitmap == null
			|| bitmap.isRecycled()
			|| view_w < 1f
			) return false;
		
		int action = ev.getAction();
		
		if( action == MotionEvent.ACTION_DOWN ){
			time_touch_start = SystemClock.elapsedRealtime();
			
			if( velocityTracker != null ){
				velocityTracker.clear();
			}else{
				velocityTracker = VelocityTracker.obtain();
			}
			
			velocityTracker.addMovement( ev );
			
			bDrag = bPointerCountChanged = false;
			trackStart( ev );
			return true;
		}
		
		if( velocityTracker != null ){
			velocityTracker.addMovement( ev );
		}
		
		switch( action ){
		case MotionEvent.ACTION_POINTER_DOWN:
		case MotionEvent.ACTION_POINTER_UP:
			// タッチ操作中に指の数を変えた
			bDrag = bPointerCountChanged = true;
			trackStart( ev );
			break;
		
		case MotionEvent.ACTION_MOVE:
			trackNext( ev );
			break;
		
		case MotionEvent.ACTION_UP:
			trackNext( ev );
			
			checkClickOrPaging();
			
			if( velocityTracker != null ){
				velocityTracker.recycle();
				velocityTracker = null;
			}
			
			break;
		}
		return true;
	}
	
	long click_time;
	int click_count;
	
	void checkClickOrPaging(){
		
		if( ! bDrag ){
			// 指を動かしていないなら
			
			long now = SystemClock.elapsedRealtime();
			
			if( now - time_touch_start >= 1000L ){
				// ロングタップはタップカウントをリセットする
				log.d("click count reset by long tap");
				click_count = 0;
				return;
			}

			long delta = now-click_time;
			click_time = now;

			if( delta > 334L ){
				// 前回のタップからの時刻が長いとタップカウントをリセットする
				log.d("click count reset by long interval");
				click_count = 0;
			}

			++click_count;

			log.d("click %d %d",click_count,delta);

			if( click_count >= 2 ){
				// ダブルタップでクリック操作
				click_count = 0;
				performClick();
			}

			return;
		}
		
		click_count = 0;
		
		if( ! bPointerCountChanged && velocityTracker != null ){
			
			// 指の数を変えていないならページめくり操作かもしれない
			
			velocityTracker.computeCurrentVelocity( 1000 );
			final float xv = velocityTracker.getXVelocity();
			final float yv = velocityTracker.getYVelocity();
			
			float image_move_x = Math.abs( current_trans_x - start_image_trans_x );
			float image_move_y = Math.abs( current_trans_y - start_image_trans_y );
			
			float draw_w = bitmap_w * current_scale;
			
			if( Math.abs( xv ) < Math.abs( yv ) / 8 ){
				// 指を動かした方向の角度が左右ではなかった
				log.d( "flick is vertical." );
				
			}else if( Math.abs( xv ) < ( draw_w <= view_w ? swipe_velocity2 : swipe_velocity ) ){
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
					, xv
				);
				
				Utils.runOnMainThread( new Runnable() {
					@Override public void run(){
						if( callback != null ) callback.onSwipe( xv >= 0f ? - 1 : 1 );
					}
				} );
			}
		}
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
	
	void trackStart( MotionEvent ev ){
		
		// 追跡開始時の指の位置
		start_pos.update( ev );
		
		// 追跡開始時の画像の位置
		start_image_trans_x = current_trans_x;
		start_image_trans_y = current_trans_y;
		start_image_scale = current_scale;
		
		if( view_aspect > bitmap_aspect ){
			scale_min = view_h / bitmap_h / 2f;
			scale_max = view_w / bitmap_w * 8f;
		}else{
			scale_min = view_w / bitmap_w / 2f;
			scale_max = view_h / bitmap_h * 8f;
		}
		if( scale_max < scale_min ) scale_max = scale_min * 16f;
	}
	
	private final Matrix tracking_matrix = new Matrix();
	private final Matrix tracking_matrix_inv = new Matrix();
	private final float[] avg_on_image1 = new float[ 2 ];
	private final float[] avg_on_image2 = new float[ 2 ];
	
	// 画面上の指の位置から画像中の指の位置を調べる
	private void getCoordinateOnImage( @NonNull float[] dst, @NonNull float[] src ){
		tracking_matrix.reset();
		tracking_matrix.postScale( current_scale, current_scale );
		tracking_matrix.postTranslate( current_trans_x, current_trans_y );
		tracking_matrix.invert( tracking_matrix_inv );
		tracking_matrix_inv.mapPoints( dst, src );
	}
	
	void trackNext( MotionEvent ev ){
		pos.update( ev );
		
		if( pos.count != start_pos.count ){
			// タッチ操作中に指の数が変わった
			log.d( "nextTracking: pointer count changed" );
			bDrag = bPointerCountChanged = true;
			trackStart( ev );
			return;
		}
		
		// ズーム操作
		if( pos.count > 1 ){
			
			// タッチ位置にある絵柄の座標を調べる
			getCoordinateOnImage( avg_on_image1, pos.avg );
			
			// ズーム率を変更する
			current_scale = clip( scale_min, scale_max, start_image_scale * pos.max_radius / start_pos.max_radius );
			
			// 再び調べる
			getCoordinateOnImage( avg_on_image2, pos.avg );
			
			// ズーム変更の前後で位置がズレた分だけ移動させると、タッチ位置にある絵柄がズレない
			start_image_trans_x += current_scale * ( avg_on_image2[ 0 ] - avg_on_image1[ 0 ] );
			start_image_trans_y += current_scale * ( avg_on_image2[ 1 ] - avg_on_image1[ 1 ] );
			
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
			
			// 画像の表示位置を更新
			current_trans_x = clipTranslate( view_w, bitmap_w, current_scale, start_image_trans_x + move_x );
			current_trans_y = clipTranslate( view_h, bitmap_h, current_scale, start_image_trans_y + move_y );
		}
		
		if( callback != null )
			callback.onMove( bitmap_w, bitmap_h, current_trans_x, current_trans_y, current_scale );
		
		invalidate();
	}
	
	// 数値を範囲内にクリップする
	private static float clip( float min, float max, float v ){
		return v < min ? min : v > max ? max : v;
	}
	
	// ビューの幅と画像の描画サイズを元に描画位置をクリップする
	private static float clipTranslate(
		float view_w // ビューの幅
		, float bitmap_w // 画像の幅
		, float current_scale // 画像の拡大率
		, float trans_x // タッチ操作による表示位置
	){
		
		// 余白(拡大率が小さい場合はプラス、拡大率が大きい場合はマイナス)
		float padding = view_w - bitmap_w * current_scale;
		
		// 余白が>=0なら画像を中心に表示する。 <0なら操作された位置をクリップする。
		return padding >= 0f ? padding / 2f : clip( padding, 0f, trans_x );
	}
	
}
