package jp.juggler.subwaytooter.util;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import jp.juggler.subwaytooter.table.AcctColor;

final class MyClickableSpan extends ClickableSpan {
	
	public LinkClickContext account;
	public String url;
	private int color_fg;
	private int color_bg;
	
	MyClickableSpan( LinkClickContext account, String url, AcctColor ac ){
		this.account = account;
		this.url = url;
		if( ac != null ){
			this.color_fg = ac.color_fg;
			this.color_bg = ac.color_bg;
		}
	}
	
	@Override public void onClick( View widget ){
		if( HTMLDecoder.link_callback != null ){
			HTMLDecoder.link_callback.onClickLink( this.account, url );
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
