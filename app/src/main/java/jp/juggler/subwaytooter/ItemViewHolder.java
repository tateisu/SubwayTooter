package jp.juggler.subwaytooter;

import android.graphics.Typeface;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootGap;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.view.MyLinkMovementMethod;
import jp.juggler.subwaytooter.view.MyListView;
import jp.juggler.subwaytooter.view.MyNetworkImageView;
import jp.juggler.subwaytooter.view.MyTextView;
import jp.juggler.subwaytooter.util.Utils;

class ItemViewHolder implements View.OnClickListener, View.OnLongClickListener {
	
	final ActMain activity;
	final Column column;
	private final ItemListAdapter list_adapter;
	private final SavedAccount access_info;
	
	private final View llBoosted;
	private final ImageView ivBoosted;
	private final TextView tvBoosted;
	private final TextView tvBoostedAcct;
	private final TextView tvBoostedTime;
	
	private final View llFollow;
	private final MyNetworkImageView ivFollow;
	private final TextView tvFollowerName;
	private final TextView tvFollowerAcct;
	private final ImageButton btnFollow;
	private final ImageView ivFollowedBy;
	
	private final View llStatus;
	private final MyNetworkImageView ivThumbnail;
	private final TextView tvName;
	private final TextView tvTime;
	private final TextView tvAcct;
	
	private final View llContentWarning;
	private final MyTextView tvContentWarning;
	private final Button btnContentWarning;
	
	private final View llContents;
	private final MyTextView tvMentions;
	private final MyTextView tvContent;
	
	private final View flMedia;
	private final View btnShowMedia;
	
	private final MyNetworkImageView ivMedia1;
	private final MyNetworkImageView ivMedia2;
	private final MyNetworkImageView ivMedia3;
	private final MyNetworkImageView ivMedia4;
	
	private final StatusButtons buttons_for_status;
	
	private final View llSearchTag;
	private final Button btnSearchTag;
	
	private final TextView tvApplication;
	
	private TootStatus status;
	private TootAccount account_thumbnail;
	private TootAccount account_boost;
	private TootAccount account_follow;
	private String search_tag;
	private TootGap gap;
	private int position;
	
	private final boolean bSimpleList;
	
