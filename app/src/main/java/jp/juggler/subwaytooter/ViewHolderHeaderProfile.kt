package jp.juggler.subwaytooter

import android.graphics.Color
import android.support.v4.view.ViewCompat
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import jp.juggler.emoji.EmojiMap201709
import jp.juggler.subwaytooter.action.Action_Follow
import jp.juggler.subwaytooter.action.Action_User
import jp.juggler.subwaytooter.api.MisskeyAccountDetailMap
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.intoStringResource
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.subwaytooter.view.MyTextView
import org.jetbrains.anko.textColor

internal class ViewHolderHeaderProfile(
	activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(activity, viewRoot), View.OnClickListener, View.OnLongClickListener {
	
	private val ivBackground : MyNetworkImageView
	private val tvCreated : TextView
	private val ivAvatar : MyNetworkImageView
	private val tvDisplayName : TextView
	private val tvAcct : TextView
	private val btnFollowing : Button
	private val btnFollowers : Button
	private val btnStatusCount : Button
	private val tvNote : TextView
	private val btnFollow : ImageButton
	private val ivFollowedBy : ImageView
	private val llProfile : View
	private val tvRemoteProfileWarning : TextView
	private val name_invalidator : NetworkEmojiInvalidator
	private val note_invalidator : NetworkEmojiInvalidator
	private val llFields : LinearLayout
	
	private var whoRef : TootAccountRef? = null
	
	private var movedRef : TootAccountRef? = null
	
	private val llMoved : View
	private val tvMoved : TextView
	private val ivMoved : MyNetworkImageView
	private val tvMovedName : TextView
	private val tvMovedAcct : TextView
	private val btnMoved : ImageButton
	private val ivMovedBy : ImageView
	private val moved_caption_invalidator : NetworkEmojiInvalidator
	private val moved_name_invalidator : NetworkEmojiInvalidator
	private val density : Float
	private val btnMore : ImageButton
	
	init {
		ivBackground = viewRoot.findViewById(R.id.ivBackground)
		llProfile = viewRoot.findViewById(R.id.llProfile)
		tvCreated = viewRoot.findViewById(R.id.tvCreated)
		ivAvatar = viewRoot.findViewById(R.id.ivAvatar)
		tvDisplayName = viewRoot.findViewById(R.id.tvDisplayName)
		tvAcct = viewRoot.findViewById(R.id.tvAcct)
		btnFollowing = viewRoot.findViewById(R.id.btnFollowing)
		btnFollowers = viewRoot.findViewById(R.id.btnFollowers)
		btnStatusCount = viewRoot.findViewById(R.id.btnStatusCount)
		tvNote = viewRoot.findViewById(R.id.tvNote)
		btnMore = viewRoot.findViewById(R.id.btnMore)
		btnFollow = viewRoot.findViewById(R.id.btnFollow)
		ivFollowedBy = viewRoot.findViewById(R.id.ivFollowedBy)
		tvRemoteProfileWarning = viewRoot.findViewById(R.id.tvRemoteProfileWarning)
		
		llMoved = viewRoot.findViewById(R.id.llMoved)
		tvMoved = viewRoot.findViewById(R.id.tvMoved)
		ivMoved = viewRoot.findViewById(R.id.ivMoved)
		tvMovedName = viewRoot.findViewById(R.id.tvMovedName)
		tvMovedAcct = viewRoot.findViewById(R.id.tvMovedAcct)
		btnMoved = viewRoot.findViewById(R.id.btnMoved)
		ivMovedBy = viewRoot.findViewById(R.id.ivMovedBy)
		llFields = viewRoot.findViewById(R.id.llFields)
		
		density = tvDisplayName.resources.displayMetrics.density
		
		ivBackground.setOnClickListener(this)
		btnFollowing.setOnClickListener(this)
		btnFollowers.setOnClickListener(this)
		btnStatusCount.setOnClickListener(this)
		btnMore.setOnClickListener(this)
		btnFollow.setOnClickListener(this)
		tvRemoteProfileWarning.setOnClickListener(this)
		
		btnMoved.setOnClickListener(this)
		llMoved.setOnClickListener(this)
		
		btnMoved.setOnLongClickListener(this)
		btnFollow.setOnLongClickListener(this)
		
		tvNote.movementMethod = MyLinkMovementMethod
		
		name_invalidator = NetworkEmojiInvalidator(activity.handler, tvDisplayName)
		note_invalidator = NetworkEmojiInvalidator(activity.handler, tvNote)
		moved_caption_invalidator = NetworkEmojiInvalidator(activity.handler, tvMoved)
		moved_name_invalidator = NetworkEmojiInvalidator(activity.handler, tvMovedName)
		
		ivBackground.measureProfileBg = true
	}
	
	override fun showColor() {
		var c = column.column_bg_color
		c = if(c == 0) {
			Styler.getAttributeColor(activity, R.attr.colorProfileBackgroundMask)
		} else {
			- 0x40000000 or (0x00ffffff and c)
		}
		llProfile.setBackgroundColor(c)
	}
	
	private var contentColor = 0
	
	override fun bindData(column : Column) {
		super.bindData(column)
		
		var f : Float
		
		f = activity.timeline_font_size_sp
		if(! f.isNaN()) {
			tvMovedName.textSize = f
			tvMoved.textSize = f
		}
		
		f = activity.acct_font_size_sp
		if(! f.isNaN()) {
			tvMovedAcct.textSize = f
			tvCreated.textSize = f
		}
		
		val contentColor = column.getContentColor(activity)
		this.contentColor = contentColor
		
		tvMoved.textColor = contentColor
		tvMovedName.textColor = contentColor
		tvDisplayName.textColor = contentColor
		tvNote.textColor = contentColor
		tvRemoteProfileWarning.textColor = contentColor
		btnStatusCount.textColor = contentColor
		btnFollowing.textColor = contentColor
		btnFollowers.textColor = contentColor
		Styler.setIconDrawableId(activity, btnMore, R.drawable.btn_more, color = contentColor)
		
		val acctColor = column.getAcctColor(activity)
		tvCreated.textColor = acctColor
		tvMovedAcct.textColor = acctColor
		
		val whoRef = column.who_account
		this.whoRef = whoRef
		val who = whoRef?.get()
		
		// Misskeyの場合はNote中のUserエンティティと /api/users/show の情報量がかなり異なる
		val whoDetail = if(who == null) {
			null
		} else {
			MisskeyAccountDetailMap.get(access_info, who.id)
		}
		
		showColor()
		
		llMoved.visibility = View.GONE
		tvMoved.visibility = View.GONE
		llFields.visibility = View.GONE
		llFields.removeAllViews()
		
		if(who == null) {
			tvCreated.text = ""
			ivBackground.setImageDrawable(null)
			ivAvatar.setImageDrawable(null)
			
			tvAcct.text = "@"
			
			tvDisplayName.text = ""
			name_invalidator.register(null)
			
			tvNote.text = ""
			note_invalidator.register(null)
			
			btnStatusCount.text = activity.getString(R.string.statuses) + "\n" + "?"
			btnFollowing.text = activity.getString(R.string.following) + "\n" + "?"
			btnFollowers.text = activity.getString(R.string.followers) + "\n" + "?"
			
			btnFollow.setImageDrawable(null)
			tvRemoteProfileWarning.visibility = View.GONE
		} else {
			tvCreated.text =
				TootStatus.formatTime(tvCreated.context, (whoDetail ?: who).time_created_at, true)
			ivBackground.setImageUrl(
				activity.pref,
				0f,
				access_info.supplyBaseUrl(who.header_static)
			)
			
			ivAvatar.setImageUrl(
				activity.pref,
				Styler.calcIconRound(ivAvatar.layoutParams),
				access_info.supplyBaseUrl(who.avatar_static),
				access_info.supplyBaseUrl(who.avatar)
			)
			
			val name = whoDetail?.decodeDisplayName(activity) ?: whoRef.decoded_display_name
			tvDisplayName.text = name
			name_invalidator.register(name)
			
			tvRemoteProfileWarning.visibility =
				if(column.access_info.isRemoteUser(who)) View.VISIBLE else View.GONE
			
			val sb = SpannableStringBuilder()
			sb.append("@").append(access_info.getFullAcct(who))
			if(whoDetail?.locked ?: who.locked) {
				sb.append(" ")
				val start = sb.length
				sb.append("locked")
				val end = sb.length
				val info = EmojiMap201709.sShortNameToImageId["lock"]
				if(info != null) {
					sb.setSpan(
						EmojiImageSpan(activity, info.image_id),
						start,
						end,
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}
			if(who.bot) {
				sb.append(" ")
				val start = sb.length
				sb.append("bot")
				val end = sb.length
				val info = EmojiMap201709.sShortNameToImageId["robot_face"]
				if(info != null) {
					sb.setSpan(
						EmojiImageSpan(activity, info.image_id),
						start,
						end,
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}
			tvAcct.text = sb
			
			val note = whoRef.decoded_note
			tvNote.text = note
			note_invalidator.register(note)
			
			btnStatusCount.text = activity.getString(R.string.statuses) + "\n" +
				(whoDetail?.statuses_count ?: who.statuses_count)
			btnFollowing.text = activity.getString(R.string.following) + "\n" +
				(whoDetail?.following_count ?: who.following_count)
			btnFollowers.text = activity.getString(R.string.followers) + "\n" +
				(whoDetail?.followers_count ?: who.followers_count)
			
			val relation = UserRelation.load(access_info.db_id, who.id)
			Styler.setFollowIcon(activity, btnFollow, ivFollowedBy, relation, who, contentColor)
			
			showMoved(who, who.movedRef)
			
			if(who.fields != null) {
				
				llFields.visibility = View.VISIBLE
				
				val decodeOptions = DecodeOptions(
					context = activity,
					decodeEmoji = true,
					linkHelper = access_info,
					short = true,
					emojiMapCustom = who.custom_emojis,
					emojiMapProfile = who.profile_emojis
				)
				
				// fieldsのnameにはカスタム絵文字が適用されない
				val decodeOptionsNoCustomEmoji = DecodeOptions(
					context = activity,
					decodeEmoji = true,
					linkHelper = access_info,
					short = true,
					emojiMapProfile = who.profile_emojis
				)
				
				
				
				val nameTypeface = ActMain.timeline_font_bold
				val valueTypeface = ActMain.timeline_font
				
				for(item in who.fields) {
					
					//
					val nameView = MyTextView(activity)
					val nameLp = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
					val nameText = decodeOptionsNoCustomEmoji.decodeEmoji(item.name)
					val nameInvalidator = NetworkEmojiInvalidator(activity.handler, nameView)
					nameInvalidator.register(nameText)
					
					nameLp.topMargin = (density * 6f).toInt()
					nameView.layoutParams = nameLp
					nameView.text = nameText
					nameView.setTextColor(contentColor)
					nameView.typeface = nameTypeface
					nameView.movementMethod = MyLinkMovementMethod
					llFields.addView(nameView)
					
					// 値の方はHTMLエンコードされている
					val valueView = MyTextView(activity)
					val valueLp = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
					
					val valueText = decodeOptions.decodeHTML(item.value)
					if(item.verified_at > 0L) {
						valueText.append('\n')
						
						val start = valueText.length
						valueText.append(activity.getString(R.string.verified_at))
						valueText.append(": ")
						valueText.append(TootStatus.formatTime(activity, item.verified_at, false))
						val end = valueText.length
						
						valueText.setSpan(
							ForegroundColorSpan(Color.BLACK or 0x7fbc99)
							, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						)
					}
					
					val valueInvalidator = NetworkEmojiInvalidator(activity.handler, valueView)
					valueInvalidator.register(valueText)
					
					valueLp.startMargin = (density * 32f).toInt()
					valueView.layoutParams = valueLp
					valueView.text = valueText
					valueView.setTextColor(contentColor)
					valueView.typeface = valueTypeface
					valueView.movementMethod = MyLinkMovementMethod
					
					if(item.verified_at > 0L) {
						valueView.setBackgroundColor(0x337fbc99)
					}
					
					llFields.addView(valueView)
					
				}
			}
		}
	}
	
	private fun showMoved(who : TootAccount, movedRef : TootAccountRef?) {
		if(movedRef == null) return
		this.movedRef = movedRef
		val moved = movedRef.get()
		
		llMoved.visibility = View.VISIBLE
		tvMoved.visibility = View.VISIBLE
		
		val caption = who.decodeDisplayName(activity).intoStringResource(
			activity,
			R.string.account_moved_to
		)
		
		tvMoved.text = caption
		moved_caption_invalidator.register(caption)
		
		ivMoved.layoutParams.width = activity.avatarIconSize
		ivMoved.setImageUrl(
			activity.pref,
			Styler.calcIconRound(ivMoved.layoutParams),
			access_info.supplyBaseUrl(moved.avatar_static)
		)
		
		tvMovedName.text = movedRef.decoded_display_name
		moved_name_invalidator.register(movedRef.decoded_display_name)
		
		setAcct(tvMovedAcct, access_info.getFullAcct(moved), moved.acct)
		
		val relation = UserRelation.load(access_info.db_id, moved.id)
		Styler.setFollowIcon(activity, btnMoved, ivMovedBy, relation, moved, contentColor)
	}
	
	private fun setAcct(tv : TextView, acctLong : String, acctShort : String) {
		val ac = AcctColor.load(acctLong)
		tv.text = when {
			AcctColor.hasNickname(ac) -> ac.nickname
			Pref.bpShortAcctLocalUser(App1.pref) -> "@$acctShort"
			else -> acctLong
		}
		
		val acct_color = when {
			column.acct_color != 0 -> column.acct_color
			else -> Styler.getAttributeColor(activity, R.attr.colorTimeSmall)
		}
		tv.setTextColor(if(AcctColor.hasColorForeground(ac)) ac.color_fg else acct_color)
		
		if(AcctColor.hasColorBackground(ac)) {
			tv.setBackgroundColor(ac.color_bg)
		} else {
			ViewCompat.setBackground(tv, null)
		}
		tv.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)
		
	}
	
	override fun onClick(v : View) {
		
		when(v.id) {
			
			R.id.ivBackground, R.id.tvRemoteProfileWarning -> whoRef?.get()?.url?.let { url ->
				App1.openCustomTab(activity, url)
			}
			
			R.id.btnFollowing -> {
				column.profile_tab = Column.TAB_FOLLOWING
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnFollowers -> {
				column.profile_tab = Column.TAB_FOLLOWERS
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnStatusCount -> {
				column.profile_tab = Column.TAB_STATUS
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnMore -> whoRef?.let { whoRef ->
				DlgContextMenu(activity, column, whoRef, null, null).show()
			}
			
			R.id.btnFollow -> whoRef?.let { whoRef ->
				DlgContextMenu(activity, column, whoRef, null, null).show()
			}
			
			R.id.btnMoved -> movedRef?.let { movedRef ->
				DlgContextMenu(activity, column, movedRef, null, null).show()
			}
			
			R.id.llMoved -> movedRef?.let { movedRef ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, movedRef, null, null).show()
				} else {
					Action_User.profileLocal(
						activity,
						activity.nextPosition(column),
						access_info,
						movedRef.get()
					)
				}
			}
		}
	}
	
	override fun onLongClick(v : View) : Boolean {
		when(v.id) {
			
			R.id.btnFollow -> {
				Action_Follow.followFromAnotherAccount(
					activity,
					activity.nextPosition(column),
					access_info,
					whoRef?.get()
				)
				return true
			}
			
			R.id.btnMoved -> {
				Action_Follow.followFromAnotherAccount(
					activity,
					activity.nextPosition(column),
					access_info,
					movedRef?.get()
				)
				return true
			}
		}
		
		return false
	}
	
	override fun onViewRecycled() {
	}
	
	//	fun updateRelativeTime() {
	//		val who = whoRef?.get()
	//		if(who != null) {
	//			tvCreated.text = TootStatus.formatTime(tvCreated.context, who.time_created_at, true)
	//		}
	//	}
	
}