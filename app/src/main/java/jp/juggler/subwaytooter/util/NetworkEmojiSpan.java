package jp.juggler.subwaytooter.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.style.ReplacementSpan;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.apng.APNGFrames;

public class NetworkEmojiSpan extends ReplacementSpan {
	
	static final LogCategory log = new LogCategory("NetworkEmojiSpan");
	
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
	
	public interface InvalidateCallback {
		void invalidate( long delay );
	}
	
	private InvalidateCallback invalidate_callback;
	
	public void setInvalidateCallback( InvalidateCallback invalidate_callback ){
		this.invalidate_callback = invalidate_callback;
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
	
	final APNGFrames.FindFrameResult mFrameFindResult = new APNGFrames.FindFrameResult();
	
	long t_start;
	long t_last_draw;
	
	@Override public void draw(
		@NonNull Canvas canvas
		, CharSequence text, int start, int end
		, float x, int top, int baseline, int bottom
		, @NonNull Paint textPaint
	){
		if( invalidate_callback == null ) return;
		
		int size = (int) ( 0.5f + scale_ratio * textPaint.getTextSize() );
		int c_descent = (int) ( 0.5f + size * descent_ratio );
		
		APNGFrames frames = App1.custom_emoji_cache.get( url, load_callback );
		if( frames != null ){
			long now = SystemClock.elapsedRealtime();
			if( t_start == 0L || now - t_last_draw >= 60000L ){
				t_start = now;
				log.d("t_start changed!");
			}
			t_last_draw = now;
			long delta = now - t_start;
			frames.findFrame( mFrameFindResult, delta );
			Bitmap b = mFrameFindResult.bitmap;
			if( b != null && ! b.isRecycled() ){
				rect_src.set( 0, 0, b.getWidth(), b.getHeight() );
				rect_dst.set( 0, 0, size, size );
				int transY = baseline - size + c_descent;
				canvas.save();
				canvas.translate( x, transY );
				canvas.drawBitmap( b, rect_src, rect_dst, mPaint );
				canvas.restore();
				
				long delay = mFrameFindResult.delay;
				if( delay != Long.MAX_VALUE ){
					invalidate_callback.invalidate( delay );
				}
			}
		}
	}
	
	final CustomEmojiCache.Callback load_callback = new CustomEmojiCache.Callback() {
		@Override public void onComplete( APNGFrames b ){
			if( invalidate_callback != null ){
				invalidate_callback.invalidate( 0 );
			}
		}
	};
}
