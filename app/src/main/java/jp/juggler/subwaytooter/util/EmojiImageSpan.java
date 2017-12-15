package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
//import android.text.style.DynamicDrawableSpan;
import android.text.style.ReplacementSpan;

import java.lang.ref.WeakReference;

public class EmojiImageSpan extends ReplacementSpan {
	
	// private static final LogCategory log = new LogCategory( "EmojiImageSpan" );

	// static DynamicDrawableSpan x = null;
	
	private static final float scale_ratio = 1.14f;
	private static final float descent_ratio = 0.211f;
	
	private final Context context;
	private final int res_id;
	private WeakReference< Drawable > mDrawableRef;
	
	public EmojiImageSpan( @NonNull Context context, int res_id ){
		super();
		this.context = context.getApplicationContext();
		this.res_id = res_id;
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
		, @NonNull Paint paint
	){
		int size = (int) ( 0.5f + scale_ratio * paint.getTextSize() );
		int c_descent = (int) ( 0.5f + size * descent_ratio );
		
		Drawable d = getCachedDrawable();
		d.setBounds( 0, 0, size, size );
		
		int transY = baseline - size + c_descent;
		
		canvas.save();
		canvas.translate( x, transY );
		d.draw( canvas );
		canvas.restore();
	}
	
	private Drawable getCachedDrawable(){
		Drawable d = null;
		
		if( mDrawableRef != null ){
			d = mDrawableRef.get();
		}
		
		if( d == null ){
			d = ContextCompat.getDrawable( context, res_id );
			mDrawableRef = new WeakReference<>( d );
		}
		
		return d;
	}
	
}