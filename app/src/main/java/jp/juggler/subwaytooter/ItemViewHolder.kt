package jp.juggler.subwaytooter

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextUtils.ellipsize
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.*
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
		var toot_color_unlisted : Int = 0
		var toot_color_follower : Int = 0
		var toot_color_direct_user : Int = 0
		var toot_color_direct_me : Int = 0
	}
	
	val viewRoot : View
	
	private var bSimpleList : Boolean = false
	
	lateinit var column : Column
	
	internal lateinit var list_adapter : ItemListAdapter
	
	private lateinit var llBoosted : View
	private lateinit var ivBoosted : ImageView
	private lateinit var tvBoosted : TextView
	private lateinit var tvBoostedAcct : TextView
	private lateinit var tvBoostedTime : TextView
	
	private lateinit var llReply : View
	private lateinit var ivReply : ImageView
	private lateinit var tvReply : TextView
	
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
	
	private lateinit var statusButtonsViewHolder : StatusButtonsViewHolder
	private lateinit var llButtonBar : View
	
	private lateinit var llSearchTag : View
	private lateinit var btnSearchTag : Button
	private lateinit var llTrendTag : View
	private lateinit var tvTrendTagName : TextView
	private lateinit var tvTrendTagDesc : TextView
	private lateinit var tvTrendTagCount : TextView
	private lateinit var cvTrendTagHistory : TrendTagHistoryView
	
	private lateinit var llList : View
	private lateinit var btnListTL : Button
	private lateinit var btnListMore : ImageButton
	
	private lateinit var llFollowRequest : View
	private lateinit var btnFollowRequestAccept : View
	private lateinit var btnFollowRequestDeny : View
	
	private lateinit var llFilter : View
	private lateinit var tvFilterPhrase : TextView
	private lateinit var tvFilterDetail : TextView
	
	private lateinit var llExtra : LinearLayout
	
	private lateinit var tvApplication : TextView
	
	private lateinit var tvMessageHolder : TextView
	private lateinit var access_info : SavedAccount
	
	private var buttons_for_status : StatusButtons? = null
	
	private var item : TimelineItem? = null
	
	private var status_showing : TootStatus? = null
	private var status_reply : TootStatus? = null
	private var status_account : TootAccountRef? = null
	private var boost_account : TootAccountRef? = null
	private var follow_account : TootAccountRef? = null
	
	private var boost_time : Long = 0L
	
	private val content_color_default : Int
	private var acct_color : Int = 0
	
	private val boost_invalidator : NetworkEmojiInvalidator
	private val reply_invalidator : NetworkEmojiInvalidator
	private val follow_invalidator : NetworkEmojiInvalidator
	private val name_invalidator : NetworkEmojiInvalidator
	private val content_invalidator : NetworkEmojiInvalidator
	private val spoiler_invalidator : NetworkEmojiInvalidator
	private val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()
	
	init {
		this.viewRoot = inflate(activity)
		
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
		
		llBoosted.setOnClickListener(this)
		llBoosted.setOnLongClickListener(this)
		
		llReply.setOnClickListener(this)
		llReply.setOnLongClickListener(this)
		
		llFollow.setOnClickListener(this)
		llFollow.setOnLongClickListener(this)
		
		btnFollow.setOnClickListener(this)
		
		
		btnFollowRequestAccept.setOnClickListener(this)
		btnFollowRequestDeny.setOnClickListener(this)
		
		// ロングタップ
		ivThumbnail.setOnLongClickListener(this)
		
		//
		tvContent.movementMethod = MyLinkMovementMethod
		tvMentions.movementMethod = MyLinkMovementMethod
		tvContentWarning.movementMethod = MyLinkMovementMethod
		
		btnHideMedia.setOnClickListener(this)
		
		llTrendTag.setOnClickListener(this)
		llTrendTag.setOnLongClickListener(this)
		llFilter.setOnClickListener(this)
		
		
		this.content_color_default = tvContent.textColors.defaultColor
		
		if(! activity.timeline_font_size_sp.isNaN()) {
			tvFollowerName.textSize = activity.timeline_font_size_sp
			tvName.textSize = activity.timeline_font_size_sp
			tvMentions.textSize = activity.timeline_font_size_sp
			tvContentWarning.textSize = activity.timeline_font_size_sp
			tvContent.textSize = activity.timeline_font_size_sp
			btnShowMedia.textSize = activity.timeline_font_size_sp
			tvApplication.textSize = activity.timeline_font_size_sp
			tvMessageHolder.textSize = activity.timeline_font_size_sp
			btnListTL.textSize = activity.timeline_font_size_sp
			tvTrendTagName.textSize = activity.timeline_font_size_sp
			tvTrendTagCount.textSize = activity.timeline_font_size_sp
			tvFilterPhrase.textSize = activity.timeline_font_size_sp
		}
		if(! activity.notification_tl_font_size_sp.isNaN()) {
			tvBoosted.textSize = activity.notification_tl_font_size_sp
			tvReply.textSize = activity.notification_tl_font_size_sp
		}
		
		if(! activity.acct_font_size_sp.isNaN()) {
			tvBoostedAcct.textSize = activity.acct_font_size_sp
			tvBoostedTime.textSize = activity.acct_font_size_sp
			tvFollowerAcct.textSize = activity.acct_font_size_sp
			tvAcct.textSize = activity.acct_font_size_sp
			tvTime.textSize = activity.acct_font_size_sp
			tvTrendTagDesc.textSize = activity.acct_font_size_sp
			tvFilterDetail.textSize = activity.acct_font_size_sp
		}
		
		ivThumbnail.layoutParams.height = activity.avatarIconSize
		ivThumbnail.layoutParams.width = activity.avatarIconSize
		ivFollow.layoutParams.width = activity.avatarIconSize
		ivBoosted.layoutParams.width = activity.avatarIconSize
		ivBoosted.layoutParams.height = activity.notificationTlIconSize
		
		this.content_invalidator = NetworkEmojiInvalidator(activity.handler, tvContent)
		this.spoiler_invalidator = NetworkEmojiInvalidator(activity.handler, tvContentWarning)
		this.boost_invalidator = NetworkEmojiInvalidator(activity.handler, tvBoosted)
		this.reply_invalidator = NetworkEmojiInvalidator(activity.handler, tvReply)
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
		
		val font_bold = ActMain.timeline_font_bold
		val font_normal = ActMain.timeline_font
		viewRoot.scan { v ->
			try {
				when(v) {
					// ボタンは太字なので触らない
					is CountImageButton -> {
					}
					// ボタンは太字なので触らない
					is Button -> {
					}
					
					is TextView -> v.typeface = when {
						v === tvName ||
							v === tvFollowerName ||
							v === tvBoosted ||
							v === tvReply ||
							v === tvTrendTagCount ||
							v === tvTrendTagName ||
							v === tvFilterPhrase -> font_bold
						else -> font_normal
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
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
					val popup =
						StatusButtonsPopup(activity, column, bSimpleList, this@ItemViewHolder)
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
				statusButtonsViewHolder,
				this
			)
		}
		
		this.status_showing = null
		this.status_reply = null
		this.status_account = null
		this.boost_account = null
		this.follow_account = null
		this.boost_time = 0L
		this.viewRoot.setBackgroundColor(0)
		this.boostedAction = defaultBoostedAction
		
		llBoosted.visibility = View.GONE
		llReply.visibility = View.GONE
		llFollow.visibility = View.GONE
		llStatus.visibility = View.GONE
		llSearchTag.visibility = View.GONE
		llList.visibility = View.GONE
		llFollowRequest.visibility = View.GONE
		removeExtraView()
		tvMessageHolder.visibility = View.GONE
		llTrendTag.visibility = View.GONE
		llFilter.visibility = View.GONE
		
		var c : Int
		c = if(column.content_color != 0) column.content_color else content_color_default
		tvBoosted.setTextColor(c)
		tvReply.setTextColor(c)
		tvFollowerName.setTextColor(c)
		tvName.setTextColor(c)
		tvMentions.setTextColor(c)
		tvContentWarning.setTextColor(c)
		tvContent.setTextColor(c)
		//NSFWは文字色固定 btnShowMedia.setTextColor( c );
		tvApplication.setTextColor(c)
		tvMessageHolder.setTextColor(c)
		tvTrendTagName.setTextColor(c)
		tvTrendTagCount.setTextColor(c)
		cvTrendTagHistory.setColor(c)
		tvFilterPhrase.setTextColor(c)
		
		c = if(column.acct_color != 0) column.acct_color else Styler.getAttributeColor(
			activity,
			R.attr.colorTimeSmall
		)
		this.acct_color = c
		tvBoostedTime.setTextColor(c)
		tvTime.setTextColor(c)
		tvTrendTagDesc.setTextColor(c)
		//			tvBoostedAcct.setTextColor( c );
		//			tvFollowerAcct.setTextColor( c );
		//			tvAcct.setTextColor( c );
		tvFilterPhrase.setTextColor(c)
		
		this.item = item
		when(item) {
			is TootStatus -> {
				val reblog = item.reblog
				when {
					reblog == null -> showStatusOrReply(item)
					
					item.hasAnyContent() -> {
						// 引用Renote
						showReply(
							reblog,
							R.attr.btn_boost,
							R.string.renote_to
						)
						showStatus(item)
					}
					
					else -> {
						// 引用なしブースト
						showBoost(
							item.accountRef,
							item.time_created_at,
							R.attr.btn_boost,
							R.string.display_name_boosted_by
						)
						showStatusOrReply(item.reblog)
					}
				}
			}
			
			is TootAccountRef -> showAccount(item)
			
			is TootNotification -> showNotification(item)
			
			is TootGap -> showGap()
			is TootDomainBlock -> showDomainBlock(item)
			is TootList -> showList(item)
			
			is TootMessageHolder -> showMessageHolder(item)
			
			// TootTrendTag の後に TootTagを判定すること
			is TootTrendTag -> showTrendTag(item)
			is TootTag -> showSearchTag(item)
			
			is TootFilter -> showFilter(item)
			
			is TootConversationSummary -> {
				showStatus(item.last_status)
				showConversationIcons(item.accounts)
			}
			
			else -> {
			}
		}
	}
	
	private fun removeExtraView() {
		llExtra.scan { v ->
			if(v is MyNetworkImageView) {
				v.cancelLoading()
			}
		}
		llExtra.removeAllViews()
	}
	
	private fun showConversationIcons(accounts : ArrayList<TootAccountRef>) {
		if(accounts.isEmpty()) return
		
		// 絵文字スパンにしてもやはり消えたりちらついたりする。なんでだ。
		//		val density = llExtra.resources.displayMetrics.density
		//		val wh = (activity.avatarIconSize * 0.75f + 0.5f).toInt()
		//		val me = (density * 3f + 0.5f).toInt()
		//		val mt = (density * 3f + 0.5f).toInt()
		//
		//		val lp = LinearLayout.LayoutParams(
		//			LinearLayout.LayoutParams.MATCH_PARENT,
		//			LinearLayout.LayoutParams.WRAP_CONTENT
		//		)
		//		lp.topMargin = mt
		//
		//		val b = MyTextView(activity)
		//		b.layoutParams = lp
		//		b.isAllCaps = false
		//		llExtra.addView(b)
		//
		//		val sb = SpannableStringBuilder()
		//		val invalidator = NetworkEmojiInvalidator(activity.handler, b)
		//		extra_invalidator_list.add(invalidator)
		//		for(ar in accounts) {
		//			val a = ar.get()
		//			val url = access_info.supplyBaseUrl(a.avatar_static)
		//			if(url?.isNotEmpty() == true) {
		//				if(sb.isNotEmpty()) sb.append(' ')
		//				val start = sb.length
		//				sb.append(a.acct)
		//				val end = sb.length
		//				sb.setSpan(
		//					NetworkEmojiSpan(url)
		//					, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		//				)
		//			}
		//		}
		//		b.text = sb
		//		invalidator.register(sb)
		
		// 消えてしまったりちらついたりするので保留
		//
		//		val llIconBar = FlexboxLayout(activity)
		//		val boxLp = LinearLayout.LayoutParams(
		//			LinearLayout.LayoutParams.MATCH_PARENT,
		//			LinearLayout.LayoutParams.WRAP_CONTENT
		//		)
		//		boxLp.topMargin = mt
		//		llIconBar.layoutParams = boxLp
		//		llIconBar.flexWrap = FlexWrap.WRAP
		//		llIconBar.justifyContent = JustifyContent.FLEX_START
		//		llExtra.addView(llIconBar)
		//
		//		for(whoRef in accounts) {
		//			val who = whoRef.get()
		//			val icon = MyNetworkImageView(activity)
		//			val lp = FlexboxLayout.LayoutParams(wh, wh)
		//			lp.marginEnd = me
		//			icon.layoutParams = lp
		//			icon.contentDescription = who.acct
		//			icon.scaleType = ImageView.ScaleType.CENTER_CROP
		//			llIconBar.addView(icon)
		//
		//			// ビュー階層に追加した後にURLをセットする
		//			icon.setImageUrl(
		//				activity.pref,
		//				Styler.calcIconRound(lp),
		//				access_info.supplyBaseUrl(who.avatar_static),
		//				access_info.supplyBaseUrl(who.avatar)
		//			)
		//			icon.setOnClickListener {
		//				val pos = activity.nextPosition(column)
		//				when {
		//					access_info.isMisskey -> Action_User.profileLocal(
		//						activity,
		//						pos,
		//						access_info,
		//						who
		//					)
		//					access_info.isPseudo -> DlgContextMenu(
		//						activity,
		//						column,
		//						whoRef,
		//						null,
		//						null
		//					).show()
		//					else -> Action_User.profileLocal(activity, pos, access_info, who)
		//				}
		//			}
		//
		//		}
		
	}
	
	private fun showStatusOrReply(item : TootStatus) {
		val reply = item.reply
		if(reply != null) {
			// 返信
			showReply(
				reply,
				R.attr.btn_reply,
				R.string.reply_to
			)
			showStatus(item)
		} else {
			showStatus(item)
		}
	}
	
	private fun showTrendTag(item : TootTrendTag) {
		llTrendTag.visibility = View.VISIBLE
		tvTrendTagName.text = "#${item.name}"
		tvTrendTagDesc.text =
			activity.getString(R.string.people_talking, item.accountDaily, item.accountWeekly)
		tvTrendTagCount.text = "${item.countDaily}(${item.countWeekly})"
		cvTrendTagHistory.setHistory(item.history)
	}
	
	private fun showMessageHolder(item : TootMessageHolder) {
		tvMessageHolder.visibility = View.VISIBLE
		tvMessageHolder.text = item.text
		tvMessageHolder.gravity = item.gravity
	}
	
	private fun showNotification(n : TootNotification) {
		val n_status = n.status
		val n_accountRef = n.accountRef
		val n_account = n_accountRef?.get()
		
		fun showNotificationStatus(item : TootStatus) {
			val reblog = item.reblog
			when {
				reblog == null -> showStatusOrReply(item)
				! item.hasAnyContent() -> showStatusOrReply(reblog) // ブースト表示は通知イベントと被るのでしない
				else -> {
					// 引用Renote
					showReply(
						reblog,
						R.attr.btn_boost,
						R.string.renote_to
					)
					showStatus(item)
				}
			}
		}
		
		when(n.type) {
			
			TootNotification.TYPE_FAVOURITE -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					if(access_info.isNicoru(n_account)) R.attr.ic_nicoru else R.attr.btn_favourite,
					R.string.display_name_favourited_by
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_REBLOG -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.btn_boost,
					R.string.display_name_boosted_by
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
				
			}
			
			TootNotification.TYPE_RENOTE -> {
				// 引用のないreblog
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.btn_boost,
					R.string.display_name_boosted_by
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_FOLLOW -> {
				if(n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.attr.ic_follow_plus,
						R.string.display_name_followed_by
					)
					showAccount(n_accountRef)
				}
			}
			
			TootNotification.TYPE_UNFOLLOW -> {
				if(n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.attr.ic_follow_cross,
						R.string.display_name_unfollowed_by
					)
					showAccount(n_accountRef)
				}
			}
			
			TootNotification.TYPE_MENTION, TootNotification.TYPE_REPLY -> {
				if(! bSimpleList && ! access_info.isMisskey) {
					if(n_account != null) showBoost(
						n_accountRef,
						n.time_created_at,
						R.attr.btn_reply,
						R.string.display_name_replied_by
					)
				}
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_REACTION -> {
				val reaction = MisskeyReaction.shortcodeMap[n.reaction ?: ""]
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.ic_question,
					R.string.display_name_reaction_by
					, reaction?.btnDrawableId
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_QUOTE -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.btn_boost,
					R.string.display_name_quoted_by
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_VOTE -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.ic_vote,
					R.string.display_name_voted_by
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
			}
			
			TootNotification.TYPE_FOLLOW_REQUEST -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.ic_follow_wait,
					R.string.display_name_follow_request_by
				)
				boostedAction = {
					activity.addColumn(
						activity.nextPosition(column)
						, access_info
						, Column.TYPE_FOLLOW_REQUESTS
					)
				}
			}
			
			else -> {
				if(n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.attr.ic_question,
					R.string.unknown_notification_from
				)
				if(n_status != null) {
					showNotificationStatus(n_status)
				}
				tvMessageHolder.visibility = View.VISIBLE
				tvMessageHolder.text = "notification type is ${n.type}"
				tvMessageHolder.gravity = Gravity.CENTER
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
	
	private fun showFilter(filter : TootFilter) {
		llFilter.visibility = View.VISIBLE
		tvFilterPhrase.text = filter.phrase
		
		val sb = StringBuffer()
		//
		sb.append(activity.getString(R.string.filter_context))
			.append(": ")
			.append(filter.getContextNames(activity).joinToString("/"))
		//
		val flags = ArrayList<String>()
		if(filter.irreversible) flags.add(activity.getString(R.string.filter_irreversible))
		if(filter.whole_word) flags.add(activity.getString(R.string.filter_word_match))
		if(flags.isNotEmpty()) {
			sb.append('\n')
				.append(flags.joinToString(", "))
		}
		//
		if(filter.time_expires_at != 0L) {
			sb.append('\n')
				.append(activity.getString(R.string.filter_expires_at))
				.append(": ")
				.append(TootStatus.formatTime(activity, filter.time_expires_at, false))
		}
		
		tvFilterDetail.text = sb.toString()
	}
	
	private fun showSearchTag(tag : TootTag) {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = "#" + tag.name
	}
	
	private fun showGap() {
		llSearchTag.visibility = View.VISIBLE
		btnSearchTag.text = activity.getString(R.string.read_gap)
	}
	
	private fun showReply(
		reply : TootStatus,
		iconAttrId : Int,
		stringId : Int
	) {
		status_reply = reply
		
		llReply.visibility = View.VISIBLE
		
		// val who = reply.account
		// showStatusTime(activity, tvReplyTime, who, time = reply.time_created_at)
		// setAcct(tvReplyAcct, access_info.getFullAcct(who), who.acct)
		
		ivReply.setImageResource(Styler.getAttributeResourceId(activity, iconAttrId))
		
		val text = reply.accountRef.decoded_display_name.intoStringResource(activity, stringId)
		tvReply.text = text
		reply_invalidator.register(text)
	}
	
	private fun showBoost(
		whoRef : TootAccountRef,
		time : Long,
		icon_attr_id : Int,
		string_id : Int,
		reactionDrawableId : Int? = null
	) {
		boost_account = whoRef
		val who = whoRef.get()
		
		val text : Spannable = if(string_id == R.string.display_name_followed_by) {
			// フォローの場合 decoded_display_name が2箇所で表示に使われるのを避ける必要がある
			who.decodeDisplayName(activity)
		} else {
			// それ以外の場合は decoded_display_name を再利用して構わない
			whoRef.decoded_display_name
		}.intoStringResource(activity, string_id)
		
		if(reactionDrawableId != null) {
			ivBoosted.setImageResource(reactionDrawableId)
		} else {
			ivBoosted.setImageResource(Styler.getAttributeResourceId(activity, icon_attr_id))
			
		}
		
		boost_time = time
		llBoosted.visibility = View.VISIBLE
		showStatusTime(activity, tvBoostedTime, who, time = time)
		tvBoosted.text = text
		boost_invalidator.register(text)
		setAcct(tvBoostedAcct, access_info.getFullAcct(who), who.acct)
	}
	
	private fun showAccount(whoRef : TootAccountRef) {
		follow_account = whoRef
		val who = whoRef.get()
		llFollow.visibility = View.VISIBLE
		ivFollow.setImageUrl(
			activity.pref,
			Styler.calcIconRound(ivFollow.layoutParams),
			access_info.supplyBaseUrl(who.avatar_static)
		)
		tvFollowerName.text = whoRef.decoded_display_name
		follow_invalidator.register(whoRef.decoded_display_name)
		
		setAcct(tvFollowerAcct, access_info.getFullAcct(who), who.acct)
		
		val relation = UserRelation.load(access_info.db_id, who.id)
		Styler.setFollowIcon(activity, btnFollow, ivFollowedBy, relation, who)
		
		if(column.column_type == Column.TYPE_FOLLOW_REQUESTS) {
			llFollowRequest.visibility = View.VISIBLE
		}
	}
	
	private fun showStatus(status : TootStatus) {
		
		if(status.filtered) {
			showMessageHolder(TootMessageHolder(activity.getString(R.string.filtered)))
			return
		}
		
		this.status_showing = status
		llStatus.visibility = View.VISIBLE
		
		if(status.conversation_main) {
			this.viewRoot.setBackgroundColor(
				(Styler.getAttributeColor(
					activity,
					R.attr.colorImageButtonAccent
				) and 0xffffff) or 0x20000000
			)
		} else {
			val c = when(status.getBackgroundColorType(access_info)) {
				TootVisibility.UnlistedHome -> toot_color_unlisted
				TootVisibility.PrivateFollowers -> toot_color_follower
				TootVisibility.DirectSpecified -> toot_color_direct_user
				TootVisibility.DirectPrivate -> toot_color_direct_me
				else -> 0
			}
			if(c != 0) {
				this.viewRoot.setBackgroundColor(c)
			}
		}
		
		showStatusTime(activity, tvTime, who = status.account, status = status)
		
		val whoRef = status.accountRef
		val who = whoRef.get()
		this.status_account = whoRef
		
		setAcct(tvAcct, access_info.getFullAcct(who), who.acct)
		
		//		if(who == null) {
		//			tvName.text = "?"
		//			name_invalidator.register(null)
		//			ivThumbnail.setImageUrl(activity.pref, 16f, null, null)
		//		} else {
		tvName.text = whoRef.decoded_display_name
		name_invalidator.register(whoRef.decoded_display_name)
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
		if(enquete != null) {
			if(access_info.isMisskey || NicoEnquete.TYPE_ENQUETE == enquete.type) {
				val question = enquete.decoded_question
				val items = enquete.items
				
				if(question.isNotBlank()) content = question
				if(items != null) {
					val now = System.currentTimeMillis()
					var n = 0
					for(item in items) {
						makeEnqueteChoiceView(enquete, now, n ++, item)
					}
				}
				
				if(! access_info.isMisskey) makeEnqueteTimerView(enquete)
			}
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
		
		if(status.time_deleted_at > 0L) {
			val s = SpannableStringBuilder()
				.append('(')
				.append(
					activity.getString(
						R.string.deleted_at,
						TootStatus.formatTime(activity, status.time_deleted_at, true)
					)
				)
				.append(')')
			content = s
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
		
		makeReactionsView(status.reactionCounts)
		
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
	
	private fun showStatusTime(
		activity : ActMain,
		tv : TextView,
		@Suppress("UNUSED_PARAMETER") who : TootAccount,
		status : TootStatus? = null,
		time : Long? = null
	) {
		val sb = SpannableStringBuilder()
		
		//		if(access_info.getFullAcct(who) == "unarist@mstdn.maud.io") {
		//			// if(sb.isNotEmpty()) sb.append(' ')
		//
		//			val start = sb.length
		//			sb.append("unarist")
		//			val end = sb.length
		//			val icon_id = R.drawable.unarist
		//			sb.setSpan(
		//				EmojiImageSpan(activity, icon_id),
		//				start,
		//				end,
		//				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		//			)
		//		}
		
		if(status != null) {
			
			if(status.account.isAdmin) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_shield, "admin")
			}
			
			if(status.account.isPro) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_authorized, "pro")
			}
			
			if(status.account.isCat) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_cat, "cat")
			}
			
			// botマーク
			if(status.account.bot) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_bot, "bot")
			}
			
			// mobileマーク
			if(status.viaMobile) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_mobile, "mobile")
			}
			
			// NSFWマーク
			if(status.hasMedia() && status.sensitive) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				
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
			
			// visibility
			val visIconAttrId =
				Styler.getVisibilityIconAttr(access_info.isMisskey, status.visibility)
			if(R.attr.ic_public != visIconAttrId) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				val start = sb.length
				sb.append(
					Styler.getVisibilityString(
						activity,
						access_info.isMisskey,
						status.visibility
					)
				)
				val end = sb.length
				val iconResId = Styler.getAttributeResourceId(activity, visIconAttrId)
				sb.setSpan(
					EmojiImageSpan(activity, iconResId),
					start,
					end,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}
			
			// pinned
			if(status.pinned) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				sb.appendColorShadeIcon(activity, R.drawable.ic_pin, "pinned")
				
				//				val start = sb.length
				//				sb.append("pinned")
				//				val end = sb.length
				//				val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
				//				sb.setSpan(
				//					EmojiImageSpan(activity, icon_id),
				//					start,
				//					end,
				//					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				//				)
			}
			
			// unread
			if(status.conversationSummary?.unread == true) {
				if(sb.isNotEmpty()) sb.append('\u200B')
				
				val color = if(MyClickableSpan.defaultLinkColor != 0) {
					MyClickableSpan.defaultLinkColor
				} else {
					Styler.getAttributeColor(activity, R.attr.colorLink)
				}
				
				sb.appendColorShadeIcon(
					activity,
					R.drawable.ic_unread,
					"unread",
					color = color
				)
				
				//				val start = sb.length
				//				sb.append("pinned")
				//				val end = sb.length
				//				val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
				//				sb.setSpan(
				//					EmojiImageSpan(activity, icon_id),
				//					start,
				//					end,
				//					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				//				)
			}
		}
		
		if(sb.isNotEmpty()) sb.append(' ')
		sb.append(
			when {
				time != null -> TootStatus.formatTime(
					activity,
					time,
					column.column_type != Column.TYPE_CONVERSATION
				)
				status != null -> TootStatus.formatTime(
					activity,
					status.time_created_at,
					column.column_type != Column.TYPE_CONVERSATION
				)
				else -> "?"
			}
		)
		
		tv.text = sb
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
				
				iv.setFocusPoint(ta.focusX, ta.focusY)
				
				if(Pref.bpDontCropMediaThumb(App1.pref)) {
					iv.scaleType = ImageView.ScaleType.FIT_CENTER
				} else {
					iv.setScaleTypeForMedia()
				}
				
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
	
	private val defaultBoostedAction : () -> Unit = {
		val pos = activity.nextPosition(column)
		val notification = (item as? TootNotification)
		boost_account?.let { whoRef ->
			if(access_info.isPseudo) {
				DlgContextMenu(activity, column, whoRef, null, notification).show()
			} else {
				Action_User.profileLocal(activity, pos, access_info, whoRef.get())
			}
		}
	}
	private var boostedAction : () -> Unit = defaultBoostedAction
	
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
			
			ivThumbnail -> status_account?.let { whoRef ->
				when {
					access_info.isMisskey -> Action_User.profileLocal(
						activity,
						pos,
						access_info,
						whoRef.get()
					)
					access_info.isPseudo -> DlgContextMenu(
						activity,
						column,
						whoRef,
						null,
						notification
					).show()
					else -> Action_User.profileLocal(activity, pos, access_info, whoRef.get())
				}
			}
			
			llBoosted -> boostedAction()
			
			llReply -> {
				status_reply?.let { s ->
					Action_Toot.conversation(activity, pos, access_info, s)
				}
			}
			
			llFollow -> follow_account?.let { whoRef ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, whoRef, null, notification).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, whoRef.get())
				}
			}
			
			btnFollow -> follow_account?.let { who ->
				DlgContextMenu(activity, column, who, null, notification).show()
			}
			
			btnSearchTag, llTrendTag -> when(item) {
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
						activity.addColumn(
							false,
							pos,
							access_info,
							Column.TYPE_LIST_MEMBER,
							item.id
						)
					}
					.addAction(activity.getString(R.string.rename)) {
						Action_List.rename(activity, access_info, item)
					}
					.addAction(activity.getString(R.string.delete)) {
						Action_List.delete(activity, access_info, item)
					}
					.show(activity, item.title)
			}
			
			btnFollowRequestAccept -> follow_account?.let { whoRef ->
				val who = whoRef.get()
				DlgConfirm.openSimple(
					activity,
					activity.getString(
						R.string.follow_accept_confirm,
						AcctColor.getNickname(access_info.getFullAcct(who))
					)
				) {
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, true)
				}
			}
			
			btnFollowRequestDeny -> follow_account?.let { whoRef ->
				val who = whoRef.get()
				DlgConfirm.openSimple(
					activity,
					activity.getString(
						R.string.follow_deny_confirm,
						AcctColor.getNickname(access_info.getFullAcct(who))
					)
				) {
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, false)
				}
			}
			
			llFilter -> if(item is TootFilter) {
				openFilterMenu(item)
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
			
			llReply -> {
				status_reply?.let { s ->
					DlgContextMenu(
						activity,
						column,
						s.accountRef,
						s,
						notification
					).show()
				}
			}
			
			llFollow -> {
				follow_account?.let { whoRef ->
					DlgContextMenu(
						activity,
						column,
						whoRef,
						null,
						notification
					).show()
				}
				return true
			}
			
			btnFollow -> {
				follow_account?.let { whoRef ->
					Action_Follow.followFromAnotherAccount(
						activity,
						activity.nextPosition(column),
						access_info,
						whoRef.get()
					)
				}
				return true
			}
			
			btnSearchTag, llTrendTag -> {
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
						val serviceType = when(access_info.isMisskey) {
							true -> ServiceType.MISSKEY
							else -> ServiceType.MASTODON
						}
						ActMediaViewer.open(activity, serviceType, media_attachments, i)
						
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
			
			val limit = Pref.spCardDescriptionLength.toInt(activity.pref)
			
			sb.append(HTMLDecoder.encodeEntity(ellipsize(description,if( limit <= 0 ) 64 else limit)))
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
	
	private fun ellipsize(src:String,limit:Int):String{
		return if( src.codePointCount(0,src.length) <= limit ) {
			src
		}else {
			"${src.substring(0,  src.offsetByCodePoints(0,limit))}…"
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
	
	private fun makeReactionsView(reactionsCount : HashMap<String, Int>?) {
		
		if(! access_info.isMisskey) return
		
		//		reactionsCount?:return
		//		MisskeyReaction.values().find {
		//			val c = reactionsCount[it.shortcode]
		//			c != null && c > 0
		//		} ?: return
		
		val textColor =
			if(column.content_color != 0) column.content_color else content_color_default
		
		val density = activity.resources.displayMetrics.density
		
		val buttonHeight = ActMain.boostButtonSize
		val marginBetween = (ActMain.boostButtonSize.toFloat() * 0.05f + 0.5f).toInt()
		
		val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
		val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
		val compoundPaddingDp =
			0f // ActMain.boostButtonSize.toFloat() * 0f / activity.resources.displayMetrics.density
		
		val box = FlexboxLayout(activity)
		val boxLp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
		box.layoutParams = boxLp
		boxLp.topMargin = (0.5f + density * 3f).toInt()
		box.flexWrap = FlexWrap.WRAP
		box.justifyContent = JustifyContent.FLEX_START
		
		// +ボタン
		run {
			val b = ImageButton(activity)
			val blp = FlexboxLayout.LayoutParams(
				buttonHeight,
				buttonHeight
			)
			blp.endMargin = marginBetween
			b.layoutParams = blp
			b.background = ContextCompat.getDrawable(
				activity,
				R.drawable.btn_bg_transparent
			)
			b.contentDescription = activity.getString(R.string.reaction_add)
			b.imageResource = Styler.getAttributeResourceId(activity, R.attr.ic_add)
			b.scaleType = ImageView.ScaleType.FIT_CENTER
			b.padding = paddingV
			b.setOnClickListener { addReaction(status_showing, null) }
			box.addView(b)
		}
		var lastButton : View? = null
		for(mr in MisskeyReaction.values()) {
			val count = reactionsCount?.get(mr.shortcode)
			if(count == null || count <= 0) continue
			val b = CountImageButton(activity)
			val blp = FlexboxLayout.LayoutParams(
				FlexboxLayout.LayoutParams.WRAP_CONTENT,
				buttonHeight
			)
			b.minimumWidth = buttonHeight
			
			b.imageResource = mr.btnDrawableId
			b.scaleType = ImageView.ScaleType.FIT_CENTER
			
			b.layoutParams = blp
			blp.endMargin = marginBetween
			b.background = ContextCompat.getDrawable(
				activity,
				R.drawable.btn_bg_transparent
			)
			b.setTextColor(textColor)
			b.setPaddingAndText(
				paddingH, paddingV
				, count.toString()
				, 14f
				, compoundPaddingDp
			)
			b.tag = mr.shortcode
			b.setOnClickListener { addReaction(status_showing, it.tag as? String) }
			box.addView(b)
			lastButton = b
		}
		
		if(lastButton != null) {
			val lp = lastButton.layoutParams
			if(lp is ViewGroup.MarginLayoutParams) {
				lp.endMargin = 0
			}
		}
		
		llExtra.addView(box)
	}
	
	private fun addReaction(status : TootStatus?, code : String?) {
		status ?: return
		
		if(status.myReaction?.isNotEmpty() == true) {
			showToast(activity, false, R.string.already_reactioned)
			return
		}
		
		if(access_info.isPseudo || ! access_info.isMisskey) return
		
		if(code == null) {
			val ad = ActionsDialog()
			for(mr in MisskeyReaction.values()) {
				val newCode = mr.shortcode
				val sb = SpannableStringBuilder()
					.appendDrawableIcon(activity, mr.drawableId, " ")
					.append(' ')
					.append(mr.shortcode)
				ad.addAction(sb) {
					addReaction(status, newCode)
				}
			}
			ad.show(activity)
			return
		}
		
		TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
			object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					val params = access_info.putMisskeyApiToken(JSONObject())
						.put("noteId", status.id.toString())
						.put("reaction", code)
					
					@Suppress("UnnecessaryVariable")
					val result =
						client.request("/api/notes/reactions/create", params.toPostRequestBuilder())
					
					// 成功すると204 no content
					
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
					result ?: return
					
					val error = result.error
					if(error != null) {
						showToast(activity, false, error)
						return
					}
					
					if((result.response?.code() ?: - 1) in 200 until 300) {
						if(status.increaseReaction(code,true,"addReaction")){
							// 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
							list_adapter.notifyChange(reason = "addReaction complete", reset = true)
						}
					}
					
				}
				
			})
	}
	
	private fun makeEnqueteChoiceView(
		enquete : NicoEnquete,
		now : Long,
		i : Int,
		item : NicoEnquete.Choice
	) {
		val canVote = if(access_info.isMisskey) {
			enquete.myVoted == null
		} else {
			val remain = enquete.time_start + NicoEnquete.ENQUETE_EXPIRE - now
			enquete.myVoted == null && remain > 0L
		}
		
		val lp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
		if(i == 0)
			lp.topMargin = (0.5f + llExtra.resources.displayMetrics.density * 3f).toInt()
		val b = Button(activity)
		b.layoutParams = lp
		b.isAllCaps = false
		
		val text = if(access_info.isMisskey) {
			val sb = SpannableStringBuilder()
				.append(item.decoded_text)
			
			if(enquete.myVoted != null) {
				sb.append(" / ")
				sb.append(activity.getString(R.string.vote_count_text, item.votes))
				if(i == enquete.myVoted) sb.append(' ').append(0x2713.toChar())
			}
			sb
		} else {
			item.decoded_text
		}
		b.text = text
		val invalidator = NetworkEmojiInvalidator(activity.handler, b)
		extra_invalidator_list.add(invalidator)
		invalidator.register(text)
		if(! canVote) {
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
		if(enquete.myVoted != null) {
			showToast(context, false, R.string.already_voted)
			return
		}
		if(! accessInfo.isMisskey) {
			val remain = enquete.time_start + NicoEnquete.ENQUETE_EXPIRE - now
			if(remain <= 0L) {
				showToast(context, false, R.string.enquete_was_end)
				return
			}
		}
		
		TootTaskRunner(context).run(accessInfo, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				if(accessInfo.isMisskey) {
					val params = accessInfo.putMisskeyApiToken(JSONObject())
						.put("noteId", enquete.status_id.toString())
						.put("choice", idx)
					return client.request("/api/notes/polls/vote", params.toPostRequestBuilder())
				} else {
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
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return  // cancelled.
				
				val data = result.jsonObject
				if(data != null) {
					if(accessInfo.isMisskey) {
						if( enquete.increaseVote(activity,idx,true) ){
							showToast(context, false, R.string.enquete_voted)

							// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
							list_adapter.notifyChange(reason = "onClickEnqueteChoice", reset = true)
						}
						
						
					} else {
						val message = data.parseString("message") ?: "?"
						val valid = data.optBoolean("valid")
						if(valid) {
							showToast(context, false, R.string.enquete_voted)
						} else {
							showToast(context, true, R.string.enquete_vote_failed, message)
						}
						
					}
				} else {
					showToast(context, true, result.error)
				}
				
			}
		})
	}
	
	private fun openFilterMenu(item : TootFilter) {
		val ad = ActionsDialog()
		ad.addAction(activity.getString(R.string.edit)) {
			ActKeywordFilter.open(activity, access_info, item.id)
		}
		ad.addAction(activity.getString(R.string.delete)) {
			Action_Filter.delete(activity, access_info, item)
		}
		ad.show(activity, activity.getString(R.string.filter_of, item.phrase))
	}
	
	/////////////////////////////////////////////////////////////////////
	
	private fun inflate(activity : ActMain) = with(activity.UI {}) {
		verticalLayout {
			// トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
			layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
			
			
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
						}.lparams(matchParent, wrapContent)
						
						llReply = linearLayout {
							lparams(matchParent, wrapContent) {
								bottomMargin = dip(3)
							}
							
							background =
								ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
							gravity = Gravity.CENTER_VERTICAL
							
							ivReply = imageView {
								scaleType = ImageView.ScaleType.FIT_END
								importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
							}.lparams(dip(32), dip(32)) {
								endMargin = dip(4)
							}
							
							tvReply = textView {
							}.lparams(dip(0), wrapContent) {
								weight = 1f
							}
						}
						
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
							
							val actMain = activity as? ActMain
							val thumbnailHeight = actMain?.app_state?.media_thumb_height ?: dip(64)
							val verticalArrangeThumbnails = Pref.bpVerticalArrangeThumbnails(
								actMain?.pref ?: Pref.pref(context)
							)
							
							flMedia = if(verticalArrangeThumbnails) {
								frameLayout {
									lparams(matchParent, wrapContent) {
										topMargin = dip(3)
									}
									llMedia = verticalLayout {
										lparams(matchParent, matchParent)
										
										btnHideMedia = imageButton {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.btn_bg_transparent
											)
											contentDescription = "@string/hide"
											imageResource =
												Styler.getAttributeResourceId(
													context,
													R.attr.btn_close
												)
										}.lparams(dip(32), dip(32)) {
											gravity = Gravity.END
										}
										
										ivMedia1 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
											scaleType = ImageView.ScaleType.CENTER_CROP
											
										}.lparams(matchParent, thumbnailHeight) {
											topMargin = dip(3)
										}
										
										ivMedia2 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
											scaleType = ImageView.ScaleType.CENTER_CROP
											
										}.lparams(matchParent, thumbnailHeight) {
											topMargin = dip(3)
										}
										
										ivMedia3 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
											scaleType = ImageView.ScaleType.CENTER_CROP
											
										}.lparams(matchParent, thumbnailHeight) {
											topMargin = dip(3)
										}
										
										ivMedia4 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
											scaleType = ImageView.ScaleType.CENTER_CROP
											
										}.lparams(matchParent, thumbnailHeight) {
											topMargin = dip(3)
										}
									}
									
									btnShowMedia = textView {
										
										backgroundColor = Styler.getAttributeColor(
											context,
											R.attr.colorShowMediaBackground
										)
										gravity = Gravity.CENTER_VERTICAL or Gravity.END
										text = context.getString(R.string.tap_to_show)
										textColor =
											Styler.getAttributeColor(
												context,
												R.attr.colorShowMediaText
											)
										endPadding = dip(4)
										minHeightCompat = dip(32)
									}.lparams(matchParent, matchParent)
								}
							} else {
								frameLayout {
									lparams(matchParent, thumbnailHeight) {
										topMargin = dip(3)
									}
									llMedia = linearLayout {
										lparams(matchParent, matchParent)
										
										ivMedia1 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
											scaleType = ImageView.ScaleType.CENTER_CROP
											
										}.lparams(0, matchParent) {
											weight = 1f
										}
										
										ivMedia2 = myNetworkImageView {
											
											background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
											contentDescription =
												context.getString(R.string.thumbnail)
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
											contentDescription =
												context.getString(R.string.thumbnail)
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
											contentDescription =
												context.getString(R.string.thumbnail)
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
												Styler.getAttributeResourceId(
													context,
													R.attr.btn_close
												)
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
											Styler.getAttributeColor(
												context,
												R.attr.colorShowMediaText
											)
										
									}.lparams(matchParent, matchParent)
								}
							}
							
							
							
							
							llExtra = verticalLayout {
								lparams(matchParent, wrapContent) {
									topMargin = dip(0)
								}
							}
						}
						
						// button bar
						statusButtonsViewHolder = StatusButtonsViewHolder(
							activity
							, matchParent
							, 3f
							, justifyContent = when(Pref.ipBoostButtonJustify(App1.pref)) {
								0 -> JustifyContent.FLEX_START
								1 -> JustifyContent.CENTER
								else -> JustifyContent.FLEX_END
							}
						)
						llButtonBar = statusButtonsViewHolder.viewRoot
						addView(llButtonBar)
						
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
			
			llTrendTag = linearLayout {
				lparams(matchParent, wrapContent)
				
				gravity = Gravity.CENTER_VERTICAL
				background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
				
				verticalLayout {
					lparams(0, wrapContent) {
						weight = 1f
					}
					tvTrendTagName = textView {
					}.lparams(matchParent, wrapContent)
					tvTrendTagDesc = textView {
						textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
						textSize = 12f // SP
					}.lparams(matchParent, wrapContent)
				}
				tvTrendTagCount = textView {
				
				}.lparams(wrapContent, wrapContent) {
					startMargin = dip(6)
					endMargin = dip(6)
				}
				
				cvTrendTagHistory = trendTagHistoryView {
				
				}.lparams(dip(64), dip(32))
				
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
			
			tvMessageHolder = textView {
				padding = dip(4)
			}.lparams(matchParent, wrapContent)
			
			llFollowRequest = linearLayout {
				lparams(matchParent, wrapContent) {
					topMargin = dip(6)
				}
				gravity = Gravity.END
				
				btnFollowRequestAccept = imageButton {
					background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
					contentDescription = context.getString(R.string.follow_accept)
					imageResource = Styler.getAttributeResourceId(context, R.attr.ic_check)
					setPadding(0, 0, 0, 0)
				}.lparams(dip(48f), dip(32f))
				
				btnFollowRequestDeny = imageButton {
					background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent)
					contentDescription = context.getString(R.string.follow_deny)
					imageResource = Styler.getAttributeResourceId(context, R.attr.btn_close)
					setPadding(0, 0, 0, 0)
				}.lparams(dip(48f), dip(32f)) {
					startMargin = dip(4)
				}
			}
			
			llFilter = verticalLayout {
				lparams(matchParent, wrapContent) {
				}
				minimumHeight = dip(40)
				
				tvFilterPhrase = textView {
					typeface = Typeface.DEFAULT_BOLD
				}.lparams(matchParent, wrapContent)
				
				tvFilterDetail = textView {
					textColor = Styler.getAttributeColor(context, R.attr.colorTimeSmall)
					textSize = 12f // SP
				}.lparams(matchParent, wrapContent)
			}
		}
	}
	
}