	ItemViewHolder( ActMain arg_activity, Column column, ItemListAdapter list_adapter, View view ,boolean bSimpleList ){
		this.activity = arg_activity;
		this.column = column;
		this.access_info = column.access_info;
		this.list_adapter = list_adapter;
		this.bSimpleList = bSimpleList;
		
		this.tvName = (TextView) view.findViewById( R.id.tvName );
		this.tvFollowerName = (TextView) view.findViewById( R.id.tvFollowerName );
		this.tvBoosted = (TextView) view.findViewById( R.id.tvBoosted );
		
		
		if( activity.timeline_font != null ){
			Utils.scanView( view, new Utils.ScanViewCallback() {
				@Override public void onScanView( View v ){
					try{
						if( v instanceof Button ){
							// ボタンは太字なので触らない
						}else if( v instanceof TextView ){
							( (TextView) v ).setTypeface( activity.timeline_font );
						}
					}catch(Throwable ex){
						ex.printStackTrace();
					}
				}
			} );
		}else{
			tvName.setTypeface( Typeface.DEFAULT_BOLD );
			tvFollowerName.setTypeface( Typeface.DEFAULT_BOLD );
			tvBoosted.setTypeface( Typeface.DEFAULT_BOLD );
		}
		
		this.llBoosted = view.findViewById( R.id.llBoosted );
		this.ivBoosted = (ImageView) view.findViewById( R.id.ivBoosted );
		this.tvBoostedTime = (TextView) view.findViewById( R.id.tvBoostedTime );
		this.tvBoostedAcct = (TextView) view.findViewById( R.id.tvBoostedAcct );
		
		this.llFollow = view.findViewById( R.id.llFollow );
		this.ivFollow = (MyNetworkImageView) view.findViewById( R.id.ivFollow );
		this.tvFollowerAcct = (TextView) view.findViewById( R.id.tvFollowerAcct );
		this.btnFollow = (ImageButton) view.findViewById( R.id.btnFollow );
		this.ivFollowedBy = (ImageView) view.findViewById( R.id.ivFollowedBy );
		
		this.llStatus = view.findViewById( R.id.llStatus );
		
		this.ivThumbnail = (MyNetworkImageView) view.findViewById( R.id.ivThumbnail );
		this.tvTime = (TextView) view.findViewById( R.id.tvTime );
		this.tvAcct = (TextView) view.findViewById( R.id.tvAcct );
		
		this.llContentWarning = view.findViewById( R.id.llContentWarning );
		this.tvContentWarning = (MyTextView) view.findViewById( R.id.tvContentWarning );
		this.btnContentWarning = (Button) view.findViewById( R.id.btnContentWarning );
		
		this.llContents = view.findViewById( R.id.llContents );
		this.tvContent = (MyTextView) view.findViewById( R.id.tvContent );
		this.tvMentions = (MyTextView) view.findViewById( R.id.tvMentions );
		
		this.buttons_for_status = bSimpleList ? null : new StatusButtons( activity, column, view , false );
		
		this.flMedia = view.findViewById( R.id.flMedia );
		this.btnShowMedia = view.findViewById( R.id.btnShowMedia );
		this.ivMedia1 = (MyNetworkImageView) view.findViewById( R.id.ivMedia1 );
		this.ivMedia2 = (MyNetworkImageView) view.findViewById( R.id.ivMedia2 );
		this.ivMedia3 = (MyNetworkImageView) view.findViewById( R.id.ivMedia3 );
		this.ivMedia4 = (MyNetworkImageView) view.findViewById( R.id.ivMedia4 );
		
		this.llSearchTag = view.findViewById( R.id.llSearchTag );
		this.btnSearchTag = (Button) view.findViewById( R.id.btnSearchTag );
		this.tvApplication = (TextView) view.findViewById( R.id.tvApplication );
		
		btnSearchTag.setOnClickListener( this );
		btnContentWarning.setOnClickListener( this );
		btnShowMedia.setOnClickListener( this );
		ivMedia1.setOnClickListener( this );
		ivMedia2.setOnClickListener( this );
		ivMedia3.setOnClickListener( this );
		ivMedia4.setOnClickListener( this );
		btnFollow.setOnClickListener( this );
		btnFollow.setOnLongClickListener( this );
		
		ivThumbnail.setOnClickListener( this );
		// ここを個別タップにすると邪魔すぎる tvName.setOnClickListener( this );
		llBoosted.setOnClickListener( this );
		llFollow.setOnClickListener( this );
		btnFollow.setOnClickListener( this );
		
		// ロングタップ
		ivThumbnail.setOnLongClickListener( this );
		
		//
		tvContent.setMovementMethod( MyLinkMovementMethod.getInstance() );
		tvMentions.setMovementMethod( MyLinkMovementMethod.getInstance() );
		tvContentWarning.setMovementMethod( MyLinkMovementMethod.getInstance() );
		
		View v;
		//
		v = view.findViewById( R.id.btnHideMedia );
		v.setOnClickListener( this );
		
		ViewGroup.LayoutParams lp = flMedia.getLayoutParams();
		lp.height = activity.app_state.media_thumb_height;
	}
	
