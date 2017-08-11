package com.bumptech.glide.load.resource.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;

import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;

import java.lang.reflect.Field;

import jp.juggler.subwaytooter.util.LogCategory;

import static com.bumptech.glide.gifdecoder.GifDecoder.TOTAL_ITERATION_COUNT_FOREVER;

@SuppressWarnings({ "WeakerAccess", "unused", "ObsoleteSdkInt" })
public class MyGifDrawable extends GlideDrawable implements GifFrameLoader.FrameCallback {
	
	static final LogCategory log = new LogCategory( "MyGifDrawable" );
	
	private final Paint paint;
	private final Rect destRect = new Rect();
	private final GifDrawable.GifState state;
	private final GifDecoder decoder;
	private final GifFrameLoader frameLoader;
	
	/**
	 * True if the drawable is currently animating.
	 */
	private boolean isRunning;
	/**
	 * True if the drawable should animate while visible.
	 */
	private boolean isStarted;
	/**
	 * True if the drawable's resources have been recycled.
	 */
	private boolean isRecycled;
	/**
	 * True if the drawable is currently visible. Default to true because on certain platforms (at least 4.1.1),
	 * setVisible is not called on {@link android.graphics.drawable.Drawable Drawables} during
	 * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}. See issue #130.
	 */
	private boolean isVisible = true;
	/**
	 * The number of times we've looped over all the frames in the gif.
	 */
	private int loopCount;
	/**
	 * The number of times to loop through the gif animation.
	 */
	private int maxLoopCount = LOOP_FOREVER;
	
	private boolean applyGravity;
	
	static Field field_state;
	
	static GifDrawable.GifState cloneState( GifDrawable other ){
		try{
			if( field_state == null ){
				field_state = GifDrawable.class.getDeclaredField( "state" );
				field_state.setAccessible( true );
			}
			GifDrawable.GifState other_state = (GifDrawable.GifState) field_state.get( other );
			
			return new GifDrawable.GifState(
				other_state.gifHeader,
				other_state.data,
				other_state.context,
				other.getFrameTransformation(),
				other_state.targetWidth,
				other_state.targetHeight,
				other_state.bitmapProvider,
				other_state.bitmapPool,
				other.getFirstFrame()
			);
		}catch( Throwable ex ){
			throw new RuntimeException( "cloning GifDrawable.GifState failed.", ex );
		}
	}
	
	float mCornerRadius;
	
	public MyGifDrawable( GifDrawable other, float radius ){
		this( cloneState( other ) );
		this.mCornerRadius = radius;
	}
	
	private MyGifDrawable( GifDrawable.GifState state ){
		if( state == null ){
			throw new NullPointerException( "GifState must not be null" );
		}
		
		this.state = state;
		this.decoder = new GifDecoder( state.bitmapProvider );
		this.paint  = new Paint( Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG );

		decoder.setData( state.gifHeader, state.data );
		frameLoader = new GifFrameLoader( state.context, this, decoder, state.targetWidth, state.targetHeight );
		frameLoader.setFrameTransformation( state.frameTransformation );
	}
	
	// Visible for testing.
	MyGifDrawable( GifDecoder decoder, GifFrameLoader frameLoader, Bitmap firstFrame, BitmapPool bitmapPool, Paint paint ){
		this.decoder = decoder;
		this.frameLoader = frameLoader;
		this.state = new GifDrawable.GifState( null );
		this.paint = paint;
		state.bitmapPool = bitmapPool;
		state.firstFrame = firstFrame;
	}
	
	public Bitmap getFirstFrame(){
		return state.firstFrame;
	}
	
	public void setFrameTransformation( Transformation< Bitmap > frameTransformation, Bitmap firstFrame ){
		if( firstFrame == null ){
			throw new NullPointerException( "The first frame of the GIF must not be null" );
		}
		if( frameTransformation == null ){
			throw new NullPointerException( "The frame transformation must not be null" );
		}
		state.frameTransformation = frameTransformation;
		state.firstFrame = firstFrame;
		frameLoader.setFrameTransformation( frameTransformation );
	}
	
	public GifDecoder getDecoder(){
		return decoder;
	}
	
	public Transformation< Bitmap > getFrameTransformation(){
		return state.frameTransformation;
	}
	
	public byte[] getData(){
		return state.data;
	}
	
	public int getFrameCount(){
		return decoder.getFrameCount();
	}
	
	private void resetLoopCount(){
		loopCount = 0;
	}
	
	@Override
	public void start(){
		log.d("start");
		isStarted = true;
		resetLoopCount();
		if( isVisible ){
			startRunning();
		}
	}
	
