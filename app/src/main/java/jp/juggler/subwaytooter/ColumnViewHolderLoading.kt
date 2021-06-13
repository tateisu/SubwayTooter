package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.abs
import java.io.Closeable

private class ErrorFlickListener(
    private val cvh: ColumnViewHolder,
) : View.OnTouchListener, GestureDetector.OnGestureListener {

    private val gd = GestureDetector(cvh.activity, this)
    val density = cvh.activity.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        return gd.onTouchEvent(event)
    }

    override fun onShowPress(e: MotionEvent?) {
    }

    override fun onLongPress(e: MotionEvent?) {
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {

        val vx = velocityX.abs()
        val vy = velocityY.abs()
        if (vy < vx * 1.5f) {
            // フリック方向が上下ではない
            ColumnViewHolder.log.d("fling? not vertical view. $vx $vy")
        } else {

            val vyDp = vy / density
            val limit = 1024f
            ColumnViewHolder.log.d("fling? $vyDp/$limit")
            if (vyDp >= limit) {
                val column = cvh.column
                if (column != null && column.lastTask == null) {
                    column.startLoading()
                }
            }
        }
        return true
    }
}

private class AdapterItemHeightWorkarea(
    val listView: RecyclerView,
    val adapter: ItemListAdapter
) : Closeable {

    private val item_width: Int
    private val widthSpec: Int
    var lastViewType: Int = -1
    var lastViewHolder: RecyclerView.ViewHolder? = null

    init {
        this.item_width = listView.width - listView.paddingLeft - listView.paddingRight
        this.widthSpec = View.MeasureSpec.makeMeasureSpec(item_width, View.MeasureSpec.EXACTLY)
    }

    override fun close() {
        val childViewHolder = lastViewHolder
        if (childViewHolder != null) {
            adapter.onViewRecycled(childViewHolder)
            lastViewHolder = null
        }
    }

    // この関数はAdapterViewの項目の(marginを含む)高さを返す
    fun getAdapterItemHeight(adapterIndex: Int): Int {

        fun View.getTotalHeight(): Int {
            measure(widthSpec, ColumnViewHolder.heightSpec)
            val lp = layoutParams as? ViewGroup.MarginLayoutParams
            return measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
        }

        listView.findViewHolderForAdapterPosition(adapterIndex)?.itemView?.let {
            return it.getTotalHeight()
        }

        ColumnViewHolder.log.d("getAdapterItemHeight idx=$adapterIndex createView")

        val viewType = adapter.getItemViewType(adapterIndex)

        var childViewHolder = lastViewHolder
        if (childViewHolder == null || lastViewType != viewType) {
            if (childViewHolder != null) {
                adapter.onViewRecycled(childViewHolder)
            }
            childViewHolder = adapter.onCreateViewHolder(listView, viewType)
            lastViewHolder = childViewHolder
            lastViewType = viewType
        }
        adapter.onBindViewHolder(childViewHolder, adapterIndex)
        return childViewHolder.itemView.getTotalHeight()
    }
}

@SuppressLint("ClickableViewAccessibility")
fun ColumnViewHolder.initLoadingTextView() {
    llLoading.setOnTouchListener(ErrorFlickListener(this))
}

// 特定の要素が特定の位置に来るようにスクロール位置を調整する
fun ColumnViewHolder.setListItemTop(listIndex: Int, yArg: Int) {
    var adapterIndex = column?.toAdapterIndex(listIndex) ?: return

    val adapter = status_adapter
    if (adapter == null) {
        ColumnViewHolder.log.e("setListItemTop: missing status adapter")
        return
    }

    var y = yArg
    AdapterItemHeightWorkarea(listView, adapter).use { workarea ->
        while (y > 0 && adapterIndex > 0) {
            --adapterIndex
            y -= workarea.getAdapterItemHeight(adapterIndex)
            y -= ListDivider.height
        }
    }

    if (adapterIndex == 0 && y > 0) y = 0
    listLayoutManager.scrollToPositionWithOffset(adapterIndex, y)
}

