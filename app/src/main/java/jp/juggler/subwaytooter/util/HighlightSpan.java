package jp.juggler.subwaytooter.util;

import android.text.TextPaint;
import android.text.style.CharacterStyle;

public class HighlightSpan extends CharacterStyle {
	
	public final int color_fg;
	public final int color_bg;
	
	HighlightSpan( int color_fg ,int color_bg ){
		super();
		this.color_fg = color_fg;
		this.color_bg = color_bg;
	}
	
	@Override public void updateDrawState( TextPaint ds ){
		 // super.updateDrawState( ds );
		
		if( color_fg != 0 ){
			ds.setColor( color_fg );
		}
		if( color_bg != 0 ){
			ds.bgColor = color_bg;
		}
	}
	
}
