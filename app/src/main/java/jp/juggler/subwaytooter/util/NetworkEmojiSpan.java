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

public class NetworkEmojiSpan extends ReplacementSpan implements CustomEmojiCache.Callback {
	
	@SuppressWarnings("unused")
	static final LogCategory log = new LogCategory( "NetworkEmojiSpan" );
	
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
		long getTimeFromStart();
		void delayInvalidate( long delay );
	}
	
	private InvalidateCallback invalidate_callback;
	private Object draw_target_tag;
	
	public void setInvalidateCallback( Object draw_target_tag,InvalidateCallback invalidate_callback ){
		this.draw_target_tag = draw_target_tag;
		this.invalidate_callback = invalidate_callback;
	}
	
	// implements CustomEmojiCache.Callback
	@Override public void onAPNGLoadComplete(){
		if( invalidate_callback != null ){
			invalidate_callback.delayInvalidate( 0 );
		}
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
	
	// フレーム探索結果を格納する構造体を確保しておく
	private final APNGFrames.FindFrameResult mFrameFindResult = new APNGFrames.FindFrameResult();
	
	@Override public void draw(
		@NonNull Canvas canvas
		, CharSequence text, int start, int end
		, float x, int top, int baseline, int bottom
		, @NonNull Paint textPaint
	){
		if( invalidate_callback == null ){
			log.e("draw: invalidate_callback is null.");
			return;
		}
		
		// APNGデータの取得
		APNGFrames frames = App1.custom_emoji_cache.get( draw_target_tag, url, this );
		if( frames == null ){
			return;
		}

		
		long t = App1.disable_emoji_animation ? 0L : invalidate_callback.getTimeFromStart();
		
		// アニメーション開始時刻からの経過時間に応じたフレームを探索
		frames.findFrame( mFrameFindResult, t );

		Bitmap b = mFrameFindResult.bitmap;
		if( b == null || b.isRecycled() ){
			log.e("draw: bitmap is null or recycled.");
			return;
		}
		
		int size = (int) ( 0.5f + scale_ratio * textPaint.getTextSize() );
		int c_descent = (int) ( 0.5f + size * descent_ratio );
		int transY = baseline - size + c_descent;
		
		canvas.save();
		canvas.translate( x, transY );
		rect_src.set( 0, 0, b.getWidth(), b.getHeight() );
		rect_dst.set( 0, 0, size, size );
		canvas.drawBitmap( b, rect_src, rect_dst, mPaint );
		canvas.restore();
		
		// 少し後に描画しなおす
		long delay = mFrameFindResult.delay;
		if( delay != Long.MAX_VALUE && ! App1.disable_emoji_animation ){
			invalidate_callback.delayInvalidate( delay );
		}
	}
}
