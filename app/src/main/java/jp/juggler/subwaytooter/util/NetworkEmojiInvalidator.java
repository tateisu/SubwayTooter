package jp.juggler.subwaytooter.util;

import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.view.View;

import java.util.ArrayList;

import jp.juggler.subwaytooter.App1;

public class NetworkEmojiInvalidator implements Runnable, NetworkEmojiSpan.InvalidateCallback {
	@NonNull final View view;
	@NonNull final Handler handler;
	
	public NetworkEmojiInvalidator( @NonNull Handler handler, @NonNull View view ){
		this.handler = handler;
		this.view = view;
	}
	
	final ArrayList<Object> draw_target_list = new ArrayList<>(  );
	
	// 装飾テキスト中のカスタム絵文字スパンにコールバックを登録する
	public void register( @Nullable Spannable dst ){
		for(Object o :draw_target_list){
			App1.custom_emoji_cache.cancelRequest( o );
		}
		draw_target_list.clear();

		if( dst != null ){
			for( NetworkEmojiSpan span : dst.getSpans( 0, dst.length(), NetworkEmojiSpan.class ) ){
				Object tag = new Object();
				draw_target_list.add( tag);
				span.setInvalidateCallback( tag, this );
			}
		}
		
		
	}
	
	// 絵文字スパンを描画した直後に呼ばれる
	// (絵文字が多いと描画の度に大量に呼び出される)
	@Override public void delayInvalidate( long delay ){
		handler.postDelayed( this, delay <10L ? 10L : delay > 711L ? 711L :delay );
	}
	
	// Handler経由で遅延実行される
	@Override public void run(){
		handler.removeCallbacks( this );
		if( view.isAttachedToWindow() ){
			view.postInvalidateOnAnimation();
		}
	}
	
	// 最後に描画した時刻
	private long t_last_draw;
	
	// アニメーション開始時刻
	private long t_start;
	
	@Override public long getTimeFromStart(){
		
		long now = SystemClock.elapsedRealtime();
		
		// アニメーション開始時刻を計算する
		if( t_start == 0L || now - t_last_draw >= 60000L ){
			t_start = now;
		}
		t_last_draw = now;
		
		return now - t_start;
	}
	
	
}