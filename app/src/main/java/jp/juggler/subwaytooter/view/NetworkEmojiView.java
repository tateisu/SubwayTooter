package jp.juggler.subwaytooter.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.APNGFrames;
import jp.juggler.subwaytooter.util.CustomEmojiCache;

public class NetworkEmojiView  extends View implements CustomEmojiCache.Callback {
	public NetworkEmojiView( Context context ){
		super( context );
	}
	
	public NetworkEmojiView( Context context, @Nullable AttributeSet attrs ){
		super( context, attrs );
	}
	
	public NetworkEmojiView( Context context, @Nullable AttributeSet attrs, int defStyleAttr ){
		super( context, attrs, defStyleAttr );
	}
	
	@TargetApi( 21 )
	public NetworkEmojiView( Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes ){
		super( context, attrs, defStyleAttr, defStyleRes );
	}
	
	String url;
	public void setEmoji( String url){
		this.url = url;
		mPaint.setFilterBitmap( true );
	}
	
	
	// フレーム探索結果を格納する構造体を確保しておく
	private final APNGFrames.FindFrameResult mFrameFindResult = new APNGFrames.FindFrameResult();
	
	// 最後に描画した時刻
	private long t_last_draw;
	
	// アニメーション開始時刻
	private long t_start;
	
	@NonNull private final Paint mPaint = new Paint();
	@NonNull private final Rect rect_src = new Rect();
	@NonNull private final RectF rect_dst = new RectF();
	
	@Override protected void onDraw( Canvas canvas ){
		super.onDraw( canvas );
		
		// APNGデータの取得
		APNGFrames frames = App1.custom_emoji_cache.get( this, url, this );
		if( frames == null ) return;
		
		long now = SystemClock.elapsedRealtime();
		
		// アニメーション開始時刻を計算する
		if( t_start == 0L || now - t_last_draw >= 60000L ){
			t_start = now;
		}
		t_last_draw = now;
		
		// アニメーション開始時刻からの経過時間に応じたフレームを探索
		frames.findFrame( mFrameFindResult, now - t_start );
		
		Bitmap b = mFrameFindResult.bitmap;
		if( b == null || b.isRecycled() ) return;
		
		rect_src.set( 0, 0, b.getWidth(), b.getHeight() );
		rect_dst.set( 0, 0, this.getWidth(), this.getHeight() );
		canvas.drawBitmap( b, rect_src, rect_dst, mPaint );
		
		// 少し後に描画しなおす
		long delay = mFrameFindResult.delay;
		if( delay != Long.MAX_VALUE ){
			postInvalidateDelayed( delay );
		}
	}
	
	@Override public void onAPNGLoadComplete(){
		postInvalidateOnAnimation();;
	}
}
