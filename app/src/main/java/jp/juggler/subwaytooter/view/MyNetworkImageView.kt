package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory

import android.util.AttributeSet
import android.view.ViewGroup
import android.support.v7.widget.AppCompatImageView

import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
//import com.bumptech.glide.load.resource.bitmap.MyGlideBitmapDrawable
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.load.resource.gif.MyGifDrawable
// import com.bumptech.glide.request.RequestOptions
//import com.bumptech.glide.load.resource.gif.MyGifDrawable
import com.bumptech.glide.request.target.BaseTarget
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition

import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.util.LogCategory

class MyNetworkImageView @JvmOverloads constructor(
	context : Context,
	attrs : AttributeSet? = null,
	defStyle : Int = 0
) : AppCompatImageView(context, attrs, defStyle) {
	
	
	companion object {
		
		internal val log = LogCategory("MyNetworkImageView")
		
		@SuppressLint("StaticFieldLeak") internal var app_context : Context? = null
		
		//		private const val SQUARE_RATIO_MARGIN = 0.05f
		//		private const val maxLoopCount = GifDrawable.LOOP_FOREVER
	}
	
	// ロード中などに表示するDrawableのリソースID
	private var mDefaultImageId : Int = 0
	
	// エラー時に表示するDrawableのリソースID
	private var mErrorImageId : Int = 0
	
	// 角丸の半径。元画像の短辺に対する割合を指定するらしい
	internal var mCornerRadius : Float = 0.toFloat()
	
	// 表示したい画像のURL
	private var mUrl : String? = null
	private var mMayGif : Boolean = false
	
	// 非同期処理のキャンセル
	private var mTarget : BaseTarget<*>? = null
	
	private val proc_load_image : Runnable = Runnable { loadImageIfNecessary() }
	
	private var media_type_drawable : Drawable? = null
	private var media_type_bottom : Int = 0
	private var media_type_left : Int = 0
	
	fun setDefaultImageResId(defaultImage : Int) {
		mDefaultImageId = defaultImage
		loadImageIfNecessary()
	}
	
	fun setErrorImageResId(errorImage : Int) {
		mErrorImageId = errorImage
		loadImageIfNecessary()
	}
	
	@JvmOverloads
	fun setImageUrl(pref : SharedPreferences, r : Float, url : String?, gifUrlArg : String? = null) {
		
		if(app_context == null) {
			val context = context
			if(context != null) {
				app_context = context.applicationContext
			}
		}
		
		
		mCornerRadius = if(Pref.bpDontRound(pref)) 0f else r
		
		val gif_url = if(Pref.bpEnableGifAnimation(pref)) gifUrlArg else null
		
		if(gif_url != null && gif_url.isNotEmpty()) {
			mUrl = gif_url
			mMayGif = true
		} else {
			mUrl = url
			mMayGif = false
		}
		loadImageIfNecessary()
	}
	
	private fun getGLide() : RequestManager? {
		try {
			return Glide.with(context)
		} catch(ex : IllegalArgumentException) {
			if(ex.message?.contains("destroyed activity") == true) {
				// ignore it
			} else {
				log.e(ex, "Glide.with() failed.")
			}
		} catch(ex : Throwable) {
			log.e(ex, "Glide.with() failed.")
		}
		return null
	}
	
	private fun cancelLoading() {
		val target = mTarget
		if(target != null) {
			val d = drawable
			if(d is Animatable) {
				if(d.isRunning) {
					//log.d("cancelLoading: Animatable.stop()")
					d.stop()
				}
			}
			setImageDrawable(null)
			try {
				getGLide()?.clear(target)
			} catch(ex : Throwable) {
				log.e(ex, "Glide.clear() failed.")
			}
			
			mTarget = null
		}
		
	}
	
	// デフォルト画像かnullを表示する
	private fun setDefaultImageOrNull() {
		
		val d = drawable
		if(d is Animatable) {
			if(d.isRunning) {
				log.d("setDefaultImageOrNull: Animatable.stop()")
				d.stop()
			}
		}
		
		if(mDefaultImageId != 0) {
			setImageResource(mDefaultImageId)
		} else {
			setImageDrawable(null)
		}
	}
	
	// 必要なら非同期処理を開始する
	private fun loadImageIfNecessary() {
		try {
			val url = mUrl
			if(url?.isEmpty() != false) {
				// if the URL to be loaded in this view is empty,
				// cancel any old requests and clear the currently loaded image.
				cancelLoading()
				setDefaultImageOrNull()
				return
			}
			
			// すでにリクエストが発行済みで、リクエストされたURLが同じなら何もしない
			if((mTarget as? UrlTarget)?.urlLoading == url) return
			
			// if there is a pre-existing request, cancel it if it's fetching a different URL.
			cancelLoading()
			setDefaultImageOrNull()
			
			var wrapWidth = false
			var wrapHeight = false
			val lp = layoutParams
			if(lp != null) {
				wrapWidth = lp.width == ViewGroup.LayoutParams.WRAP_CONTENT
				wrapHeight = lp.height == ViewGroup.LayoutParams.WRAP_CONTENT
			}
			
			// Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
			val desiredWidth = if(wrapWidth) Target.SIZE_ORIGINAL else width
			val desiredHeight = if(wrapHeight) Target.SIZE_ORIGINAL else height
			
			if(desiredWidth != Target.SIZE_ORIGINAL && desiredWidth <= 0
				|| desiredHeight != Target.SIZE_ORIGINAL && desiredHeight <= 0
				) {
				// desiredWidth,desiredHeight の指定がおかしいと非同期処理中にSimpleTargetが落ちる
				// おそらくレイアウト後に再度呼び出される
				return
			}
			
			mTarget = if(mMayGif) {
				getGLide()?.load(url)?.into(MyTargetGif(url))
			} else {
				getGLide()?.asBitmap()?.load(url)?.into(MyTarget(url, desiredWidth, desiredHeight))
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	private interface UrlTarget {
		val urlLoading : String
	}
	
	// 静止画用のターゲット
	private inner class MyTarget internal constructor(
		override val urlLoading : String,
		desiredWidth : Int,
		desiredHeight : Int
	) : SimpleTarget<Bitmap>(desiredWidth, desiredHeight), UrlTarget {
		
		// errorDrawable The error drawable to optionally show, or null.
		override fun onLoadFailed(errorDrawable : Drawable?) {
			try {
				// このViewは別の画像を表示するように指定が変わっていた
				if(urlLoading != mUrl) return
				
				// とりあえず今のBitmapはもう使えないらしい
				if(mErrorImageId != 0) {
					setImageResource(mErrorImageId)
				} else {
					setImageDrawable(null)
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		override fun onResourceReady(
			bitmap : Bitmap,
			transition : Transition<in Bitmap>?
		) {
			try {
				// このViewは別の画像を表示するように指定が変わっていた
				if(urlLoading != mUrl) return
				
				if(mCornerRadius <= 0f) {
					setImageBitmap(bitmap)
				} else {
					val d = RoundedBitmapDrawableFactory.create(resources, bitmap)
					d.cornerRadius = mCornerRadius
					setImageDrawable(d)
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
	}
	
	private inner class MyTargetGif internal constructor(
		override val urlLoading : String
	) : ImageViewTarget<Drawable>(this@MyNetworkImageView), UrlTarget {
		
		private var glide_drawable : Drawable? = null
		
		override fun onLoadFailed(errorDrawable : Drawable?) {
			try {
				// このViewは別の画像を表示するように指定が変わっていた
				if(urlLoading != mUrl) return
				
				// 今の画像はもう表示できない
				if(mErrorImageId != 0) {
					setImageResource(mErrorImageId)
				} else {
					setImageDrawable(null)
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		override fun onResourceReady(
			resource : Drawable,
			transition : Transition<in Drawable>?
		) {
			try {
				// このViewは別の画像を表示するように指定が変わっていた
				if(urlLoading != mUrl) return
				
				afterResourceReady(when {
					mCornerRadius <= 0f -> {
						// 角丸でないならそのまま使う
						resource
					}
				
				// GidDrawableを置き換える
					resource is GifDrawable -> replaceGifDrawable(resource)
				
				// Glide 4.xから、静止画はBitmapDrawableになった
					resource is BitmapDrawable -> {
						val bitmap = resource.bitmap
						if(bitmap == null) {
							resource
						} else {
							val d = RoundedBitmapDrawableFactory.create(resources, bitmap)
							d.cornerRadius = mCornerRadius
							d
						}
					}
					
					else -> {
						log.d("onResourceReady: drawable class=%s", resource.javaClass)
						resource
					}
				}, transition)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		private fun replaceGifDrawable(resource : GifDrawable) : Drawable {
			// ディスクキャッシュから読んだ画像は角丸が正しく扱われない
			// MyGifDrawable に差し替えて描画させる
			if(app_context != null) {
				try {
					return MyGifDrawable(resource, mCornerRadius)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			return resource
		}
		
		private fun afterResourceReady(resource : Drawable, transition : Transition<in Drawable>?) {
			super.onResourceReady(resource, transition)
			
			//				if( ! resource.isAnimated() ){
			//					//XXX: Try to generalize this to other sizes/shapes.
			//					// This is a dirty hack that tries to make loading square thumbnails and then square full images less costly
			//					// by forcing both the smaller thumb and the larger version to have exactly the same intrinsic dimensions.
			//					// If a drawable is replaced in an ImageView by another drawable with different intrinsic dimensions,
			//					// the ImageView requests a layout. Scrolling rapidly while replacing thumbs with larger images triggers
			//					// lots of these calls and causes significant amounts of jank.
			//					float viewRatio = view.getWidth() / (float) view.getHeight();
			//					float drawableRatio = resource.getIntrinsicWidth() / (float) resource.getIntrinsicHeight();
			//					if( Math.abs( viewRatio - 1f ) <= SQUARE_RATIO_MARGIN
			//						&& Math.abs( drawableRatio - 1f ) <= SQUARE_RATIO_MARGIN ){
			//						resource = new SquaringDrawable( resource, view.getWidth() );
			//					}
			//				}
			
			this.glide_drawable = resource
			if(resource is GifDrawable) {
				resource.setLoopCount(GifDrawable.LOOP_FOREVER)
				resource.start()
			}
		}
		
		override fun setResource(resource : Drawable?) {
			// GifDrawable かもしれない
			this@MyNetworkImageView.setImageDrawable(resource)
		}
		
		override fun onStart() {
			val drawable = glide_drawable
			if(drawable is Animatable && ! drawable.isRunning) {
				log.d("MyTargetGif onStart glide_drawable=%s", drawable)
				drawable.start()
			}
		}
		
		override fun onStop() {
			val drawable = glide_drawable
			if(drawable is Animatable && drawable.isRunning) {
				log.d("MyTargetGif onStop glide_drawable=%s", drawable)
				drawable.stop()
			}
		}
		
		override fun onDestroy() {
			val drawable = glide_drawable
			log.d("MyTargetGif onDestroy glide_drawable=%s", drawable)
			super.onDestroy()
		}
		
	}
	
	override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		post(proc_load_image)
	}
	
	override fun onLayout(changed : Boolean, left : Int, top : Int, right : Int, bottom : Int) {
		super.onLayout(changed, left, top, right, bottom)
		post(proc_load_image)
	}
	
	override fun onDetachedFromWindow() {
		cancelLoading()
		super.onDetachedFromWindow()
	}
	
	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		loadImageIfNecessary()
	}
	
	override fun drawableStateChanged() {
		super.drawableStateChanged()
		invalidate()
	}
	
	fun setMediaType(drawable_id : Int) {
		if(drawable_id == 0) {
			media_type_drawable = null
		} else {
			media_type_drawable = ContextCompat.getDrawable(context, drawable_id)?.mutate()
			// DisplayMetrics dm = getResources().getDisplayMetrics();
			media_type_bottom = 0
			media_type_left = 0
		}
		invalidate()
	}
	
	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		val media_type_drawable = this.media_type_drawable
		if(media_type_drawable != null) {
			val drawable_w = media_type_drawable.intrinsicWidth
			val drawable_h = media_type_drawable.intrinsicHeight
			// int view_w = getWidth();
			val view_h = height
			media_type_drawable.setBounds(
				0,
				view_h - drawable_h,
				drawable_w,
				view_h
			)
			media_type_drawable.draw(canvas)
		}
	}
	
	/////////////////////////////////////////////////////////////////////
	// プロフ表示の背景画像のレイアウト崩れの対策
	var measureProfileBg = false
	
	override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
		if(measureProfileBg) {
			val w_mode = MeasureSpec.getMode(widthMeasureSpec)
			val w_size = MeasureSpec.getSize(widthMeasureSpec)
			val h_mode = MeasureSpec.getMode(heightMeasureSpec)
			val h_size = MeasureSpec.getSize(heightMeasureSpec)
			
			val w = when(w_mode) {
				MeasureSpec.EXACTLY -> w_size
				MeasureSpec.AT_MOST -> w_size
				MeasureSpec.UNSPECIFIED ->0
				else -> 0
			}
			val h = when(h_mode) {
				MeasureSpec.EXACTLY -> h_size
				MeasureSpec.AT_MOST -> h_size
				MeasureSpec.UNSPECIFIED ->0
				else -> 0
			}
			setMeasuredDimension(w, h)
		}else{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}
}
