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
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.dialog.DlgQRCode;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.LogCategory;

class DlgContextMenu implements View.OnClickListener, View.OnLongClickListener {
	
	private static final LogCategory log = new LogCategory( "DlgContextMenu" );
	
	@NonNull final ActMain activity;
	@NonNull private final Column column;
	@NonNull private final SavedAccount access_info;
	@NonNull private final UserRelation relation;
	
	@Nullable private final TootAccount who;
	@Nullable private final TootStatusLike status;
	@Nullable private final TootNotification notification;
	
	private final Dialog dialog;
	
	private final ArrayList< SavedAccount > account_list_non_pseudo = new ArrayList<>();
	
	DlgContextMenu(
		@NonNull ActMain activity
		, @NonNull Column column
		, @Nullable TootAccount who
		, @Nullable TootStatusLike status
		, @Nullable TootNotification notification
	){
		this.activity = activity;
		this.column = column;
		this.access_info = column.access_info;
		int column_type = column.column_type;
		
		this.who = who;
		this.status = status;
		this.notification = notification;
		
		this.relation = UserRelation.load( access_info.db_id, who == null ? - 1 : who.id );
		
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
		Button btnMuteApp = viewRoot.findViewById( R.id.btnMuteApp );
		View llAccountActionBar = viewRoot.findViewById( R.id.llAccountActionBar );
		ImageView btnFollow = viewRoot.findViewById( R.id.btnFollow );
		
		ImageView btnMute = viewRoot.findViewById( R.id.btnMute );
		ImageView btnBlock = viewRoot.findViewById( R.id.btnBlock );
		View btnProfile = viewRoot.findViewById( R.id.btnProfile );
		View btnSendMessage = viewRoot.findViewById( R.id.btnSendMessage );
		View btnAccountWebPage = viewRoot.findViewById( R.id.btnAccountWebPage );
		View btnFollowRequestOK = viewRoot.findViewById( R.id.btnFollowRequestOK );
		View btnFollowRequestNG = viewRoot.findViewById( R.id.btnFollowRequestNG );
		View btnFollowFromAnotherAccount = viewRoot.findViewById( R.id.btnFollowFromAnotherAccount );
		View btnSendMessageFromAnotherAccount = viewRoot.findViewById( R.id.btnSendMessageFromAnotherAccount );
		View btnOpenProfileFromAnotherAccount = viewRoot.findViewById( R.id.btnOpenProfileFromAnotherAccount );
		Button btnDomainBlock = viewRoot.findViewById( R.id.btnDomainBlock );
		Button btnInstanceInformation = viewRoot.findViewById( R.id.btnInstanceInformation );
		ImageView ivFollowedBy = viewRoot.findViewById( R.id.ivFollowedBy );
		Button btnOpenTimeline = viewRoot.findViewById( R.id.btnOpenTimeline );
		View btnConversationAnotherAccount = viewRoot.findViewById( R.id.btnConversationAnotherAccount );
		View btnAvatarImage = viewRoot.findViewById( R.id.btnAvatarImage );
		
		View llNotification = viewRoot.findViewById( R.id.llNotification );
		View btnNotificationDelete = viewRoot.findViewById( R.id.btnNotificationDelete );
		Button btnConversationMute = viewRoot.findViewById( R.id.btnConversationMute );
		
		btnStatusWebPage.setOnClickListener( this );
		btnText.setOnClickListener( this );
		btnFavouriteAnotherAccount.setOnClickListener( this );
		btnBoostAnotherAccount.setOnClickListener( this );
		btnReplyAnotherAccount.setOnClickListener( this );
		btnReport.setOnClickListener( this );
		btnMuteApp.setOnClickListener( this );
		btnDelete.setOnClickListener( this );
		btnFollow.setOnClickListener( this );
		btnMute.setOnClickListener( this );
		btnBlock.setOnClickListener( this );
		btnFollow.setOnLongClickListener( this );
		btnProfile.setOnClickListener( this );
		btnSendMessage.setOnClickListener( this );
		btnAccountWebPage.setOnClickListener( this );
		btnFollowRequestOK.setOnClickListener( this );
		btnFollowRequestNG.setOnClickListener( this );
		btnFollowFromAnotherAccount.setOnClickListener( this );
		btnSendMessageFromAnotherAccount.setOnClickListener( this );
		btnOpenProfileFromAnotherAccount.setOnClickListener( this );
		btnOpenTimeline.setOnClickListener( this );
		btnConversationAnotherAccount.setOnClickListener( this );
		btnAvatarImage.setOnClickListener( this );
		btnNotificationDelete.setOnClickListener( this );
		btnConversationMute.setOnClickListener( this );
		
		viewRoot.findViewById( R.id.btnQuoteUrlStatus ).setOnClickListener( this );
		viewRoot.findViewById( R.id.btnQuoteUrlAccount ).setOnClickListener( this );
		viewRoot.findViewById( R.id.btnQuoteName ).setOnClickListener( this );
		
		final ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( activity, log );
		//	final ArrayList< SavedAccount > account_list_non_pseudo_same_instance = new ArrayList<>();
		
		for( SavedAccount a : account_list ){
			if( ! a.isPseudo() ){
				account_list_non_pseudo.add( a );
				//				if( a.host.equalsIgnoreCase( access_info.host ) ){
				//					account_list_non_pseudo_same_instance.add( a );
				//				}
			}
		}
		
		if( status == null ){
			llStatus.setVisibility( View.GONE );
		}else{
			boolean status_by_me = access_info.isMe( status.account );
			
			btnDelete.setVisibility( status_by_me ? View.VISIBLE : View.GONE );
			
			btnReport.setVisibility( status_by_me || access_info.isPseudo() ? View.GONE : View.VISIBLE );
			
			if( status_by_me || status.application == null || TextUtils.isEmpty( status.application.name ) ){
				btnMuteApp.setVisibility( View.GONE );
			}else{
				btnMuteApp.setText( activity.getString( R.string.mute_app_of, status.application.name ) );
			}

			View btnBoostedBy = viewRoot.findViewById( R.id.btnBoostedBy );
			View btnFavouritedBy= viewRoot.findViewById( R.id.btnFavouritedBy );
			btnBoostedBy.setOnClickListener( this );
			btnFavouritedBy.setOnClickListener( this );
			boolean isNA = access_info.isNA();
			btnBoostedBy.setVisibility( isNA ? View.GONE : View.VISIBLE );
			btnFavouritedBy.setVisibility( isNA ? View.GONE : View.VISIBLE );
			
			View btnProfilePin= viewRoot.findViewById( R.id.btnProfilePin );
			View btnProfileUnpin = viewRoot.findViewById( R.id.btnProfileUnpin );
			btnProfilePin.setOnClickListener( this );
			btnProfileUnpin.setOnClickListener( this );
			boolean canPin = status.canPin( access_info);
			btnProfileUnpin.setVisibility(canPin && status.pinned ? View.VISIBLE : View.GONE );
			btnProfilePin.setVisibility( canPin && !status.pinned ? View.VISIBLE : View.GONE );
			
			
		}
		
		if( notification == null ){
			llNotification.setVisibility( View.GONE );
		}else{
			if( status == null || ! TootNotification.TYPE_MENTION.equals( notification.type ) ){
				btnConversationMute.setVisibility( View.GONE );
			}else{
				btnConversationMute.setText( status.muted ? R.string.unmute_this_conversation : R.string.mute_this_conversation );
			}
		}
		
		if( access_info.isPseudo() ){
			llAccountActionBar.setVisibility( View.GONE );
		}else{
			
			// 被フォロー状態
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
		
		
		if( who == null ){
			btnInstanceInformation.setVisibility( View.GONE );
			btnDomainBlock.setVisibility( View.GONE );
		}else{
			btnInstanceInformation.setVisibility( View.VISIBLE );
			btnInstanceInformation.setOnClickListener( this );
			btnInstanceInformation.setText( activity.getString( R.string.instance_information_of, access_info.getAccountHost( who )));
			
			int acct_delm = who.acct.indexOf( "@" );
			if( - 1 == acct_delm || access_info.isPseudo() ){
				// 疑似アカウントではドメインブロックできない
				// 自ドメインはブロックできない
				btnDomainBlock.setVisibility( View.GONE );
			}else{
				btnInstanceInformation.setVisibility( View.VISIBLE );
				btnInstanceInformation.setOnClickListener( this );
				btnDomainBlock.setText( activity.getString( R.string.block_domain_that, who.acct.substring( acct_delm + 1 ) ) );
			}
		}
		
		viewRoot.findViewById( R.id.btnAccountText ).setOnClickListener( this );
		
		if( access_info.isPseudo() ){
			btnProfile.setVisibility( View.GONE );
			btnSendMessage.setVisibility( View.GONE );
		}
		
		if( column_type != Column.TYPE_FOLLOW_REQUESTS ){
			btnFollowRequestOK.setVisibility( View.GONE );
			btnFollowRequestNG.setVisibility( View.GONE );
		}
		
		if( account_list_non_pseudo.isEmpty() ){
			btnFollowFromAnotherAccount.setVisibility( View.GONE );
			btnSendMessageFromAnotherAccount.setVisibility( View.GONE );
		}
		
		viewRoot.findViewById( R.id.btnNickname ).setOnClickListener( this );
		viewRoot.findViewById( R.id.btnCancel ).setOnClickListener( this );
		viewRoot.findViewById( R.id.btnAccountQrCode ).setOnClickListener( this );
		
		String host = access_info.getAccountHost( who );
		if( TextUtils.isEmpty( host ) || host.equals( "?" ) ){
			btnOpenTimeline.setVisibility( View.GONE );
		}else{
			btnOpenTimeline.setText( activity.getString( R.string.open_local_timeline_for, host ) );
		}
		
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
		
		try{
			dialog.dismiss();
		}catch( Throwable ignored ){
			// IllegalArgumentException がたまに出る
		}
		
		int pos = activity.nextPosition( column );
		
		switch( v.getId() ){
		
		case R.id.btnStatusWebPage:
			if( status != null ){
				activity.openChromeTab( pos, access_info, status.url, true );
			}
			break;
		
		case R.id.btnText:
			if( status != null ){
				ActText.open( activity, ActMain.REQUEST_CODE_TEXT, access_info, status );
			}
			break;
		
		case R.id.btnFavouriteAnotherAccount:
			activity.openFavouriteFromAnotherAccount( access_info, status );
			break;
		
		case R.id.btnBoostAnotherAccount:
			activity.openBoostFromAnotherAccount( access_info, status );
			break;
		
		case R.id.btnReplyAnotherAccount:
			activity.openReplyFromAnotherAccount( status );
			break;
		
		case R.id.btnConversationAnotherAccount:
			if( status != null ){
				activity.openStatusOtherInstance( pos, access_info, status );
			}
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
			if( who != null && status instanceof TootStatus ){
				activity.openReportForm( access_info, who, (TootStatus) status );
			}
			break;
		
		case R.id.btnMuteApp:
			if( status != null && status.application != null ){
				activity.performMuteApp( status.application );
			}
			break;
		
		case R.id.btnBoostedBy:
			if( status != null ){
				activity.addColumn( pos, access_info, Column.TYPE_BOOSTED_BY, status.id );
			}
			break;
		
		case R.id.btnFavouritedBy:
			if( status != null ){
				activity.addColumn( pos, access_info, Column.TYPE_FAVOURITED_BY, status.id );
			}
			break;
		
		case R.id.btnFollow:
			if( who == null ){
				// サーバのバグで誰のことか分からないので何もできない
			}else if( access_info.isPseudo() ){
				activity.openFollowFromAnotherAccount( access_info, who );
			}else if( relation.following || relation.requested ){
				activity.callFollow( access_info, who, false, false, activity.unfollow_complete_callback );
			}else{
				activity.callFollow( access_info, who, true, false, activity.follow_complete_callback );
			}
			break;
		
		case R.id.btnAccountText:
			if( who != null ){
				ActText.open( activity, ActMain.REQUEST_CODE_TEXT, access_info, who );
			}
			break;
		
		case R.id.btnMute:
			if( who == null ){
				// サーバのバグで誰のことか分からないので何もできない
			}else if( relation.muting ){
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
			if( who == null ){
				// サーバのバグで誰のことか分からないので何もできない
			}else if( relation.blocking ){
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
			if( who != null ){
				activity.openProfile( pos, access_info, who );
			}
			break;
		
		case R.id.btnSendMessage:
			if( who != null ){
				activity.performMention( access_info, who );
			}
			break;
		
		case R.id.btnAccountWebPage:
			if( who != null ){
				activity.openChromeTab( pos, access_info, who.url, true );
			}
			break;
		
		case R.id.btnFollowRequestOK:
			if( who != null ){
				activity.callFollowRequestAuthorize( access_info, who, true );
			}
			break;
		
		case R.id.btnFollowRequestNG:
			if( who != null ){
				activity.callFollowRequestAuthorize( access_info, who, false );
			}
			break;
		
		case R.id.btnFollowFromAnotherAccount:
			if( who != null ){
				activity.openFollowFromAnotherAccount( access_info, who );
			}
			break;
		
		case R.id.btnSendMessageFromAnotherAccount:
			if( who != null ){
				activity.performMentionFromAnotherAccount( access_info, who, account_list_non_pseudo );
			}
			break;
		
		case R.id.btnOpenProfileFromAnotherAccount:
			if( who != null ){
				activity.openProfileFromAnotherAccount( pos, access_info, who );
			}
			break;
		
		case R.id.btnNickname:
			if( who != null ){
				ActNickname.open( activity, access_info.getFullAcct( who ), true, ActMain.REQUEST_CODE_NICKNAME );
			}
			break;
		
		case R.id.btnCancel:
			dialog.cancel();
			break;
		
		case R.id.btnAccountQrCode:
			if( who != null ){
				DlgQRCode.open( activity, who.decoded_display_name, access_info.getUserUrl( who.acct ) );
			}
			break;
		
		case R.id.btnDomainBlock:
			if( who == null ){
				// サーバのバグで誰のことか分からないので何もできない
			}else if( access_info.isPseudo() ){
				// 疑似アカウントではドメインブロックできない
			}else{
				int acct_delm = who.acct.indexOf( "@" );
				if( - 1 == acct_delm ){
					// 疑似アカウントではドメインブロックできない
					// 自ドメインはブロックできない
				}else{
					final String domain = who.acct.substring( acct_delm + 1 );
					new AlertDialog.Builder( activity )
						.setMessage( activity.getString( R.string.confirm_block_domain, domain ) )
						.setNegativeButton( R.string.cancel, null )
						.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
							@Override public void onClick( DialogInterface dialog, int which ){
								activity.callDomainBlock( access_info, domain, true, null );
							}
						} )
						.show();
				}
			}
			break;
		
		case R.id.btnOpenTimeline:
			String host = access_info.getAccountHost( who );
			if( TextUtils.isEmpty( host ) || host.equals( "?" ) ){
				// 何もしない
			}else{
				activity.openTimelineFor( host );
			}
			break;
		
		case R.id.btnAvatarImage:
			if( who != null ){
				String url = ! TextUtils.isEmpty( who.avatar ) ? who.avatar : who.avatar_static;
				if( url != null ) activity.openChromeTab( pos, access_info, url, true );
			}
			break;
		
		case R.id.btnQuoteUrlStatus:
			if( status != null ){
				String url = TextUtils.isEmpty( status.url ) ? "" : status.url + " ";
				activity.openPost( url );
			}
			break;
		
		case R.id.btnQuoteUrlAccount:
			if( who != null ){
				String url = TextUtils.isEmpty( who.url ) ? "" : who.url + " ";
				activity.openPost( url );
			}
			break;
		
		case R.id.btnNotificationDelete:
			if( notification != null ){
				activity.deleteNotificationOne( access_info, notification );
			}
			break;
		
		case R.id.btnConversationMute:
			if( notification != null && status != null ){
				activity.toggleConversationMute( access_info, status );
			}
			break;
		
		case R.id.btnQuoteName:
			if( who != null ){
				String sv = who.display_name;
				try{
					String fmt = activity.pref.getString( Pref.KEY_QUOTE_NAME_FORMAT, null );
					if( fmt != null && fmt.contains( "%1$s" ) ){
						sv = String.format( fmt, sv );
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
				activity.openPost( sv );
			}
			break;
		case R.id.btnInstanceInformation:
			if( who != null ){
				activity.openInstanceInformation( pos, access_info.getAccountHost( who ).toLowerCase() );
			}
			break;
		
		case R.id.btnProfilePin:
			activity.setProfilePin( access_info, status, true);
			break;
		case R.id.btnProfileUnpin:
			activity.setProfilePin( access_info, status, false);
			break;
		}
	}
	
	@Override public boolean onLongClick( View v ){
		
		switch( v.getId() ){
		case R.id.btnFollow:
			try{
				dialog.dismiss();
			}catch( Throwable ignored ){
				// IllegalArgumentException がたまに出る
			}
			activity.openFollowFromAnotherAccount( access_info, who );
			return true;
			
		}
		return false;
	}
}
