package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import java.util.ArrayList;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.AccountPicker;
import jp.juggler.subwaytooter.dialog.DlgQRCode;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.LogCategory;

class DlgContextMenu implements View.OnClickListener, View.OnLongClickListener {
	
	private static final LogCategory log = new LogCategory( "DlgContextMenu" );
	
	@NonNull final ActMain activity;
	@NonNull private final SavedAccount access_info;
	@NonNull private final TootAccount who;
	@Nullable private final TootStatus status;
	@NonNull private final UserRelation relation;
	@NonNull private final Column column;
	
	private final Dialog dialog;
	
	private final ArrayList< SavedAccount > account_list_non_pseudo_same_instance = new ArrayList<>();
	private final ArrayList< SavedAccount > account_list_non_pseudo = new ArrayList<>();
	
	DlgContextMenu(
		@NonNull ActMain activity
	    , @NonNull Column column
		, @NonNull TootAccount who
		, @Nullable TootStatus status
	){
		this.activity = activity;
		this.column = column;
		this.access_info = column.access_info;
		this.who = who;
		this.status = status;
		int column_type = column.column_type;
		
		this.relation = UserRelation.load( access_info.db_id, who.id );
		
		@SuppressLint("InflateParams") View viewRoot = activity.getLayoutInflater().inflate( R.layout.dlg_context_menu, null, false );
		this.dialog = new Dialog( activity );
		dialog.setContentView( viewRoot );
		dialog.setCancelable( true );
		dialog.setCanceledOnTouchOutside( true );
		
		View llStatus = viewRoot.findViewById( R.id.llStatus );
		View btnStatusWebPage = viewRoot.findViewById( R.id.btnStatusWebPage );
		View btnText = viewRoot.findViewById( R.id.btnText );
		View btnFavouriteAnotherAccount = viewRoot.findViewById( R.id.btnFavouriteAnotherAccount );
		View btnBoostAnotherAccount = viewRoot.findViewById( R.id.btnBoostAnotherAccount );
		View btnReplyAnotherAccount = viewRoot.findViewById( R.id.btnReplyAnotherAccount );
		View btnDelete = viewRoot.findViewById( R.id.btnDelete );
		View btnReport = viewRoot.findViewById( R.id.btnReport );
		Button btnMuteApp = (Button) viewRoot.findViewById( R.id.btnMuteApp );
		View llAccountActionBar = viewRoot.findViewById( R.id.llAccountActionBar );
		ImageView btnFollow = (ImageView) viewRoot.findViewById( R.id.btnFollow );
		
		ImageView btnMute = (ImageView) viewRoot.findViewById( R.id.btnMute );
		ImageView btnBlock = (ImageView) viewRoot.findViewById( R.id.btnBlock );
		View btnProfile = viewRoot.findViewById( R.id.btnProfile );
		View btnSendMessage = viewRoot.findViewById( R.id.btnSendMessage );
		View btnAccountWebPage = viewRoot.findViewById( R.id.btnAccountWebPage );
		View btnFollowRequestOK = viewRoot.findViewById( R.id.btnFollowRequestOK );
		View btnFollowRequestNG = viewRoot.findViewById( R.id.btnFollowRequestNG );
		View btnFollowFromAnotherAccount = viewRoot.findViewById( R.id.btnFollowFromAnotherAccount );
		View btnSendMessageFromAnotherAccount = viewRoot.findViewById( R.id.btnSendMessageFromAnotherAccount );
		View btnOpenProfileFromAnotherAccount = viewRoot.findViewById( R.id.btnOpenProfileFromAnotherAccount );
		
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( log );
		
		for( SavedAccount a : account_list ){
			if( ! a.isPseudo() ){
				account_list_non_pseudo.add( a );
				if( a.host.equalsIgnoreCase( access_info.host ) ){
					account_list_non_pseudo_same_instance.add( a );
				}
			}
		}
		
		if( status == null ){
			llStatus.setVisibility( View.GONE );
		}else{
			boolean status_by_me = access_info.isMe( status.account );
			
			btnStatusWebPage.setOnClickListener( this );
			btnText.setOnClickListener( this );
			
			if( account_list_non_pseudo_same_instance.isEmpty() ){
				btnFavouriteAnotherAccount.setVisibility( View.GONE );
				btnBoostAnotherAccount.setVisibility( View.GONE );
				btnReplyAnotherAccount.setVisibility( View.GONE );
			}else{
				btnFavouriteAnotherAccount.setOnClickListener( this );
				btnBoostAnotherAccount.setOnClickListener( this );
				btnReplyAnotherAccount.setOnClickListener( this );
			}
			if( access_info.isPseudo() ){
				btnDelete.setVisibility( View.GONE );
				btnReport.setVisibility( View.GONE );
				btnMuteApp.setVisibility( View.GONE );
			}else if( status_by_me ){
				btnDelete.setOnClickListener( this );
				btnReport.setVisibility( View.GONE );
				btnMuteApp.setVisibility( View.GONE );
			}else{
				btnDelete.setVisibility( View.GONE );
				btnReport.setOnClickListener( this );
				if( status.application == null || TextUtils.isEmpty( status.application.name ) ){
					btnMuteApp.setVisibility( View.GONE );
				}else{
					btnMuteApp.setText( activity.getString( R.string.mute_app_of, status.application.name ) );
					btnMuteApp.setOnClickListener( this );
				}
			}
		}
		
		if( access_info.isPseudo() ){
			llAccountActionBar.setVisibility( View.GONE );
		}else{
			btnFollow.setOnClickListener( this );
			btnMute.setOnClickListener( this );
			btnBlock.setOnClickListener( this );
			
			btnFollow.setOnLongClickListener( this );
			
			// 被フォロー状態
			ImageView ivFollowedBy = (ImageView) viewRoot.findViewById( R.id.ivFollowedBy );
			if( ! relation.followed_by ){
				ivFollowedBy.setVisibility( View.GONE );
			}else{
				ivFollowedBy.setVisibility( View.VISIBLE );
				ivFollowedBy.setImageResource( Styler.getAttributeResourceId( activity, R.attr.ic_followed_by ) );
			}
			
			// follow button
			int icon_attr = ( relation.following ? R.attr.ic_follow_cross : R.attr.ic_follow_plus );
			int color_attr = ( relation.requested ? R.attr.colorRegexFilterError
				: relation.following ? R.attr.colorImageButtonAccent
				: R.attr.colorImageButton );
			int color = Styler.getAttributeColor( activity, color_attr );
			Drawable d = Styler.getAttributeDrawable( activity, icon_attr ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			btnFollow.setImageDrawable( d );
			
			// mute button
			icon_attr = R.attr.ic_mute;
			color_attr = ( relation.muting ? R.attr.colorImageButtonAccent : R.attr.colorImageButton );
			color = Styler.getAttributeColor( activity, color_attr );
			d = Styler.getAttributeDrawable( activity, icon_attr ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			btnMute.setImageDrawable( d );
			
			// block button
			icon_attr = R.attr.ic_block;
			color_attr = ( relation.blocking ? R.attr.colorImageButtonAccent : R.attr.colorImageButton );
			color = Styler.getAttributeColor( activity, color_attr );
			d = Styler.getAttributeDrawable( activity, icon_attr ).mutate();
			d.setColorFilter( color, PorterDuff.Mode.SRC_ATOP );
			btnBlock.setImageDrawable( d );
		}
		
		if( access_info.isPseudo() ){
			btnProfile.setVisibility( View.GONE );
			btnSendMessage.setVisibility( View.GONE );
		}else{
			btnProfile.setOnClickListener( this );
			btnSendMessage.setOnClickListener( this );
		}
		
		btnAccountWebPage.setOnClickListener( this );
		
		if( column_type == Column.TYPE_FOLLOW_REQUESTS ){
			btnFollowRequestOK.setOnClickListener( this );
			btnFollowRequestNG.setOnClickListener( this );
		}else{
			btnFollowRequestOK.setVisibility( View.GONE );
			btnFollowRequestNG.setVisibility( View.GONE );
		}
		
		if( account_list_non_pseudo.isEmpty() ){
			btnFollowFromAnotherAccount.setVisibility( View.GONE );
			btnSendMessageFromAnotherAccount.setVisibility( View.GONE );
		}else{
			btnFollowFromAnotherAccount.setOnClickListener( this );
			btnSendMessageFromAnotherAccount.setOnClickListener( this );
		}
		
		if( account_list_non_pseudo_same_instance.isEmpty() ){
			btnOpenProfileFromAnotherAccount.setVisibility( View.GONE );
		}else{
			btnOpenProfileFromAnotherAccount.setOnClickListener( this );
		}
		
		View v = viewRoot.findViewById( R.id.btnNickname );
		v.setOnClickListener( this );
		
		v = viewRoot.findViewById( R.id.btnCancel );
		v.setOnClickListener( this );
		
		v = viewRoot.findViewById( R.id.btnBoostedBy );
		v.setOnClickListener( this );
		
		v = viewRoot.findViewById( R.id.btnFavouritedBy );
		v.setOnClickListener( this );
		
		v = viewRoot.findViewById( R.id.btnAccountQrCode );
		v.setOnClickListener( this );
		
	}
	
	void show(){
		//noinspection ConstantConditions
		WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
		lp.width = (int) ( 0.5f + 280f * activity.density );
		lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
		dialog.getWindow().setAttributes( lp );
		
		dialog.show();
	}
	
	@Override public void onClick( View v ){
		
		dialog.dismiss();
		
		int pos = activity.nextPosition( column ) ;
		
		switch( v.getId() ){
		
		case R.id.btnStatusWebPage:
			if( status != null ){
				activity.openChromeTab(pos,access_info, status.url, true );
			}
			break;
		
		case R.id.btnText:
			if( status != null ){
				ActText.open( activity, access_info, status );
			}
			break;
		
		case R.id.btnFavouriteAnotherAccount:
			activity.openFavouriteFromAnotherAccount( access_info,status );
			break;
		
		case R.id.btnBoostAnotherAccount:
			activity.openBoostFromAnotherAccount( access_info,status );
			break;

		case R.id.btnReplyAnotherAccount:
			activity.openReplyFromAnotherAccount( access_info,status );
			break;
		
		case R.id.btnDelete:
			if( status != null ){
				new AlertDialog.Builder( activity )
					.setMessage( activity.getString( R.string.confirm_delete_status ) )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							activity.deleteStatus( access_info, status.id );
						}
					} )
					.show();
			}
			break;
		
		case R.id.btnReport:
			if( status != null ){
				activity.openReportForm( access_info, who, status );
			}
			break;
		
		case R.id.btnMuteApp:
			if( status != null && status.application != null ){
				activity.performMuteApp( status.application );
			}
			break;
		
		case R.id.btnBoostedBy:
			if( status != null ){
				activity.addColumn( pos,access_info, Column.TYPE_BOOSTED_BY, status.id );
			}
			break;
		
		case R.id.btnFavouritedBy:
			if( status != null ){
				activity.addColumn( pos,access_info, Column.TYPE_FAVOURITED_BY, status.id );
			}
			break;
		
		case R.id.btnFollow:
			if( access_info.isPseudo() ){
				activity.openFollowFromAnotherAccount( access_info, who );
			}else if( relation.following || relation.requested ){
				activity.callFollow( access_info, who, false, false, activity.unfollow_complete_callback );
			}else{
				activity.callFollow( access_info, who, true, false, activity.follow_complete_callback );
			}
			break;
		
		case R.id.btnMute:
			if( relation.muting ){
				activity.callMute( access_info, who, false, null );
			}else{
				new AlertDialog.Builder( activity )
					.setMessage( activity.getString( R.string.confirm_mute_user, who.username ) )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							activity.callMute( access_info, who, true, null );
						}
					} )
					.show();
			}
			break;
		
		case R.id.btnBlock:
			if( relation.blocking ){
				activity.callBlock( access_info, who, false, null );
			}else{
				new AlertDialog.Builder( activity )
					.setMessage( activity.getString( R.string.confirm_block_user, who.username ) )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							activity.callBlock( access_info, who, true, null );
						}
					} )
					.show();
			}
			break;
		
		case R.id.btnProfile:
			activity.performOpenUser( pos,access_info, who );
			break;
		
		case R.id.btnSendMessage:
			activity.performMention( access_info, who );
			break;
		
		case R.id.btnAccountWebPage:
			activity.openChromeTab( pos, access_info, who.url, true );
			break;
		
		case R.id.btnFollowRequestOK:
			activity.callFollowRequestAuthorize( access_info, who, true );
			break;
		
		case R.id.btnFollowRequestNG:
			activity.callFollowRequestAuthorize( access_info, who, false );
			break;
		
		case R.id.btnFollowFromAnotherAccount:
			activity.openFollowFromAnotherAccount( access_info, who );
			break;
		
		case R.id.btnSendMessageFromAnotherAccount:
			activity.performMentionFromAnotherAccount( access_info, who, account_list_non_pseudo );
			break;
		
		case R.id.btnOpenProfileFromAnotherAccount:
			activity.performOpenUserFromAnotherAccount( pos,who, account_list_non_pseudo_same_instance );
			break;
		
		case R.id.btnNickname:
			ActNickname.open( activity, access_info.getFullAcct( who ), ActMain.REQUEST_CODE_NICKNAME );
			break;
		
		case R.id.btnCancel:
			dialog.cancel();
			break;
		
		case R.id.btnAccountQrCode:
			DlgQRCode.open( activity, who.display_name, access_info.getUserUrl( who.acct ) );
			break;
			
		}
	}
	
	@Override public boolean onLongClick( View v ){
		
		switch( v.getId() ){
		case R.id.btnFollow:
			dialog.dismiss();
			activity.openFollowFromAnotherAccount( access_info, who );
			return true;
			
		}
		return false;
	}
}
