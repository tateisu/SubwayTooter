package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller

import jp.juggler.util.LogCategory

class GravitySnapHelper @SuppressLint("RtlHardcoded")
constructor(gravity : Int) : androidx.recyclerview.widget.LinearSnapHelper() {
	
	companion object {
		internal val log = LogCategory("GravitySnapHelper")
	}

	private var verticalHelper : androidx.recyclerview.widget.OrientationHelper? = null
	private var horizontalHelper : androidx.recyclerview.widget.OrientationHelper? = null
	private var gravity : Int = 0
	private var isRTL : Boolean = false
	
	private var mRecyclerView : androidx.recyclerview.widget.RecyclerView? = null
	private var mGravityScroller : Scroller? = null
	

	init {
		this.gravity = gravity
		if(this.gravity == Gravity.LEFT) {
			this.gravity = Gravity.START
		} else if(this.gravity == Gravity.RIGHT) {
			this.gravity = Gravity.END
		}
	}
	
	@Throws(IllegalStateException::class)
	override fun attachToRecyclerView(recyclerView : androidx.recyclerview.widget.RecyclerView?) {
		mRecyclerView = recyclerView
		if(recyclerView != null) {
			isRTL = ViewCompat.getLayoutDirection(recyclerView) == ViewCompat.LAYOUT_DIRECTION_RTL
			
			mGravityScroller = Scroller(recyclerView.context, DecelerateInterpolator())
		}
		super.attachToRecyclerView(recyclerView)
	}
	
	override fun calculateDistanceToFinalSnap(
		layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager, targetView : View
	) : IntArray? {
		val out = IntArray(2)
		
		if(! layoutManager.canScrollHorizontally()) {
			out[0] = 0
		} else if(gravity == Gravity.START) {
			out[0] = distanceToStart(targetView, getHorizontalHelper(layoutManager))
		} else {
			out[0] = distanceToEnd(targetView, getHorizontalHelper(layoutManager))
		}
		
		if(! layoutManager.canScrollVertically()) {
			out[1] = 0
		} else if(gravity == Gravity.TOP) {
			out[1] = distanceToStart(targetView, getVerticalHelper(layoutManager))
		} else {
			out[1] = distanceToEnd(targetView, getVerticalHelper(layoutManager))
		}
		
		return out
	}
	
	override fun findSnapView(layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager) : View? {
		if(layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
			when(gravity) {
				
				Gravity.START -> return findStartView(layoutManager, getHorizontalHelper(layoutManager))
				
				Gravity.TOP -> return findStartView(layoutManager, getVerticalHelper(layoutManager))
				
				Gravity.END -> return findEndView(layoutManager, getHorizontalHelper(layoutManager))
				
				Gravity.BOTTOM -> return findEndView(layoutManager, getVerticalHelper(layoutManager))
			}
		}
		
		return super.findSnapView(layoutManager)
	}
	
	private fun distanceToStart(targetView : View, helper : androidx.recyclerview.widget.OrientationHelper) : Int {
		return if(isRTL) {
			helper.getDecoratedEnd(targetView) - helper.endAfterPadding
		} else {
			helper.getDecoratedStart(targetView) - helper.startAfterPadding
		}
	}
	
	private fun distanceToEnd(targetView : View, helper : androidx.recyclerview.widget.OrientationHelper) : Int {
		return if(isRTL) {
			helper.getDecoratedStart(targetView) - helper.startAfterPadding
		} else {
			helper.getDecoratedEnd(targetView) - helper.endAfterPadding
		}
	}
	
	private fun findStartView(
		layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager, helper : androidx.recyclerview.widget.OrientationHelper
	) : View? {
		
		if(layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
			
			val firstChild = layoutManager.findFirstVisibleItemPosition()
			
			if(firstChild == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
				return null
			}
			
			val child = layoutManager.findViewByPosition(firstChild)
			
			@Suppress("CascadeIf")
			return if(helper.getDecoratedEnd(child) >= helper.getDecoratedMeasurement(child) / 2 && helper.getDecoratedEnd(child) > 0) {
				child
			} else if(layoutManager.findLastCompletelyVisibleItemPosition() == layoutManager.getItemCount() - 1) {
				null
			} else {
				layoutManager.findViewByPosition(firstChild + 1)
			}
		}
		
		return super.findSnapView(layoutManager)
	}
	
	private fun findEndView(
		layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager, helper : androidx.recyclerview.widget.OrientationHelper
	) : View? {
		
		if(layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
			
			val lastChild = layoutManager.findLastVisibleItemPosition()
			
			if(lastChild == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
				return null
			}
			
			val child = layoutManager.findViewByPosition(lastChild)
			
			@Suppress("CascadeIf")
			return if(helper.getDecoratedStart(child) + helper.getDecoratedMeasurement(child) / 2 <= helper.totalSpace) {
				child
			} else if(layoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
				null
			} else {
				layoutManager.findViewByPosition(lastChild - 1)
			}
		}
		
		return super.findSnapView(layoutManager)
	}
	
	private fun getVerticalHelper(layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager) : androidx.recyclerview.widget.OrientationHelper {
		var verticalHelper = this.verticalHelper
		if(verticalHelper == null) {
			verticalHelper = androidx.recyclerview.widget.OrientationHelper.createVerticalHelper(layoutManager) as androidx.recyclerview.widget.OrientationHelper
			this.verticalHelper = verticalHelper
		}
		return verticalHelper
	}
	
	private fun getHorizontalHelper(layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager) : androidx.recyclerview.widget.OrientationHelper {
		var horizontalHelper = this.horizontalHelper
		if(horizontalHelper == null) {
			horizontalHelper = androidx.recyclerview.widget.OrientationHelper.createHorizontalHelper(layoutManager) as androidx.recyclerview.widget.OrientationHelper
			this.horizontalHelper = horizontalHelper
		}
		return horizontalHelper
	}
	
	// var columnWidth : Int = 0
	
	override fun findTargetSnapPosition(
		layoutManager : androidx.recyclerview.widget.RecyclerView.LayoutManager, velocityX : Int, velocityY : Int
	) : Int {
		
		var targetPos = super.findTargetSnapPosition(layoutManager, velocityX, velocityY)
		if(targetPos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
			val currentView = findSnapView(layoutManager )
			if(currentView != null) {
				val currentPosition = layoutManager.getPosition(currentView)
				
				val clip = 1
				
				if(targetPos - currentPosition > clip) {
					targetPos = currentPosition + clip
				} else if(targetPos - currentPosition < - clip) {
					targetPos = currentPosition - clip
				}
			}
			
		}
		return targetPos
	}
	
}