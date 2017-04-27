package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import java.util.Locale;

import jp.juggler.subwaytooter.api.entity.TootStatus;

class Styler {
	static int getVisibilityIcon( Context context,String visibility ){
		return
			getAttributeResourceId(  context,
			TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? R.attr.ic_public
				: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? R.attr.ic_lock_open
				: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? R.attr.ic_lock
				: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? R.attr.ic_mail
				: R.attr.ic_public );
		
	}
	
	static String getVisibilityString( Context context, String visibility ){
		return
			TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? context.getString( R.string.visibility_public )
				: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? context.getString( R.string.visibility_unlisted )
				: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? context.getString( R.string.visibility_private )
				: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? context.getString( R.string.visibility_direct )
				: "?";
	}
	
	static int getAttributeColor( @NonNull Context context, int attr_id ){
		Resources.Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes( new int[]{ attr_id } );
		int color = a.getColor( 0, 0xFFFF0000 );
		a.recycle();
		return color;
	}
	
	@NonNull static Drawable getAttributeDrawable( @NonNull Context context, int attr_id ){
		int res_id = getAttributeResourceId( context,attr_id );
		Drawable d = ContextCompat.getDrawable( context, res_id );
		if( d == null ) throw new RuntimeException( String.format( Locale.JAPAN,"getDrawable failed. drawable_id=0x%x",res_id));
		return d;
	}
	
	static int getAttributeResourceId(@NonNull Context context, int attr_id ){
		Resources.Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes( new int[]{ attr_id } );
		int res_id = a.getResourceId( 0, 0 );
		a.recycle();
		if( res_id == 0) throw new RuntimeException( String.format( Locale.JAPAN,"attr not defined.attr_id=0x%x",attr_id));
		return res_id;
	}
}
