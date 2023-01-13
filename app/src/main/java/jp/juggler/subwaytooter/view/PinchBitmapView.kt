package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import jp.juggler.util.coroutine.runOnMainLooper
import jp.juggler.util.log.LogCategory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class PinchBitmapView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    View(context, attrs, defStyle) {

    companion object {

        internal val log = LogCategory("PinchImageView")

        // 数値を範囲内にクリップする
        private fun clip(min: Float, max: Float, v: Float): Float {
            return if (v < min) min else if (v > max) max else v
        }

        // ビューの幅と画像の描画サイズを元に描画位置をクリップする
        private fun clipTranslate(
            viewW: Float, // ビューの幅
            bitmapW: Float, // 画像の幅
            currentScale: Float, // 画像の拡大率
            transX: Float, // タッチ操作による表示位置
        ): Float {

            // 余白(拡大率が小さい場合はプラス、拡大率が大きい場合はマイナス)
            val padding = viewW - bitmapW * currentScale

            // 余白が>=0なら画像を中心に表示する。 <0なら操作された位置をクリップする。
            return if (padding >= 0f) padding / 2f else clip(padding, 0f, transX)
        }
    }

    private var callback: Callback? = null

    private var bitmap: Bitmap? = null
    private var bitmapW: Float = 0.toFloat()
    private var bitmapH: Float = 0.toFloat()
    private var bitmapAspect: Float = 0.toFloat()

    // 画像を表示する位置と拡大率
    private var currentTransX: Float = 0.toFloat()
    private var currentTransY: Float = 0.toFloat()
    private var currentScale: Float = 0.toFloat()

    // 画像表示に使う構造体
    private val drawMatrix = Matrix()
    internal val paint = Paint()

    // タッチ操作中に指を動かした
    private var bDrag: Boolean = false

    // タッチ操作中に指の数を変えた
    private var bPointerCountChanged: Boolean = false

    // ページめくりに必要なスワイプ強度
    private var swipeVelocity = 0f
    private var swipeVelocity2 = 0f

    // 指を動かしたと判断する距離
    private var dragLength = 0f

    private var timeTouchStart = 0L

    // フリック操作の検出に使う
    private var velocityTracker: VelocityTracker? = null

    private var clickTime = 0L
    private var clickCount = 0

    // 移動後の指の位置
    internal val pos = PointerAvg()

    // 移動開始時の指の位置
    private val posStart = PointerAvg()

    // 移動開始時の画像の位置
    private var startImageTransX: Float = 0.toFloat()
    private var startImageTransY: Float = 0.toFloat()
    private var startImageScale: Float = 0.toFloat()

    private var scaleMin: Float = 0.toFloat()
    private var scaleMax: Float = 0.toFloat()

    private var viewW: Float = 0.toFloat()
    private var viewH: Float = 0.toFloat()
    private var viewAspect: Float = 0.toFloat()

    private val trackingMatrix = Matrix()
    private val trackingMatrixInv = Matrix()
    private val avgOnImage1 = FloatArray(2)
    private val avgOnImage2 = FloatArray(2)

    constructor(context: Context) : this(context, null) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
        init(context)
    }

    init {
        init(context)
    }

    internal fun init(context: Context) {

        // 定数をdpからpxに変換
        val density = context.resources.displayMetrics.density
        swipeVelocity = 1000f * density
        swipeVelocity2 = 250f * density
        dragLength = 4f * density // 誤反応しがちなのでやや厳しめ
    }

    // ページめくり操作のコールバック
    interface Callback {

        fun onSwipe(deltaX: Int, deltaY: Int)

        fun onMove(bitmapW: Float, bitmapH: Float, tx: Float, ty: Float, scale: Float)
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun setBitmap(b: Bitmap?) {

        bitmap?.recycle()

        this.bitmap = b

        initializeScale()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = this.bitmap
        if (bitmap != null && !bitmap.isRecycled) {

            drawMatrix.reset()
            drawMatrix.postScale(currentScale, currentScale)
            drawMatrix.postTranslate(currentTransX, currentTransY)

            paint.isFilterBitmap = currentScale < 4f
            canvas.drawBitmap(bitmap, drawMatrix, paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        viewW = max(1f, w.toFloat())
        viewH = max(1f, h.toFloat())
        viewAspect = viewW / viewH

        initializeScale()
    }

    override fun performClick(): Boolean {
        super.performClick()

        initializeScale()

        return true
    }

    private var defaultScale: Float = 1f

    // 表示位置の初期化
    // 呼ばれるのは、ビットマップを変更した時、ビューのサイズが変わった時、画像をクリックした時
    private fun initializeScale() {
        val bitmap = this.bitmap
        if (bitmap != null && !bitmap.isRecycled && viewW >= 1f) {

            bitmapW = max(1f, bitmap.width.toFloat())
            bitmapH = max(1f, bitmap.height.toFloat())
            bitmapAspect = bitmapW / bitmapH

            if (viewAspect > bitmapAspect) {
                scaleMin = viewH / bitmapH / 2f
                scaleMax = viewW / bitmapW * 8f
            } else {
                scaleMin = viewW / bitmapW / 2f
                scaleMax = viewH / bitmapH * 8f
            }
            if (scaleMax < scaleMin) scaleMax = scaleMin * 16f

            defaultScale = if (viewAspect > bitmapAspect) {
                viewH / bitmapH
            } else {
                viewW / bitmapW
            }

            val drawW = bitmapW * defaultScale
            val drawH = bitmapH * defaultScale

            currentScale = defaultScale
            currentTransX = (viewW - drawW) / 2f
            currentTransY = (viewH - drawH) / 2f

            callback?.onMove(bitmapW, bitmapH, currentTransX, currentTransY, currentScale)
        } else {
            defaultScale = 1f
            scaleMin = 1f
            scaleMax = 1f

            currentScale = defaultScale
            currentTransY = 0f
            currentTransX = 0f

            callback?.onMove(0f, 0f, currentTransX, currentTransY, currentScale)
        }

        // 画像がnullに変化した時も再描画が必要
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {

        val bitmap = this.bitmap
        if (bitmap == null || bitmap.isRecycled || viewW < 1f) return false

        val action = ev.action

        if (action == MotionEvent.ACTION_DOWN) {
            timeTouchStart = SystemClock.elapsedRealtime()

            velocityTracker?.clear()
            velocityTracker = VelocityTracker.obtain()
            velocityTracker?.addMovement(ev)

            bPointerCountChanged = false
            bDrag = false
            trackStart(ev)
            return true
        }

        velocityTracker?.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // タッチ操作中に指の数を変えた
                bPointerCountChanged = true
                bDrag = true
                trackStart(ev)
            }

            MotionEvent.ACTION_MOVE -> trackNext(ev)

            MotionEvent.ACTION_UP -> {
                trackNext(ev)

                checkClickOrPaging()

                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    private fun checkClickOrPaging() {

        if (!bDrag) {
            // 指を動かしていないなら

            val now = SystemClock.elapsedRealtime()

            if (now - timeTouchStart >= 1000L) {
                // ロングタップはタップカウントをリセットする
                log.d("click count reset by long tap")
                clickCount = 0
                return
            }

            val delta = now - clickTime
            clickTime = now

            if (delta > 334L) {
                // 前回のタップからの時刻が長いとタップカウントをリセットする
                log.d("click count reset by long interval")
                clickCount = 0
            }

            ++clickCount

            log.d("click $clickCount $delta")

            if (clickCount >= 2) {
                // ダブルタップでクリック操作
                clickCount = 0
                performClick()
            }

            return
        }

        clickCount = 0

        val velocityTracker = this.velocityTracker
        if (!bPointerCountChanged && velocityTracker != null) {

            // 指の数を変えていないならページめくり操作かもしれない

            // 「画像を動かした」かどうかのチェック
            val imageMoved = max(
                abs(currentTransX - startImageTransX),
                abs(currentTransY - startImageTransY)
            )
            if (imageMoved >= dragLength) {
                log.d("image moved. not flick action. $imageMoved")
                return
            }

            velocityTracker.computeCurrentVelocity(1000)
            val vx = velocityTracker.xVelocity
            val vy = velocityTracker.yVelocity
            val avx = abs(vx)
            val avy = abs(vy)
            val velocity = sqrt(vx * vx + vy * vy)
            val aspect = try {
                avx / avy
            } catch (ignored: Throwable) {
                Float.MAX_VALUE
            }

            when {
                aspect >= 0.9f -> {
                    // 指を動かした方向が左右だった

                    val vMin = when {
                        currentScale * bitmapW <= viewW -> swipeVelocity2
                        else -> swipeVelocity
                    }

                    if (velocity < vMin) {
                        log.d("velocity $velocity not enough to pagingX")
                        return
                    }

                    log.d("pagingX! m=$imageMoved a=$aspect v=$velocity")
                    runOnMainLooper { callback?.onSwipe(if (vx >= 0f) -1 else 1, 0) }
                }

                aspect <= 0.333f -> {
                    // 指を動かした方向が上下だった

                    val vMin = when {
                        currentScale * bitmapH <= viewH -> swipeVelocity2
                        else -> swipeVelocity
                    }

                    if (velocity < vMin) {
                        log.d("velocity $velocity not enough to pagingY")
                        return
                    }

                    log.d("pagingY! m=$imageMoved a=$aspect v=$velocity")
                    runOnMainLooper { callback?.onSwipe(0, if (vy >= 0f) -1 else 1) }
                }

                else -> log.d("flick is not horizontal/vertical. aspect=$aspect")
            }
        }
    }

    // マルチタッチの中心位置の計算
    internal class PointerAvg {

        // タッチ位置の数
        var count: Int = 0

        // タッチ位置の平均
        val avg = FloatArray(2)

        // 中心と、中心から最も離れたタッチ位置の間の距離
        var maxRadius: Float = 0.toFloat()

        fun update(ev: MotionEvent) {

            count = ev.pointerCount
            if (count <= 1) {
                avg[0] = ev.x
                avg[1] = ev.y
                maxRadius = 0f
            } else {
                avg[0] = 0f
                avg[1] = 0f
                for (i in 0 until count) {
                    avg[0] += ev.getX(i)
                    avg[1] += ev.getY(i)
                }
                avg[0] /= count.toFloat()
                avg[1] /= count.toFloat()
                maxRadius = 0f
                for (i in 0 until count) {
                    val dx = ev.getX(i) - avg[0]
                    val dy = ev.getY(i) - avg[1]
                    val radius = dx * dx + dy * dy
                    if (radius > maxRadius) maxRadius = radius
                }
                maxRadius = sqrt(maxRadius.toDouble()).toFloat()
                if (maxRadius < 1f) maxRadius = 1f
            }
        }
    }

    private fun trackStart(ev: MotionEvent) {

        // 追跡開始時の指の位置
        posStart.update(ev)

        // 追跡開始時の画像の位置
        startImageTransX = currentTransX
        startImageTransY = currentTransY
        startImageScale = currentScale
    }

    // 画面上の指の位置から画像中の指の位置を調べる
    private fun getCoordinateOnImage(dst: FloatArray, src: FloatArray) {
        trackingMatrix.reset()
        trackingMatrix.postScale(currentScale, currentScale)
        trackingMatrix.postTranslate(currentTransX, currentTransY)
        trackingMatrix.invert(trackingMatrixInv)
        trackingMatrixInv.mapPoints(dst, src)
    }

    private fun trackNext(ev: MotionEvent) {
        pos.update(ev)

        if (pos.count != posStart.count) {
            // タッチ操作中に指の数が変わった
            log.d("nextTracking: pointer count changed")
            bPointerCountChanged = true
            bDrag = true
            trackStart(ev)
            return
        }

        // ズーム操作
        if (pos.count > 1) {

            // タッチ位置にある絵柄の座標を調べる
            getCoordinateOnImage(avgOnImage1, pos.avg)

            // ズーム率を変更する
            currentScale = clip(
                scaleMin,
                scaleMax,
                startImageScale * pos.maxRadius / posStart.maxRadius
            )

            // 再び調べる
            getCoordinateOnImage(avgOnImage2, pos.avg)

            // ズーム変更の前後で位置がズレた分だけ移動させると、タッチ位置にある絵柄がズレない
            startImageTransX += currentScale * (avgOnImage2[0] - avgOnImage1[0])
            startImageTransY += currentScale * (avgOnImage2[1] - avgOnImage1[1])
        }

        // 平行移動
        run {
            // start時から指を動かした量
            val moveX = pos.avg[0] - posStart.avg[0]
            val moveY = pos.avg[1] - posStart.avg[1]

            // 「指を動かした」と判断したらフラグを立てる
            if (abs(moveX) >= dragLength || abs(moveY) >= dragLength) {
                bDrag = true
            }

            // 画像の表示位置を更新
            currentTransX =
                clipTranslate(viewW, bitmapW, currentScale, startImageTransX + moveX)
            currentTransY =
                clipTranslate(viewH, bitmapH, currentScale, startImageTransY + moveY)
        }

        callback?.onMove(bitmapW, bitmapH, currentTransX, currentTransY, currentScale)
        invalidate()
    }
}
