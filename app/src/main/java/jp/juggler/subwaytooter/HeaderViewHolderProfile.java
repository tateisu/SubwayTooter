package jp.juggler.subwaytooter;

import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import jp.juggler.subwaytooter.action.Action_Follow;
import jp.juggler.subwaytooter.action.Action_User;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.EmojiImageSpan;
import jp.juggler.subwaytooter.util.EmojiMap201709;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.MyLinkMovementMethod;
import jp.juggler.subwaytooter.view.MyNetworkImageView;

class HeaderViewHolderProfile extends HeaderViewHolderBase implements View.OnClickListener, View.OnLongClickListener {
	
	private final MyNetworkImageView ivBackground;
	private final TextView tvCreated;
	private final MyNetworkImageView ivAvatar;
	private final TextView tvDisplayName;
	private final TextView tvAcct;
	private final Button btnFollowing;
	private final Button btnFollowers;
	private final Button btnStatusCount;
	private final TextView tvNote;
	private final ImageButton btnFollow;
	private final ImageView ivFollowedBy;
	private final View llProfile;
	private final TextView tvRemoteProfileWarning;
	private final NetworkEmojiInvalidator name_invalidator;
	private final NetworkEmojiInvalidator note_invalidator;
	
	private TootAccount who;
	
	private TootAccount who_moved;
	
