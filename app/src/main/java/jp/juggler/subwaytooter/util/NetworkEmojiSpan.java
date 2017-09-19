package jp.juggler.subwaytooter.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.style.ReplacementSpan;

import jp.juggler.subwaytooter.App1;

public class NetworkEmojiSpan extends ReplacementSpan {
	
	private static final float scale_ratio = 1.14f;
	private static final float descent_ratio = 0.211f;
	
	@NonNull private final String url;
	@NonNull private final Paint mPaint = new Paint();
	@NonNull private final Rect rect_src = new Rect();
	@NonNull private final RectF rect_dst = new RectF();
	
	NetworkEmojiSpan( @NonNull String url ){
		super();
		this.url = url;
		mPaint.setFilterBitmap( true );
	}
	
	private CustomEmojiCache.Callback load_callback;
	public void setLoadCompleteCallback(CustomEmojiCache.Callback load_callback){
		this.load_callback = load_callback;
	}
	
	@Override
	public int getSize(
		@NonNull Paint paint
		, CharSequence text
		, @IntRange(from = 0) int start
		, @IntRange(from = 0) int end
		, @Nullable Paint.FontMetricsInt fm
	){
		int size = (int) ( 0.5f + scale_ratio * paint.getTextSize() );
		
		if( fm != null ){
			int c_descent = (int) ( 0.5f + size * descent_ratio );
			int c_ascent = c_descent - size;
			if( fm.ascent > c_ascent ) fm.ascent = c_ascent;
			if( fm.top > c_ascent ) fm.top = c_ascent;
			if( fm.descent < c_descent ) fm.descent = c_descent;
			if( fm.bottom < c_descent ) fm.bottom = c_descent;
		}
		return size;
	}
	
	@Override public void draw(
		@NonNull Canvas canvas
		, CharSequence text, int start, int end
		, float x, int top, int baseline, int bottom
		, @NonNull Paint textPaint
	){
		if(load_callback == null ) return;

		int size = (int) ( 0.5f + scale_ratio * textPaint.getTextSize() );
		int c_descent = (int) ( 0.5f + size * descent_ratio );
		
		Bitmap b = App1.custom_emoji_cache.get( url ,load_callback);
		if( b != null && ! b.isRecycled() ){
			rect_src.set(0,0,b.getWidth(),b.getHeight() );
			rect_dst.set(0,0,size,size);
			
			int transY = baseline - size + c_descent;
			
			canvas.save();
			canvas.translate( x, transY );
			canvas.drawBitmap( b, rect_src,rect_dst,mPaint );
			canvas.restore();
		}
	}
}
