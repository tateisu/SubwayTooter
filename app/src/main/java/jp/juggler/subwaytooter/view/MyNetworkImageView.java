package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.support.v7.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BaseTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.util.Util;

import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.util.Utils;

public class MyNetworkImageView extends AppCompatImageView {
	
	public MyNetworkImageView( Context context ){
		this( context, null );
	}
	
	public MyNetworkImageView( Context context, AttributeSet attrs ){
		this( context, attrs, 0 );
	}
	
	public MyNetworkImageView( Context context, AttributeSet attrs, int defStyle ){
		super( context, attrs, defStyle );
	}
	
	/**
	 * Resource ID of the image to be used as a placeholder until the network image is loaded.
	 */
	private int mDefaultImageId;
	
	public void setDefaultImageResId( int defaultImage ){
		mDefaultImageId = defaultImage;
	}
	
	/**
	 * Resource ID of the image to be used if the network response fails.
	 */
	private int mErrorImageId;
	
	public void setErrorImageResId( int errorImage ){
		mErrorImageId = errorImage;
	}
	
	float mCornerRadius;
	
	// 元画像の短辺に対する割合を指定するらしい
	public void setCornerRadius( SharedPreferences pref, float r ){
		if( ! pref.getBoolean( Pref.KEY_DONT_ROUND, false ) ){
			mCornerRadius = r;
		}
	}
	
	/**
	 * The URL of the network image to load
	 */
	private String mUrl;
	
	public void setImageUrl( String url ){
		mUrl = url;
		// The URL has potentially changed. See if we need to load it.
		loadImageIfNecessary( false );
	}
	
	Target< ? > mTarget;
	String mTargetUrl;
	
	private void cancelLoading(){
		if( mTarget != null ){
			setImageDrawable( null );
			Glide.clear( mTarget );
			mTarget = null;
			mTargetUrl = null;
		}
	}
	
	private void setDefaultImageOrNull(){
		if( mDefaultImageId != 0 ){
			setImageResource( mDefaultImageId );
		}else{
			setImageDrawable( null );
		}
	}
	
