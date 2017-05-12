//package jp.juggler.subwaytooter.view;
//
//import android.content.Context;
//import android.content.res.TypedArray;
//import android.graphics.Canvas;
//import android.graphics.Paint;
//import android.graphics.Rect;
//import android.support.v7.widget.RecyclerView;
//import android.view.View;
//
//import jp.juggler.subwaytooter.R;
//import jp.juggler.subwaytooter.Styler;
//
//public class TabletColumnDivider extends  RecyclerView.ItemDecoration  {
//
//	final Paint paint = new Paint();
//	int width;
//
//	public TabletColumnDivider(Context context) {
//
//		width = (int)(0.5f + 1f * context.getResources().getDisplayMetrics().density);
//		int color = Styler.getAttributeColor(context, R.attr.colorSettingDivider);
//
//		paint.setColor( color );
//	}
//
//	@Override public void onDraw( Canvas c, RecyclerView parent, RecyclerView.State state) {
//		drawDivider(c, parent);
//	}
//
//	void drawDivider(Canvas c, RecyclerView parent) {
//		final int t = parent.getPaddingTop();
//		final int b = parent.getHeight() - parent.getPaddingBottom();
//
//		final int childCount = parent.getChildCount();
//		for (int i = 0; i < childCount; i++) {
//			final View child = parent.getChildAt(i);
//			final int r = child.getRight();
//			final int l = r - width;
//			c.drawRect( l,t,r,b,paint );
//
//		}
//	}
//
//	@Override
//	public void getItemOffsets( Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
//		outRect.set(width,0,width,0);
//	}
//
//}
