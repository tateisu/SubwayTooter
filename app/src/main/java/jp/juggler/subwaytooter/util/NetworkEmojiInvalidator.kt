package jp.juggler.subwaytooter.util

import android.os.Handler
import android.os.SystemClock
import android.text.Spannable
import android.view.View

import java.util.ArrayList

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.span.AnimatableSpan
import jp.juggler.subwaytooter.span.AnimatableSpanInvalidator
import java.lang.ref.WeakReference

class NetworkEmojiInvalidator(internal val handler : Handler, internal val view : View) : Runnable, AnimatableSpanInvalidator {
	
	private val draw_target_list = ArrayList<WeakReference<Any>>()
	
	// 最後に描画した時刻
	private var t_last_draw : Long = 0
	
	// アニメーション開始時刻
	private var t_start : Long = 0
	
	// アニメーション開始時刻を計算する
	override val timeFromStart : Long
		get() {
			val now = SystemClock.elapsedRealtime()
			if(t_start == 0L || now - t_last_draw >= 60000L) {
				t_start = now
			}
			t_last_draw = now
			
			return now - t_start
		}
	
	// 装飾テキスト中のカスタム絵文字スパンにコールバックを登録する
	fun register(dst : Spannable?) {
		for(o in draw_target_list) {
			App1.custom_emoji_cache.cancelRequest(o)
		}
		draw_target_list.clear()
		
		if(dst != null) {
			for(span in dst.getSpans(0, dst.length, AnimatableSpan::class.java)) {
				val tag = WeakReference(Any())
				draw_target_list.add(tag)
				span.setInvalidateCallback(tag, this)
			}
		}
		
	}
	
	// 絵文字スパンを描画した直後に呼ばれる
	// (絵文字が多いと描画の度に大量に呼び出される)
	override fun delayInvalidate(delay : Long) {
		handler.postDelayed(this, if(delay < 10L) 10L else if(delay > 711L) 711L else delay)
	}
	
	// Handler経由で遅延実行される
	override fun run() {
		handler.removeCallbacks(this)
		if(view.isAttachedToWindow) {
			view.postInvalidateOnAnimation()
		}
	}
	
}