package jp.juggler.subwaytooter;

import android.text.Spannable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.EmojiDecoder;
import jp.juggler.subwaytooter.util.EmojiMap201709;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
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
		
		ivBackground.setOnClickListener( this );
		btnFollowing.setOnClickListener( this );
		btnFollowers.setOnClickListener( this );
		btnStatusCount.setOnClickListener( this );
		btnMore.setOnClickListener( this );
		btnFollow.setOnClickListener( this );
		tvRemoteProfileWarning.setOnClickListener( this );
		
		btnFollow.setOnLongClickListener( this );
		
		tvNote.setMovementMethod( MyLinkMovementMethod.getInstance() );
		
		name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvAcct );
		note_invalidator = new NetworkEmojiInvalidator( activity.handler, tvNote );
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
			
			String s = "@" + access_info.getFullAcct( who );
			if( who.locked ){
				s += " " + EmojiMap201709.sShortNameToImageId.get("lock" ).unified;
			}

			Spannable note = who.decoded_note;
			tvNote.setText(note );
			note_invalidator.register( note );
			
			btnStatusCount.setText( activity.getString( R.string.statuses ) + "\n" + who.statuses_count );
			btnFollowing.setText( activity.getString( R.string.following ) + "\n" + who.following_count );
			btnFollowers.setText( activity.getString( R.string.followers ) + "\n" + who.followers_count );
			
			UserRelation relation = UserRelation.load( access_info.db_id, who.id );
			Styler.setFollowIcon( activity, btnFollow, ivFollowedBy, relation ,who );
		}
	}
	
	@Override
	public void onClick( View v ){
		
		switch( v.getId() ){
		
		case R.id.ivBackground:
		case R.id.tvRemoteProfileWarning:
			if( who != null ){
				// 強制的にブラウザで開く
				activity.openChromeTab( activity.nextPosition( column ), access_info, who.url, true );
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
			
		}
	}
	
	@Override public boolean onLongClick( View v ){
		switch( v.getId() ){
		case R.id.btnFollow:
			activity.openFollowFromAnotherAccount( access_info, who );
			return true;
		}
		
		return false;
	}
	
}