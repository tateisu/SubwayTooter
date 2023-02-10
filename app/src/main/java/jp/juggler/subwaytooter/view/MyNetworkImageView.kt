package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.*
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.load.resource.gif.MyGifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.github.penfeizhou.animation.apng.APNGDrawable
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.clip
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import java.io.File

class MyNetworkImageView : AppCompatImageView {

    companion object {
        private val log = LogCategory("MyNetworkImageView")
        private val listenerDrawable = MyRequestListener<Drawable>()
        private val listenerBitmap = MyRequestListener<Bitmap>()
        private val listenerFile = MyRequestListener<File>()
    }

    class MyRequestListener<T> : RequestListener<T> {
        override fun onResourceReady(
            resource: T,
            model: Any?,
            target: Target<T>?,
            dataSource: DataSource?,
            isFirstResource: Boolean,
        ): Boolean {
            return false // Allow calling onResourceReady on the Target.
        }

        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<T>?,
            isFirstResource: Boolean,
        ): Boolean {
            e?.let {
                log.e(it, "onLoadFailed")
                it.rootCauses?.forEach { cause ->
                    val message = cause?.message
                    when {
                        cause == null -> Unit
                        message?.contains("setDataSource failed: status") == true ||
                                message?.contains("etDataSourceCallback failed: status") == true
                        -> log.w(message)
                        else -> log.e(cause, "caused by")
                    }
                }
            }
            return false // Allow calling onLoadFailed on the Target.
        }
    }

    // ロード中などに表示するDrawableのリソースID
    private var mDefaultImage: Drawable? = null

    // エラー時に表示するDrawableのリソースID
    private var mErrorImage: Drawable? = null

    // 角丸の半径。元画像の短辺に対する割合を指定するらしい
    private var mCornerRadius = 0f

    // 表示したい画像のURL
    private var mUrl: String? = null
    private var mMayAnime: Boolean = false

    // 非同期処理のキャンセル
    private var mTarget: Target<*>? = null

    private val procLoadImage: Runnable = Runnable { loadImageIfNecessary() }
    private val procFocusPoint: Runnable = Runnable { updateFocusPoint() }

    private var mediaTypeDrawable1: Drawable? = null
    private var mediaTypeBottom = 0
    private var mediaTypeLeft = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setDefaultImage(defaultImage: Drawable?) {
        mDefaultImage = defaultImage
        loadImageIfNecessary()
    }

    fun setErrorImage(errorImage: Drawable?) {
        mErrorImage = errorImage
        loadImageIfNecessary()
    }

    fun setImageUrl(
        r: Float,
        urlStatic: String?,
        urlAnime: String? = null,
    ) {
        mCornerRadius = r
        if (PrefB.bpImageAnimationEnable.value) {
            urlAnime?.notEmpty()?.let {
                mUrl = it
                mMayAnime = true
                loadImageIfNecessary()
                return
            }
        }
        mUrl = urlStatic
        mMayAnime = false
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

    fun cancelLoading(defaultDrawable: Drawable? = mDefaultImage) {

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

            updatePath()

            val glideHeaders = LazyHeaders.Builder()
                .addHeader("Accept", "image/webp,image/*,*/*;q=0.8")
                .build()

            val glideUrl = GlideUrl(url, glideHeaders)

            mTarget = getGlide()
                ?.load(glideUrl)
                ?.listener(listenerDrawable)
                ?.into(MyImageViewTarget(url))
        } catch (ex: Throwable) {
            log.e(ex, "loadImageIfNecessary failed.")
        }
    }

    private fun replaceGifDrawable(resource: GifDrawable): Drawable {
        // ディスクキャッシュから読んだ画像は角丸が正しく扱われない
        // MyGifDrawable に差し替えて描画させる
        try {
            return MyGifDrawable(resource, mCornerRadius)
        } catch (ex: Throwable) {
            log.e(ex, "replaceGifDrawable failed.")
        }
        return resource
    }

    private fun replaceBitmapDrawable(resource: BitmapDrawable): Drawable {
        try {
            resource.bitmap?.let { return replaceBitmapDrawable(it) }
        } catch (ex: Throwable) {
            log.e(ex, "replaceBitmapDrawable failed.")
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
                // このタイミングでImageViewのDrawableを変更するとチラつきの元になるので何もしない
                null -> Unit

                else -> setImageDrawable(drawable)
            }
        } catch (ex: Throwable) {
            log.e(ex, "onLoadFailed/setImageDrawable failed.")
        }
    }

    private interface UrlTarget {
        val urlLoading: String
    }

    private inner class MyImageViewTarget(
        override val urlLoading: String,
    ) : ImageViewTarget<Drawable>(this@MyNetworkImageView), UrlTarget {

        private var glideDrawable: Drawable? = null

        override fun onLoadFailed(errorDrawable: Drawable?) = onLoadFailed(urlLoading)

        override fun onResourceReady(
            drawable: Drawable,
            transition: Transition<in Drawable>?,
        ) {
            try {
                when {
                    // ロード中に表示対象が変わった
                    urlLoading != mUrl -> return

                    drawable is Animatable && !mMayAnime -> {
                        // アニメーションするDrawableを止めたいので、ビットマップに変換する
                        // URL指定でないと色々あるので、やや無駄だが再ロード
                        // Handlerを通さないとGlideに怒られる
                        post {
                            if (urlLoading != mUrl) return@post
                            mTarget = getGlide()
                                ?.asBitmap()
                                ?.load(urlLoading)
                                ?.listener(listenerBitmap)
                                ?.into(MyImageViewTargetBitmap(urlLoading))
                        }
                    }

                    // その他はDrawableのままビューにセットする
                    else -> {
                        glideDrawable = drawable
                        if (drawable is Animatable) {
                            when (drawable) {
                                is GifDrawable -> drawable.setLoopCount(GifDrawable.LOOP_FOREVER)
                                is MyGifDrawable -> drawable.setLoopCount(GifDrawable.LOOP_FOREVER)
                                is APNGDrawable -> drawable.setLoopLimit(0)
                                // WebPは AnimatedImageDrawable (APIレベル28以降)
                            }
                            drawable.start()
                        }
                        super.onResourceReady(drawable, transition)
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "onResourceReady failed.")
            }
        }

        //  super.onResourceReady から呼ばれる
        override fun setResource(drawable: Drawable?) {
            setImageDrawable(drawable)
        }

        override fun onStart() {
            val drawable = glideDrawable
            if (drawable is Animatable && !drawable.isRunning && mMayAnime) {
                log.d("MyImageViewTarget onStart glide_drawable=$drawable")
                drawable.start()
            }
        }

        override fun onStop() {
            val drawable = glideDrawable
            if (drawable is Animatable && drawable.isRunning) {
                log.d("MyImageViewTarget onStop glide_drawable=$drawable")
                drawable.stop()
            }
        }
    }

    private inner class MyImageViewTargetBitmap(
        override val urlLoading: String,
    ) : ImageViewTarget<Bitmap>(this@MyNetworkImageView), UrlTarget {
        override fun onLoadFailed(errorDrawable: Drawable?) = onLoadFailed(urlLoading)
        override fun onResourceReady(
            drawable: Bitmap,
            transition: Transition<in Bitmap>?,
        ) {
            try {
                if (urlLoading != mUrl) return
                super.onResourceReady(drawable, transition)
            } catch (ex: Throwable) {
                log.e(ex, "onResourceReady failed.")
            }
        }

        override fun setResource(resource: Bitmap?) {
            setImageBitmap(resource)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pathRect.right = w.toFloat()
        pathRect.bottom = h.toFloat()
        updatePath()
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
            log.e(ex, "onDraw failed.")
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

        this.focusX = focusX.clip(-1f, 1f)
        this.focusY = focusY.clip(-1f, 1f).times(-1)
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

    private val path = Path()
    private val pathRect = RectF() // onSizeChangedでW,Hが与えられる
    private fun updatePath() {
        path.reset()
        val r = mCornerRadius
        if (r > 0f) {
            path.addRoundRect(pathRect, r, r, Path.Direction.CW)
        }
    }

    override fun draw(canvas: Canvas?) {
        canvas ?: return
        when (path.isEmpty) {
            true -> super.draw(canvas)
            else -> {
                // API18からハードウェアアクセラレーションが効く
                val save = canvas.save()
                canvas.clipPath(path)
                super.draw(canvas)
                canvas.restoreToCount(save)
            }
        }
    }
}
