package jp.juggler.subwaytooter

import android.view.Gravity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.view.GravitySnapHelper
import jp.juggler.subwaytooter.view.TabletColumnDivider
import jp.juggler.util.clipRange
import kotlin.math.abs
import kotlin.math.min

class TabletViews(
    val actMain: ActMain,
) {
    internal lateinit var tabletPager: RecyclerView
    internal lateinit var tabletPagerAdapter: TabletColumnPagerAdapter
    internal lateinit var tabletLayoutManager: LinearLayoutManager
    private lateinit var tabletSnapHelper: GravitySnapHelper

    val visibleColumnsIndices: IntRange
        get() {
            var vs = tabletLayoutManager.findFirstVisibleItemPosition()
            var ve = tabletLayoutManager.findLastVisibleItemPosition()
            if (vs == RecyclerView.NO_POSITION || ve == RecyclerView.NO_POSITION) {
                return IntRange(-1, -2) // empty and less than zero
            }

            val child = tabletLayoutManager.findViewByPosition(vs)
            val slideRatio =
                clipRange(0f, 1f, abs((child?.left ?: 0) / actMain.nColumnWidth.toFloat()))
            if (slideRatio >= 0.95f) {
                ++vs
                ++ve
            }
            return IntRange(vs, min(ve, vs + actMain.nScreenColumn - 1))
        }

    val visibleColumns: List<Column>
        get() {
            val list = actMain.appState.columnList
            return visibleColumnsIndices.mapNotNull { list.elementAtOrNull(it) }
        }

    fun initUI(tmpTabletPager: RecyclerView) {
        this.tabletPager = tmpTabletPager
        this.tabletPagerAdapter = TabletColumnPagerAdapter(actMain)
        this.tabletLayoutManager =
            LinearLayoutManager(
                actMain,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        if (this.tabletPager.itemDecorationCount == 0) {
            this.tabletPager.addItemDecoration(TabletColumnDivider(actMain))
        }

        this.tabletPager.adapter = this.tabletPagerAdapter
        this.tabletPager.layoutManager = this.tabletLayoutManager
        this.tabletPager.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int,
            ) {
                super.onScrollStateChanged(recyclerView, newState)

                val vs = tabletLayoutManager.findFirstVisibleItemPosition()
                val ve = tabletLayoutManager.findLastVisibleItemPosition()
                // 端に近い方に合わせる
                val distance_left = abs(vs)
                val distance_right = abs(actMain.appState.columnCount - 1 - ve)
                if (distance_left < distance_right) {
                    actMain.scrollColumnStrip(vs)
                } else {
                    actMain.scrollColumnStrip(ve)
                }
            }

            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                super.onScrolled(recyclerView, dx, dy)
                actMain.updateColumnStripSelection(-1, -1f)
            }
        })

        this.tabletPager.itemAnimator = null
        //			val animator = this.tablet_pager.itemAnimator
        //			if( animator is DefaultItemAnimator){
        //				animator.supportsChangeAnimations = false
        //			}

        this.tabletSnapHelper = GravitySnapHelper(Gravity.START)
        this.tabletSnapHelper.attachToRecyclerView(this.tabletPager)
    }
}