	/**
	 * Loads the image for the view if it isn't already loaded.
	 */
	void loadImageIfNecessary( final boolean isInLayoutPass ){

		boolean wrapWidth = false, wrapHeight = false;
		if( getLayoutParams() != null ){
			wrapWidth = getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT;
			wrapHeight = getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT;
		}
		
		// Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
		final int desiredWidth = wrapWidth ? Target.SIZE_ORIGINAL : getWidth();
		final int desiredHeight = wrapHeight ? Target.SIZE_ORIGINAL : getHeight();

		if( (desiredWidth != Target.SIZE_ORIGINAL && desiredWidth <=0 )
			|| (desiredHeight != Target.SIZE_ORIGINAL && desiredHeight <=0 )
			){
			// desiredWidth,desiredHeight の指定がおかしいと非同期処理中にSimpleTargetが落ちる

			// if the view's bounds aren't known yet, and this is not a wrap-content/wrap-content
			// view, hold off on loading the image.
			return;
		}

		// if the URL to be loaded in this view is empty, cancel any old requests and clear the
		// currently loaded image.
		if( TextUtils.isEmpty( mUrl ) ){
			cancelLoading();
			setDefaultImageOrNull();
			return;
		}else if( mTarget != null ){
			// if there was an old request in this view, check if it needs to be canceled.
			// if the request is from the same URL, return.
			if( mUrl.equals( mTargetUrl ) ) return;
			
			// if there is a pre-existing request, cancel it if it's fetching a different URL.
			cancelLoading();
		}
		
		setDefaultImageOrNull();
		
		
		final AtomicBoolean isImmediate = new AtomicBoolean( true );
		mTargetUrl = mUrl;
		try{
			mTarget = Glide.with( getContext() )
				.load( mUrl )
				.asBitmap()
				.into(
					new SimpleTarget< Bitmap >( desiredWidth, desiredHeight ) {
						
						@Override public void onLoadFailed( Exception e, Drawable errorDrawable ){
							try{
								// このViewは別の画像を表示するように指定が変わっていた
								if( mTargetUrl == null || ! mTargetUrl.equals( mUrl ) ) return;
								
								e.printStackTrace();
								if( mErrorImageId != 0 ) setImageResource( mErrorImageId );
							}catch( Throwable ex ){
								ex.printStackTrace();
								//								java.lang.NullPointerException:
								//								at jp.juggler.subwaytooter.view.MyNetworkImageView$1.onLoadFailed(MyNetworkImageView.java:147)
								//								at com.bumptech.glide.request.GenericRequest.setErrorPlaceholder(GenericRequest.java:404)
								//								at com.bumptech.glide.request.GenericRequest.onException(GenericRequest.java:548)
								//								at com.bumptech.glide.load.engine.EngineJob.handleExceptionOnMainThread(EngineJob.java:183)
							}
						}
						
						@Override public void onResourceReady(
							final Bitmap bitmap
							, final GlideAnimation< ? super Bitmap > glideAnimation
						){
							try{
								if( isImmediate.get() && isInLayoutPass ){
									post( new Runnable() {
										@Override public void run(){
											onResourceReady( bitmap, glideAnimation );
										}
									} );
									return;
								}
								
								// このViewは別の画像を表示するように指定が変わっていた
								if( mTargetUrl == null || ! mTargetUrl.equals( mUrl ) ) return;
								
								if( bitmap == null ){
									setDefaultImageOrNull();
								}else if( mCornerRadius <= 0f ){
									setImageBitmap( bitmap );
								}else{
									RoundedBitmapDrawable d = RoundedBitmapDrawableFactory
										.create( getResources(), bitmap );
									d.setCornerRadius( mCornerRadius );
									setImageDrawable( d );
								}
							}catch( Throwable ex ){
								ex.printStackTrace();
							}
						}
					}
				);
			isImmediate.set( false );
		}catch( Throwable ex ){
			ex.printStackTrace();
			// java.lang.IllegalArgumentException:
			//			at com.bumptech.glide.manager.RequestManagerRetriever.assertNotDestroyed(RequestManagerRetriever.java:134)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:102)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:87)
			//			at com.bumptech.glide.Glide.with(Glide.java:657)
		}
	}
	
	@Override protected void onLayout( boolean changed, int left, int top, int right, int bottom ){
		super.onLayout( changed, left, top, right, bottom );
		loadImageIfNecessary( true );
	}
	
	@Override protected void onDetachedFromWindow(){
		cancelLoading();
		super.onDetachedFromWindow();
	}
	
	@Override protected void onAttachedToWindow(){
		super.onAttachedToWindow();
		loadImageIfNecessary( true );
	}
	
	@Override protected void drawableStateChanged(){
		super.drawableStateChanged();
		invalidate();
	}
	
	Drawable media_type_drawable;
	int media_type_bottom;
	int media_type_left;
	public void setMediaType( int drawable_id ){
		if( drawable_id == 0){
			media_type_drawable = null;
		}else{
			media_type_drawable = ContextCompat.getDrawable(getContext(),drawable_id).mutate();
			// DisplayMetrics dm = getResources().getDisplayMetrics();
			media_type_bottom = 0;
			media_type_left =0;
		}
		invalidate();
	}
	
	@Override protected void onDraw( Canvas canvas ){
		super.onDraw( canvas );
		if( media_type_drawable != null ){
			int drawable_w = media_type_drawable.getIntrinsicWidth();
			int drawable_h = media_type_drawable.getIntrinsicHeight();
			// int view_w = getWidth();
			int view_h = getHeight();
			media_type_drawable.setBounds(
				0,
				view_h - drawable_h,
				drawable_w,
				view_h
			);
			media_type_drawable.draw( canvas );
		}
	}
}
