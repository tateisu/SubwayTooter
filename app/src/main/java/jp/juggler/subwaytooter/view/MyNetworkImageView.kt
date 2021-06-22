package jp.juggler.subwaytooter.view

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.load.resource.gif.MyGifDrawable
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import jp.juggler.subwaytooter.PrefB
import jp.juggler.util.LogCategory
import jp.juggler.util.clipRange

class MyNetworkImageView : AppCompatImageView {

    companion object {

        internal val log = LogCategory("MyNetworkImageView")
    }

    // ロード中などに表示するDrawableのリソースID
    private var mDefaultImage: Drawable? = null

    // エラー時に表示するDrawableのリソースID
    private var mErrorImage: Drawable? = null

    // 角丸の半径。元画像の短辺に対する割合を指定するらしい
    internal var mCornerRadius = 0f

    // 表示したい画像のURL
    private var mUrl: String? = null
    private var mMayGif: Boolean = false

    // 非同期処理のキャンセル
    private var mTarget: Target<*>? = null

    private val procLoadImage: Runnable = Runnable { loadImageIfNecessary() }
    private val procFocusPoint: Runnable = Runnable { updateFocusPoint() }

    private var mediaTypeDrawable1: Drawable? = null
    private var mediaTypeBottom = 0
    private var mediaTypeLeft = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setDefaultImage(defaultImage: Drawable?) {
        mDefaultImage = defaultImage
        loadImageIfNecessary()
    }

    fun setErrorImage(errorImage: Drawable?) {
        mErrorImage = errorImage
        loadImageIfNecessary()
    }

    fun setImageUrl(
        pref: SharedPreferences,
        r: Float,
        url: String?,
        gifUrlArg: String? = null,
    ) {

        mCornerRadius = r

        val gifUrl = if (PrefB.bpEnableGifAnimation(pref)) gifUrlArg else null

        if (gifUrl?.isNotEmpty() == true) {
            mUrl = gifUrl
            mMayGif = true
        } else {
            mUrl = url
            mMayGif = false
        }
        loadImageIfNecessary()
    }

    private fun getGlide(): RequestManager? {
        try {
            return Glide.with(context)
        } catch (ex: IllegalArgumentException) {
            if (ex.message?.contains("destroyed activity") == true) {
                // ignore it
            } else {
                log.e(ex, "Glide.with() failed.")
            }
        } catch (ex: Throwable) {
            log.e(ex, "Glide.with() failed.")
        }
        return null
    }

    fun cancelLoading(defaultDrawable: Drawable? = null) {

        val d = drawable
        if (d is Animatable) {
            if (d.isRunning) {
                //warning.d("cancelLoading: Animatable.stop()")
                d.stop()
            }
        }

        setImageDrawable(defaultDrawable)

        val target = mTarget
        if (target != null) {
            try {
                getGlide()?.clear(target)
            } catch (ex: Throwable) {
                log.e(ex, "Glide.clear() failed.")
            }

            mTarget = null
        }
    }

