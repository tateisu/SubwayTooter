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
		void onClickLink( View widget,@NonNull MyClickableSpan span);
	}
	
	public static WeakReference<LinkClickCallback> link_callback = null;
	
	public @NonNull LinkClickContext lcc;
	public @NonNull String text;
	public @NonNull String url;
	public @Nullable Object tag;
	public int color_fg;
	public int color_bg;
	
	MyClickableSpan(
		@NonNull LinkClickContext lcc
	    ,@NonNull  String text
		,@NonNull  String url
		, AcctColor ac
		,@Nullable Object tag
	){
		this.lcc = lcc;
		this.text = text;
		this.url = url;
		this.tag = tag;
		
		if( ac != null ){
			this.color_fg = ac.color_fg;
			this.color_bg = ac.color_bg;
		}
	}
	
	@Override public void onClick( View widget ){
		LinkClickCallback cb = (link_callback == null ? null : link_callback.get() );
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