	@Override
	public void stop(){
		isStarted = false;
		stopRunning();
		
		// On APIs > honeycomb we know our drawable is not being displayed anymore when it's callback is cleared and so
		// we can use the absence of a callback as an indication that it's ok to clear our temporary data. Prior to
		// honeycomb we can't tell if our callback is null and instead eagerly reset to avoid holding on to resources we
		// no longer need.
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ){
			reset();
		}
	}
	
	/**
	 * Clears temporary data and resets the drawable back to the first frame.
	 */
	private void reset(){
		frameLoader.clear();
		invalidateSelf();
	}
	
	private void startRunning(){
		// If we have only a single frame, we don't want to decode it endlessly.
		if( decoder.getFrameCount() == 1 ){
			invalidateSelf();
		}else if( ! isRunning ){
			isRunning = true;
			frameLoader.start();
			invalidateSelf();
		}
	}
	
	private void stopRunning(){
		isRunning = false;
		frameLoader.stop();
	}
	
	@Override
	public boolean setVisible( boolean visible, boolean restart ){
		isVisible = visible;
		if( ! visible ){
			stopRunning();
		}else if( isStarted ){
			startRunning();
		}
		return super.setVisible( visible, restart );
	}
	
	@Override
	public int getIntrinsicWidth(){
		return state.firstFrame.getWidth();
	}
	
	@Override
	public int getIntrinsicHeight(){
		return state.firstFrame.getHeight();
	}
	
	@Override
	public boolean isRunning(){
		return isRunning;
	}
	
	// For testing.
	void setIsRunning( boolean isRunning ){
		this.isRunning = isRunning;
	}
	
	@Override
	protected void onBoundsChange( Rect bounds ){
		super.onBoundsChange( bounds );
		applyGravity = true;
	}
	
	final Matrix mShaderMatrix = new Matrix();
	final RectF mDstRectF = new RectF();
	
	@Override
	public void draw( @NonNull Canvas canvas ){
		if( isRecycled ){
			return;
		}
		
		if( applyGravity ){
			Gravity.apply( Gravity.FILL, getIntrinsicWidth(), getIntrinsicHeight(), getBounds(), destRect );
			applyGravity = false;
		}
		
		Bitmap currentFrame = frameLoader.getCurrentFrame();
		Bitmap toDraw = currentFrame != null ? currentFrame : state.firstFrame;
		
		if( mCornerRadius <= 0f ){
			paint.setShader( null );
			canvas.drawBitmap( toDraw, null, destRect, paint );
		}else{
			drawRoundImage(canvas,toDraw );
		}
	}
	
	private void drawRoundImage( Canvas canvas, Bitmap src){
		if( src == null ) return;
		int src_w = src.getWidth();
		int src_h = src.getHeight();
		if( src_w < 1 || src_h < 1 ) return;
		int outWidth = destRect.width();
		int outHeight = destRect.height();
		
		mDstRectF.set( destRect );
		mShaderMatrix.preScale( mDstRectF.width() / src_w, mDstRectF.height() / src_h );
		
		BitmapShader mBitmapShader = new BitmapShader( src, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP );
		mBitmapShader.setLocalMatrix( mShaderMatrix );
		
		paint.setShader( mBitmapShader );
		canvas.drawRoundRect( mDstRectF, mCornerRadius, mCornerRadius, paint );
		
	}
	
	@Override
	public void setAlpha( int i ){
		paint.setAlpha( i );
	}
	
	@Override
	public void setColorFilter( ColorFilter colorFilter ){
		paint.setColorFilter( colorFilter );
	}
	
	@Override
	public int getOpacity(){
		// We can't tell, so default to transparent to be safe.
		return PixelFormat.TRANSPARENT;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onFrameReady( int frameIndex ){
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && getCallback() == null ){
			stop();
			reset();
			return;
		}
		
		invalidateSelf();
		
		if( frameIndex == decoder.getFrameCount() - 1 ){
			loopCount++;
		}
		
		if( maxLoopCount != LOOP_FOREVER && loopCount >= maxLoopCount ){
			stop();
		}
	}
	
	@Override
	public ConstantState getConstantState(){
		return state;
	}
	
	/**
	 * Clears any resources for loading frames that are currently held on to by this object.
	 */
	public void recycle(){
		isRecycled = true;
		state.bitmapPool.put( state.firstFrame );
		frameLoader.clear();
		frameLoader.stop();
	}
	
	// For testing.
	boolean isRecycled(){
		return isRecycled;
	}
	
	@Override
	public boolean isAnimated(){
		return true;
	}
	
	@Override
	public void setLoopCount( int loopCount ){
		if( loopCount <= 0 && loopCount != LOOP_FOREVER && loopCount != LOOP_INTRINSIC ){
			throw new IllegalArgumentException( "Loop count must be greater than 0, or equal to "
				+ "GlideDrawable.LOOP_FOREVER, or equal to GlideDrawable.LOOP_INTRINSIC" );
		}
		
		if( loopCount == LOOP_INTRINSIC ){
			int intrinsicCount = decoder.getTotalIterationCount();
			maxLoopCount = ( intrinsicCount == TOTAL_ITERATION_COUNT_FOREVER ) ? LOOP_FOREVER : intrinsicCount;
		}else{
			maxLoopCount = loopCount;
		}
	}
}
