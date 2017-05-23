package jp.juggler.subwaytooter.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.support.v7.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;

import jp.juggler.subwaytooter.Pref;

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
	
	public void setCornerRadius( SharedPreferences pref, float r ){
		if( ! pref.getBoolean( Pref.KEY_DONT_ROUND, false ) ){
			mCornerRadius = r;
		}
	}
	
	// 表示したい画像のURL
	private String mUrl;
	
	public void setImageUrl( String url ){
		mUrl = url;
		loadImageIfNecessary();
	}
	
	// 非同期処理のキャンセル
	MyTarget mTarget;
	
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
			
			if( mTarget != null && mUrl.equals( mTarget.url ) ){
				// すでにリクエストが発行済みで、リクエストされたURLが同じなら何もしない
				return;
			}
			
			// if there is a pre-existing request, cancel it if it's fetching a different URL.
			cancelLoading();
			setDefaultImageOrNull();
			
			boolean wrapWidth = false, wrapHeight = false;
			if( getLayoutParams() != null ){
				wrapWidth = getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT;
				wrapHeight = getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT;
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
			
			mTarget = Glide.with( getContext() )
				.load( mUrl )
				.asBitmap()
				.into( new MyTarget( mUrl, desiredWidth, desiredHeight ) );
		}catch( Throwable ex ){
			ex.printStackTrace();
			// java.lang.IllegalArgumentException:
			//			at com.bumptech.glide.manager.RequestManagerRetriever.assertNotDestroyed(RequestManagerRetriever.java:134)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:102)
			//			at com.bumptech.glide.manager.RequestManagerRetriever.get(RequestManagerRetriever.java:87)
			//			at com.bumptech.glide.Glide.with(Glide.java:657)
		}
	}
	
	private class MyTarget extends SimpleTarget< Bitmap > {
		
		@NonNull final String url;
		
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
