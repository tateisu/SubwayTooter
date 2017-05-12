package jp.juggler.subwaytooter.view;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;

/**
 * Created by tateisu on 2017/05/12.
 */

public class GravitySnapHelper  extends LinearSnapHelper {
	
	private OrientationHelper verticalHelper;
	private OrientationHelper horizontalHelper;
	private int gravity;
	private boolean isRTL;
	
	@SuppressLint("RtlHardcoded")
	public GravitySnapHelper(int gravity) {
		this.gravity = gravity;
		if (this.gravity == Gravity.LEFT) {
			this.gravity = Gravity.START;
		} else if (this.gravity == Gravity.RIGHT) {
			this.gravity = Gravity.END;
		}
	}
	
	@Override
	public void attachToRecyclerView(@Nullable RecyclerView recyclerView)
		throws IllegalStateException {
		if (recyclerView != null) {
			isRTL = ViewCompat.getLayoutDirection(recyclerView) == ViewCompat.LAYOUT_DIRECTION_RTL;
		}
		super.attachToRecyclerView(recyclerView);
	}
	
	@Override
	public int[] calculateDistanceToFinalSnap(
		@NonNull RecyclerView.LayoutManager layoutManager
		,@NonNull View targetView
	) {
		int[] out = new int[2];
		
		if (layoutManager.canScrollHorizontally()) {
			if (gravity == Gravity.START) {
				out[0] = distanceToStart(targetView, getHorizontalHelper(layoutManager));
			} else { // END
				out[0] = distanceToEnd(targetView, getHorizontalHelper(layoutManager));
			}
		} else {
			out[0] = 0;
		}
		
		if (layoutManager.canScrollVertically()) {
			if (gravity == Gravity.TOP) {
				out[1] = distanceToStart(targetView, getVerticalHelper(layoutManager));
			} else { // BOTTOM
				out[1] = distanceToEnd(targetView, getVerticalHelper(layoutManager));
			}
		} else {
			out[1] = 0;
		}
		return out;
	}
	
	@Override
	public View findSnapView(RecyclerView.LayoutManager layoutManager) {
		if (layoutManager instanceof LinearLayoutManager ) {
			switch (gravity) {
			case Gravity.START:
				return findStartView(layoutManager, getHorizontalHelper(layoutManager));
			case Gravity.TOP:
				return findStartView(layoutManager, getVerticalHelper(layoutManager));
			case Gravity.END:
				return findEndView(layoutManager, getHorizontalHelper(layoutManager));
			case Gravity.BOTTOM:
				return findEndView(layoutManager, getVerticalHelper(layoutManager));
			}
		}
		
		return super.findSnapView(layoutManager);
	}
	
	private int distanceToStart(View targetView, OrientationHelper helper) {
		if ( isRTL ) {
			return distanceToEnd(targetView, helper);
		}
		return helper.getDecoratedStart(targetView) - helper.getStartAfterPadding();
	}
	
	private int distanceToEnd(View targetView, OrientationHelper helper) {
		if ( isRTL ) {
			return helper.getDecoratedStart(targetView) - helper.getStartAfterPadding();
		}
		return helper.getDecoratedEnd(targetView) - helper.getEndAfterPadding();
	}
	
	private View findStartView(RecyclerView.LayoutManager layoutManager,
	                           OrientationHelper helper) {
		
		if (layoutManager instanceof LinearLayoutManager) {
			int firstChild = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
			
			if (firstChild == RecyclerView.NO_POSITION) {
				return null;
			}
			
			View child = layoutManager.findViewByPosition(firstChild);
			
			if (helper.getDecoratedEnd(child) >= helper.getDecoratedMeasurement(child) / 2
				&& helper.getDecoratedEnd(child) > 0) {
				return child;
			} else {
				if (((LinearLayoutManager) layoutManager).findLastCompletelyVisibleItemPosition()
					== layoutManager.getItemCount() - 1) {
					return null;
				} else {
					return layoutManager.findViewByPosition(firstChild + 1);
				}
			}
		}
		
		return super.findSnapView(layoutManager);
	}
	
	private View findEndView(RecyclerView.LayoutManager layoutManager,
	                         OrientationHelper helper) {
		
		if (layoutManager instanceof LinearLayoutManager) {
			int lastChild = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
			
			if (lastChild == RecyclerView.NO_POSITION) {
				return null;
			}
			
			View child = layoutManager.findViewByPosition(lastChild);
			
			if (helper.getDecoratedStart(child) + helper.getDecoratedMeasurement(child) / 2
				<= helper.getTotalSpace()) {
				return child;
			} else {
				if (((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition()
					== 0) {
					return null;
				} else {
					return layoutManager.findViewByPosition(lastChild - 1);
				}
			}
		}
		
		return super.findSnapView(layoutManager);
	}
	
	private OrientationHelper getVerticalHelper(RecyclerView.LayoutManager layoutManager) {
		if (verticalHelper == null) {
			verticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
		}
		return verticalHelper;
	}
	
	private OrientationHelper getHorizontalHelper(RecyclerView.LayoutManager layoutManager) {
		if (horizontalHelper == null) {
			horizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
		}
		return horizontalHelper;
	}
}