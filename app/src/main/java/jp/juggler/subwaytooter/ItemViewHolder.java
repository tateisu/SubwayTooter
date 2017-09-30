package jp.juggler.subwaytooter;

import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import jp.juggler.subwaytooter.api.entity.NicoEnquete;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootDomainBlock;
import jp.juggler.subwaytooter.api.entity.TootGap;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.api_msp.entity.MSPToot;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.ContentWarning;
import jp.juggler.subwaytooter.table.MediaShown;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.EmojiImageSpan;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
import jp.juggler.subwaytooter.util.NetworkEmojiSpan;
import jp.juggler.subwaytooter.view.MyLinkMovementMethod;
import jp.juggler.subwaytooter.view.MyListView;
import jp.juggler.subwaytooter.view.MyNetworkImageView;
import jp.juggler.subwaytooter.view.MyTextView;
import jp.juggler.subwaytooter.util.Utils;

class ItemViewHolder implements View.OnClickListener, View.OnLongClickListener {
	private static final LogCategory log = new LogCategory( "ItemViewHolder" );
	
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
	private final TextView btnShowMedia;
	
	private final MyNetworkImageView ivMedia1;
	private final MyNetworkImageView ivMedia2;
	private final MyNetworkImageView ivMedia3;
	private final MyNetworkImageView ivMedia4;
	
	private final StatusButtons buttons_for_status;
	
	private final View llSearchTag;
	private final Button btnSearchTag;
	
	private final LinearLayout llExtra;
	
	@Nullable private final TextView tvApplication;
	
	@Nullable private TootStatusLike status;
	@Nullable private TootAccount account_thumbnail;
	@Nullable private TootAccount account_boost;
	@Nullable private TootAccount account_follow;
	@Nullable private String search_tag;
	@Nullable private TootGap gap;
	@Nullable private TootDomainBlock domain_block;
	@Nullable private TootNotification notification;
	
	private final boolean bSimpleList;
	
	private final int content_color_default;
	private int acct_color;
	
	private final NetworkEmojiInvalidator boost_invalidator;
	private final NetworkEmojiInvalidator follow_invalidator;
	private final NetworkEmojiInvalidator name_invalidator;
	private final NetworkEmojiInvalidator content_invalidator;
	private final NetworkEmojiInvalidator spoiler_invalidator;
	private final ArrayList<NetworkEmojiInvalidator> extra_invalidator_list = new ArrayList<>(  );

