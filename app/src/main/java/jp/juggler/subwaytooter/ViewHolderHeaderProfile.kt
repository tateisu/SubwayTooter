package jp.juggler.subwaytooter

import android.app.Dialog
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import jp.juggler.emoji.EmojiMap
import jp.juggler.subwaytooter.action.Action_Follow
import jp.juggler.subwaytooter.action.Action_User
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.createSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.util.*
import org.jetbrains.anko.textColor

internal class ViewHolderHeaderProfile(
	activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(activity, viewRoot), View.OnClickListener, View.OnLongClickListener {
	
	private val ivBackground : MyNetworkImageView
	private val tvCreated : TextView
	private val tvLastStatusAt : TextView
	private val ivAvatar : MyNetworkImageView
	private val tvDisplayName : TextView
	private val tvAcct : TextView
	private val btnFollowing : Button
	private val btnFollowers : Button
	private val btnStatusCount : Button
	private val tvNote : TextView
	private val tvMisskeyExtra : TextView
	
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
	
	private val tvPersonalNotes : TextView
	private val btnPersonalNotesEdit : ImageButton
	
	init {
		ivBackground = viewRoot.findViewById(R.id.ivBackground)
		llProfile = viewRoot.findViewById(R.id.llProfile)
		tvCreated = viewRoot.findViewById(R.id.tvCreated)
		tvLastStatusAt = viewRoot.findViewById(R.id.tvLastStatusAt)
		ivAvatar = viewRoot.findViewById(R.id.ivAvatar)
		tvDisplayName = viewRoot.findViewById(R.id.tvDisplayName)
		tvAcct = viewRoot.findViewById(R.id.tvAcct)
		btnFollowing = viewRoot.findViewById(R.id.btnFollowing)
		btnFollowers = viewRoot.findViewById(R.id.btnFollowers)
		btnStatusCount = viewRoot.findViewById(R.id.btnStatusCount)
		tvNote = viewRoot.findViewById(R.id.tvNote)
		tvMisskeyExtra = viewRoot.findViewById(R.id.tvMisskeyExtra)
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
		
		tvPersonalNotes = viewRoot.findViewById(R.id.tvPersonalNotes)
		btnPersonalNotesEdit = viewRoot.findViewById(R.id.btnPersonalNotesEdit)
		
		
		density = tvDisplayName.resources.displayMetrics.density
		
		for(v in arrayOf(
			ivBackground,
			btnFollowing,
			btnFollowers,
			btnStatusCount,
			btnMore,
			btnFollow,
			tvRemoteProfileWarning,
			btnPersonalNotesEdit,
			
			btnMoved,
			llMoved,
			btnPersonalNotesEdit
		)) {
			v.setOnClickListener(this)
		}
		
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
		llProfile.setBackgroundColor(
			when(val c = column.column_bg_color) {
				0 -> activity.getAttributeColor(R.attr.colorProfileBackgroundMask)
				else -> - 0x40000000 or (0x00ffffff and c)
			}
		)
	}
	
	private var contentColor = 0
	
	private var relation : UserRelation? = null
	
	override fun bindData(column : Column) {
		super.bindData(column)
		
		var f : Float
		
		f = activity.timeline_font_size_sp
		if(! f.isNaN()) {
			tvMovedName.textSize = f
			tvMoved.textSize = f
			tvPersonalNotes.textSize = f
		}
		
		f = activity.acct_font_size_sp
		if(! f.isNaN()) {
			tvMovedAcct.textSize = f
			tvCreated.textSize = f
			tvLastStatusAt.textSize = f
		}
		
		val spacing = activity.timeline_spacing
		if(spacing != null) {
			tvMovedName.setLineSpacing(0f, spacing)
			tvMoved.setLineSpacing(0f, spacing)
		}
		
		val contentColor = column.getContentColor()
		this.contentColor = contentColor
		
		tvPersonalNotes.textColor = contentColor
		tvMoved.textColor = contentColor
		tvMovedName.textColor = contentColor
		tvDisplayName.textColor = contentColor
		tvNote.textColor = contentColor
		tvRemoteProfileWarning.textColor = contentColor
		btnStatusCount.textColor = contentColor
		btnFollowing.textColor = contentColor
		btnFollowers.textColor = contentColor
		
		setIconDrawableId(
			activity,
			btnMore,
			R.drawable.ic_more,
			color = contentColor,
			alphaMultiplier = Styler.boost_alpha
		)
		
		setIconDrawableId(
			activity,
			btnPersonalNotesEdit,
			R.drawable.ic_edit,
			color = contentColor,
			alphaMultiplier = Styler.boost_alpha
		)
		
		val acctColor = column.getAcctColor()
		tvCreated.textColor = acctColor
		tvMovedAcct.textColor = acctColor
		tvLastStatusAt.textColor = acctColor
		
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
			relation = null
			tvCreated.text = ""
			tvLastStatusAt.vg(false)
			ivBackground.setImageDrawable(null)
			ivAvatar.setImageDrawable(null)
			
			tvAcct.text = "@"
			
			tvDisplayName.text = ""
			name_invalidator.register(null)
			
			tvNote.text = ""
			tvMisskeyExtra.text = ""
			note_invalidator.register(null)
			
			btnStatusCount.text = activity.getString(R.string.statuses) + "\n" + "?"
			btnFollowing.text = activity.getString(R.string.following) + "\n" + "?"
			btnFollowers.text = activity.getString(R.string.followers) + "\n" + "?"
			
			btnFollow.setImageDrawable(null)
			tvRemoteProfileWarning.visibility = View.GONE
		} else {
			tvCreated.text =
				TootStatus.formatTime(tvCreated.context, (whoDetail ?: who).time_created_at, true)
			
			who.setAccountExtra(
				access_info,
				tvLastStatusAt,
				invalidator = null,
				fromProfileHeader = true
			)
			
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
			
			fun SpannableStringBuilder.appendSpan(text : String, span : Any) {
				val start = length
				append(text)
				setSpan(
					span,
					start,
					length,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}
			
			tvAcct.text = SpannableStringBuilder().apply {
				
				append("@")
				
				append(access_info.getFullAcct(who).pretty)
				
				if(whoDetail?.locked ?: who.locked) {
					append(" ")
					val info = EmojiMap.sShortNameToEmojiInfo["lock"]
					if(info != null) {
						appendSpan("locked", info.er.createSpan(activity))
					} else {
						append("locked")
					}
				}
				
				if(who.bot) {
					append(" ")
					val info = EmojiMap.sShortNameToEmojiInfo["robot_face"]
					if(info != null) {
						appendSpan("bot", info.er.createSpan(activity))
					} else {
						append("bot")
					}
				}
			}
			
			val note = whoRef.decoded_note
			tvNote.text = note
			note_invalidator.register(note)
			
			tvMisskeyExtra.text = SpannableStringBuilder().apply {
				var s = whoDetail?.location
				if(s?.isNotEmpty() == true) {
					if(isNotEmpty()) append('\n')
					appendSpan(
						activity.getString(R.string.location),
						EmojiImageSpan(
							activity,
							R.drawable.ic_location,
							useColorShader = true
						)
					)
					append(' ')
					append(s)
				}
				s = whoDetail?.birthday
				if(s?.isNotEmpty() == true) {
					if(isNotEmpty()) append('\n')
					appendSpan(
						activity.getString(R.string.birthday),
						EmojiImageSpan(
							activity,
							R.drawable.ic_cake,
							useColorShader = true
						)
					)
					append(' ')
					append(s)
				}
			}
			tvMisskeyExtra.vg(tvMisskeyExtra.text.isNotEmpty())
			
			btnStatusCount.text =
				"${activity.getString(R.string.statuses)}\n${
					whoDetail?.statuses_count
						?: who.statuses_count
				}"
			
			if(Pref.bpHideFollowCount(activity.pref)) {
				btnFollowing.text = activity.getString(R.string.following)
				btnFollowers.text = activity.getString(R.string.followers)
			} else {
				btnFollowing.text =
					"${activity.getString(R.string.following)}\n${
						whoDetail?.following_count ?: who.following_count
					}"
				btnFollowers.text =
					"${activity.getString(R.string.followers)}\n${
						whoDetail?.followers_count ?: who.followers_count
					}"
				
			}
			
			val relation = UserRelation.load(access_info.db_id, who.id)
			this.relation = relation
			
			Styler.setFollowIcon(
				activity,
				btnFollow,
				ivFollowedBy,
				relation,
				who,
				contentColor,
				alphaMultiplier = Styler.boost_alpha
			)
			
			tvPersonalNotes.text = relation.note ?: ""
			
			showMoved(who, who.movedRef)
			
			val fields = whoDetail?.fields ?: who.fields
			if(fields != null) {
				
				llFields.visibility = View.VISIBLE
				
				// fieldsのnameにはカスタム絵文字が適用されるようになった
				// https://github.com/tootsuite/mastodon/pull/11350
				// fieldsのvalueはMisskeyならMFM、MastodonならHTML
				val fieldDecodeOptions = DecodeOptions(
					context = activity,
					decodeEmoji = true,
					linkHelper = access_info,
					short = true,
					emojiMapCustom = who.custom_emojis,
					emojiMapProfile = who.profile_emojis,
					mentionDefaultHostDomain = who
				)
				
				val nameTypeface = ActMain.timeline_font_bold
				val valueTypeface = ActMain.timeline_font
				
				for(item in fields) {
					
					//
					val nameView = MyTextView(activity)
					val nameLp = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
					val nameText = fieldDecodeOptions.decodeEmoji(item.name)
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
					
					val valueText = fieldDecodeOptions.decodeHTML(item.value)
					if(item.verified_at > 0L) {
						valueText.append('\n')
						
						val start = valueText.length
						valueText.append(activity.getString(R.string.verified_at))
						valueText.append(": ")
						valueText.append(TootStatus.formatTime(activity, item.verified_at, false))
						val end = valueText.length
						
						val linkFgColor = Pref.ipVerifiedLinkFgColor(activity.pref).notZero()
							?: (Color.BLACK or 0x7fbc99)
						
						valueText.setSpan(
							ForegroundColorSpan(linkFgColor),
							start,
							end,
							Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
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
						val linkBgColor = Pref.ipVerifiedLinkBgColor(activity.pref).notZero()
							?: (0x337fbc99)
						
						valueView.setBackgroundColor(linkBgColor)
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
		
		val caption = who.decodeDisplayName(activity)
			.intoStringResource(activity, R.string.account_moved_to)
		
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
		
		setAcct(tvMovedAcct, access_info, moved)
		
		val relation = UserRelation.load(access_info.db_id, moved.id)
		Styler.setFollowIcon(
			activity,
			btnMoved,
			ivMovedBy,
			relation,
			moved,
			contentColor,
			alphaMultiplier = Styler.boost_alpha
		)
	}
	
	private fun setAcct(tv : TextView, accessInfo : SavedAccount, who : TootAccount) {
		val ac = AcctColor.load(accessInfo, who)
		tv.text = when {
			AcctColor.hasNickname(ac) -> ac.nickname
			Pref.bpShortAcctLocalUser(App1.pref) -> "@${who.acct.pretty}"
			else -> "@${ac.nickname}"
		}
		
		tv.textColor = ac.color_fg.notZero() ?: column.getAcctColor()
		
		tv.setBackgroundColor(ac.color_bg) // may 0
		tv.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)
		
	}
	
	override fun onClick(v : View) {
		
		when(v.id) {
			
			R.id.ivBackground, R.id.tvRemoteProfileWarning ->
				activity.openCustomTab(whoRef?.get()?.url)
			
			R.id.btnFollowing -> {
				column.profile_tab = ProfileTab.Following
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnFollowers -> {
				column.profile_tab = ProfileTab.Followers
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnStatusCount -> {
				column.profile_tab = ProfileTab.Status
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnMore -> whoRef?.let { whoRef ->
				DlgContextMenu(activity, column, whoRef, null, null, null).show()
			}
			
			R.id.btnFollow -> whoRef?.let { whoRef ->
				DlgContextMenu(activity, column, whoRef, null, null, null).show()
			}
			
			R.id.btnMoved -> movedRef?.let { movedRef ->
				DlgContextMenu(activity, column, movedRef, null, null, null).show()
			}
			
			R.id.llMoved -> movedRef?.let { movedRef ->
				if(access_info.isPseudo) {
					DlgContextMenu(activity, column, movedRef, null, null, null).show()
				} else {
					Action_User.profileLocal(
						activity,
						activity.nextPosition(column),
						access_info,
						movedRef.get()
					)
				}
			}
			
			R.id.btnPersonalNotesEdit -> whoRef?.let { whoRef ->
				val who = whoRef.get()
				val relation = this.relation
				val lastColumn = column
				DlgTextInput.show(
					activity,
					AcctColor.getStringWithNickname(activity, R.string.personal_notes_of, who.acct),
					relation?.note ?: "",
					allowEmpty = true,
					callback = object : DlgTextInput.Callback {
						override fun onEmptyError() {
						}
						
						override fun onOK(dialog : Dialog, text : String) {
							TootTaskRunner(activity).run(column.access_info, object : TootTask {
								override fun background(client : TootApiClient) : TootApiResult? {
									
									if(access_info.isPseudo)
										return TootApiResult("Personal notes is not supported on pseudo account.")
									
									if(access_info.isMisskey)
										return TootApiResult("Personal notes is not supported on Misskey account.")
									
									return client.request(
										"/api/v1/accounts/${who.id}/note",
										jsonObject {
											put("comment", text)
										}.toPostRequestBuilder()
									)
								}
								
								override fun handleResult(result : TootApiResult?) {
									if(result == null) return
									if(result.error != null)
										activity.showToast(true, result.error)
									else {
										relation?.note = text
										dialog.dismissSafe()
										if(lastColumn == column) bindData(column)
									}
								}
							})
						}
					}
				)
				
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
	
	override fun getAccount() : TootAccountRef? = whoRef
}
