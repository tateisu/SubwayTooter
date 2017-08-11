package jp.juggler.subwaytooter.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.support.v7.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.BaseTarget;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.SquaringDrawable;
import com.bumptech.glide.request.target.Target;

import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.util.LogCategory;

public class MyNetworkImageView extends AppCompatImageView {
	
	static final LogCategory log = new LogCategory( "MyNetworkImageView" );
	
	public MyNetworkImageView( Context context ){
		this( context, null );
	}
	
	public MyNetworkImageView( Context context, AttributeSet attrs ){
		this( context, attrs, 0 );
	}
	
	public MyNetworkImageView( Context context, AttributeSet attrs, int defStyle ){
		super( context, attrs, defStyle );
	}
	
	@SuppressLint("StaticFieldLeak") static Context app_context;
	
	// ロード中などに表示するDrawableのリソースID
	private int mDefaultImageId;
	
	public void setDefaultImageResId( int defaultImage ){
		mDefaultImageId = defaultImage;
	}
	
	// エラー時に表示するDrawableのリソースID
	private int mErrorImageId;
	
	public void setErrorImageResId( int errorImage ){
		mErrorImageId = errorImage;
	}
	
	// 角丸の半径。元画像の短辺に対する割合を指定するらしい
	float mCornerRadius;
	
	// 表示したい画像のURL
	private String mUrl;
	private boolean mMayGif;
	
	public void setImageUrl( SharedPreferences pref, float r, String url ){
		setImageUrl( pref, r, url, null );
	}
	
	public void setImageUrl( SharedPreferences pref, float r, String url, String gif_url ){
		if( app_context == null ){
			Context context = getContext();
			if( context != null ){
				app_context = context.getApplicationContext();
			}
		}
		
		if( pref.getBoolean( Pref.KEY_DONT_ROUND, false ) ){
			mCornerRadius = 0f;
		}else{
			mCornerRadius = r;
		}
		
		if( pref.getBoolean( Pref.KEY_DISABLE_GIF_ANIMATION, false ) ){
			gif_url = null;
		}
		
		if( ! TextUtils.isEmpty( gif_url ) ){
			mUrl = gif_url;
			mMayGif = true;
		}else{
			mUrl = url;
			mMayGif = false;
		}
		loadImageIfNecessary();
	}
	
	// 非同期処理のキャンセル
	BaseTarget< ? > mTarget;
	
	private void cancelLoading(){
		if( mTarget != null ){
			setImageDrawable( null );
			Glide.clear( mTarget );
			mTarget = null;
		}
	}
	
	// デフォルト画像かnullを表示する
	private void setDefaultImageOrNull(){
		if( mDefaultImageId != 0 ){
			setImageResource( mDefaultImageId );
		}else{
			setImageDrawable( null );
		}
	}
	
