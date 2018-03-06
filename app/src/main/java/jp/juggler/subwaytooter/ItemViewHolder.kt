package jp.juggler.subwaytooter

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

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
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.*
import okhttp3.Request
import okhttp3.RequestBody
import org.jetbrains.anko.*
import org.json.JSONObject

internal class ItemViewHolder(
	val activity : ActMain
) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		private val log = LogCategory("ItemViewHolder")
	}
	
	val viewRoot : View
	
	private var bSimpleList : Boolean = false
	
	lateinit var column : Column
	
	private lateinit var list_adapter : ItemListAdapter
	private lateinit var llBoosted : View
	private lateinit var ivBoosted : ImageView
	private lateinit var tvBoosted : TextView
	private lateinit var tvBoostedAcct : TextView
	private lateinit var tvBoostedTime : TextView
	
	private lateinit var llFollow : View
	private lateinit var ivFollow : MyNetworkImageView
	private lateinit var tvFollowerName : TextView
	private lateinit var tvFollowerAcct : TextView
	private lateinit var btnFollow : ImageButton
	private lateinit var ivFollowedBy : ImageView
	
	private lateinit var llStatus : View
	private lateinit var ivThumbnail : MyNetworkImageView
	private lateinit var tvName : TextView
	private lateinit var tvTime : TextView
	private lateinit var tvAcct : TextView
	
	private lateinit var llContentWarning : View
	private lateinit var tvContentWarning : MyTextView
	private lateinit var btnContentWarning : Button
	
	private lateinit var llContents : View
	private lateinit var tvMentions : MyTextView
	private lateinit var tvContent : MyTextView
	
	private lateinit var flMedia : View
	private lateinit var llMedia : View
	private lateinit var btnShowMedia : TextView
	private lateinit var ivMedia1 : MyNetworkImageView
	private lateinit var ivMedia2 : MyNetworkImageView
	private lateinit var ivMedia3 : MyNetworkImageView
	private lateinit var ivMedia4 : MyNetworkImageView
	private lateinit var btnHideMedia : View
	
	private lateinit var llButtonBar : View
	private lateinit var btnConversation : ImageButton
	private lateinit var btnReply : ImageButton
	private lateinit var btnBoost : Button
	private lateinit var btnFavourite : Button
	private lateinit var llFollow2 : View
	private lateinit var btnFollow2 : ImageButton
	private lateinit var ivFollowedBy2 : ImageView
	private lateinit var btnMore : ImageButton
	
	private lateinit var llSearchTag : View
	private lateinit var btnSearchTag : Button
	
	private lateinit var llList : View
	private lateinit var btnListTL : Button
	private lateinit var btnListMore : ImageButton
	
	private lateinit var llExtra : LinearLayout
	
	private lateinit var tvApplication : TextView
	
	private lateinit var access_info : SavedAccount
	
	private var buttons_for_status : StatusButtons? = null
	
	private var item : TimelineItem? = null
	
	private var status_showing : TootStatus? = null
	private var status_account : TootAccount? = null
	private var boost_account : TootAccount? = null
	private var follow_account : TootAccount? = null
	
	private var boost_time : Long = 0L
	
	private val content_color_default : Int
	private var acct_color : Int = 0
	
	private val boost_invalidator : NetworkEmojiInvalidator
	private val follow_invalidator : NetworkEmojiInvalidator
	private val name_invalidator : NetworkEmojiInvalidator
	private val content_invalidator : NetworkEmojiInvalidator
	private val spoiler_invalidator : NetworkEmojiInvalidator
	private val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()
	
	init {
		this.viewRoot = inflate(activity.UI {})
		
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
		
		btnHideMedia.setOnClickListener(this)
		
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
			tvApplication.textSize = activity.timeline_font_size_sp
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
		ivThumbnail.layoutParams.width = activity.avatarIconSize
		ivFollow.layoutParams.width = activity.avatarIconSize
		ivBoosted.layoutParams.width = activity.avatarIconSize
		
		this.content_invalidator = NetworkEmojiInvalidator(activity.handler, tvContent)
		this.spoiler_invalidator = NetworkEmojiInvalidator(activity.handler, tvContentWarning)
		this.boost_invalidator = NetworkEmojiInvalidator(activity.handler, tvBoosted)
		this.follow_invalidator = NetworkEmojiInvalidator(activity.handler, tvFollowerName)
		this.name_invalidator = NetworkEmojiInvalidator(activity.handler, tvName)
	}
	
	fun onViewRecycled() {
	
	}
	
	fun bind(
		list_adapter : ItemListAdapter,
		column : Column,
		bSimpleList : Boolean,
		item : TimelineItem
	) {
		this.list_adapter = list_adapter
		this.column = column
		this.bSimpleList = bSimpleList
		
		this.access_info = column.access_info
		
		if(activity.timeline_font != null || activity.timeline_font_bold != null) {
			viewRoot.scan { v ->
				try {
					if(v is Button) {
						// ボタンは太字なので触らない
					} else if(v is TextView) {
						val typeface = when {
							v === tvName || v === tvFollowerName || v === tvBoosted -> activity.timeline_font_bold
								?: activity.timeline_font
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
		
		if(bSimpleList) {
			
			viewRoot.setOnTouchListener { _, ev ->
				// ポップアップを閉じた時にクリックでリストを触ったことになってしまう不具合の回避
				val now = SystemClock.elapsedRealtime()
				// ポップアップを閉じた直後はタッチダウンを無視する
				if(now - StatusButtonsPopup.last_popup_close >= 30L) {
					false
				} else {
					val action = ev.action
					log.d("onTouchEvent action=$action")
					true
				}
			}
			
			viewRoot.setOnClickListener { viewClicked ->
				activity.closeListItemPopup()
				status_showing?.let { status ->
					val popup = StatusButtonsPopup(activity, column, bSimpleList)
					activity.listItemPopup = popup
					popup.show(
						list_adapter.columnVh.listView,
						viewClicked,
						status,
						item as? TootNotification
					)
				}
			}
			llButtonBar.visibility = View.GONE
			this.buttons_for_status = null
		} else {
			viewRoot.isClickable = false
			llButtonBar.visibility = View.VISIBLE
			this.buttons_for_status = StatusButtons(
				activity,
				column,
				false,
				
				btnConversation = btnConversation,
				btnReply = btnReply,
				btnBoost = btnBoost,
				btnFavourite = btnFavourite,
				llFollow2 = llFollow2,
				btnFollow2 = btnFollow2,
				ivFollowedBy2 = ivFollowedBy2,
				btnMore = btnMore
			
			)
		}
		
		this.status_showing = null
		this.status_account = null
		this.boost_account = null
		this.follow_account = null
		this.boost_time = 0L
		
		llBoosted.visibility = View.GONE
		llFollow.visibility = View.GONE
		llStatus.visibility = View.GONE
		llSearchTag.visibility = View.GONE
		llList.visibility = View.GONE
		llExtra.removeAllViews()
		
		var c : Int
		c = if(column.content_color != 0) column.content_color else content_color_default
		tvBoosted.setTextColor(c)
		tvFollowerName.setTextColor(c)
		tvName.setTextColor(c)
		tvMentions.setTextColor(c)
		tvContentWarning.setTextColor(c)
		tvContent.setTextColor(c)
		//NSFWは文字色固定 btnShowMedia.setTextColor( c );
		tvApplication.setTextColor(c)
		
		c = if(column.acct_color != 0) column.acct_color else Styler.getAttributeColor(
			activity,
			R.attr.colorTimeSmall
		)
		this.acct_color = c
		tvBoostedTime.setTextColor(c)
		tvTime.setTextColor(c)
		//			tvBoostedAcct.setTextColor( c );
		//			tvFollowerAcct.setTextColor( c );
		//			tvAcct.setTextColor( c );
		
		this.item = item
		when(item) {
			is TootTag -> showSearchTag(item)
			is TootAccount -> showAccount(item)
			is TootNotification -> showNotification(item)
			is TootGap -> showGap()
			is TootDomainBlock -> showDomainBlock(item)
			is TootList -> showList(item)
			
			is TootStatus -> {
				val reblog = item.reblog
				if(reblog != null) {
					showBoost(
						item.account,
						item.time_created_at,
						R.attr.btn_boost,
						item.account.decoded_display_name.intoStringResource(
							activity,
							R.string.display_name_boosted_by
						)
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
					n_account,
					n.time_created_at,
					if(access_info.isNicoru(n_account)) R.attr.ic_nicoru else R.attr.btn_favourite,
					n_account.decoded_display_name.intoStringResource(
						activity,
						R.string.display_name_favourited_by
					)
				)
				if(n_status != null) showStatus(activity, n_status)
			}
			
			TootNotification.TYPE_REBLOG -> {
				if(n_account != null) showBoost(
					n_account,
					n.time_created_at,
					R.attr.btn_boost,
					n_account.decoded_display_name.intoStringResource(
						activity,
						R.string.display_name_boosted_by
					)
				)
				if(n_status != null) showStatus(activity, n_status)
				
			}
			
			TootNotification.TYPE_FOLLOW -> {
				if(n_account != null) {
					showBoost(
						n_account,
						n.time_created_at,
						R.attr.ic_follow_plus,
						n_account.decoded_display_name.intoStringResource(
							activity,
							R.string.display_name_followed_by
						)
					)
					showAccount(n_account)
				}
			}
			
			TootNotification.TYPE_MENTION -> {
				if(! bSimpleList) {
					if(n_account != null) showBoost(
						n_account,
						n.time_created_at,
						R.attr.btn_reply,
						n_account.decoded_display_name.intoStringResource(
							activity,
							R.string.display_name_replied_by
						)
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
	
	private fun showSearchTag(tag : TootTag) {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = "#" + tag.name
	}
	
	private fun showGap() {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = activity.getString(R.string.read_gap)
	}
	
	private fun showBoost(who : TootAccount, time : Long, icon_attr_id : Int, text : Spannable) {
		boost_account = who
		boost_time = time
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
		ivFollow.setImageUrl(
			activity.pref,
			Styler.calcIconRound(ivFollow.layoutParams),
			access_info.supplyBaseUrl(who.avatar_static)
		)
		tvFollowerName.text = who.decoded_display_name
		follow_invalidator.register(who.decoded_display_name)
		
		setAcct(tvFollowerAcct, access_info.getFullAcct(who), who.acct)
		
		val relation = UserRelation.load(access_info.db_id, who.id)
		Styler.setFollowIcon(activity, btnFollow, ivFollowedBy, relation, who)
	}
	
	private fun showStatus(activity : ActMain, status : TootStatus) {
		this.status_showing = status
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
			activity.pref,
			Styler.calcIconRound(ivThumbnail.layoutParams),
			access_info.supplyBaseUrl(who.avatar_static),
			access_info.supplyBaseUrl(who.avatar)
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
			llMedia.visibility = View.GONE
			btnShowMedia.visibility = View.GONE
		} else {
			flMedia.visibility = View.VISIBLE
			
			// hide sensitive media
			val default_shown = when {
				column.hide_media_default -> false
				access_info.dont_hide_nsfw -> true
				else -> ! status.sensitive
			}
			val is_shown = MediaShown.isShown(status, default_shown)
			
			btnShowMedia.visibility = if(! is_shown) View.VISIBLE else View.GONE
			llMedia.visibility = if(! is_shown) View.GONE else View.VISIBLE
			setMedia(ivMedia1, status, media_attachments, 0)
			setMedia(ivMedia2, status, media_attachments, 1)
			setMedia(ivMedia3, status, media_attachments, 2)
			setMedia(ivMedia4, status, media_attachments, 3)
		}
		
		buttons_for_status?.bind(status, (item as? TootNotification))
		
		val application = status.application
		if(application != null
			&& (column.column_type == Column.TYPE_CONVERSATION || Pref.bpShowAppName(activity.pref))
		) {
			tvApplication.visibility = View.VISIBLE
			tvApplication.text =
				activity.getString(R.string.application_is, application.name ?: "")
		} else {
			tvApplication.visibility = View.GONE
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
			sb.setSpan(
				EmojiImageSpan(activity, icon_id),
				start,
				end,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
		}
		
		val visIconAttrId = Styler.getVisibilityIconAttr(status.visibility)
		if(R.attr.ic_public != visIconAttrId) {
			if(sb.isNotEmpty()) sb.append(' ')
			val start = sb.length
			sb.append(status.visibility)
			val end = sb.length
			val iconResId = Styler.getAttributeResourceId(activity, visIconAttrId)
			sb.setSpan(
				EmojiImageSpan(activity, iconResId),
				start,
				end,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
		}
		
		if(status.pinned) {
			if(sb.isNotEmpty()) sb.append(' ')
			val start = sb.length
			sb.append("pinned")
			val end = sb.length
			val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
			sb.setSpan(
				EmojiImageSpan(activity, icon_id),
				start,
				end,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
		}
		
		if(sb.isNotEmpty()) sb.append(' ')
		sb.append(
			TootStatus.formatTime(
				activity,
				status.time_created_at,
				column.column_type != Column.TYPE_CONVERSATION
			)
		)
		tvTime.text = sb
	}
	
	//	fun updateRelativeTime() {
	//		val boost_time = this.boost_time
	//		if(boost_time != 0L) {
	//			tvBoostedTime.text = TootStatus.formatTime(tvBoostedTime.context, boost_time, true)
	//		}
	//		val status_showing = this.status_showing
	//		if(status_showing != null) {
	//			showStatusTime(activity, status_showing)
	//		}
	//	}
	
	private fun setAcct(tv : TextView, acctLong : String, acctShort : String?) {
		
		val ac = AcctColor.load(acctLong)
		tv.text = when {
			AcctColor.hasNickname(ac) -> ac.nickname
			Pref.bpShortAcctLocalUser(App1.pref) -> "@" + (acctShort ?: "?")
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
		status_showing?.let { status ->
			val r = status.auto_cw
			tvContent.minLines = r?.originalLineCount ?: - 1
			if(r?.decoded_spoiler_text != null) {
				// 自動CWの場合はContentWarningのテキストを切り替える
				tvContentWarning.text =
					if(shown) activity.getString(R.string.auto_cw_prefix) else r.decoded_spoiler_text
			}
		}
	}
	
	private fun setMedia(
		iv : MyNetworkImageView,
		status : TootStatus,
		media_attachments : ArrayList<TootAttachmentLike>,
		idx : Int
	) {
		val ta = if(idx < media_attachments.size) media_attachments[idx] else null
		if(ta != null) {
			val url = ta.urlForThumbnail
			if(url != null && url.isNotEmpty()) {
				iv.visibility = View.VISIBLE
				iv.scaleType =
					if(Pref.bpDontCropMediaThumb(App1.pref)) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
				
				val mediaType = ta.type
				when(mediaType) {
					TootAttachmentLike.TYPE_VIDEO -> iv.setMediaType(R.drawable.media_type_video)
					TootAttachmentLike.TYPE_GIFV -> iv.setMediaType(R.drawable.media_type_gifv)
					TootAttachmentLike.TYPE_UNKNOWN -> iv.setMediaType(R.drawable.media_type_unknown)
					else -> iv.setMediaType(0)
				}
				
				iv.setImageUrl(
					activity.pref,
					0f,
					access_info.supplyBaseUrl(url),
					access_info.supplyBaseUrl(url)
				)
				
				val description = ta.description
				if(description != null && description.isNotEmpty()) {
					val lp = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
					lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
					val tv = MyTextView(activity)
					tv.layoutParams = lp
					//
					tv.movementMethod = MyLinkMovementMethod
					if(! activity.timeline_font_size_sp.isNaN()) {
						tv.textSize = activity.timeline_font_size_sp
					}
					val c =
						if(column.content_color != 0) column.content_color else content_color_default
					tv.setTextColor(c)
					
					//
					val desc =
						activity.getString(R.string.media_description, idx + 1, ta.description)
					tv.text = DecodeOptions(
						activity,
						emojiMapCustom = status.custom_emojis,
						emojiMapProfile = status.profile_emojis
					).decodeEmoji(desc)
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
		when(v) {
			
			btnHideMedia -> status_showing?.let { status ->
				MediaShown.save(status, false)
				btnShowMedia.visibility = View.VISIBLE
				llMedia.visibility = View.GONE
			}
			
			btnShowMedia -> status_showing?.let { status ->
				MediaShown.save(status, true)
				btnShowMedia.visibility = View.GONE
				llMedia.visibility = View.VISIBLE
			}
			
			ivMedia1 -> clickMedia(0)
			ivMedia2 -> clickMedia(1)
			ivMedia3 -> clickMedia(2)
			ivMedia4 -> clickMedia(3)
			
			btnContentWarning -> status_showing?.let { status ->
				val new_shown = llContents.visibility == View.GONE
				ContentWarning.save(status, new_shown)
				
				// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
				list_adapter.notifyChange(reason = "ContentWarning onClick", reset = true)
				
			}
			
			ivThumbnail -> status_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			
			llBoosted -> boost_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			
			llFollow -> follow_account?.let { who ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, who, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, who)
				}
			}
			btnFollow -> follow_account?.let { who ->
				DlgContextMenu(activity, column, who, null, notification).show()
			}
			
			btnSearchTag -> when(item) {
				is TootGap -> column.startGap(item)
				
				is TootDomainBlock -> {
					val domain = item.domain
					AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.confirm_unblock_domain, domain))
						.setNegativeButton(R.string.cancel, null)
						.setPositiveButton(R.string.ok) { _, _ ->
							Action_Instance.blockDomain(
								activity,
								access_info,
								domain,
								false
							)
						}
						.show()
				}
				
				is TootTag -> {
					Action_HashTag.timeline(
						activity,
						activity.nextPosition(column),
						access_info,
						item.name // #を含まない
					)
				}
			}
			
			btnListTL -> if(item is TootList) {
				activity.addColumn(pos, access_info, Column.TYPE_LIST_TL, item.id)
			}
			
			btnListMore -> if(item is TootList) {
				ActionsDialog()
					.addAction(activity.getString(R.string.list_timeline)) {
						activity.addColumn(pos, access_info, Column.TYPE_LIST_TL, item.id)
					}
					.addAction(activity.getString(R.string.list_member)) {
						activity.addColumn(false,pos, access_info, Column.TYPE_LIST_MEMBER, item.id)
					}
					.addAction(activity.getString(R.string.rename)){
						Action_List.rename(activity, access_info, item)
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
			
			else -> when(v.id) {
				R.id.ivCardThumbnail -> status_showing?.card?.url?.let { url ->
					if(url.isNotEmpty()) App1.openCustomTab(activity, url)
				}
			}
			
		}
	}
	
	override fun onLongClick(v : View) : Boolean {
		
		val notification = (item as? TootNotification)
		
		when(v) {
			
			ivThumbnail -> {
				status_account?.let { who ->
					DlgContextMenu(
						activity,
						column,
						who,
						null,
						notification
					).show()
				}
				return true
			}
			
			llBoosted -> {
				boost_account?.let { who ->
					DlgContextMenu(
						activity,
						column,
						who,
						null,
						notification
					).show()
				}
				return true
			}
			
			llFollow -> {
				follow_account?.let { who ->
					DlgContextMenu(
						activity,
						column,
						who,
						null,
						notification
					).show()
				}
				return true
			}
			
			btnFollow -> {
				follow_account?.let { who ->
					Action_Follow.followFromAnotherAccount(
						activity,
						activity.nextPosition(column),
						access_info,
						who
					)
				}
				return true
			}
			
			btnSearchTag -> {
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
					
					is TootTag -> {
						// search_tag は#を含まない
						val tagEncoded = item.name.encodePercent()
						val host = access_info.host
						val url = "https://$host/tags/$tagEncoded"
						Action_HashTag.timelineOtherInstance(
							activity = activity,
							pos = activity.nextPosition(column),
							url = url,
							host = host,
							tag_without_sharp = item.name
						)
					}
					
				}
				return true
			}
		}
		
		return false
	}
	
	private fun clickMedia(i : Int) {
		val status = status_showing ?: return
		try {
			val media_attachments = status.media_attachments ?: return
			val item = if(i < media_attachments.size) media_attachments[i] else return
			when(item) {
				is TootAttachmentMSP -> {
					// マストドン検索ポータルのデータではmedia_attachmentsが簡略化されている
					// 会話の流れを表示する
					Action_Toot.conversationOtherInstance(
						activity,
						activity.nextPosition(column),
						status_showing
					)
				}
				
				is TootAttachment -> {
					if(Pref.bpUseInternalMediaViewer(App1.pref)) {
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
	
	private fun makeCardView(activity : ActMain, llExtra : LinearLayout, card : TootCard) {
		var lp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
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
		addLinkAndCaption(
			sb,
			activity.getString(R.string.card_header_author),
			card.author_url,
			card.author_name
		)
		addLinkAndCaption(
			sb,
			activity.getString(R.string.card_header_provider),
			card.provider_url,
			card.provider_name
		)
		
		val description = card.description
		if(description != null && description.isNotEmpty()) {
			if(sb.isNotEmpty()) sb.append("<br>")
			sb.append(HTMLDecoder.encodeEntity(description))
		}
		val html = sb.toString()
		//
		tv.text = DecodeOptions(activity, access_info).decodeHTML(html)
		llExtra.addView(tv)
		
		val image = card.image
		if(image != null && image.isNotEmpty()) {
			lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				activity.app_state.media_thumb_height
			)
			lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
			val iv = MyNetworkImageView(activity)
			iv.layoutParams = lp
			//
			iv.id = R.id.ivCardThumbnail
			iv.setOnClickListener(this)
			iv.scaleType =
				if(Pref.bpDontCropMediaThumb(App1.pref)) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
			iv.setImageUrl(
				activity.pref,
				0f,
				access_info.supplyBaseUrl(image),
				access_info.supplyBaseUrl(image)
			)
			
			llExtra.addView(iv)
		}
	}
	
	private fun addLinkAndCaption(
		sb : StringBuilder,
		header : String,
		url : String?,
		caption : String?
	) {
		
		if(url.isNullOrEmpty() && caption.isNullOrEmpty()) return
		
		if(sb.isNotEmpty()) sb.append("<br>")
		
		sb.append(HTMLDecoder.encodeEntity(header)).append(": ")
		
		if(url != null && url.isNotEmpty()) {
			sb.append("<a href=\"").append(HTMLDecoder.encodeEntity(url)).append("\">")
		}
		sb.append(
			HTMLDecoder.encodeEntity(
				when {
					caption != null && caption.isNotEmpty() -> caption
					url != null && url.isNotEmpty() -> url
					else -> "???"
				}
			)
		)
		
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
		
		val lp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
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
		view.layoutParams =
			LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
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
			showToast(context, false, R.string.enquete_was_end)
			return
		}
		
		TootTaskRunner(context).run(accessInfo, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				val form = JSONObject()
				try {
					form.put("item_index", Integer.toString(idx))
				} catch(ex : Throwable) {
					log.e(ex, "json encode failed.")
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
					val message = data.parseString("message") ?: "?"
					val valid = data.optBoolean("valid")
					if(valid) {
						showToast(context, false, R.string.enquete_voted)
					} else {
						showToast(context, true, R.string.enquete_vote_failed, message)
					}
				} else {
					showToast(context, true, result.error)
				}
				
			}
		})
	}
	
	private fun inflate(ui : AnkoContext<Context>) = with(ui) {
		verticalLayout {
			// トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
			lparams(matchParent, wrapContent)
			
			topPadding = dip(3)
			bottomPadding = dip(3)
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			
			llBoosted = linearLayout {
				lparams(matchParent, wrapContent) {
					bottomMargin = dip(6)
				}
				
				background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
				gravity = Gravity.CENTER_VERTICAL
				
				ivBoosted = imageView {
					scaleType = ImageView.ScaleType.FIT_END
					importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
				}.lparams(dip(48), dip(32)) {
					endMargin = dip(4)
				}
				
				verticalLayout {
					lparams(dip(0), wrapContent) {
						weight = 1f
					}
					
					linearLayout {
						lparams(matchParent, wrapContent)
						
						tvBoostedAcct = textView {
							ellipsize = TextUtils.TruncateAt.END
							gravity = Gravity.END
							maxLines = 1
							textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
							textSize = 12f // textSize の単位はSP
							// tools:text ="who@hoge"
						}.lparams(dip(0), wrapContent) {
							weight = 1f
						}
						
						tvBoostedTime = textView {
							
							startPadding = dip(2)
							
							gravity = Gravity.END
							textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
							textSize = 12f // textSize の単位はSP
							// tools:ignore="RtlSymmetry"
							// tools:text="2017-04-16 09:37:14"
						}.lparams(wrapContent, wrapContent)
						
					}
					
					tvBoosted = textView {
						// tools:text = "～にブーストされました"
					}.lparams(matchParent, wrapContent)
				}
			}
			
			llFollow = linearLayout {
				lparams(matchParent, wrapContent)
				
				background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
				gravity = Gravity.CENTER_VERTICAL
				
				ivFollow = myNetworkImageView {
					contentDescription = context.getString(R.string.thumbnail)
					scaleType = ImageView.ScaleType.FIT_END
				}.lparams(dip(48), dip(40)) {
					endMargin = dip(4)
				}
				
				verticalLayout {
					
					lparams(dip(0), wrapContent) {
						weight = 1f
					}
					
					tvFollowerName = textView {
						// tools:text="Follower Name"
					}.lparams(matchParent, wrapContent)
					
					tvFollowerAcct = textView {
						setPaddingStartEnd(dip(4), dip(4))
						textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
						textSize = 12f // SP
						// tools:text="aaaaaaaaaaaaaaaa"
					}.lparams(matchParent, wrapContent)
				}
				
				frameLayout {
					lparams(dip(40), dip(40)) {
						startMargin = dip(4)
					}
					
					btnFollow = imageButton {
						background =
							ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
						contentDescription = context.getString(R.string.follow)
						scaleType = ImageView.ScaleType.CENTER
						// tools:src="?attr/ic_follow_plus"
					}.lparams(matchParent, matchParent)
					
					ivFollowedBy = imageView {
						scaleType = ImageView.ScaleType.CENTER
						// tools:src="?attr/ic_followed_by"
						importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
					}.lparams(matchParent, matchParent)
					
				}
			}
			
			llStatus = verticalLayout {
				lparams(matchParent, wrapContent)
				
				linearLayout {
					lparams(matchParent, wrapContent)
					
					tvAcct = textView {
						ellipsize = TextUtils.TruncateAt.END
						gravity = Gravity.END
						maxLines = 1
						textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
						textSize = 12f // SP
						// tools:text="who@hoge"
					}.lparams(dip(0), wrapContent) {
						weight = 1f
					}
					
					tvTime = textView {
						gravity = Gravity.END
						startPadding = dip(2)
						textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
						textSize = 12f // SP
						// tools:ignore="RtlSymmetry"
						// tools:text="2017-04-16 09:37:14"
					}.lparams(wrapContent, wrapContent)
					
				}
				
				linearLayout {
					lparams(matchParent, wrapContent)
					
					ivThumbnail = myNetworkImageView {
						background =
							ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
						contentDescription = context.getString(R.string.thumbnail)
						scaleType = ImageView.ScaleType.CENTER_CROP
					}.lparams(dip(48), dip(48)) {
						topMargin = dip(4)
						endMargin = dip(4)
					}
					
					verticalLayout {
						lparams(dip(0), wrapContent) {
							weight = 1f
						}
						
						tvName = textView {
							
							// tools:text="Displayname"
						}.lparams(matchParent, wrapContent)
						
						llContentWarning = linearLayout {
							lparams(matchParent, wrapContent) {
								topMargin = dip(3)
								isBaselineAligned = false
							}
							gravity = Gravity.CENTER_VERTICAL
							
							btnContentWarning = button {
								
								background =
									ContextCompat.getDrawable(context, R.drawable.btn_bg_ddd)
								minWidthCompat = dip(40)
								padding = dip(4)
								//tools:text="見る"
							}.lparams(wrapContent, dip(40)) {
								endMargin = dip(8)
							}
							
							verticalLayout {
								lparams(dip(0), wrapContent) {
									weight = 1f
								}
								
								tvMentions = myTextView {
								}.lparams(matchParent, wrapContent)
								
								tvContentWarning = myTextView {
								}.lparams(matchParent, wrapContent) {
									topMargin = dip(3)
								}
								
							}
							
						}
						
						llContents = verticalLayout {
							lparams(matchParent, wrapContent)
							
							tvContent = myTextView {
								setLineSpacing(lineSpacingExtra, 1.1f)
								// tools:text="Contents\nContents"
							}.lparams(matchParent, wrapContent) {
								topMargin = dip(3)
							}
							
							flMedia = frameLayout {
								lparams(matchParent, dip(64)) {
									topMargin = dip(3)
								}
								
								llMedia = linearLayout {
									lparams(matchParent, matchParent)
									
									ivMedia1 = myNetworkImageView {
										
										
										background = ContextCompat.getDrawable(
											context,
											R.drawable.bg_thumbnail
										)
										contentDescription = context.getString(R.string.thumbnail)
										scaleType = ImageView.ScaleType.CENTER_CROP
										
									}.lparams(0, matchParent) {
										weight = 1f
									}
									
									ivMedia2 = myNetworkImageView {
										
										background = ContextCompat.getDrawable(
											context,
											R.drawable.bg_thumbnail
										)
										contentDescription = context.getString(R.string.thumbnail)
										scaleType = ImageView.ScaleType.CENTER_CROP
										
									}.lparams(0, matchParent) {
										startMargin = dip(8)
										weight = 1f
									}
									
									ivMedia3 = myNetworkImageView {
										
										background = ContextCompat.getDrawable(
											context,
											R.drawable.bg_thumbnail
										)
										contentDescription = context.getString(R.string.thumbnail)
										scaleType = ImageView.ScaleType.CENTER_CROP
										
									}.lparams(0, matchParent) {
										startMargin = dip(8)
										weight = 1f
									}
									
									ivMedia4 = myNetworkImageView {
										
										background = ContextCompat.getDrawable(
											context,
											R.drawable.bg_thumbnail
										)
										contentDescription = context.getString(R.string.thumbnail)
										scaleType = ImageView.ScaleType.CENTER_CROP
										
									}.lparams(0, matchParent) {
										startMargin = dip(8)
										weight = 1f
									}
									
									btnHideMedia = imageButton {
										
										background = ContextCompat.getDrawable(
											context,
											R.drawable.btn_bg_transparent
										)
										contentDescription = "@string/hide"
										imageResource =
											Styler.getAttributeResourceId(context, R.attr.btn_close)
									}.lparams(dip(32), matchParent) {
										startMargin = dip(8)
									}
								}
								
								btnShowMedia = textView {
									
									backgroundColor = Styler.getAttributeColor(
										context,
										R.attr.colorShowMediaBackground
									)
									gravity = Gravity.CENTER
									text = context.getString(R.string.tap_to_show)
									textColor =
										Styler.getAttributeColor(context, R.attr.colorShowMediaText)
									
								}.lparams(matchParent, matchParent)
							}
							
							
							llExtra = verticalLayout {
								lparams(matchParent, wrapContent) {
									topMargin = dip(0)
								}
							}
						}
						
						// button bar
						llButtonBar = linearLayout {
							lparams(wrapContent, dip(40)) {
								topMargin = dip(3)
							}
							
							btnConversation = imageButton {
								
								background = ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent
								)
								contentDescription = context.getString(R.string.conversation_view)
								minimumWidth = dip(40)
								imageResource =
									Styler.getAttributeResourceId(context, R.attr.ic_conversation)
							}.lparams(wrapContent, matchParent)
							
							btnReply = imageButton {
								
								background = ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent
								)
								contentDescription = context.getString(R.string.reply)
								minimumWidth = dip(40)
								imageResource =
									Styler.getAttributeResourceId(context, R.attr.btn_reply)
								
							}.lparams(wrapContent, matchParent) {
								startMargin = dip(2)
							}
							
							btnBoost = button {
								
								background = ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent
								)
								compoundDrawablePadding = dip(4)
								
								minWidthCompat = dip(48)
								setPaddingStartEnd(dip(4), dip(4))
							}.lparams(wrapContent, matchParent) {
								startMargin = dip(2)
							}
							
							btnFavourite = button {
								background = ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent
								)
								compoundDrawablePadding = dip(4)
								minWidthCompat = dip(48)
								setPaddingStartEnd(dip(4), dip(4))
								
							}.lparams(wrapContent, matchParent) {
								startMargin = dip(2)
							}
							
							llFollow2 = frameLayout {
								lparams(dip(40), dip(40)) {
									startMargin = dip(2)
								}
								
								btnFollow2 = imageButton {
									
									background = ContextCompat.getDrawable(
										context,
										R.drawable.btn_bg_transparent
									)
									contentDescription = context.getString(R.string.follow)
									scaleType = ImageView.ScaleType.CENTER
									// tools:src="?attr/ic_follow_plus"
									minimumWidth = dip(40)
									
								}.lparams(matchParent, matchParent)
								
								ivFollowedBy2 = imageView {
									
									scaleType = ImageView.ScaleType.CENTER
									imageResource = Styler.getAttributeResourceId(
										context,
										R.attr.ic_followed_by
									)
									importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
								}.lparams(matchParent, matchParent)
							}
							
							btnMore = imageButton {
								background = ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent
								)
								contentDescription = context.getString(R.string.more)
								imageResource =
									Styler.getAttributeResourceId(context, R.attr.btn_more)
								minimumWidth = dip(40)
							}.lparams(wrapContent, matchParent) {
								startMargin = dip(2)
							}
							
						}
						
						tvApplication = textView {
							gravity = Gravity.END
						}.lparams(matchParent, wrapContent)
						
					}
				}
				
			}
			
			llSearchTag = linearLayout {
				lparams(matchParent, wrapContent)
				
				btnSearchTag = button {
					background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
					allCaps = false
				}.lparams(matchParent, wrapContent)
			}
			
			llList = linearLayout {
				lparams(matchParent, wrapContent)
				
				btnListTL = button {
					background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
					allCaps = false
				}.lparams(0, wrapContent) {
					weight = 1f
				}
				
				btnListMore = imageButton {
					
					background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
					imageResource = Styler.getAttributeResourceId(context, R.attr.btn_more)
					contentDescription = context.getString(R.string.more)
				}.lparams(dip(40), dip(40)) {
					startMargin = dip(4)
				}
			}
		}
	}
	
}


