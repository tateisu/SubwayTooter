package jp.juggler.subwaytooter

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.ArrayList

import jp.juggler.subwaytooter.action.Action_Follow
import jp.juggler.subwaytooter.action.Action_HashTag
import jp.juggler.subwaytooter.action.Action_Instance
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.action.Action_User
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.ContentWarning
import jp.juggler.subwaytooter.table.MediaShown
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.util.HTMLDecoder
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.view.*
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

internal class ItemViewHolder(
	val activity : ActMain,
	val column : Column,
	private val list_adapter : ItemListAdapter,
	view : View,
	private val bSimpleList : Boolean
) : View.OnClickListener, View.OnLongClickListener {
	
	
	companion object {
		private val log = LogCategory("ItemViewHolder")
	}
	
	private val access_info : SavedAccount
	
	private val llBoosted : View
	private val ivBoosted : ImageView
	private val tvBoosted : TextView
	private val tvBoostedAcct : TextView
	private val tvBoostedTime : TextView
	
	private val llFollow : View
	private val ivFollow : MyNetworkImageView
	private val tvFollowerName : TextView
	private val tvFollowerAcct : TextView
	private val btnFollow : ImageButton
	private val ivFollowedBy : ImageView
	
	private val llStatus : View
	private val ivThumbnail : MyNetworkImageView
	private val tvName : TextView
	private val tvTime : TextView
	private val tvAcct : TextView
	
	private val llContentWarning : View
	private val tvContentWarning : MyTextView
	private val btnContentWarning : Button
	
	private val llContents : View
	private val tvMentions : MyTextView
	private val tvContent : MyTextView
	
	private val flMedia : View
	private val btnShowMedia : TextView
	
	private val ivMedia1 : MyNetworkImageView
	private val ivMedia2 : MyNetworkImageView
	private val ivMedia3 : MyNetworkImageView
	private val ivMedia4 : MyNetworkImageView
	
	private val buttons_for_status : StatusButtons?
	
	private val llSearchTag : View
	private val btnSearchTag : Button
	
	private val llList : View
	private val btnListTL : Button
	
	private val llExtra : LinearLayout
	
	private val tvApplication : TextView?
	
	private var item : Any? = null
	
	private var status__showing : TootStatus? = null
	private var status_account : TootAccount? = null
	private var boost_account : TootAccount? = null
	private var follow_account : TootAccount? = null
	
	private val content_color_default : Int
	private var acct_color : Int = 0
	
	private val boost_invalidator : NetworkEmojiInvalidator
	private val follow_invalidator : NetworkEmojiInvalidator
	private val name_invalidator : NetworkEmojiInvalidator
	private val content_invalidator : NetworkEmojiInvalidator
	private val spoiler_invalidator : NetworkEmojiInvalidator
	private val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()
	
	init {
		this.access_info = column.access_info
		
		this.tvName = view.findViewById(R.id.tvName)
		this.tvFollowerName = view.findViewById(R.id.tvFollowerName)
		this.tvBoosted = view.findViewById(R.id.tvBoosted)
		
		if(activity.timeline_font != null || activity.timeline_font_bold != null) {
			Utils.scanView(view) { v ->
				try {
					if(v is Button) {
						// ボタンは太字なので触らない
					} else if(v is TextView) {
						val typeface = when(v.getId()) {
							R.id.tvName,
							R.id.tvFollowerName,
							R.id.tvBoosted -> activity.timeline_font_bold ?: activity.timeline_font
							else -> activity.timeline_font ?: activity.timeline_font_bold
						}
						if(typeface != null) v.typeface = typeface
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		} else {
			tvName.typeface = Typeface.DEFAULT_BOLD
			tvFollowerName.typeface = Typeface.DEFAULT_BOLD
			tvBoosted.typeface = Typeface.DEFAULT_BOLD
		}
		
		this.llBoosted = view.findViewById(R.id.llBoosted)
		this.ivBoosted = view.findViewById(R.id.ivBoosted)
		this.tvBoostedTime = view.findViewById(R.id.tvBoostedTime)
		this.tvBoostedAcct = view.findViewById(R.id.tvBoostedAcct)
		
		this.llFollow = view.findViewById(R.id.llFollow)
		this.ivFollow = view.findViewById(R.id.ivFollow)
		this.tvFollowerAcct = view.findViewById(R.id.tvFollowerAcct)
		this.btnFollow = view.findViewById(R.id.btnFollow)
		this.ivFollowedBy = view.findViewById(R.id.ivFollowedBy)
		
		this.llStatus = view.findViewById(R.id.llStatus)
		
		this.ivThumbnail = view.findViewById(R.id.ivThumbnail)
		this.tvTime = view.findViewById(R.id.tvTime)
		this.tvAcct = view.findViewById(R.id.tvAcct)
		
		this.llContentWarning = view.findViewById(R.id.llContentWarning)
		this.tvContentWarning = view.findViewById(R.id.tvContentWarning)
		this.btnContentWarning = view.findViewById(R.id.btnContentWarning)
		
		this.llContents = view.findViewById(R.id.llContents)
		this.tvContent = view.findViewById(R.id.tvContent)
		this.tvMentions = view.findViewById(R.id.tvMentions)
		
		this.llExtra = view.findViewById(R.id.llExtra)
		
		this.buttons_for_status = if(bSimpleList) null else StatusButtons(activity, column, view, false)
		
		this.flMedia = view.findViewById(R.id.flMedia)
		this.btnShowMedia = view.findViewById(R.id.btnShowMedia)
		this.ivMedia1 = view.findViewById(R.id.ivMedia1)
		this.ivMedia2 = view.findViewById(R.id.ivMedia2)
		this.ivMedia3 = view.findViewById(R.id.ivMedia3)
		this.ivMedia4 = view.findViewById(R.id.ivMedia4)
		
		this.llSearchTag = view.findViewById(R.id.llSearchTag)
		this.btnSearchTag = view.findViewById(R.id.btnSearchTag)
		this.tvApplication = view.findViewById(R.id.tvApplication)
		
		this.llList = view.findViewById(R.id.llList)
		this.btnListTL = view.findViewById(R.id.btnListTL)
		val btnListMore = view.findViewById<View>(R.id.btnListMore)
		
		btnListTL.setOnClickListener(this)
		btnListMore.setOnClickListener(this)
		
		btnSearchTag.setOnClickListener(this)
		btnSearchTag.setOnLongClickListener(this)
		btnContentWarning.setOnClickListener(this)
		btnShowMedia.setOnClickListener(this)
		ivMedia1.setOnClickListener(this)
		ivMedia2.setOnClickListener(this)
		ivMedia3.setOnClickListener(this)
		ivMedia4.setOnClickListener(this)
		btnFollow.setOnClickListener(this)
		btnFollow.setOnLongClickListener(this)
		
		ivThumbnail.setOnClickListener(this)
		// ここを個別タップにすると邪魔すぎる tvName.setOnClickListener( this );
		llBoosted.setOnClickListener(this)
		llBoosted.setOnLongClickListener(this)
		llFollow.setOnClickListener(this)
		llFollow.setOnLongClickListener(this)
		btnFollow.setOnClickListener(this)
		
		// ロングタップ
		ivThumbnail.setOnLongClickListener(this)
		
		//
		tvContent.movementMethod = MyLinkMovementMethod
		tvMentions.movementMethod = MyLinkMovementMethod
		tvContentWarning.movementMethod = MyLinkMovementMethod
		
		val v : View
		//
		v = view.findViewById(R.id.btnHideMedia)
		v.setOnClickListener(this)
		
		val lp = flMedia.layoutParams
		lp.height = activity.app_state.media_thumb_height
		
		this.content_color_default = tvContent.textColors.defaultColor
		
		if(! activity.timeline_font_size_sp.isNaN()) {
			tvBoosted.textSize = activity.timeline_font_size_sp
			tvFollowerName.textSize = activity.timeline_font_size_sp
			tvName.textSize = activity.timeline_font_size_sp
			tvMentions.textSize = activity.timeline_font_size_sp
			tvContentWarning.textSize = activity.timeline_font_size_sp
			tvContent.textSize = activity.timeline_font_size_sp
			btnShowMedia.textSize = activity.timeline_font_size_sp
			if(tvApplication != null) {
				tvApplication.textSize = activity.timeline_font_size_sp
			}
			btnListTL.textSize = activity.timeline_font_size_sp
		}
		
		if(! activity.acct_font_size_sp.isNaN()) {
			tvBoostedAcct.textSize = activity.acct_font_size_sp
			tvBoostedTime.textSize = activity.acct_font_size_sp
			tvFollowerAcct.textSize = activity.acct_font_size_sp
			tvAcct.textSize = activity.acct_font_size_sp
			tvTime.textSize = activity.acct_font_size_sp
		}
		
		ivThumbnail.layoutParams.height = activity.avatarIconSize
		ivThumbnail.layoutParams.width = ivThumbnail.layoutParams.height
		ivFollow.layoutParams.width = ivThumbnail.layoutParams.width
		ivBoosted.layoutParams.width = ivFollow.layoutParams.width
		
		this.content_invalidator = NetworkEmojiInvalidator(activity.handler, tvContent)
		this.spoiler_invalidator = NetworkEmojiInvalidator(activity.handler, tvContentWarning)
		this.boost_invalidator = NetworkEmojiInvalidator(activity.handler, tvBoosted)
		this.follow_invalidator = NetworkEmojiInvalidator(activity.handler, tvFollowerName)
		this.name_invalidator = NetworkEmojiInvalidator(activity.handler, tvName)
	}
	
	fun bind(item : Any?) {
		this.item = null
		this.status__showing = null
		this.status_account = null
		this.boost_account = null
		this.follow_account = null
		
		llBoosted.visibility = View.GONE
		llFollow.visibility = View.GONE
		llStatus.visibility = View.GONE
		llSearchTag.visibility = View.GONE
		llList.visibility = View.GONE
		llExtra.removeAllViews()
		
		if(item == null) return
		
		var c: Int
		
		c = if(column.content_color != 0) column.content_color else content_color_default
		tvBoosted.setTextColor(c)
		tvFollowerName.setTextColor(c)
		tvName.setTextColor(c)
		tvMentions.setTextColor(c)
		tvContentWarning.setTextColor(c)
		tvContent.setTextColor(c)
		//NSFWは文字色固定 btnShowMedia.setTextColor( c );
		tvApplication?.setTextColor(c)
		
			c =if(column.acct_color != 0) column.acct_color else Styler.getAttributeColor(activity, R.attr.colorTimeSmall)
			this.acct_color = c
			tvBoostedTime.setTextColor(c)
			tvTime.setTextColor(c)
			//			tvBoostedAcct.setTextColor( c );
			//			tvFollowerAcct.setTextColor( c );
			//			tvAcct.setTextColor( c );
		
		this.item = item
		when(item) {
			is String -> showSearchTag(item)
			is TootAccount -> showAccount(item)
			is TootNotification -> showNotification(item)
			is TootGap -> showGap()
			is TootDomainBlock -> showDomainBlock(item)
			is TootList -> showList(item)
			
			is TootStatus -> {
				val reblog = item.reblog
				if(reblog != null) {
					showBoost(
						item.account
						, item.time_created_at
						, R.attr.btn_boost
						, Utils.formatSpannable1(activity, R.string.display_name_boosted_by, item.account.decoded_display_name)
					)
					showStatus(activity, reblog)
				} else {
					showStatus(activity, item)
				}
			}
			
			else -> {
			}
		}
	}
	
	private fun showNotification(n : TootNotification) {
		val n_status = n.status
		val n_account = n.account
		when(n.type) {
			TootNotification.TYPE_FAVOURITE -> {
				if(n_account != null) showBoost(
					n_account
					, n.time_created_at
					, if(access_info.isNicoru(n_account)) R.attr.ic_nicoru else R.attr.btn_favourite
					, Utils.formatSpannable1(activity, R.string.display_name_favourited_by, n_account.decoded_display_name)
				)
				if(n_status != null) showStatus(activity, n_status)
			}
			
			TootNotification.TYPE_REBLOG -> {
				if(n_account != null) showBoost(
					n_account
					, n.time_created_at
					, R.attr.btn_boost
					, Utils.formatSpannable1(activity, R.string.display_name_boosted_by, n_account.decoded_display_name)
				)
				if(n_status != null) showStatus(activity, n_status)
				
			}
			
			TootNotification.TYPE_FOLLOW -> {
				if(n_account != null) {
					showBoost(
						n_account
						, n.time_created_at
						, R.attr.ic_follow_plus
						, Utils.formatSpannable1(activity, R.string.display_name_followed_by, n_account.decoded_display_name)
					)
					showAccount(n_account)
				}
			}
			
			TootNotification.TYPE_MENTION -> {
				if(! bSimpleList) {
					if(n_account != null) showBoost(
						n_account
						, n.time_created_at
						, R.attr.btn_reply
						, Utils.formatSpannable1(activity, R.string.display_name_replied_by, n_account.decoded_display_name)
					)
				}
				if(n_status != null) showStatus(activity, n_status)
				
			}
			
			else -> {
			}
		}
	}
	
	private fun showList(list : TootList) {
		llList.visibility = View.VISIBLE
		btnListTL.text = list.title
	}
	
	private fun showDomainBlock(domain_block : TootDomainBlock) {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = domain_block.domain
	}
	
	private fun showSearchTag(tag : String) {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = "#" + tag
	}
	
	private fun showGap() {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = activity.getString(R.string.read_gap)
	}
	
	private fun showBoost(who : TootAccount, time : Long, icon_attr_id : Int, text : Spannable) {
		boost_account = who
		llBoosted.visibility = View.VISIBLE
		ivBoosted.setImageResource(Styler.getAttributeResourceId(activity, icon_attr_id))
		tvBoostedTime.text = TootStatus.formatTime(tvBoostedTime.context, time, true)
		tvBoosted.text = text
		boost_invalidator.register(text)
		setAcct(tvBoostedAcct, access_info.getFullAcct(who), who.acct)
	}
	
	private fun showAccount(who : TootAccount) {
		follow_account = who
		llFollow.visibility = View.VISIBLE
		ivFollow.setImageUrl(activity.pref, 16f, access_info.supplyBaseUrl(who.avatar_static))
		tvFollowerName.text = who.decoded_display_name
		follow_invalidator.register(who.decoded_display_name)
		
		setAcct(tvFollowerAcct, access_info.getFullAcct(who), who.acct)
		
		val relation = UserRelation.load(access_info.db_id, who.id)
		Styler.setFollowIcon(activity, btnFollow, ivFollowedBy, relation, who)
	}
	
	private fun showStatus(activity : ActMain, status : TootStatus) {
		this.status__showing = status
		llStatus.visibility = View.VISIBLE
		
		showStatusTime(activity, status)
		
		val who = status.account
		this.status_account = who
		
		setAcct(tvAcct, access_info.getFullAcct(who), who.acct)
		
		//		if(who == null) {
		//			tvName.text = "?"
		//			name_invalidator.register(null)
		//			ivThumbnail.setImageUrl(activity.pref, 16f, null, null)
		//		} else {
		tvName.text = who.decoded_display_name
		name_invalidator.register(who.decoded_display_name)
		ivThumbnail.setImageUrl(
			activity.pref, 16f, access_info.supplyBaseUrl(who.avatar_static), access_info.supplyBaseUrl(who.avatar)
		)
		//		}
		
		var content = status.decoded_content
		llExtra.removeAllViews()
		for(invalidator in extra_invalidator_list) {
			invalidator.register(null)
		}
		extra_invalidator_list.clear()
		
		// ニコフレのアンケートの表示
		val enquete = status.enquete
		if(enquete != null && NicoEnquete.TYPE_ENQUETE == enquete.type) {
			val question = enquete.question
			val items = enquete.items
			
			if(question.isNotBlank()) content = question
			if(items != null) {
				val now = System.currentTimeMillis()
				var n = 0
				for(item in items) {
					makeEnqueteChoiceView(enquete, now, n ++, item)
				}
			}
			makeEnqueteTimerView(enquete)
		}
		
		// カードの表示(会話ビューのみ)
		val card = status.card
		if(card != null) {
			makeCardView(activity, llExtra, card)
		}
		
		//			if( status.decoded_tags == null ){
		//				tvTags.setVisibility( View.GONE );
		//			}else{
		//				tvTags.setVisibility( View.VISIBLE );
		//				tvTags.setText( status.decoded_tags );
		//			}
		
		if(status.decoded_mentions.isEmpty()) {
			tvMentions.visibility = View.GONE
		} else {
			tvMentions.visibility = View.VISIBLE
			tvMentions.text = status.decoded_mentions
		}
		
		tvContent.text = content
		content_invalidator.register(content)
		
		activity.checkAutoCW(status, content)
		val r = status.auto_cw
		
		tvContent.minLines = r?.originalLineCount ?: - 1
		
		val decoded_spoiler_text = status.decoded_spoiler_text
		when {
			decoded_spoiler_text.isNotEmpty() -> {
				// 元データに含まれるContent Warning を使う
				llContentWarning.visibility = View.VISIBLE
				tvContentWarning.text = status.decoded_spoiler_text
				spoiler_invalidator.register(status.decoded_spoiler_text)
				val cw_shown = ContentWarning.isShown(status, false)
				showContent(cw_shown)
			}
			
			r?.decoded_spoiler_text != null -> {
				// 自動CW
				llContentWarning.visibility = View.VISIBLE
				tvContentWarning.text = r.decoded_spoiler_text
				spoiler_invalidator.register(r.decoded_spoiler_text)
				val cw_shown = ContentWarning.isShown(status, false)
				showContent(cw_shown)
			}
			
			else -> {
				// CWしない
				llContentWarning.visibility = View.GONE
				llContents.visibility = View.VISIBLE
			}
		}
		
		val media_attachments = status.media_attachments
		if(media_attachments == null || media_attachments.isEmpty()) {
			flMedia.visibility = View.GONE
		} else {
			flMedia.visibility = View.VISIBLE
			
			setMedia(ivMedia1, status, media_attachments, 0)
			setMedia(ivMedia2, status, media_attachments, 1)
			setMedia(ivMedia3, status, media_attachments, 2)
			setMedia(ivMedia4, status, media_attachments, 3)
			
			// hide sensitive media
			val default_shown = when {
				column.hide_media_default -> false
				access_info.dont_hide_nsfw -> true
				else -> ! status.sensitive
			}
			
			val is_shown = MediaShown.isShown(status, default_shown)
			btnShowMedia.visibility = if(! is_shown) View.VISIBLE else View.GONE
		}
		
		buttons_for_status?.bind(status, (item as? TootNotification))
		
		if(tvApplication != null) {
			val application = status.application
			when(column.column_type) {
				
				Column.TYPE_CONVERSATION -> if(application == null) {
					tvApplication.visibility = View.GONE
				} else {
					tvApplication.visibility = View.VISIBLE
					tvApplication.text = activity.getString(R.string.application_is, application.name ?: "")
				}
				else -> tvApplication.visibility = View.GONE
			}
		}
	}
	
	private fun showStatusTime(activity : ActMain, status : TootStatus) {
		val sb = SpannableStringBuilder()
		
		if(status.hasMedia() && status.sensitive) {
			// if( sb.length() > 0 ) sb.append( ' ' );
			
			val start = sb.length
			sb.append("NSFW")
			val end = sb.length
			val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_eye_off)
			sb.setSpan(EmojiImageSpan(activity, icon_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		
		val visibility_icon_id = Styler.getVisibilityIcon(activity, status.visibility)
		if(R.attr.ic_public != visibility_icon_id) {
			if(sb.isNotEmpty()) sb.append(' ')
			val start = sb.length
			sb.append(status.visibility)
			val end = sb.length
			sb.setSpan(EmojiImageSpan(activity, visibility_icon_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		
		if(status.pinned) {
			if(sb.isNotEmpty()) sb.append(' ')
			val start = sb.length
			sb.append("pinned")
			val end = sb.length
			val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
			sb.setSpan(EmojiImageSpan(activity, icon_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		
		if(sb.isNotEmpty()) sb.append(' ')
		sb.append(TootStatus.formatTime(activity, status.time_created_at, column.column_type != Column.TYPE_CONVERSATION))
		tvTime.text = sb
	}
	
	private fun setAcct(tv : TextView, acctLong : String, acctShort : String?) {
		
		val ac = AcctColor.load(acctLong)
		tv.text = when {
			AcctColor.hasNickname(ac) -> ac.nickname
			activity.shortAcctLocalUser -> "@" + (acctShort ?: "?")
			else -> acctLong
		}
		tv.setTextColor(if(AcctColor.hasColorForeground(ac)) ac.color_fg else this.acct_color)
		
		if(AcctColor.hasColorBackground(ac)) {
			tv.setBackgroundColor(ac.color_bg)
		} else {
			ViewCompat.setBackground(tv, null)
		}
		tv.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)
		
	}
	
	private fun showContent(shown : Boolean) {
		llContents.visibility = if(shown) View.VISIBLE else View.GONE
		btnContentWarning.setText(if(shown) R.string.hide else R.string.show)
		status__showing?.let { status ->
			val r = status.auto_cw
			tvContent.minLines = r?.originalLineCount ?: - 1
			if(r?.decoded_spoiler_text != null) {
				// 自動CWの場合はContentWarningのテキストを切り替える
				tvContentWarning.text = if(shown) activity.getString(R.string.auto_cw_prefix) else r.decoded_spoiler_text
			}
		}
	}
	
	private fun setMedia(iv : MyNetworkImageView, status : TootStatus, media_attachments : ArrayList<TootAttachmentLike>, idx : Int) {
		val ta = if(idx < media_attachments.size) media_attachments[idx] else null
		if(ta != null) {
			val url = ta.urlForThumbnail
			if(url != null && url.isNotEmpty()) {
				iv.visibility = View.VISIBLE
				iv.scaleType = if(activity.dont_crop_media_thumbnail) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
				
				val mediaType = ta.type
				when(mediaType) {
					TootAttachmentLike.TYPE_VIDEO -> iv.setMediaType(R.drawable.media_type_video)
					TootAttachmentLike.TYPE_GIFV -> iv.setMediaType(R.drawable.media_type_gifv)
					TootAttachmentLike.TYPE_UNKNOWN -> iv.setMediaType(R.drawable.media_type_unknown)
					else -> iv.setMediaType(0)
				}
				
				iv.setImageUrl(activity.pref, 0f, access_info.supplyBaseUrl(url), access_info.supplyBaseUrl(url))
				
				val description = ta.description
				if(description != null && description.isNotEmpty()) {
					val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
					lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
					val tv = MyTextView(activity)
					tv.layoutParams = lp
					//
					tv.movementMethod = MyLinkMovementMethod
					if(! activity.timeline_font_size_sp.isNaN()) {
						tv.textSize = activity.timeline_font_size_sp
					}
					val c = if(column.content_color != 0) column.content_color else content_color_default
					tv.setTextColor(c)
					
					//
					val desc = activity.getString(R.string.media_description, idx + 1, ta.description)
					tv.text = DecodeOptions()
						.setCustomEmojiMap(status.custom_emojis)
						.setProfileEmojis(status.profile_emojis)
						.decodeEmoji(activity, desc)
					llExtra.addView(tv)
				}
				
				return
			}
		}
		
		iv.visibility = View.GONE
	}
	
	override fun onClick(v : View) {
		
		val pos = activity.nextPosition(column)
		val item = this.item
		val notification = (item as? TootNotification)
		when(v.id) {
			
			R.id.btnHideMedia -> status__showing?.let { status ->
				MediaShown.save(status, false)
				btnShowMedia.visibility = View.VISIBLE
			}
			
			R.id.btnShowMedia -> status__showing?.let { status ->
				MediaShown.save(status, true)
				btnShowMedia.visibility = View.GONE
			}
			
			R.id.ivMedia1 -> clickMedia(0)
			R.id.ivMedia2 -> clickMedia(1)
			R.id.ivMedia3 -> clickMedia(2)
			R.id.ivMedia4 -> clickMedia(3)
			
			R.id.ivCardThumbnail -> status__showing?.card?.url?.let { url ->
				if(url.isNotEmpty()) App1.openCustomTab(activity, url)
			}
			
			R.id.btnContentWarning -> status__showing?.let { status ->
				val new_shown = llContents.visibility == View.GONE
				ContentWarning.save(status, new_shown)
				list_adapter.notifyDataSetChanged()
			}
			
			R.id.ivThumbnail -> status_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			
			R.id.llBoosted -> boost_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			
			R.id.llFollow -> follow_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			R.id.btnFollow -> follow_account?.let { who ->
				DlgContextMenu(activity, column, who, null, notification).show()
			}
			
			R.id.btnSearchTag -> when(item) {
				is TootGap -> column.startGap(item)
				
				is TootDomainBlock -> {
					val domain = item.domain
					AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.confirm_unblock_domain, domain))
						.setNegativeButton(R.string.cancel, null)
						.setPositiveButton(R.string.ok) { _, _ -> Action_Instance.blockDomain(activity, access_info, domain, false) }
						.show()
				}
				
				is String -> {
					// search_tag は#を含まない
					Action_HashTag.timeline(activity, activity.nextPosition(column), access_info, item)
				}
			}
			
			R.id.btnListTL -> if(item is TootList) {
				activity.addColumn(pos, access_info, Column.TYPE_LIST_TL, item.id)
			}
			
			R.id.btnListMore -> if(item is TootList) {
				ActionsDialog()
					.addAction(activity.getString(R.string.list_timeline)) {
						activity.addColumn(pos, access_info, Column.TYPE_LIST_TL, item.id)
					}
					.addAction(activity.getString(R.string.list_member)) {
						activity.addColumn(pos, access_info, Column.TYPE_LIST_MEMBER, item.id)
					}
					.addAction(activity.getString(R.string.delete)) {
						DlgConfirm.openSimple(
							activity
							, activity.getString(R.string.list_delete_confirm, item.title)
						) {
							Action_List.delete(activity, access_info, item.id)
						}
					}
					.show(activity, item.title)
			}
		}
	}
	
	override fun onLongClick(v : View) : Boolean {
		
		val notification = (item as? TootNotification)
		
		when(v.id) {
			
			R.id.ivThumbnail -> {
				status_account?.let { who -> DlgContextMenu(activity, column, who, null, notification).show() }
				return true
			}
			
			R.id.llBoosted -> {
				boost_account?.let { who -> DlgContextMenu(activity, column, who, null, notification).show() }
				return true
			}
			
			R.id.llFollow -> {
				follow_account?.let { who -> DlgContextMenu(activity, column, who, null, notification).show() }
				return true
			}
			
			R.id.btnFollow -> {
				follow_account?.let { who -> Action_Follow.followFromAnotherAccount(activity, activity.nextPosition(column), access_info, who) }
				return true
			}
			
			R.id.btnSearchTag -> {
				val item = this.item
				when(item) {
//					is TootGap -> column.startGap(item)
//
//					is TootDomainBlock -> {
//						val domain = item.domain
//						AlertDialog.Builder(activity)
//							.setMessage(activity.getString(R.string.confirm_unblock_domain, domain))
//							.setNegativeButton(R.string.cancel, null)
//							.setPositiveButton(R.string.ok) { _, _ -> Action_Instance.blockDomain(activity, access_info, domain, false) }
//							.show()
//					}
					
					is String -> {
						// search_tag は#を含まない
						val tagEncoded = Uri.encode(item)
						val host = access_info.host
						val url = "https://$host/tags/$tagEncoded"
						Action_HashTag.timelineOtherInstance(
							activity  =activity,
							pos =activity.nextPosition(column),
							url =url,
							host =host,
							tag_without_sharp = item
						)
					}
					
				}
				return true
			}
		}
		
		return false
	}
	
	private fun clickMedia(i : Int) {
		val status = status__showing ?: return
		try {
			val media_attachments = status.media_attachments ?: return
			val item = if(i < media_attachments.size) media_attachments[i] else return
			when(item) {
				is TootAttachmentMSP -> {
					// マストドン検索ポータルのデータではmedia_attachmentsが簡略化されている
					// 会話の流れを表示する
					Action_Toot.conversationOtherInstance(activity, activity.nextPosition(column), status__showing)
				}
				
				is TootAttachment -> {
					if(App1.pref.getBoolean(Pref.KEY_USE_INTERNAL_MEDIA_VIEWER, true)) {
						// 内蔵メディアビューア
						ActMediaViewer.open(activity, media_attachments, i)
						
					} else {
						// ブラウザで開く
						App1.openCustomTab(activity, item)
						
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	// 簡略ビューの時だけ呼ばれる
	// StatusButtonsPopupを表示する
	fun onItemClick(listView : MyListView, anchor : View) {
		activity.closeListItemPopup()
		status__showing?.let { status ->
			val popup = StatusButtonsPopup(activity, column, bSimpleList)
			activity.listItemPopup = popup
			popup.show(listView, anchor, status, item as? TootNotification)
		}
	}
	
	private fun makeCardView(activity : ActMain, llExtra : LinearLayout, card : TootCard) {
		var lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
		lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
		val tv = MyTextView(activity)
		tv.layoutParams = lp
		//
		tv.movementMethod = MyLinkMovementMethod
		if(! activity.timeline_font_size_sp.isNaN()) {
			tv.textSize = activity.timeline_font_size_sp
		}
		val c = if(column.content_color != 0) column.content_color else content_color_default
		tv.setTextColor(c)
		
		val sb = StringBuilder()
		addLinkAndCaption(sb, activity.getString(R.string.card_header_card), card.url, card.title)
		addLinkAndCaption(sb, activity.getString(R.string.card_header_author), card.author_url, card.author_name)
		addLinkAndCaption(sb, activity.getString(R.string.card_header_provider), card.provider_url, card.provider_name)
		
		val description = card.description
		if( description != null && description.isNotEmpty() ) {
			if(sb.isNotEmpty()) sb.append("<br>")
			sb.append(HTMLDecoder.encodeEntity(description))
		}
		val html = sb.toString()
		//
		tv.text = DecodeOptions().decodeHTML(activity, access_info, html)
		llExtra.addView(tv)
		
		val image = card.image
		if( image != null && image.isNotEmpty()) {
			lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.app_state.media_thumb_height)
			lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
			val iv = MyNetworkImageView(activity)
			iv.layoutParams = lp
			//
			iv.id = R.id.ivCardThumbnail
			iv.setOnClickListener(this)
			iv.scaleType = if(activity.dont_crop_media_thumbnail) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
			iv.setImageUrl(activity.pref, 0f, access_info.supplyBaseUrl(image), access_info.supplyBaseUrl(image))
			
			llExtra.addView(iv)
		}
	}
	
	private fun addLinkAndCaption(sb : StringBuilder, header : String, url : String?, caption : String?) {
		
		if(url.isNullOrEmpty() && caption.isNullOrEmpty()) return
		
		if(sb.isNotEmpty()) sb.append("<br>")
		
		sb.append(HTMLDecoder.encodeEntity(header)).append(": ")
		
		if(url != null && url.isNotEmpty()) {
			sb.append("<a href=\"").append(HTMLDecoder.encodeEntity(url)).append("\">")
		}
		sb.append(HTMLDecoder.encodeEntity(
			when {
				caption != null && caption.isNotEmpty() -> caption
				url != null && url.isNotEmpty() -> url
				else -> "???"
			}
		))
		
		if(url != null && url.isNotEmpty()) {
			sb.append("</a>")
		}
		
	}
	
	private fun makeEnqueteChoiceView(
		enquete : NicoEnquete,
		now : Long,
		i : Int,
		item : Spannable
	) {
		
		val remain = enquete.time_start + NicoEnquete.ENQUETE_EXPIRE - now
		
		val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
		if(i == 0)
			lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
		val b = Button(activity)
		b.layoutParams = lp
		b.setAllCaps(false)
		b.text = item
		val invalidator = NetworkEmojiInvalidator(activity.handler, b)
		extra_invalidator_list.add(invalidator)
		invalidator.register(item)
		if(remain <= 0) {
			b.isEnabled = false
		} else {
			val accessInfo = this@ItemViewHolder.access_info
			b.setOnClickListener { view ->
				val context = view.context ?: return@setOnClickListener
				onClickEnqueteChoice(enquete, context, accessInfo, i)
			}
		}
		llExtra.addView(b)
	}
	
	private fun makeEnqueteTimerView(enquete : NicoEnquete) {
		val density = llExtra.resources.displayMetrics.density
		val height = (0.5f + 6 * density).toInt()
		val view = EnqueteTimerView(activity)
		view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
		view.setParams(enquete.time_start, NicoEnquete.ENQUETE_EXPIRE)
		llExtra.addView(view)
	}
	
	private fun onClickEnqueteChoice(
		enquete : NicoEnquete,
		context : Context,
		accessInfo : SavedAccount,
		idx : Int
	) {
		val now = System.currentTimeMillis()
		val remain = enquete.time_start + NicoEnquete.ENQUETE_EXPIRE - now
		if(remain <= 0) {
			Utils.showToast(context, false, R.string.enquete_was_end)
			return
		}
		
		TootTaskRunner(context).run(accessInfo, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				val form = JSONObject()
				try {
					form.put("item_index", Integer.toString(idx))
				} catch(ex : Throwable) {
					ex.printStackTrace()
				}
				
				val request_builder = Request.Builder()
					.post(RequestBody.create(TootApiClient.MEDIA_TYPE_JSON, form.toString()))
				
				return client.request("/api/v1/votes/" + enquete.status_id, request_builder)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return  // cancelled.
				
				val data = result.jsonObject
				if(data != null) {
					val message = Utils.optStringX(data, "message") ?: "?"
					val valid = data.optBoolean("valid")
					if(valid) {
						Utils.showToast(context, false, R.string.enquete_voted)
					} else {
						Utils.showToast(context, true, R.string.enquete_vote_failed, message)
					}
				} else {
					Utils.showToast(context, true, result.error)
				}
				
			}
		})
	}
}