    // 必要なら非同期処理を開始する
    private fun loadImageIfNecessary() {
        try {
            val url = mUrl
            if (url?.isEmpty() != false) {
                // if the URL to be loaded in this view is empty,
                // cancel any old requests and clear the currently loaded image.
                cancelLoading(mDefaultImage)
                return
            }

            // すでにリクエストが発行済みで、リクエストされたURLが同じなら何もしない
            if ((mTarget as? UrlTarget)?.urlLoading == url) return

            // if there is a pre-existing request, cancel it if it's fetching a different URL.
            cancelLoading(mDefaultImage)

            // 非表示状態ならロードを延期する
            if (!isShown) return

            var wrapWidth = false
            var wrapHeight = false
            val lp = layoutParams
            if (lp != null) {
                wrapWidth = lp.width == ViewGroup.LayoutParams.WRAP_CONTENT
                wrapHeight = lp.height == ViewGroup.LayoutParams.WRAP_CONTENT
            }

            // Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
            val desiredWidth = if (wrapWidth) Target.SIZE_ORIGINAL else width
            val desiredHeight = if (wrapHeight) Target.SIZE_ORIGINAL else height

            if (desiredWidth != Target.SIZE_ORIGINAL && desiredWidth <= 0 ||
                desiredHeight != Target.SIZE_ORIGINAL && desiredHeight <= 0
            ) {
                // desiredWidth,desiredHeight の指定がおかしいと非同期処理中にSimpleTargetが落ちる
                // おそらくレイアウト後に再度呼び出される
                return
            }

            val glideHeaders = LazyHeaders.Builder()
                .addHeader("Accept", "image/webp,image/*,*/*;q=0.8")
                .build()

            val glideUrl = GlideUrl(url, glideHeaders)

            mTarget = if (mMayGif) {
                getGlide()
                    ?.load(glideUrl)
                    ?.into(MyTargetGif(url))
            } else {
                getGlide()
                    ?.load(glideUrl)
                    ?.into(MyTarget(url))
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun replaceGifDrawable(resource: GifDrawable): Drawable {
        // ディスクキャッシュから読んだ画像は角丸が正しく扱われない
        // MyGifDrawable に差し替えて描画させる
        try {
            return MyGifDrawable(resource, mCornerRadius)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
        return resource
    }

    private fun replaceBitmapDrawable(resource: BitmapDrawable): Drawable {
        try {
            val bitmap = resource.bitmap
            if (bitmap != null) return replaceBitmapDrawable(bitmap)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
        return resource
    }

    private fun replaceBitmapDrawable(bitmap: Bitmap): Drawable {
        val d = RoundedBitmapDrawableFactory.create(resources, bitmap)
        d.cornerRadius = mCornerRadius
        return d
    }

    private fun onLoadFailed(urlLoading: String) {
        try {
            // 別の画像を表示するよう指定が変化していたなら何もしない
            if (urlLoading != mUrl) return

            // エラー表示用の画像リソースが指定されていたら使う
            when (val drawable = mErrorImage) {
                null -> {
                    // このタイミングでImageViewのDrawableを変更するとチラつきの元になるので何もしない
                }

                else -> setImageDrawable(drawable)
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private interface UrlTarget {

        val urlLoading: String
    }

    // 静止画用のターゲット
    private inner class MyTarget(
        override val urlLoading: String,
    ) : ImageViewTarget<Drawable>(this@MyNetworkImageView), UrlTarget {

        // errorDrawable The error drawable to optionally show, or null.
        override fun onLoadFailed(errorDrawable: Drawable?) {
            onLoadFailed(urlLoading)
        }

        override fun setResource(resource: Drawable?) {
            try {
                // 別の画像を表示するよう指定が変化していたなら何もしない
                if (urlLoading != mUrl) return

                if (mCornerRadius > 0f) {
                    if (resource is BitmapDrawable) {
                        // BitmapDrawableは角丸処理が可能。
                        setImageDrawable(replaceBitmapDrawable(resource.bitmap))
                        return
                    }
                    // その他のDrawable
                    // たとえばInstanceTickerのアイコンにSVGが使われていたらPictureDrawableになる
                    log.w("cornerRadius=$mCornerRadius,drawable=$resource,url=$urlLoading")
                }

                setImageDrawable(resource)
                return
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }
    }

    private inner class MyTargetGif(
        override val urlLoading: String,
    ) : ImageViewTarget<Drawable>(this@MyNetworkImageView), UrlTarget {

        private var glideDrawable: Drawable? = null

        override fun onLoadFailed(errorDrawable: Drawable?) = onLoadFailed(urlLoading)

        override fun onResourceReady(
            drawable: Drawable,
            transition: Transition<in Drawable>?,
        ) {
            try {
                // 別の画像を表示するよう指定が変化していたなら何もしない
                if (urlLoading != mUrl) return

                afterResourceReady(
                    transition,
                    when {
                        mCornerRadius <= 0f -> {
                            // 角丸でないならそのまま使う
                            drawable
                        }

                        // GidDrawableを置き換える
                        drawable is GifDrawable -> replaceGifDrawable(drawable)

                        // Glide 4.xから、静止画はBitmapDrawableになった
                        drawable is BitmapDrawable -> replaceBitmapDrawable(drawable)

                        else -> {
                            log.d("onResourceReady: drawable class=${drawable.javaClass.simpleName}")
                            drawable
                        }
                    }
                )
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        private fun afterResourceReady(transition: Transition<in Drawable>?, drawable: Drawable) {
            super.onResourceReady(drawable, transition)

            //if( ! drawable.isAnimated() ){
            //    //XXX: Try to generalize this to other sizes/shapes.
            //    // This is a dirty hack that tries to make loading square thumbnails and then square full images less costly
            //    // by forcing both the smaller thumb and the larger version to have exactly the same intrinsic dimensions.
            //    // If a drawable is replaced in an ImageView by another drawable with different intrinsic dimensions,
            //    // the ImageView requests a layout. Scrolling rapidly while replacing thumbs with larger images triggers
            //    // lots of these calls and causes significant amounts of junk.
            //    float viewRatio = view.getWidth() / (float) view.getHeight();
            //    float drawableRatio = drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
            //    if( Math.abs( viewRatio - 1f ) <= SQUARE_RATIO_MARGIN
            //        && Math.abs( drawableRatio - 1f ) <= SQUARE_RATIO_MARGIN ){
            //        drawable = new SquaringDrawable( drawable, view.getWidth() );
            //    }
            //}

            this.glideDrawable = drawable
            if (drawable is GifDrawable) {
                drawable.setLoopCount(GifDrawable.LOOP_FOREVER)
                drawable.start()
            } else if (drawable is MyGifDrawable) {
                drawable.setLoopCount(GifDrawable.LOOP_FOREVER)
                drawable.start()
            }
        }

        // super.onResourceReady から呼ばれる
        override fun setResource(drawable: Drawable?) {
            setImageDrawable(drawable)
        }

        override fun onStart() {
            val drawable = glideDrawable
            if (drawable is Animatable && !drawable.isRunning) {
                log.d("MyTargetGif onStart glide_drawable=$drawable")
                drawable.start()
            }
        }

        override fun onStop() {
            val drawable = glideDrawable
            if (drawable is Animatable && drawable.isRunning) {
                log.d("MyTargetGif onStop glide_drawable=$drawable")
                drawable.stop()
            }
        }

        override fun onDestroy() {
            val drawable = glideDrawable
            log.d("MyTargetGif onDestroy glide_drawable=$drawable")
            super.onDestroy()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post(procLoadImage)
        post(procFocusPoint)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post(procLoadImage)
    }

    override fun onDetachedFromWindow() {
        cancelLoading(null)
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        loadImageIfNecessary()
    }

    fun setMediaType(drawableId: Int) {
        if (drawableId == 0) {
            mediaTypeDrawable1 = null
        } else {
            mediaTypeDrawable1 = ContextCompat.getDrawable(context, drawableId)?.mutate()
            // DisplayMetrics dm = getResources().getDisplayMetrics();
            mediaTypeBottom = 0
            mediaTypeLeft = 0
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        // bitmapがrecycledされた場合に例外をキャッチする
        try {
            super.onDraw(canvas)
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        // media type の描画
        val mediaTypeDrawable = this.mediaTypeDrawable1
        if (mediaTypeDrawable != null) {
            val drawableW = mediaTypeDrawable.intrinsicWidth
            val drawableH = mediaTypeDrawable.intrinsicHeight
            // int view_w = getWidth();
            val viewH = height
            mediaTypeDrawable.setBounds(
                0,
                viewH - drawableH,
                drawableW,
                viewH
            )
            mediaTypeDrawable.draw(canvas)
        }
    }

    /////////////////////////////////////////////////////////////////////

    // プロフ表示の背景画像のレイアウト崩れの対策
    var measureProfileBg = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (measureProfileBg) {
            // このモードではコンテンツを一切見ずにサイズを決める
            val wSize = MeasureSpec.getSize(widthMeasureSpec)
            val wMeasured = when (MeasureSpec.getMode(widthMeasureSpec)) {
                MeasureSpec.EXACTLY -> wSize
                MeasureSpec.AT_MOST -> wSize
                MeasureSpec.UNSPECIFIED -> 0
                else -> 0
            }
            val hSize = MeasureSpec.getSize(heightMeasureSpec)
            val hMeasured = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> hSize
                MeasureSpec.AT_MOST -> hSize
                MeasureSpec.UNSPECIFIED -> 0
                else -> 0
            }
            setMeasuredDimension(wMeasured, hMeasured)
        } else {
            // 通常のImageViewは内容を見てサイズを決める
            // たとえLayoutParamがw,hともmatchParentでも内容を見てしまう
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /////////////////////////////////////////////////////////////////////

    private var focusX: Float = 0f
    private var focusY: Float = 0f

    fun setFocusPoint(focusX: Float, focusY: Float) {
        // フォーカスポイントは上がプラスで下がマイナス
        // https://github.com/jonom/jquery-focuspoint#1-calculate-your-images-focus-point
        // このタイミングで正規化してしまう

        this.focusX = clipRange(-1f, 1f, focusX)
        this.focusY = -clipRange(-1f, 1f, focusY)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        updateFocusPoint()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateFocusPoint()
    }

    private fun updateFocusPoint() {

        // ビューのサイズが0より大きい
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // 画像のサイズが0より大きい
        val drawable = this.drawable ?: return
        val drawableW = drawable.intrinsicWidth.toFloat()
        val drawableH = drawable.intrinsicHeight.toFloat()
        if (drawableW <= 0f || drawableH <= 0f) return

        when (scaleType) {
            ScaleType.CENTER_CROP, ScaleType.MATRIX -> {
                val viewAspect = viewW / viewH
                val drawableAspect = drawableW / drawableH

                if (drawableAspect >= viewAspect) {
                    // ビューより画像の方が横長
                    val focusX1 = this.focusX
                    if (focusX1 == 0f) {
                        scaleType = ScaleType.CENTER_CROP
                    } else {
                        val matrix = Matrix()
                        val scale = viewH / drawableH
                        val delta = focusX1 * ((drawableW * scale) - viewW)
                        log.d("updateFocusPoint x delta=$delta")
                        matrix.postTranslate(drawableW / -2f, drawableH / -2f)
                        matrix.postScale(scale, scale)
                        matrix.postTranslate((viewW - delta) / 2f, viewH / 2f)
                        scaleType = ScaleType.MATRIX
                        imageMatrix = matrix
                    }
                } else {
                    // ビューより画像の方が縦長
                    val focusY1 = this.focusY
                    if (focusY1 == 0f) {
                        scaleType = ScaleType.CENTER_CROP
                    } else {
                        val matrix = Matrix()
                        val scale = viewW / drawableW
                        val delta = focusY1 * ((drawableH * scale) - viewH)
                        matrix.postTranslate(drawableW / -2f, drawableH / -2f)
                        matrix.postScale(scale, scale)
                        matrix.postTranslate(viewW / 2f, (viewH - delta) / 2f)
                        scaleType = ScaleType.MATRIX
                        imageMatrix = matrix
                    }
                }
            }

            else -> {
                // not supported.
            }
        }
    }

    fun setScaleTypeForMedia() {
        when (scaleType) {
            ScaleType.CENTER_CROP, ScaleType.MATRIX -> {
                // nothing to do
            }

            else -> {
                scaleType = ScaleType.CENTER_CROP
            }
        }
    }
}
