package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.Locale;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.EmojiImageSpan;

public class Styler {
	static int getVisibilityIcon( Context context, String visibility ){
		return
			getAttributeResourceId( context,
				TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? R.attr.ic_public
					: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? R.attr.ic_lock_open
					: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? R.attr.ic_lock
					: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? R.attr.ic_mail
					: TootStatus.VISIBILITY_WEB_SETTING.equals( visibility ) ? R.attr.ic_question
					: R.attr.ic_question );
		
	}
	
	static String getVisibilityString( Context context, String visibility ){
		return
			TootStatus.VISIBILITY_PUBLIC.equals( visibility ) ? context.getString( R.string.visibility_public )
				: TootStatus.VISIBILITY_UNLISTED.equals( visibility ) ? context.getString( R.string.visibility_unlisted )
				: TootStatus.VISIBILITY_PRIVATE.equals( visibility ) ? context.getString( R.string.visibility_private )
				: TootStatus.VISIBILITY_DIRECT.equals( visibility ) ? context.getString( R.string.visibility_direct )
				: TootStatus.VISIBILITY_WEB_SETTING.equals( visibility ) ? context.getString( R.string.visibility_web_setting )
				: "?";
	}
	
	static CharSequence getVisibilityCaption( Context context, String visibility ){
		SpannableStringBuilder sb = new SpannableStringBuilder();
		
		int icon_id = getVisibilityIcon( context, visibility );
		int start = sb.length();
		sb.append( visibility );
		int end = sb.length();
		sb.setSpan( new EmojiImageSpan( context, icon_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		sb.append( ' ' );
		sb.append( getVisibilityString( context, visibility ) );
		if( TootStatus.VISIBILITY_WEB_SETTING.equals( visibility ) ){
			sb.append( "\n　　(" );
			sb.append( context.getString( R.string.mastodon_1_6_later ) );
			sb.append( ")" );
		}
		return sb;
	}
	
	public static int getAttributeColor( @NonNull Context context, int attr_id ){
		Resources.Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes( new int[]{ attr_id } );
		int color = a.getColor( 0, 0xFFFF0000 );
		a.recycle();
		return color;
	}
	
	@NonNull static Drawable getAttributeDrawable( @NonNull Context context, int attr_id ){
		int res_id = getAttributeResourceId( context, attr_id );
		Drawable d = ContextCompat.getDrawable( context, res_id );
		if( d == null )
			throw new RuntimeException( String.format( Locale.JAPAN, "getDrawable failed. drawable_id=0x%x", res_id ) );
		return d;
	}
	
	static int getAttributeResourceId( @NonNull Context context, int attr_id ){
		Resources.Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes( new int[]{ attr_id } );
		int res_id = a.getResourceId( 0, 0 );
		a.recycle();
		if( res_id == 0 )
			throw new RuntimeException( String.format( Locale.JAPAN, "attr not defined.attr_id=0x%x", attr_id ) );
		return res_id;
	}
	
	static void setFollowIcon(
		@NonNull Context context
		, @NonNull ImageButton ib
		, @NonNull ImageView iv
		, @NonNull UserRelation relation
		, @NonNull TootAccount who
	){
		
		// 被フォロー状態
		if( ! relation.followed_by ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			iv.setImageResource( Styler.getAttributeResourceId( context, R.attr.ic_followed_by ) );
			
			// 被フォローリクエスト状態の時に followed_by が 真と偽の両方がありえるようなので
			// Relationshipだけを見ても被フォローリクエスト状態は分からないっぽい
			// 仕方ないので馬鹿正直に「 followed_byが真ならバッジをつける」しかできない
		}
		
		// follow button
		int color_attr;
		int icon_attr;
		
		if( relation.blocking ){
			icon_attr = R.attr.ic_block;
			color_attr = R.attr.colorImageButton;
		}else if( relation.muting ){
			icon_attr = R.attr.ic_mute;
			color_attr = R.attr.colorImageButton;
		}else if( relation.getFollowing( who ) ){
			icon_attr = R.attr.ic_follow_cross;
			color_attr = R.attr.colorImageButtonAccent;
		}else if( relation.getRequested( who ) ){
			icon_attr = R.attr.ic_follow_wait;
			color_attr = R.attr.colorRegexFilterError;
		}else{
			icon_attr = R.attr.ic_follow_plus;
			color_attr = R.attr.colorImageButton;
		}
		
		int color = Styler.getAttributeColor( context, color_attr );
		Drawable d = Styler.getAttributeDrawable( context, icon_attr ).mutate();
		d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
		ib.setImageDrawable( d );
		
	}
	
	static void setIconDefaultColor( Context context, ImageView iv, int icon_attr ){
		iv.setImageResource( Styler.getAttributeResourceId( context, icon_attr ) );
	}
	
	static void setIconCustomColor( Context context, ImageView iv, int color, int icon_attr ){
		Drawable d = Styler.getAttributeDrawable( context, icon_attr ).mutate();
		d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
		iv.setImageDrawable( d );
	}
	
	static Drawable getAdaptiveRippleDrawable( int normalColor, int pressedColor ){
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ){
			return new RippleDrawable(
				ColorStateList.valueOf( pressedColor )
				, getShape( normalColor )
				, null
			);
		}else{
			return getStateListDrawable( normalColor, pressedColor );
		}
	}
	
	private static Drawable getShape( int color ){
		RectShape r = new RectShape();
		ShapeDrawable shapeDrawable = new ShapeDrawable( r );
		shapeDrawable.getPaint().setColor( color );
		return shapeDrawable;
	}
	
	private static StateListDrawable getStateListDrawable( int normalColor, int pressedColor ){
		StateListDrawable states = new StateListDrawable();
		states.addState( new int[]{ android.R.attr.state_pressed },
			new ColorDrawable( pressedColor ) );
		states.addState( new int[]{ android.R.attr.state_focused },
			new ColorDrawable( pressedColor ) );
		states.addState( new int[]{ android.R.attr.state_activated },
			new ColorDrawable( pressedColor ) );
		states.addState( new int[]{},
			new ColorDrawable( normalColor ) );
		return states;
	}
	
	private static final float form_width_max = 420f;
	
	private static int getHorizontalPadding( View v, float delta_dp ){
		DisplayMetrics dm = v.getResources().getDisplayMetrics();
		int screen_w = dm.widthPixels;
		int content_w = (int) ( 0.5f + form_width_max * dm.density );
		int pad_lr = ( screen_w - content_w ) / 2;
		return ( pad_lr < 0 ? 0 : pad_lr ) + (int) ( 0.5f + delta_dp * dm.density );
	}
	
	static void fixHorizontalPadding( View v ){
		int pad_lr = getHorizontalPadding( v, 12f );
		
		int pad_t = v.getPaddingTop();
		int pad_b = v.getPaddingBottom();
		v.setPaddingRelative( pad_lr, pad_t, pad_lr, pad_b );
	}
	
	static void fixHorizontalPadding2( View v ){
		int pad_lr = getHorizontalPadding( v, 0f );
		
		int pad_t = v.getPaddingTop();
		int pad_b = v.getPaddingBottom();
		v.setPaddingRelative( pad_lr, pad_t, pad_lr, pad_b );
	}
	
	static void fixHorizontalMargin( View v ){
		int pad_lr = getHorizontalPadding( v, 0f );
		
		ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
		lp.leftMargin = pad_lr;
		lp.rightMargin = pad_lr;
	}
	
}