	private final View llMoved;
	private final TextView tvMoved;
	private final MyNetworkImageView ivMoved;
	private final TextView tvMovedName;
	private final TextView tvMovedAcct;
	private final ImageButton btnMoved;
	private final ImageView ivMovedBy;
	private final NetworkEmojiInvalidator moved_caption_invalidator;
	private final NetworkEmojiInvalidator moved_name_invalidator;
	
	
	HeaderViewHolderProfile( ActMain arg_activity, Column column, ListView parent ){
		super( arg_activity, column
			, arg_activity.getLayoutInflater().inflate( R.layout.lv_header_account, parent, false )
		);
		
		ivBackground = viewRoot.findViewById( R.id.ivBackground );
		llProfile = viewRoot.findViewById( R.id.llProfile );
		tvCreated = viewRoot.findViewById( R.id.tvCreated );
		ivAvatar = viewRoot.findViewById( R.id.ivAvatar );
		tvDisplayName = viewRoot.findViewById( R.id.tvDisplayName );
		tvAcct = viewRoot.findViewById( R.id.tvAcct );
		btnFollowing = viewRoot.findViewById( R.id.btnFollowing );
		btnFollowers = viewRoot.findViewById( R.id.btnFollowers );
		btnStatusCount = viewRoot.findViewById( R.id.btnStatusCount );
		tvNote = viewRoot.findViewById( R.id.tvNote );
		View btnMore = viewRoot.findViewById( R.id.btnMore );
		btnFollow = viewRoot.findViewById( R.id.btnFollow );
		ivFollowedBy = viewRoot.findViewById( R.id.ivFollowedBy );
		tvRemoteProfileWarning = viewRoot.findViewById( R.id.tvRemoteProfileWarning );
		
		llMoved = viewRoot.findViewById( R.id.llMoved );
		tvMoved = viewRoot.findViewById( R.id.tvMoved );
		ivMoved = viewRoot.findViewById( R.id.ivMoved );
		tvMovedName = viewRoot.findViewById( R.id.tvMovedName );
		tvMovedAcct = viewRoot.findViewById( R.id.tvMovedAcct );
		btnMoved = viewRoot.findViewById( R.id.btnMoved );
		ivMovedBy = viewRoot.findViewById( R.id.ivMovedBy );
		
		
		ivBackground.setOnClickListener( this );
		btnFollowing.setOnClickListener( this );
		btnFollowers.setOnClickListener( this );
		btnStatusCount.setOnClickListener( this );
		btnMore.setOnClickListener( this );
		btnFollow.setOnClickListener( this );
		tvRemoteProfileWarning.setOnClickListener( this );

		btnMoved.setOnClickListener( this );
		llMoved.setOnClickListener( this );
		
		btnMoved.setOnLongClickListener( this );
		btnFollow.setOnLongClickListener( this );
		
		tvNote.setMovementMethod( MyLinkMovementMethod.getInstance() );
		
		name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvDisplayName );
		note_invalidator = new NetworkEmojiInvalidator( activity.handler, tvNote );
		moved_caption_invalidator = new NetworkEmojiInvalidator( activity.handler, tvMoved );
		moved_name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvMovedName );
		
		if( ! Float.isNaN( activity.timeline_font_size_sp ) ){
			tvMovedName.setTextSize( activity.timeline_font_size_sp );
			tvMoved.setTextSize( activity.timeline_font_size_sp );
		}
		
		if( ! Float.isNaN( activity.acct_font_size_sp ) ){
			tvMovedAcct.setTextSize( activity.acct_font_size_sp );
			tvCreated.setTextSize( activity.acct_font_size_sp );
		}
	}
	
	void showColor(){
		int c = column.column_bg_color;
		if( c == 0 ){
			c = Styler.getAttributeColor( activity, R.attr.colorProfileBackgroundMask );
		}else{
			c = 0xc0000000 | ( 0x00ffffff & c );
		}
		llProfile.setBackgroundColor( c );
	}
	
	void bindData( Column column ){
		this.who = column.who_account;
		
		showColor();
		
		llMoved.setVisibility( View.GONE );
		tvMovedAcct.setVisibility( View.GONE );
		
		if( who == null ){
			tvCreated.setText( "" );
			ivBackground.setImageDrawable( null );
			ivAvatar.setImageDrawable( null );
			
			tvAcct.setText( "@" );

			tvDisplayName.setText( "" );
			name_invalidator.register( null );

			tvNote.setText( "" );
			note_invalidator.register( null );

			btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + "?" );
			btnFollowing.setText( activity.getString( R.string.following ) + "\n" + "?" );
			btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + "?" );
			
			btnFollow.setImageDrawable( null );
			tvRemoteProfileWarning.setVisibility( View.GONE );
		}else{
			tvCreated.setText( TootStatus.formatTime( tvCreated.getContext(), who.time_created_at, true ) );
			ivBackground.setImageUrl( activity.pref, 0f, access_info.supplyBaseUrl( who.header_static ) );

			ivAvatar.setImageUrl( activity.pref, 16f, access_info.supplyBaseUrl( who.avatar_static ), access_info.supplyBaseUrl( who.avatar ) );
			
			
			Spannable name =who.decoded_display_name ;
			tvDisplayName.setText( name );
			name_invalidator.register( name );
			
			tvRemoteProfileWarning.setVisibility( column.access_info.isRemoteUser( who ) ? View.VISIBLE : View.GONE );
			
			SpannableStringBuilder sb = new SpannableStringBuilder(  );
			sb.append( "@" ).append( access_info.getFullAcct( who ) );
			if( who.locked ){
				sb.append(" ");
				int start = sb.length();
				sb.append( "locked" );
				int end = sb.length();
				EmojiMap201709.EmojiInfo info = EmojiMap201709.sShortNameToImageId.get( "lock" );
				sb.setSpan( new EmojiImageSpan( activity, info.image_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
			}
			tvAcct.setText( sb );

			Spannable note = who.decoded_note;
			tvNote.setText(note );
			note_invalidator.register( note );
			
			btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + who.statuses_count );
			btnFollowing.setText( activity.getString( R.string.following ) + "\n" + who.following_count );
			btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + who.followers_count );
			
			UserRelation relation = UserRelation.load( access_info.db_id, who.id );
			Styler.setFollowIcon( activity, btnFollow, ivFollowedBy, relation ,who );
			
			if( who.moved != null ){
				showMoved(who, who.moved);
			}
		}
	}
	
	private void showMoved( @NonNull TootAccount who, @NonNull TootAccount who_moved ){
		this.who_moved = who_moved;
		
		llMoved.setVisibility( View.VISIBLE );
		tvMoved.setVisibility( View.VISIBLE );
		
		Spannable caption = Utils.formatSpannable1( activity, R.string.account_moved_to , who.decodeDisplayName( activity ));
		tvMoved.setText( caption);
		moved_caption_invalidator.register( caption );

		ivMoved.getLayoutParams().width = activity.mAvatarIconSize;
		ivMoved.setImageUrl( activity.pref, 16f, access_info.supplyBaseUrl( who_moved.avatar_static ) );

		tvMovedName.setText( who_moved.decoded_display_name );
		moved_name_invalidator.register( who_moved.decoded_display_name );
		
		setAcct( tvMovedAcct, access_info.getFullAcct( who_moved ), who_moved.acct );
		
		UserRelation relation = UserRelation.load( access_info.db_id, who_moved.id );
		Styler.setFollowIcon( activity, btnMoved, ivMovedBy, relation, who_moved );
	}
	
	private void setAcct( @NonNull TextView tv, @NonNull String acctLong, @NonNull String acctShort ){
		AcctColor ac = AcctColor.load( acctLong );
		tv.setText( AcctColor.hasNickname( ac ) ? ac.nickname : activity.mShortAcctLocalUser ? "@" + acctShort : acctLong );
		
		int acct_color = column.acct_color != 0 ? column.acct_color : Styler.getAttributeColor( activity, R.attr.colorTimeSmall );
		tv.setTextColor( AcctColor.hasColorForeground( ac ) ? ac.color_fg : acct_color );
		
		if( AcctColor.hasColorBackground( ac ) ){
			tv.setBackgroundColor( ac.color_bg );
		}else{
			ViewCompat.setBackground( tv, null );
		}
		tv.setPaddingRelative( activity.acct_pad_lr, 0, activity.acct_pad_lr, 0 );
		
	}
	
	
	@Override
	public void onClick( View v ){
		
		switch( v.getId() ){
		
		case R.id.ivBackground:
		case R.id.tvRemoteProfileWarning:
			if( who != null ){
				// 強制的にブラウザで開く
				App1.openCustomTab( activity, who.url );
			}
			break;
		
		case R.id.btnFollowing:
			column.profile_tab = Column.TAB_FOLLOWING;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.btnFollowers:
			column.profile_tab = Column.TAB_FOLLOWERS;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.btnStatusCount:
			column.profile_tab = Column.TAB_STATUS;
			activity.app_state.saveColumnList();
			column.startLoading();
			break;
		
		case R.id.btnMore:
			if( who != null ){
				new DlgContextMenu( activity, column, who, null, null ).show();
			}
			break;
		
		case R.id.btnFollow:
			if( who != null ){
				new DlgContextMenu( activity, column, who, null, null ).show();
			}
			break;
			
		case R.id.btnMoved:
			if( who_moved != null ){
				new DlgContextMenu( activity, column, who_moved, null, null ).show();
			}
			break;
		
		case R.id.llMoved:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, who_moved, null, null ).show();
			}else{
				Action_User.profile( activity, activity.nextPosition( column ), access_info, who_moved );
			}
			break;
		}
	}
	
	@Override public boolean onLongClick( View v ){
		switch( v.getId() ){

		case R.id.btnFollow:
			Action_Follow.followFromAnotherAccount( activity, activity.nextPosition( column),access_info, who );
			return true;

		case R.id.btnMoved:
			Action_Follow.followFromAnotherAccount( activity, activity.nextPosition( column),access_info, who_moved );
			return true;
		}
		
		return false;
	}
	
}