package com.bumptech.glide.load.resource.bitmap;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.Gravity;

import com.bumptech.glide.load.resource.drawable.GlideDrawable;

import java.lang.reflect.Field;

import jp.juggler.subwaytooter.util.LogCategory;

@SuppressWarnings("unused") public class MyGlideBitmapDrawable extends GlideDrawable {
	
	private static final LogCategory log = new LogCategory( "MyGlideBitmapDrawable" );
	

	private final Rect destRect = new Rect();
	private int width;
	private int height;
	private boolean applyGravity;
	private boolean mutated;
	private GlideBitmapDrawable.BitmapState state;
	
	private static Field field_state;
	
	private static GlideBitmapDrawable.BitmapState cloneState( GlideBitmapDrawable other ){
		try{
			if( field_state == null ){
				field_state = GlideBitmapDrawable.class.getDeclaredField( "state" );
				field_state.setAccessible( true );
			}
			GlideBitmapDrawable.BitmapState other_state = (GlideBitmapDrawable.BitmapState) field_state.get( other );
			
			return new GlideBitmapDrawable.BitmapState( other_state );
		}catch( Throwable ex ){
			throw new RuntimeException( "cloning GlideBitmapDrawable.BitmapState failed.", ex );
		}
	}
	
	private float mCornerRadius;
	
	public MyGlideBitmapDrawable( Resources res,GlideBitmapDrawable other, float radius ){
		this( res, cloneState( other ) );
		this.mCornerRadius = radius;
	}
	
	private MyGlideBitmapDrawable(Resources res, GlideBitmapDrawable.BitmapState state) {
		if (state == null) {
			throw new NullPointerException("BitmapState must not be null");
		}
		
		this.state = state;
		final int targetDensity;
		if (res != null) {
			final int density = res.getDisplayMetrics().densityDpi;
			targetDensity = density == 0 ? DisplayMetrics.DENSITY_DEFAULT : density;
			state.targetDensity = targetDensity;
		} else {
			targetDensity = state.targetDensity;
		}
		width = state.bitmap.getScaledWidth(targetDensity);
		height = state.bitmap.getScaledHeight(targetDensity);
	}
	
	@Override
	public int getIntrinsicWidth() {
		return width;
	}
	
	@Override
	public int getIntrinsicHeight() {
		return height;
	}
	
	@Override
	public boolean isAnimated() {
		return false;
	}
	
	@Override
	public void setLoopCount(int loopCount) {
		// Do nothing.
	}
	
	@Override
	public void start() {
		// Do nothing.
	}
	
	@Override
	public void stop() {
		// Do nothing.
	}
	
	@Override
	public boolean isRunning() {
		return false;
	}
	
	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		applyGravity = true;
	}
	
	@Override
	public ConstantState getConstantState() {
		return state;
	}
	
	@Override
	public void draw( @NonNull Canvas canvas) {
		if (applyGravity) {
			Gravity.apply(Gravity.FILL, width, height, getBounds(), destRect);
			applyGravity = false;
			mBitmapShader = null;
		}

		Bitmap toDraw = state.bitmap;
		
		if( mCornerRadius <= 0f ){
			state.paint.setShader( null );
			canvas.drawBitmap( toDraw, null, destRect, state.paint );
			mBitmapShader = null;
		}else{
			drawRoundImage(canvas,toDraw );
		}
	}
	
	private final Matrix mShaderMatrix = new Matrix();
	private final RectF mDstRectF = new RectF();
	private BitmapShader mBitmapShader;
	
	private void drawRoundImage( Canvas canvas, Bitmap src){
		if( src == null ) return;
		int src_w = src.getWidth();
		int src_h = src.getHeight();
		if( src_w < 1 || src_h < 1 ) return;
		
		if( mBitmapShader == null ){
			int outWidth = destRect.width();
			int outHeight = destRect.height();
			mDstRectF.set( destRect );
			mShaderMatrix.preScale( mDstRectF.width() / src_w, mDstRectF.height() / src_h );

			mBitmapShader = new BitmapShader( src, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP );
			mBitmapShader.setLocalMatrix( mShaderMatrix );
		}
		
		state.paint.setShader( mBitmapShader );
		canvas.drawRoundRect( mDstRectF, mCornerRadius, mCornerRadius, state.paint );
		
	}
	
	@Override
	public void setAlpha(int alpha) {
		int currentAlpha = state.paint.getAlpha();
		if (currentAlpha != alpha) {
			state.setAlpha(alpha);
			invalidateSelf();
		}
	}
	
	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		state.setColorFilter(colorFilter);
		invalidateSelf();
	}
	
	@Override
	public int getOpacity() {
		Bitmap bm = state.bitmap;
		return bm == null || bm.hasAlpha() || state.paint.getAlpha() < 255
			? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
	}
	
	@NonNull @Override
	public Drawable mutate() {
		if (!mutated && super.mutate() == this) {
			state = new GlideBitmapDrawable.BitmapState(state);
			mutated = true;
		}
		return this;
	}
	
	public Bitmap getBitmap() {
		return state.bitmap;
	}

}