	ItemViewHolder( ActMain arg_activity, Column column, ItemListAdapter list_adapter, View view, boolean bSimpleList ){
		this.activity = arg_activity;
		this.column = column;
		this.access_info = column.access_info;
		this.list_adapter = list_adapter;
		this.bSimpleList = bSimpleList;
		
		this.tvName = view.findViewById( R.id.tvName );
		this.tvFollowerName = view.findViewById( R.id.tvFollowerName );
		this.tvBoosted = view.findViewById( R.id.tvBoosted );
		
		if( activity.timeline_font != null || activity.timeline_font_bold != null ){
			Utils.scanView( view, new Utils.ScanViewCallback() {
				@Override public void onScanView( View v ){
					try{
						if( v instanceof Button ){
							// ボタンは太字なので触らない
						}else if( v instanceof TextView ){
							switch( v.getId() ){
							default:
								( (TextView) v ).setTypeface( Utils.dor( activity.timeline_font, activity.timeline_font_bold ) );
								break;
							
							case R.id.tvName:
							case R.id.tvFollowerName:
							case R.id.tvBoosted:
								( (TextView) v ).setTypeface( Utils.dor( activity.timeline_font_bold, activity.timeline_font ) );
								break;
							}
						}
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}
			} );
		}else{
			tvName.setTypeface( Typeface.DEFAULT_BOLD );
			tvFollowerName.setTypeface( Typeface.DEFAULT_BOLD );
			tvBoosted.setTypeface( Typeface.DEFAULT_BOLD );
		}
		
		this.llBoosted = view.findViewById( R.id.llBoosted );
		this.ivBoosted = view.findViewById( R.id.ivBoosted );
		this.tvBoostedTime = view.findViewById( R.id.tvBoostedTime );
		this.tvBoostedAcct = view.findViewById( R.id.tvBoostedAcct );
		
		this.llFollow = view.findViewById( R.id.llFollow );
		this.ivFollow = view.findViewById( R.id.ivFollow );
		this.tvFollowerAcct = view.findViewById( R.id.tvFollowerAcct );
		this.btnFollow = view.findViewById( R.id.btnFollow );
		this.ivFollowedBy = view.findViewById( R.id.ivFollowedBy );
		
		this.llStatus = view.findViewById( R.id.llStatus );
		
		this.ivThumbnail = view.findViewById( R.id.ivThumbnail );
		this.tvTime = view.findViewById( R.id.tvTime );
		this.tvAcct = view.findViewById( R.id.tvAcct );
		
		this.llContentWarning = view.findViewById( R.id.llContentWarning );
		this.tvContentWarning = view.findViewById( R.id.tvContentWarning );
		this.btnContentWarning = view.findViewById( R.id.btnContentWarning );
		
		this.llContents = view.findViewById( R.id.llContents );
		this.tvContent = view.findViewById( R.id.tvContent );
		this.tvMentions = view.findViewById( R.id.tvMentions );
		
		this.llExtra = view.findViewById( R.id.llExtra );
		
		this.buttons_for_status = bSimpleList ? null : new StatusButtons( activity, column, view, false );
		
		this.flMedia = view.findViewById( R.id.flMedia );
		this.btnShowMedia = view.findViewById( R.id.btnShowMedia );
		this.ivMedia1 = view.findViewById( R.id.ivMedia1 );
		this.ivMedia2 = view.findViewById( R.id.ivMedia2 );
		this.ivMedia3 = view.findViewById( R.id.ivMedia3 );
		this.ivMedia4 = view.findViewById( R.id.ivMedia4 );
		
		this.llSearchTag = view.findViewById( R.id.llSearchTag );
		this.btnSearchTag = view.findViewById( R.id.btnSearchTag );
		this.tvApplication = view.findViewById( R.id.tvApplication );
		
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
		llBoosted.setOnLongClickListener( this );
		llFollow.setOnClickListener( this );
		llFollow.setOnLongClickListener( this );
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
		
		this.content_color_default = tvContent.getTextColors().getDefaultColor();
		
		if( ! Float.isNaN( activity.timeline_font_size_sp ) ){
			tvBoosted.setTextSize( activity.timeline_font_size_sp );
			tvFollowerName.setTextSize( activity.timeline_font_size_sp );
			tvName.setTextSize( activity.timeline_font_size_sp );
			tvMentions.setTextSize( activity.timeline_font_size_sp );
			tvContentWarning.setTextSize( activity.timeline_font_size_sp );
			tvContent.setTextSize( activity.timeline_font_size_sp );
			btnShowMedia.setTextSize( activity.timeline_font_size_sp );
			if( tvApplication != null ){
				tvApplication.setTextSize( activity.timeline_font_size_sp );
			}
			
		}
		
		if( ! Float.isNaN( activity.acct_font_size_sp ) ){
			tvBoostedAcct.setTextSize( activity.acct_font_size_sp );
			tvBoostedTime.setTextSize( activity.acct_font_size_sp );
			tvFollowerAcct.setTextSize( activity.acct_font_size_sp );
			tvAcct.setTextSize( activity.acct_font_size_sp );
			tvTime.setTextSize( activity.acct_font_size_sp );
		}
		
		ivBoosted.getLayoutParams().width =
			ivFollow.getLayoutParams().width =
				ivThumbnail.getLayoutParams().width =
					ivThumbnail.getLayoutParams().height = activity.mAvatarIconSize;
		
		this.content_invalidator = new NetworkEmojiInvalidator( activity.handler, tvContent );
		this.spoiler_invalidator = new NetworkEmojiInvalidator( activity.handler, tvContentWarning );
		this.boost_invalidator = new NetworkEmojiInvalidator( activity.handler, tvBoosted );
		this.follow_invalidator = new NetworkEmojiInvalidator( activity.handler, tvFollowerName );
		this.name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvName );
	}
	
