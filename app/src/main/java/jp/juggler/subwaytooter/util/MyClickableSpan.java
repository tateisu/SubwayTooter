package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import java.lang.ref.WeakReference;

import jp.juggler.subwaytooter.table.AcctColor;

public class MyClickableSpan extends ClickableSpan {
	
	public interface LinkClickCallback {
		void onClickLink( View widget, @NonNull MyClickableSpan span );
	}
	
	public static WeakReference< LinkClickCallback > link_callback = null;
	
	@NonNull public final LinkClickContext lcc;
	@NonNull public final String text;
	@NonNull public final String url;
	@Nullable public final Object tag;
	public final int color_fg;
	public final int color_bg;
	
	MyClickableSpan(
		@NonNull LinkClickContext lcc
		, @NonNull String text
		, @NonNull String url
		, @Nullable AcctColor ac
		, @Nullable Object tag
	){
		this.lcc = lcc;
		this.text = text;
		this.url = url;
		this.tag = tag;
		
		if( ac != null ){
			this.color_fg = ac.color_fg;
			this.color_bg = ac.color_bg;
		}else{
			this.color_fg = 0;
			this.color_bg = 0;
		}
	}
	
	@Override public void onClick( View widget ){
		LinkClickCallback cb = ( link_callback == null ? null : link_callback.get() );
		if( cb != null ){
			cb.onClickLink( widget, this );
		}
	}
	
	@Override public void updateDrawState( TextPaint ds ){
		super.updateDrawState( ds );
		
		if( color_fg != 0 ){
			ds.setColor( color_fg );
		}
		if( color_bg != 0 ){
			ds.bgColor = color_bg;
		}
		
	}
}