	void bind( Object item, int position ){
		this.position = position;
		this.status = null;
		this.account_thumbnail = null;
		this.account_boost = null;
		this.account_follow = null;
		this.search_tag = null;
		this.gap = null;
		
		llBoosted.setVisibility( View.GONE );
		llFollow.setVisibility( View.GONE );
		llStatus.setVisibility( View.GONE );
		llSearchTag.setVisibility( View.GONE );
		
		if( item == null ) return;
		
		if( item instanceof String ){
			showSearchTag( (String) item );
		}else if( item instanceof TootAccount ){
			showFollow( (TootAccount) item );
		}else if( item instanceof TootNotification ){
			TootNotification n = (TootNotification) item;
			if( TootNotification.TYPE_FAVOURITE.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, R.attr.btn_favourite
					, Utils.formatSpannable1( activity, R.string.display_name_favourited_by, n.account.display_name )
				);
				if( n.status != null ) showStatus( activity, n.status );
			}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, R.attr.btn_boost
					, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, n.account.display_name )
				);
				if( n.status != null ) showStatus( activity, n.status );
			}else if( TootNotification.TYPE_FOLLOW.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, R.attr.ic_follow_plus
					, Utils.formatSpannable1( activity, R.string.display_name_followed_by, n.account.display_name )
				);
				//
				showFollow( n.account );
			}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
				if( ! bSimpleList ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_reply
						, Utils.formatSpannable1( activity, R.string.display_name_replied_by, n.account.display_name )
					);
				}
				if( n.status != null ) showStatus( activity, n.status );
			}
		}else if( item instanceof TootStatus ){
			TootStatus status = (TootStatus) item;
			if( status.reblog != null ){
				showBoost(
					status.account
					, status.time_created_at
					, R.attr.btn_boost
					, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, status.account.display_name )
				);
				showStatus( activity, status.reblog );
			}else{
				showStatus( activity, status );
			}
		}else if( item instanceof TootGap ){
			showGap( (TootGap) item );
		}
	}
	
	private void showSearchTag( String tag ){
		this.gap = null;
		search_tag = tag;
		llSearchTag.setVisibility( View.VISIBLE );
		btnSearchTag.setText( "#" + tag );
	}
	
	private void showGap( TootGap gap ){
		this.gap = gap;
		search_tag = null;
		llSearchTag.setVisibility( View.VISIBLE );
		btnSearchTag.setText( activity.getString( R.string.read_gap ) );
	}
	
	private void showBoost( TootAccount who, long time, int icon_attr_id, CharSequence text ){
		account_boost = who;
		llBoosted.setVisibility( View.VISIBLE );
		ivBoosted.setImageResource( Styler.getAttributeResourceId( activity, icon_attr_id ) );
		tvBoostedTime.setText( TootStatus.formatTime( time ) );
		tvBoosted.setText( text );
		setAcct( tvBoostedAcct, access_info.getFullAcct( who ), R.attr.colorAcctSmall );
	}
	
	private void showFollow( TootAccount who ){
		account_follow = who;
		llFollow.setVisibility( View.VISIBLE );
		ivFollow.setCornerRadius( activity.pref, 16f );
		ivFollow.setImageUrl( access_info.supplyBaseUrl( who.avatar_static ), App1.getImageLoader() );
		tvFollowerName.setText( who.display_name );
		setAcct( tvFollowerAcct, access_info.getFullAcct( who ), R.attr.colorAcctSmall );
		
		UserRelation relation = UserRelation.load( access_info.db_id, who.id );
		Styler.setFollowIcon( activity, btnFollow, ivFollowedBy, relation, column.column_type );
	}
	
	private void showStatus( ActMain activity, TootStatus status ){
		this.status = status;
		account_thumbnail = status.account;
		llStatus.setVisibility( View.VISIBLE );
		
		setAcct( tvAcct, access_info.getFullAcct( status.account ), R.attr.colorAcctSmall );
		tvTime.setText( TootStatus.formatTime( status.time_created_at ) );
		
		tvName.setText( status.account.display_name );
		ivThumbnail.setCornerRadius( activity.pref, 16f );
		ivThumbnail.setImageUrl( access_info.supplyBaseUrl( status.account.avatar_static ), App1.getImageLoader() );
		tvContent.setText( status.decoded_content );
		
		//			if( status.decoded_tags == null ){
		//				tvTags.setVisibility( View.GONE );
		//			}else{
		//				tvTags.setVisibility( View.VISIBLE );
		//				tvTags.setText( status.decoded_tags );
		//			}
		
		if( status.decoded_mentions == null ){
			tvMentions.setVisibility( View.GONE );
		}else{
			tvMentions.setVisibility( View.VISIBLE );
			tvMentions.setText( status.decoded_mentions );
		}
		
		// Content warning
		if( TextUtils.isEmpty( status.spoiler_text ) ){
			llContentWarning.setVisibility( View.GONE );
			llContents.setVisibility( View.VISIBLE );
		}else{
			llContentWarning.setVisibility( View.VISIBLE );
			tvContentWarning.setText( status.decoded_spoiler_text );
			boolean cw_shown = ContentWarning.isShown( access_info.host, status.id, false );
			showContent( cw_shown );
		}
		
		if( status.media_attachments == null || status.media_attachments.isEmpty() ){
			flMedia.setVisibility( View.GONE );
		}else{
			flMedia.setVisibility( View.VISIBLE );
			setMedia( ivMedia1, status, 0 );
			setMedia( ivMedia2, status, 1 );
			setMedia( ivMedia3, status, 2 );
			setMedia( ivMedia4, status, 3 );
			
			@SuppressWarnings("SimplifiableConditionalExpression")
			boolean default_shown =
				column.hide_media_default ? false :
					access_info.dont_hide_nsfw ? true :
						! status.sensitive;
			
			// hide sensitive media
			boolean is_shown = MediaShown.isShown( access_info.host, status.id, default_shown );
			btnShowMedia.setVisibility( ! is_shown ? View.VISIBLE : View.GONE );
		}
		
		if( buttons_for_status != null ){
			buttons_for_status.bind( status );
		}
		
		if( tvApplication != null ){
			switch( column.column_type ){
			default:
				tvApplication.setVisibility( View.GONE );
				break;
			
			case Column.TYPE_CONVERSATION:
				if( status.application == null ){
					tvApplication.setVisibility( View.GONE );
				}else{
					tvApplication.setVisibility( View.VISIBLE );
					tvApplication.setText( activity.getString( R.string.application_is, status.application.name ) );
				}
				break;
				
			}
		}
	}
	
	private void setAcct( TextView tv, String acct, int color_attr_id ){
		AcctColor ac = AcctColor.load( acct );
		tv.setText( AcctColor.hasNickname( ac ) ? ac.nickname : acct );
		tv.setTextColor( AcctColor.hasColorForeground( ac ) ? ac.color_fg : Styler.getAttributeColor( activity, color_attr_id ) );
		
		if( AcctColor.hasColorBackground( ac ) ){
			tv.setBackgroundColor( ac.color_bg );
		}else{
			ViewCompat.setBackground( tv, null );
		}
		tv.setPaddingRelative( activity.acct_pad_lr, 0, activity.acct_pad_lr, 0 );
		
	}
	
	private void showContent( boolean shown ){
		btnContentWarning.setText( shown ? R.string.hide : R.string.show );
		llContents.setVisibility( shown ? View.VISIBLE : View.GONE );
		
	}
	
	private void setMedia( MyNetworkImageView iv, TootStatus status, int idx ){
		if( idx >= status.media_attachments.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			TootAttachment ta = status.media_attachments.get( idx );
			String url = ta.preview_url;
			if( TextUtils.isEmpty( url ) ) url = ta.remote_url;
			iv.setCornerRadius( activity.pref, 16f ); // 正方形じゃないせいか、うまく動かない activity.density * 4f );
			iv.setImageUrl( access_info.supplyBaseUrl( url ), App1.getImageLoader() );
		}
	}
	
	@Override public void onClick( View v ){
		
		int pos = activity.nextPosition( column ) ;
		
		switch( v.getId() ){
		case R.id.btnHideMedia:
			MediaShown.save( access_info.host, status.id, false );
			btnShowMedia.setVisibility( View.VISIBLE );
			break;
		case R.id.btnShowMedia:
			MediaShown.save( access_info.host, status.id, true );
			btnShowMedia.setVisibility( View.GONE );
			break;
		case R.id.ivMedia1:
			clickMedia( 0 );
			break;
		case R.id.ivMedia2:
			clickMedia( 1 );
			break;
		case R.id.ivMedia3:
			clickMedia( 2 );
			break;
		case R.id.ivMedia4:
			clickMedia( 3 );
			break;
		case R.id.btnContentWarning:{
			boolean new_shown = ( llContents.getVisibility() == View.GONE );
			ContentWarning.save( access_info.host, status.id, new_shown );
			list_adapter.notifyDataSetChanged();
			break;
		}
		
		case R.id.ivThumbnail:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, account_thumbnail, null ).show();
			}else{
				activity.performOpenUser( pos,access_info, account_thumbnail );
			}
			break;
		
		case R.id.llBoosted:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, account_boost, null ).show();
			}else{
				activity.performOpenUser( pos,access_info, account_boost );
			}
			break;
		case R.id.llFollow:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, account_follow, null ).show();
			}else{
				activity.performOpenUser( pos,access_info, account_follow );
			}
			break;
		case R.id.btnFollow:
			new DlgContextMenu( activity, column, account_follow, null ).show();
			break;
		
		case R.id.btnSearchTag:
			if( search_tag != null ){
				activity.openHashTag( activity.nextPosition( column ),access_info, search_tag );
			}else if( gap != null ){
				column.startGap( gap, position );
			}
			break;
		}
	}
	
	@Override public boolean onLongClick( View v ){
		switch( v.getId() ){
		
		case R.id.ivThumbnail:
			new DlgContextMenu( activity, column, account_thumbnail, null ).show();
			return true;
		
		case R.id.btnFollow:
			activity.openFollowFromAnotherAccount( access_info, account_follow );
			return true;
			
		}
		
		return false;
	}
	
	private void clickMedia( int i ){
		try{
			TootAttachment a = status.media_attachments.get( i );
			
			String sv;
			if( Pref.pref( activity ).getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) ){
				sv = a.url;
				if( TextUtils.isEmpty( sv ) ){
					sv = a.remote_url;
				}
			}else{
				sv = a.remote_url;
				if( TextUtils.isEmpty( sv ) ){
					sv = a.url;
				}
			}
			int pos =  activity.nextPosition( column ) ;
			activity.openChromeTab(pos, access_info, sv, false );
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
	
	// 簡略ビューの時だけ呼ばれる
	// StatusButtonsPopupを表示する
	void onItemClick( MyListView listView, View anchor ){
		if( status != null ){
			activity.closeListItemPopup();
			activity.list_item_popup = new StatusButtonsPopup( activity, column ,bSimpleList);
			activity.list_item_popup.show( listView, anchor, status );
		}
	}
	
}
	