// この関数は scrollToPositionWithOffset 用のオフセットを返す
fun ColumnViewHolder.getListItemOffset(listIndex: Int): Int {

    val adapterIndex = column?.toAdapterIndex(listIndex)
        ?: return 0

    val childView = listLayoutManager.findViewByPosition(adapterIndex)
        ?: throw IndexOutOfBoundsException("findViewByPosition($adapterIndex) returns null.")

    // スクロールとともにtopは減少する
    // しかしtopMarginがあるので最大値は4である
    // この関数は scrollToPositionWithOffset 用のオフセットを返すので top - topMargin を返す
    return childView.top - ((childView.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
        ?: 0)
}

fun ColumnViewHolder.findFirstVisibleListItem(): Int {

    val adapterIndex = listLayoutManager.findFirstVisibleItemPosition()

    if (adapterIndex == RecyclerView.NO_POSITION)
        throw IndexOutOfBoundsException()

    return column?.toListIndex(adapterIndex)
        ?: throw IndexOutOfBoundsException()

}

fun ColumnViewHolder.scrollToTop() {
    try {
        listView.stopScroll()
    } catch (ex: Throwable) {
        ColumnViewHolder.log.e(ex, "stopScroll failed.")
    }
    try {
        listLayoutManager.scrollToPositionWithOffset(0, 0)
    } catch (ex: Throwable) {
        ColumnViewHolder.log.e(ex, "scrollToPositionWithOffset failed.")
    }
}

fun ColumnViewHolder.scrollToTop2() {
    val status_adapter = this.status_adapter
    if (binding_busy || status_adapter == null) return
    if (status_adapter.itemCount > 0) {
        scrollToTop()
    }
}


fun ColumnViewHolder.saveScrollPosition(): Boolean {
    val column = this.column
    when {
        column == null ->
            ColumnViewHolder.log.d("saveScrollPosition [${page_idx}] , column==null")

        column.is_dispose.get() ->
            ColumnViewHolder.log.d("saveScrollPosition [${page_idx}] , column is disposed")

        listView.visibility != View.VISIBLE -> {
            val scroll_save = ScrollPosition()
            column.scroll_save = scroll_save
            ColumnViewHolder.log.d(
                "saveScrollPosition [${page_idx}] ${column.getColumnName(true)} , listView is not visible, save ${scroll_save.adapterIndex},${scroll_save.offset}"
            )
            return true
        }

        else -> {
            val scroll_save = ScrollPosition(this)
            column.scroll_save = scroll_save
            ColumnViewHolder.log.d(
                "saveScrollPosition [${page_idx}] ${column.getColumnName(true)} , listView is visible, save ${scroll_save.adapterIndex},${scroll_save.offset}"
            )
            return true
        }
    }
    return false
}

fun ColumnViewHolder.setScrollPosition(sp: ScrollPosition, deltaDp: Float = 0f) {
    val last_adapter = listView.adapter
    if (column == null || last_adapter == null) return

    sp.restore(this)

    // 復元した後に意図的に少し上下にずらしたい
    val dy = (deltaDp * activity.density + 0.5f).toInt()
    if (dy != 0) listView.postDelayed(Runnable {
        if (column == null || listView.adapter !== last_adapter) return@Runnable

        try {
            val recycler = ColumnViewHolder.fieldRecycler.get(listView) as RecyclerView.Recycler
            val state = ColumnViewHolder.fieldState.get(listView) as RecyclerView.State
            listLayoutManager.scrollVerticallyBy(dy, recycler, state)
        } catch (ex: Throwable) {
            ColumnViewHolder.log.trace(ex)
            ColumnViewHolder.log.e("can't access field in class ${RecyclerView::class.java.simpleName}")
        }
    }, 20L)
}


// 相対時刻を更新する
fun ColumnViewHolder.updateRelativeTime() = rebindAdapterItems()

fun ColumnViewHolder.rebindAdapterItems() {
    for (childIndex in 0 until listView.childCount) {
        val adapterIndex = listView.getChildAdapterPosition(listView.getChildAt(childIndex))
        if (adapterIndex == RecyclerView.NO_POSITION) continue
        status_adapter?.notifyItemChanged(adapterIndex)
    }
}