	void bind( Object item ){
		this.status = null;
		this.account_thumbnail = null;
		this.account_boost = null;
		this.account_follow = null;
		this.search_tag = null;
		this.gap = null;
		this.domain_block = null;
		this.notification = null;
		
		llBoosted.setVisibility( View.GONE );
		llFollow.setVisibility( View.GONE );
		llStatus.setVisibility( View.GONE );
		llSearchTag.setVisibility( View.GONE );
		llExtra.removeAllViews();
		
		if( item == null ) return;
		
		{
			int c = column.content_color != 0 ? column.content_color : content_color_default;
			tvBoosted.setTextColor( c );
			tvFollowerName.setTextColor( c );
			tvName.setTextColor( c );
			tvMentions.setTextColor( c );
			tvContentWarning.setTextColor( c );
			tvContent.setTextColor( c );
			//NSFWは文字色固定 btnShowMedia.setTextColor( c );
			if( tvApplication != null ){
				tvApplication.setTextColor( c );
			}
		}
		
		{
			int c = this.acct_color = column.acct_color != 0 ? column.acct_color : Styler.getAttributeColor( activity, R.attr.colorTimeSmall );
			tvBoostedTime.setTextColor( c );
			tvTime.setTextColor( c );
			//			tvBoostedAcct.setTextColor( c );
			//			tvFollowerAcct.setTextColor( c );
			//			tvAcct.setTextColor( c );
		}
		
		if( item instanceof MSPToot ){
			showStatus( activity, (MSPToot) item );
		}else if( item instanceof String ){
			showSearchTag( (String) item );
		}else if( item instanceof TootAccount ){
			showFollow( (TootAccount) item );
		}else if( item instanceof TootNotification ){
			TootNotification n = this.notification = (TootNotification) item;
			if( TootNotification.TYPE_FAVOURITE.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, access_info.isNicoru( n.account ) ? R.attr.ic_nicoru : R.attr.btn_favourite
					, Utils.formatSpannable1( activity, R.string.display_name_favourited_by, n.account.decoded_display_name )
				);
				if( n.status != null ) showStatus( activity, n.status );
			}else if( TootNotification.TYPE_REBLOG.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, R.attr.btn_boost
					, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, n.account.decoded_display_name )
				);
				if( n.status != null ) showStatus( activity, n.status );
			}else if( TootNotification.TYPE_FOLLOW.equals( n.type ) ){
				showBoost(
					n.account
					, n.time_created_at
					, R.attr.ic_follow_plus
					, Utils.formatSpannable1( activity, R.string.display_name_followed_by, n.account.decoded_display_name )
				);
				//
				showFollow( n.account );
			}else if( TootNotification.TYPE_MENTION.equals( n.type ) ){
				if( ! bSimpleList ){
					showBoost(
						n.account
						, n.time_created_at
						, R.attr.btn_reply
						, Utils.formatSpannable1( activity, R.string.display_name_replied_by, n.account.decoded_display_name )
					);
				}
				if( n.status != null ) showStatus( activity, n.status );
			}
		}else if( item instanceof TootStatus ){
			TootStatus status = (TootStatus) item;
			if( status.reblog != null ){
				if( status.account != null ){
					showBoost(
						status.account
						, status.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1( activity, R.string.display_name_boosted_by, status.account.decoded_display_name )
					);
				}
				showStatus( activity, status.reblog );
			}else{
				showStatus( activity, status );
			}
		}else if( item instanceof TootGap ){
			showGap( (TootGap) item );
		}else if( item instanceof TootDomainBlock ){
			showDomainBlock( (TootDomainBlock) item );
		}
	}
	
	private void showDomainBlock( @NonNull TootDomainBlock domain_block ){
		this.gap = null;
		this.domain_block = domain_block;
		llSearchTag.setVisibility( View.VISIBLE );
		btnSearchTag.setText( domain_block.domain );
	}
	
	private void showSearchTag( @NonNull String tag ){
		this.gap = null;
		search_tag = tag;
		llSearchTag.setVisibility( View.VISIBLE );
		btnSearchTag.setText( "#" + tag );
	}
	
	private void showGap( @NonNull TootGap gap ){
		this.gap = gap;
		search_tag = null;
		llSearchTag.setVisibility( View.VISIBLE );
		btnSearchTag.setText( activity.getString( R.string.read_gap ) );
	}
	
	private void showBoost( @NonNull TootAccount who, long time, int icon_attr_id, Spannable text ){
		account_boost = who;
		llBoosted.setVisibility( View.VISIBLE );
		ivBoosted.setImageResource( Styler.getAttributeResourceId( activity, icon_attr_id ) );
		tvBoostedTime.setText( TootStatus.formatTime( tvBoostedTime.getContext(), time, true ) );
		tvBoosted.setText( text );
		boost_invalidator.register( text );
		setAcct( tvBoostedAcct, access_info.getFullAcct( who ), who.acct );
	}
	
	private void showFollow( @NonNull TootAccount who ){
		account_follow = who;
		llFollow.setVisibility( View.VISIBLE );
		ivFollow.setImageUrl( activity.pref, 16f, access_info.supplyBaseUrl( who.avatar_static ) );
		tvFollowerName.setText( who.decoded_display_name );
		follow_invalidator.register( who.decoded_display_name );
		
		setAcct( tvFollowerAcct, access_info.getFullAcct( who ), who.acct );
		
		UserRelation relation = UserRelation.load( access_info.db_id, who.id );
		Styler.setFollowIcon( activity, btnFollow, ivFollowedBy, relation, who );
	}
	
	private void showStatus( @NonNull ActMain activity, @NonNull TootStatusLike status ){
		this.status = status;
		llStatus.setVisibility( View.VISIBLE );
		
		showStatusTime( activity, status );
		
		TootAccount who = account_thumbnail = status.account;
		
		setAcct( tvAcct, access_info.getFullAcct( who ), who == null ? "?" : who.acct );
		
		if( status.account == null ){
			tvName.setText( "?" );
			name_invalidator.register( null );
			ivThumbnail.setImageUrl( activity.pref, 16f, null, null );
		}else{
			tvName.setText( status.account.decoded_display_name );
			name_invalidator.register( status.account.decoded_display_name );
			ivThumbnail.setImageUrl(
				activity.pref, 16f
				, access_info.supplyBaseUrl( status.account.avatar_static )
				, access_info.supplyBaseUrl( status.account.avatar )
			);
		}
		
		Spannable content = status.decoded_content;
		llExtra.removeAllViews();
		for( NetworkEmojiInvalidator invalidator : extra_invalidator_list ){
			invalidator.register( null );
		}
		extra_invalidator_list.clear();
		
		if( status instanceof TootStatus ){
			TootStatus ts = (TootStatus) status;
			NicoEnquete enquete = ts.enquete;
			if( enquete != null && NicoEnquete.TYPE_ENQUETE.equals( enquete.type ) ){
				if( enquete.question != null ) content = enquete.question;
				int n = 0;
				for( Spannable item : enquete.items ){
					enquete.makeChoiceView( activity, access_info, llExtra, extra_invalidator_list,n++, item );
				}
				enquete.makeTimerView( activity, llExtra );
			}
		}
		
		//			if( status.decoded_tags == null ){
		//				tvTags.setVisibility( View.GONE );
		//			}else{
		//				tvTags.setVisibility( View.VISIBLE );
		//				tvTags.setText( status.decoded_tags );
		//			}
		
		if( status instanceof TootStatus ){
			TootStatus ts = (TootStatus) status;
			if( ts.decoded_mentions == null ){
				tvMentions.setVisibility( View.GONE );
			}else{
				tvMentions.setVisibility( View.VISIBLE );
				tvMentions.setText( ts.decoded_mentions );
			}
		}else{
			tvMentions.setVisibility( View.GONE );
		}
		
		tvContent.setText( content );
		content_invalidator.register( content );
		
		activity.checkAutoCW( status, content );
		TootStatusLike.AutoCW r = status.auto_cw;
		
		tvContent.setMinLines( r != null ? r.originalLineCount : - 1 );
		
		if( ! TextUtils.isEmpty( status.spoiler_text ) ){
			// 元データに含まれるContent Warning を使う
			llContentWarning.setVisibility( View.VISIBLE );
			tvContentWarning.setText( status.decoded_spoiler_text );
			spoiler_invalidator.register( status.decoded_spoiler_text );
			boolean cw_shown = ContentWarning.isShown( status, false );
			showContent( cw_shown );
		}else if( r != null && r.decoded_spoiler_text != null ){
			// 自動CW
			llContentWarning.setVisibility( View.VISIBLE );
			tvContentWarning.setText( r.decoded_spoiler_text );
			spoiler_invalidator.register( r.decoded_spoiler_text );
			boolean cw_shown = ContentWarning.isShown( status, false );
			showContent( cw_shown );
		}else{
			// CWしない
			llContentWarning.setVisibility( View.GONE );
			llContents.setVisibility( View.VISIBLE );
		}
		
		if( ! status.hasMedia() ){
			flMedia.setVisibility( View.GONE );
		}else{
			flMedia.setVisibility( View.VISIBLE );
			
			if( status instanceof TootStatus ){
				TootStatus ts = (TootStatus) status;
				setMedia( ivMedia1, ts, 0 );
				setMedia( ivMedia2, ts, 1 );
				setMedia( ivMedia3, ts, 2 );
				setMedia( ivMedia4, ts, 3 );
			}else if( status instanceof MSPToot ){
				MSPToot ts = (MSPToot) status;
				flMedia.setVisibility( View.VISIBLE );
				setMedia( ivMedia1, ts, 0 );
				setMedia( ivMedia2, ts, 1 );
				setMedia( ivMedia3, ts, 2 );
				setMedia( ivMedia4, ts, 3 );
			}
			
			// hide sensitive media
			@SuppressWarnings("SimplifiableConditionalExpression")
			boolean default_shown =
				column.hide_media_default ? false :
					access_info.dont_hide_nsfw ? true :
						! status.sensitive;
			
			boolean is_shown = MediaShown.isShown( status, default_shown );
			btnShowMedia.setVisibility( ! is_shown ? View.VISIBLE : View.GONE );
		}
		
		if( buttons_for_status != null ){
			buttons_for_status.bind( status, notification );
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
	
	private void showStatusTime( @NonNull ActMain activity, @NonNull TootStatusLike status ){
		SpannableStringBuilder sb = new SpannableStringBuilder();
		
		if( status.hasMedia() && status.sensitive ){
			// if( sb.length() > 0 ) sb.append( ' ' );
			
			int start = sb.length();
			sb.append( "NSFW" );
			int end = sb.length();
			int icon_id = Styler.getAttributeResourceId( activity, R.attr.ic_eye_off );
			sb.setSpan( new EmojiImageSpan( activity, icon_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
		
		if( status instanceof TootStatus ){
			TootStatus ts = (TootStatus) status;
			
			if( ! TootStatus.VISIBILITY_PUBLIC.equals( ts.visibility ) ){
				if( sb.length() > 0 ) sb.append( ' ' );
				
				int start = sb.length();
				sb.append( ts.visibility );
				int end = sb.length();
				int icon_id = Styler.getVisibilityIcon( activity, ts.visibility );
				sb.setSpan( new EmojiImageSpan( activity, icon_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
			}
		}
		
		if( status.pinned ){
			if( sb.length() > 0 ) sb.append( ' ' );
			int start = sb.length();
			sb.append( "pinned" );
			int end = sb.length();
			int icon_id = Styler.getAttributeResourceId( activity, R.attr.ic_pin );
			sb.setSpan( new EmojiImageSpan( activity, icon_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
		
		if( sb.length() > 0 ) sb.append( ' ' );
		sb.append( TootStatus.formatTime( activity, status.time_created_at, column.column_type != Column.TYPE_CONVERSATION ) );
		tvTime.setText( sb );
	}
	
	private void setAcct( @NonNull TextView tv, @NonNull String acctLong, @NonNull String acctShort ){
		AcctColor ac = AcctColor.load( acctLong );
		tv.setText( AcctColor.hasNickname( ac ) ? ac.nickname : activity.mShortAcctLocalUser ? "@" + acctShort : acctLong );
		tv.setTextColor( AcctColor.hasColorForeground( ac ) ? ac.color_fg : this.acct_color );
		
		if( AcctColor.hasColorBackground( ac ) ){
			tv.setBackgroundColor( ac.color_bg );
		}else{
			ViewCompat.setBackground( tv, null );
		}
		tv.setPaddingRelative( activity.acct_pad_lr, 0, activity.acct_pad_lr, 0 );
		
	}
	
	private void showContent( boolean shown ){
		llContents.setVisibility( shown ? View.VISIBLE : View.GONE );
		btnContentWarning.setText( shown ? R.string.hide : R.string.show );
		if( status != null ){
			TootStatusLike.AutoCW r = status.auto_cw;
			tvContent.setMinLines( r != null ? r.originalLineCount : - 1 );
			if( r != null && r.decoded_spoiler_text != null ){
				// 自動CWの場合はContentWarningのテキストを切り替える
				tvContentWarning.setText( shown ? activity.getString( R.string.auto_cw_prefix ) : r.decoded_spoiler_text );
			}
		}
	}
	
	private void setMedia( MyNetworkImageView iv, TootStatus status, int idx ){
		if( idx >= status.media_attachments.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			iv.setScaleType( activity.dont_crop_media_thumbnail ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP );
			
			TootAttachment ta = status.media_attachments.get( idx );
			
			if( TextUtils.isEmpty( ta.type ) ){
				iv.setMediaType( 0 );
			}else{
				switch( ta.type ){
				default:
				case TootAttachment.TYPE_IMAGE:
					iv.setMediaType( 0 );
					break;
				
				case TootAttachment.TYPE_VIDEO:
					iv.setMediaType( R.drawable.media_type_video );
					break;
				case TootAttachment.TYPE_GIFV:
					iv.setMediaType( R.drawable.media_type_gifv );
					break;
				case TootAttachment.TYPE_UNKNOWN:
					iv.setMediaType( R.drawable.media_type_unknown );
					break;
				}
			}
			
			String url = ta.preview_url;
			if( TextUtils.isEmpty( url ) ){
				url = ta.remote_url;
				if( TextUtils.isEmpty( url ) ){
					url = ta.url;
				}
			}
			// 正方形じゃないせいか、うまく動かない activity.density * 4f );
			iv.setImageUrl( activity.pref, 0f, access_info.supplyBaseUrl( url ), access_info.supplyBaseUrl( url ) );
		}
	}
	
	private void setMedia( MyNetworkImageView iv, MSPToot msp_toot, int idx ){
		if( idx >= msp_toot.media_attachments.size() ){
			iv.setVisibility( View.GONE );
		}else{
			iv.setVisibility( View.VISIBLE );
			iv.setScaleType( activity.dont_crop_media_thumbnail ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP );
			
			String url = msp_toot.media_attachments.get( idx );
			iv.setMediaType( 0 );
			// 正方形じゃないせいか、うまく動かない activity.density * 4f );
			iv.setImageUrl( activity.pref, 0f, access_info.supplyBaseUrl( url ) );
		}
	}
	
	@Override public void onClick( View v ){
		
		int pos = activity.nextPosition( column );
		
		switch( v.getId() ){
		case R.id.btnHideMedia:
			if( status != null ){
				MediaShown.save( status, false );
				btnShowMedia.setVisibility( View.VISIBLE );
			}
			break;
		case R.id.btnShowMedia:
			if( status != null ){
				MediaShown.save( status, true );
				btnShowMedia.setVisibility( View.GONE );
			}
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
		case R.id.btnContentWarning:
			if( status != null ){
				boolean new_shown = ( llContents.getVisibility() == View.GONE );
				ContentWarning.save( status, new_shown );
				list_adapter.notifyDataSetChanged();
			}
			break;
		
		case R.id.ivThumbnail:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, account_thumbnail, null, notification ).show();
			}else{
				activity.openProfile( pos, access_info, account_thumbnail );
			}
			break;
		
		case R.id.llBoosted:
			if( account_boost != null ){
				if( access_info.isPseudo() ){
					new DlgContextMenu( activity, column, account_boost, null, notification ).show();
				}else{
					activity.openProfile( pos, access_info, account_boost );
				}
			}
			break;
		case R.id.llFollow:
			if( access_info.isPseudo() ){
				new DlgContextMenu( activity, column, account_follow, null, notification ).show();
			}else{
				activity.openProfile( pos, access_info, account_follow );
			}
			break;
		case R.id.btnFollow:
			new DlgContextMenu( activity, column, account_follow, null, notification ).show();
			break;
		
		case R.id.btnSearchTag:
			if( search_tag != null ){
				activity.openHashTag( activity.nextPosition( column ), access_info, search_tag );
			}else if( gap != null ){
				column.startGap( gap );
			}else if( domain_block != null ){
				final String domain = domain_block.domain;
				new AlertDialog.Builder( activity )
					.setMessage( activity.getString( R.string.confirm_unblock_domain, domain ) )
					.setNegativeButton( R.string.cancel, null )
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialog, int which ){
							activity.callDomainBlock( access_info, domain, false, null );
						}
					} )
					.show();
			}
			break;
			
		}
	}
	
	@Override public boolean onLongClick( View v ){
		switch( v.getId() ){
		
		case R.id.ivThumbnail:
			new DlgContextMenu( activity, column, account_thumbnail, null, notification ).show();
			return true;
		
		case R.id.btnFollow:
			activity.openFollowFromAnotherAccount( access_info, account_follow );
			return true;
		
		case R.id.llBoosted:
			if( account_boost != null ){
				new DlgContextMenu( activity, column, account_boost, null, notification ).show();
			}
			return true;
		
		case R.id.llFollow:
			if( account_follow != null ){
				new DlgContextMenu( activity, column, account_follow, null, notification ).show();
			}
			return true;
			
		}
		
		return false;
	}
	
	private void clickMedia( int i ){
		try{
			if( status instanceof MSPToot ){
				activity.openStatusOtherInstance( activity.nextPosition( column ), access_info, status );
			}else if( status instanceof TootStatus ){
				TootStatus ts = (TootStatus) status;
				
				TootAttachment a = ts.media_attachments.get( i );
				
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
				int pos = activity.nextPosition( column );
				activity.openChromeTab( pos, access_info, sv, false );
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	// 簡略ビューの時だけ呼ばれる
	// StatusButtonsPopupを表示する
	void onItemClick( MyListView listView, View anchor ){
		activity.closeListItemPopup();
		if( status != null ){
			activity.list_item_popup = new StatusButtonsPopup( activity, column, bSimpleList );
			activity.list_item_popup.show( listView, anchor, status, notification );
		}
	}
	
}

