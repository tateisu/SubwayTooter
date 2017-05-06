package jp.juggler.subwaytooter;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.Utils;

class StatusButtons implements View.OnClickListener {
	private final Column column;
	private final ActMain activity;
	private final SavedAccount access_info;
	
	private final Button btnBoost;
	private final Button btnFavourite;
	private final ImageButton btnFollow2;
	private final ImageView ivFollowedBy2;
	private final View llFollow2;
	
	StatusButtons( ActMain activity,Column column, View viewRoot ){
		this.activity = activity;
		this.column = column;
		this.access_info = column.access_info;
		
		btnBoost = (Button) viewRoot.findViewById( R.id.btnBoost );
		btnFavourite = (Button) viewRoot.findViewById( R.id.btnFavourite );
		btnFollow2 = (ImageButton) viewRoot.findViewById( R.id.btnFollow2 );
		ivFollowedBy2 = (ImageView) viewRoot.findViewById( R.id.ivFollowedBy2 );
		llFollow2 = viewRoot.findViewById( R.id.llFollow2 );
		
		btnBoost.setOnClickListener( this );
		btnFavourite.setOnClickListener( this );
		btnFollow2.setOnClickListener( this );
		
		View v;
		//
		v = viewRoot.findViewById( R.id.btnMore );
		v.setOnClickListener( this );
		//
		v = viewRoot.findViewById( R.id.btnConversation );
		v.setOnClickListener( this );
		//
		v = viewRoot.findViewById( R.id.btnReply );
		v.setOnClickListener( this );
		
	}
	
	private TootStatus status;
	private UserRelation relation;
	
	void bind( TootStatus status ){
		this.status = status;
		
		int color_normal = Styler.getAttributeColor( activity, R.attr.colorImageButton );
		int color_accent = Styler.getAttributeColor( activity, R.attr.colorImageButtonAccent );
		
		if( TootStatus.VISIBILITY_DIRECT.equals( status.visibility ) ){
			setButton( btnBoost, false, color_accent, R.attr.ic_mail, "" );
		}else if( TootStatus.VISIBILITY_PRIVATE.equals( status.visibility ) ){
			setButton( btnBoost, false, color_accent, R.attr.ic_lock, "" );
		}else if( activity.app_state.isBusyBoost( access_info, status ) ){
			setButton( btnBoost, false, color_normal, R.attr.btn_refresh, "?" );
		}else{
			int color = ( status.reblogged ? color_accent : color_normal );
			setButton( btnBoost, true, color, R.attr.btn_boost, Long.toString( status.reblogs_count ) );
		}
		
		if( activity.app_state.isBusyFav( access_info, status ) ){
			setButton( btnFavourite, false, color_normal, R.attr.btn_refresh, "?" );
		}else{
			int color = ( status.favourited ? color_accent : color_normal );
			setButton( btnFavourite, true, color, R.attr.btn_favourite, Long.toString( status.favourites_count ) );
		}
		
		if( ! activity.pref.getBoolean( Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, false ) ){
			llFollow2.setVisibility( View.GONE );
			this.relation = null;
		}else{
			llFollow2.setVisibility( View.VISIBLE );
			this.relation = UserRelation.load( access_info.db_id, status.account.id );
			Styler.setFollowIcon( activity, btnFollow2, ivFollowedBy2, relation, column.column_type );
			
		}
		
	}
	
	private void setButton( Button b, boolean enabled, int color, int icon_attr, String text ){
		Drawable d = Styler.getAttributeDrawable( activity, icon_attr ).mutate();
		d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
		b.setCompoundDrawablesRelativeWithIntrinsicBounds( d, null, null, null );
		b.setText( text );
		b.setTextColor( color );
		b.setEnabled( enabled );
	}
	
	PopupWindow close_window;
	
	@Override public void onClick( View v ){
		if( close_window != null ) close_window.dismiss();
		switch( v.getId() ){
		case R.id.btnConversation:
			activity.performConversation( access_info, status );
			break;
		case R.id.btnReply:
			if( access_info.isPseudo() ){
				Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
			}else{
				activity.performReply( access_info, status );
			}
			break;
		case R.id.btnBoost:
			if( access_info.isPseudo() ){
				Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
			}else{
				activity.performBoost( access_info, status, false, column.bSimpleList ? activity.boost_complete_callback : null );
			}
			break;
		case R.id.btnFavourite:
			if( access_info.isPseudo() ){
				Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
			}else{
				activity.performFavourite( access_info, status, column.bSimpleList ? activity.favourite_complete_callback : null );
			}
			break;
		case R.id.btnMore:
			new DlgContextMenu( activity, access_info, status.account, status, column.column_type ).show();
			break;
		case R.id.btnFollow2:
			//noinspection StatementWithEmptyBody
			if( relation.blocking || relation.muting ){
				// 何もしない
			}else if( relation.following || relation.requested ){
				activity.callFollow( access_info, status.account, false, false, activity.unfollow_complete_callback );
			}else{
				activity.callFollow( access_info, status.account, true, false, activity.follow_complete_callback );
			}
			break;
		}
	}
}
	