	// 必要なら非同期処理を開始する
	void loadImageIfNecessary(){
		try{
			if( TextUtils.isEmpty( mUrl ) ){
				// if the URL to be loaded in this view is empty,
				// cancel any old requests and clear the currently loaded image.
				cancelLoading();
				setDefaultImageOrNull();
				return;
			}
			
			if( mTarget != null && mUrl.equals( ( (UrlTarget) mTarget ).getUrl() ) ){
				// すでにリクエストが発行済みで、リクエストされたURLが同じなら何もしない
				return;
			}
			
			// if there is a pre-existing request, cancel it if it's fetching a different URL.
			cancelLoading();
			setDefaultImageOrNull();
			
			boolean wrapWidth = false, wrapHeight = false;
			ViewGroup.LayoutParams lp = getLayoutParams();
			if( lp != null ){
				wrapWidth = lp.width == ViewGroup.LayoutParams.WRAP_CONTENT;
				wrapHeight = lp.height == ViewGroup.LayoutParams.WRAP_CONTENT;
			}
			
			// Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
			final int desiredWidth = wrapWidth ? Target.SIZE_ORIGINAL : getWidth();
			final int desiredHeight = wrapHeight ? Target.SIZE_ORIGINAL : getHeight();
			
			if( ( desiredWidth != Target.SIZE_ORIGINAL && desiredWidth <= 0 )
				|| ( desiredHeight != Target.SIZE_ORIGINAL && desiredHeight <= 0 )
				){
				// desiredWidth,desiredHeight の指定がおかしいと非同期処理中にSimpleTargetが落ちる
				// おそらくレイアウト後に再度呼び出される
				return;
			}
			
			if( mMayGif ){
				if( mCornerRadius > 0f ){
					mTarget = Glide.with( getContext() )
						.load( mUrl )
						.transform( new RoundTransformation( mCornerRadius ) )
						.into( new MyTargetGif( mUrl ) );
				}else{
					mTarget = Glide.with( getContext() )
						.load( mUrl )
						.into( new MyTargetGif( mUrl ) );
					
				}
			}else{
				mTarget = Glide.with( getContext() )
					.load( mUrl )
					.asBitmap()
					.into( new MyTarget( mUrl, desiredWidth, desiredHeight ) );
				
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			// java.lang.IllegalArgumentException:
			//			at com.bumptech.glide.manager.RequestManagerRetriever.assertNotDestroyed(RequestManagerRetriever.java:134)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:102)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:87)
			//			at com.bumptech.glide.Glide.with(Glide.java:657)
		}
	}
	
	private interface UrlTarget {
		@NonNull String getUrl();
	}
	
	private class MyTarget extends SimpleTarget< Bitmap > implements UrlTarget {
		
		@NonNull final String url;
		
		@Override @NonNull public String getUrl(){
			return url;
		}
		
		MyTarget( @NonNull String url, int desiredWidth, int desiredHeight ){
			super( desiredWidth, desiredHeight );
			this.url = url;
		}
		
		@Override public void onLoadFailed( Exception e, Drawable errorDrawable ){
			try{
				// このViewは別の画像を表示するように指定が変わっていた
				if( ! url.equals( mUrl ) ) return;
				
				e.printStackTrace();
				if( mErrorImageId != 0 ) setImageResource( mErrorImageId );
			}catch( Throwable ex ){
				ex.printStackTrace();
				// java.lang.NullPointerException:
				// at jp.juggler.subwaytooter.view.MyNetworkImageView$1.onLoadFailed(MyNetworkImageView.java:147)
				// at com.bumptech.glide.request.GenericRequest.setErrorPlaceholder(GenericRequest.java:404)
				// at com.bumptech.glide.request.GenericRequest.onException(GenericRequest.java:548)
				// at com.bumptech.glide.load.engine.EngineJob.handleExceptionOnMainThread(EngineJob.java:183)
			}
		}
		
		@Override public void onResourceReady(
			final Bitmap bitmap
			, final GlideAnimation< ? super Bitmap > glideAnimation
		){
			try{
				// このViewは別の画像を表示するように指定が変わっていた
				if( ! url.equals( mUrl ) ) return;
				
				if( mCornerRadius <= 0f ){
					setImageBitmap( bitmap );
				}else{
					RoundedBitmapDrawable d = RoundedBitmapDrawableFactory
						.create( getResources(), bitmap );
					d.setCornerRadius( mCornerRadius );
					setImageDrawable( d );
				}
				
				//				if( glideAnimation != null ){
				//					glideAnimation.animate(  )
				//				}
				
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
	}
	
	private class MyTargetGif
		extends ImageViewTarget< GlideDrawable >
		implements UrlTarget
	
	{
		private static final float SQUARE_RATIO_MARGIN = 0.05f;
		private int maxLoopCount = GlideDrawable.LOOP_FOREVER;
		private GlideDrawable glide_drawable;
		
		@NonNull final String url;
		
		@Override @NonNull public String getUrl(){
			return url;
		}
		
		MyTargetGif( @NonNull String url ){
			super( MyNetworkImageView.this );
			this.url = url;
		}
		
		@Override public void onLoadFailed( Exception e, Drawable errorDrawable ){
			try{
				// このViewは別の画像を表示するように指定が変わっていた
				if( ! url.equals( mUrl ) ) return;
				
				e.printStackTrace();
				if( mErrorImageId != 0 ) setImageResource( mErrorImageId );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		
		/**
		 * {@inheritDoc}
		 * If no {@link com.bumptech.glide.request.animation.GlideAnimation} is given or if the animation does not set the
		 * {@link android.graphics.drawable.Drawable} on the view, the drawable is set using
		 * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
		 *
		 * @param resource  {@inheritDoc}
		 * @param animation {@inheritDoc}
		 */
		@Override
		public void onResourceReady(
			GlideDrawable resource
			, GlideAnimation< ? super GlideDrawable > animation
		){
			try{
				// このViewは別の画像を表示するように指定が変わっていた
				if( ! url.equals( mUrl ) ) return;
				
				// ディスクキャッシュから読んだ画像は角丸が正しく扱われない
				// transformを設定しなおす
				if( ( resource instanceof GifDrawable ) && ! ( resource instanceof MyGifDrawable ) ){
					GifDrawable src = (GifDrawable) resource;
					if( app_context != null && mCornerRadius > 0f ){
						RoundTransformation t = new RoundTransformation( mCornerRadius );
						try{
							BitmapPool pool = Glide.get( app_context ).getBitmapPool();
							Bitmap first_frame =  t.transform( pool, src.getFirstFrame(), getWidth(), getHeight() );
							resource = new MyGifDrawable( src,first_frame, t );
						}catch( Throwable ex ){
							ex.printStackTrace();
							// view#getWidth() が 0 だと firstFrame の transform がnullを返してGifDrawableを作れない
							resource = new MyGifDrawable( src,src.getFirstFrame(), t );
						}
					}
				}
				
				if( ! resource.isAnimated() ){
					//TODO: Try to generalize this to other sizes/shapes.
					// This is a dirty hack that tries to make loading square thumbnails and then square full images less costly
					// by forcing both the smaller thumb and the larger version to have exactly the same intrinsic dimensions.
					// If a drawable is replaced in an ImageView by another drawable with different intrinsic dimensions,
					// the ImageView requests a layout. Scrolling rapidly while replacing thumbs with larger images triggers
					// lots of these calls and causes significant amounts of jank.
					float viewRatio = view.getWidth() / (float) view.getHeight();
					float drawableRatio = resource.getIntrinsicWidth() / (float) resource.getIntrinsicHeight();
					if( Math.abs( viewRatio - 1f ) <= SQUARE_RATIO_MARGIN
						&& Math.abs( drawableRatio - 1f ) <= SQUARE_RATIO_MARGIN ){
						resource = new SquaringDrawable( resource, view.getWidth() );
					}
				}
				super.onResourceReady( resource, animation );
				this.glide_drawable = resource;
				resource.setLoopCount( maxLoopCount );
				resource.start();
				
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		
		/**
		 * Sets the drawable on the view using
		 * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
		 *
		 * @param resource The {@link android.graphics.drawable.Drawable} to display in the view.
		 */
		@Override
		protected void setResource( GlideDrawable resource ){
			// GifDrawable かもしれない
			MyNetworkImageView.this.setImageDrawable( resource );
		}
		
		@Override
		public void onStart(){
			log.d( "MyTargetGif onStart glide_drawable=%s", glide_drawable );
			if( glide_drawable != null ){
				glide_drawable.start();
			}
		}
		
		@Override
		public void onStop(){
			log.d( "MyTargetGif onStop glide_drawable=%s", glide_drawable );
			if( glide_drawable != null ){
				glide_drawable.stop();
			}
		}
		
		@Override
		public void onDestroy(){
			log.d( "MyTargetGif onDestroy glide_drawable=%s", glide_drawable );
			super.onDestroy();
		}
		
	}
	
	private static class RoundTransformation extends BitmapTransformation {
		private final float radius;
		
		RoundTransformation( float radius ){
			super( app_context );
			this.radius = radius;
			mPaint.setAntiAlias( true );
			mPaint.setFilterBitmap( true );
		}
		
		@Override public String getId(){
			return getClass().getName();
		}
		
		final Matrix mShaderMatrix = new Matrix();
		final Rect mViewContainer = new Rect();
		final Rect mDstRect = new Rect();
		final RectF mDstRectF = new RectF();
		final Paint mPaint = new Paint( Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG );
		
		@Override
		protected Bitmap transform( BitmapPool pool, Bitmap source, int outWidth, int outHeight ){
			if( source == null ) return null;
			int src_w = source.getWidth();
			int src_h = source.getHeight();
			if( src_w < 1 || src_h < 1 ) return null;
			if( outWidth < 1 || outHeight < 1 ) return null;
			
			int mTargetDensity = DisplayMetrics.DENSITY_DEFAULT;
			int mBitmapWidth = source.getScaledWidth( mTargetDensity );
			int mBitmapHeight = source.getScaledHeight( mTargetDensity );
			
			mViewContainer.left = 0;
			mViewContainer.top = 0;
			mViewContainer.right = outWidth;
			mViewContainer.bottom = outHeight;
			
			int mGravity = Gravity.FILL;
			Gravity.apply( mGravity, mBitmapWidth, mBitmapHeight, mViewContainer, mDstRect, View.LAYOUT_DIRECTION_LTR );
			mDstRectF.set( mDstRect );
			
			mShaderMatrix.setTranslate( mDstRectF.left, mDstRectF.top );
			mShaderMatrix.preScale( mDstRectF.width() / src_w, mDstRectF.height() / src_h );
			
			BitmapShader mBitmapShader = new BitmapShader( source, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP );
			mBitmapShader.setLocalMatrix( mShaderMatrix );
			
			Bitmap result = pool.getDirty( outWidth, outHeight, Bitmap.Config.ARGB_8888 );
			if( result == null ){
				result = Bitmap.createBitmap( outWidth, outHeight, Bitmap.Config.ARGB_8888 );
			}
			Canvas canvas = new Canvas( result );
			canvas.drawColor( Color.TRANSPARENT, PorterDuff.Mode.CLEAR );
			mPaint.setShader( mBitmapShader );
			// mPaint.setColor( 0xffff0000 );
			canvas.drawRoundRect( mDstRectF, radius, radius, mPaint );
			
			//	log.d("transform radius=%.2f,outWidth=%d,outHeight=%d",radius,outWidth,outHeight);
			
			//			int dst_wh = Math.min( src_w,src_h );
			//			int offset_x = ( src_w - dst_wh ) / 2;
			//			int offset_y = ( src_h - dst_wh ) / 2;
			//
			//
			//			// TODO this could be acquired from the pool too
			//			pool.
			//			Bitmap squared = Bitmap.createBitmap( source, x, y, size, size );
			//
			//
			//			Paint paint = new Paint();
			//			paint.setShader( new BitmapShader( squared, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP ) );
			//			paint.setAntiAlias( true );
			//			float r = size / 2f;
			//			canvas.drawCircle( r, r, r, paint );
			//			// canvas.drawRoundRect( float left, float top, float right, float bottom, float rx, float ry,
			
			return result;
		}
	}
	
	final Runnable proc_load_image = new Runnable() {
		@Override public void run(){
			loadImageIfNecessary();
		}
	};
	
	@Override protected void onSizeChanged( int w, int h, int oldw, int oldh ){
		super.onSizeChanged( w, h, oldw, oldh );
		post( proc_load_image );
	}
	
	@Override protected void onLayout( boolean changed, int left, int top, int right, int bottom ){
		super.onLayout( changed, left, top, right, bottom );
		post( proc_load_image );
	}
	
	@Override protected void onDetachedFromWindow(){
		cancelLoading();
		super.onDetachedFromWindow();
	}
	
	@Override protected void onAttachedToWindow(){
		super.onAttachedToWindow();
		loadImageIfNecessary();
	}
	
	@Override protected void drawableStateChanged(){
		super.drawableStateChanged();
		invalidate();
	}
	
	Drawable media_type_drawable;
	int media_type_bottom;
	int media_type_left;
	
	public void setMediaType( int drawable_id ){
		if( drawable_id == 0 ){
			media_type_drawable = null;
		}else{
			media_type_drawable = ContextCompat.getDrawable( getContext(), drawable_id ).mutate();
			// DisplayMetrics dm = getResources().getDisplayMetrics();
			media_type_bottom = 0;
			media_type_left = 0;
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
