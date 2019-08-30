package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.DlgQRCode
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.backgroundDrawable
import java.util.*

@SuppressLint("InflateParams")
internal class DlgContextMenu(
	val activity : ActMain,
	private val column : Column,
	private val whoRef : TootAccountRef?,
	private val status : TootStatus?,
	private val notification : TootNotification? = null,
	private val contentTextView : TextView? = null
) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		private val log = LogCategory("DlgContextMenu")
	}
	
	private val access_info : SavedAccount
	private val relation : UserRelation
	
	private val dialog : Dialog
	
	init {
		this.access_info = column.access_info
		
		val columnType = column.type
		
		val who = whoRef?.get()
		val status = this.status
		
		this.relation = when {
			who == null -> UserRelation()
			access_info.isPseudo -> UserRelation.loadPseudo(access_info.getFullAcct(who))
			else -> UserRelation.load(access_info.db_id, who.id)
		}
		
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_context_menu, null, false)
		this.dialog = Dialog(activity)
		dialog.setContentView(viewRoot)
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(true)
		
		val llStatus : View = viewRoot.findViewById(R.id.llStatus)
		val btnStatusWebPage : View = viewRoot.findViewById(R.id.btnStatusWebPage)
		val btnText : View = viewRoot.findViewById(R.id.btnText)
		val btnFavouriteAnotherAccount : View =
			viewRoot.findViewById(R.id.btnFavouriteAnotherAccount)
		val btnBoostAnotherAccount : View = viewRoot.findViewById(R.id.btnBoostAnotherAccount)
		val btnReactionAnotherAccount : View = viewRoot.findViewById(R.id.btnReactionAnotherAccount)
		val btnReplyAnotherAccount : View = viewRoot.findViewById(R.id.btnReplyAnotherAccount)
		val btnQuotedRenote : View = viewRoot.findViewById(R.id.btnQuotedRenote)
		val btnDelete : View = viewRoot.findViewById(R.id.btnDelete)
		val btnRedraft : View = viewRoot.findViewById(R.id.btnRedraft)
		
		val btnReport : View = viewRoot.findViewById(R.id.btnReport)
		val btnMuteApp : Button = viewRoot.findViewById(R.id.btnMuteApp)
		val llAccountActionBar : View = viewRoot.findViewById(R.id.llAccountActionBar)
		val btnFollow : ImageButton = viewRoot.findViewById(R.id.btnFollow)
		
		val btnMute : ImageView = viewRoot.findViewById(R.id.btnMute)
		val btnBlock : ImageView = viewRoot.findViewById(R.id.btnBlock)
		val btnProfile : View = viewRoot.findViewById(R.id.btnProfile)
		val btnSendMessage : View = viewRoot.findViewById(R.id.btnSendMessage)
		val btnAccountWebPage : View = viewRoot.findViewById(R.id.btnAccountWebPage)
		val btnFollowRequestOK : View = viewRoot.findViewById(R.id.btnFollowRequestOK)
		val btnFollowRequestNG : View = viewRoot.findViewById(R.id.btnFollowRequestNG)
		val btnDeleteSuggestion : View = viewRoot.findViewById(R.id.btnDeleteSuggestion)
		val btnFollowFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnFollowFromAnotherAccount)
		val btnSendMessageFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnSendMessageFromAnotherAccount)
		val btnOpenProfileFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnOpenProfileFromAnotherAccount)
		val btnDomainBlock : Button = viewRoot.findViewById(R.id.btnDomainBlock)
		val btnInstanceInformation : Button = viewRoot.findViewById(R.id.btnInstanceInformation)
		val btnProfileDirectory  : Button = viewRoot.findViewById(R.id.btnProfileDirectory)
		val ivFollowedBy : ImageView = viewRoot.findViewById(R.id.ivFollowedBy)
		val btnOpenTimeline : Button = viewRoot.findViewById(R.id.btnOpenTimeline)
		val btnConversationAnotherAccount : View =
			viewRoot.findViewById(R.id.btnConversationAnotherAccount)
		val btnAvatarImage : View = viewRoot.findViewById(R.id.btnAvatarImage)
		
		val llNotification : View = viewRoot.findViewById(R.id.llNotification)
		val btnNotificationDelete : View = viewRoot.findViewById(R.id.btnNotificationDelete)
		val btnConversationMute : Button = viewRoot.findViewById(R.id.btnConversationMute)
		
		val btnHideBoost : View = viewRoot.findViewById(R.id.btnHideBoost)
		val btnShowBoost : View = viewRoot.findViewById(R.id.btnShowBoost)
		val btnHideFavourite : View = viewRoot.findViewById(R.id.btnHideFavourite)
		val btnShowFavourite : View = viewRoot.findViewById(R.id.btnShowFavourite)
		
		val btnListMemberAddRemove : View = viewRoot.findViewById(R.id.btnListMemberAddRemove)
		val btnEndorse : Button = viewRoot.findViewById(R.id.btnEndorse)
		
		val btnAroundAccountTL : View = viewRoot.findViewById(R.id.btnAroundAccountTL)
		val btnAroundLTL : View = viewRoot.findViewById(R.id.btnAroundLTL)
		val btnAroundFTL : View = viewRoot.findViewById(R.id.btnAroundFTL)
		val btnCopyAccountId : Button = viewRoot.findViewById(R.id.btnCopyAccountId)
		val btnOpenAccountInAdminWebUi : Button =
			viewRoot.findViewById(R.id.btnOpenAccountInAdminWebUi)
		val btnOpenInstanceInAdminWebUi : Button =
			viewRoot.findViewById(R.id.btnOpenInstanceInAdminWebUi)
		val btnBoostWithVisibility : Button = viewRoot.findViewById(R.id.btnBoostWithVisibility)
		val llLinks : LinearLayout = viewRoot.findViewById(R.id.llLinks)
		
		val btnNotificationFrom : Button = viewRoot.findViewById(R.id.btnNotificationFrom)
		
		btnNotificationFrom.setOnClickListener(this)
		
		btnAroundAccountTL.setOnClickListener(this)
		btnAroundLTL.setOnClickListener(this)
		btnAroundFTL.setOnClickListener(this)
		
		btnStatusWebPage.setOnClickListener(this)
		btnText.setOnClickListener(this)
		btnFavouriteAnotherAccount.setOnClickListener(this)
		btnBoostAnotherAccount.setOnClickListener(this)
		btnReactionAnotherAccount.setOnClickListener(this)
		btnReplyAnotherAccount.setOnClickListener(this)
		btnQuotedRenote.setOnClickListener(this)
		btnReport.setOnClickListener(this)
		btnMuteApp.setOnClickListener(this)
		btnDelete.setOnClickListener(this)
		btnRedraft.setOnClickListener(this)
		btnFollow.setOnClickListener(this)
		btnMute.setOnClickListener(this)
		btnBlock.setOnClickListener(this)
		btnFollow.setOnLongClickListener(this)
		btnProfile.setOnClickListener(this)
		btnSendMessage.setOnClickListener(this)
		btnAccountWebPage.setOnClickListener(this)
		btnFollowRequestOK.setOnClickListener(this)
		btnFollowRequestNG.setOnClickListener(this)
		btnDeleteSuggestion.setOnClickListener(this)
		btnFollowFromAnotherAccount.setOnClickListener(this)
		btnSendMessageFromAnotherAccount.setOnClickListener(this)
		btnOpenProfileFromAnotherAccount.setOnClickListener(this)
		btnOpenTimeline.setOnClickListener(this)
		btnConversationAnotherAccount.setOnClickListener(this)
		btnAvatarImage.setOnClickListener(this)
		btnNotificationDelete.setOnClickListener(this)
		btnConversationMute.setOnClickListener(this)
		btnHideBoost.setOnClickListener(this)
		btnShowBoost.setOnClickListener(this)
		btnHideFavourite.setOnClickListener(this)
		btnShowFavourite.setOnClickListener(this)
		btnListMemberAddRemove.setOnClickListener(this)
		btnInstanceInformation.setOnClickListener(this)
		btnProfileDirectory.setOnClickListener(this)
		btnDomainBlock.setOnClickListener(this)
		btnEndorse.setOnClickListener(this)
		btnCopyAccountId.setOnClickListener(this)
		btnOpenAccountInAdminWebUi.setOnClickListener(this)
		btnOpenInstanceInAdminWebUi.setOnClickListener(this)
		btnBoostWithVisibility.setOnClickListener(this)
		
		viewRoot.findViewById<View>(R.id.btnQuoteUrlStatus).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnTranslate).setOnClickListener(this)
		
		viewRoot.findViewById<View>(R.id.btnQuoteUrlAccount).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnShareUrlStatus).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnShareUrlAccount).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnQuoteName).setOnClickListener(this)
		
		val account_list = SavedAccount.loadAccountList(activity)
		//	final ArrayList< SavedAccount > account_list_non_pseudo_same_instance = new ArrayList<>();
		
		val account_list_non_pseudo = ArrayList<SavedAccount>()
		for(a in account_list) {
			if(! a.isPseudo) {
				account_list_non_pseudo.add(a)
				//				if( a.host.equalsIgnoreCase( access_info.host ) ){
				//					account_list_non_pseudo_same_instance.add( a );
				//				}
			}
		}
		
		if(status == null) {
			llStatus.visibility = View.GONE
			llLinks.visibility = View.GONE
		} else {
			val status_by_me = access_info.isMe(status.account)
			
			if(Pref.bpLinksInContextMenu(activity.pref) && contentTextView != null) {
				
				var insPos = 0
				
				fun addLinkButton(span : MyClickableSpan, caption : String) {
					val b = Button(activity)
					val lp = LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT
					)
					b.layoutParams = lp
					b.backgroundDrawable =
						ContextCompat.getDrawable(activity, R.drawable.btn_bg_transparent)
					b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
					b.minHeight = (activity.density * 32f + 0.5f).toInt()
					b.minimumHeight = (activity.density * 32f + 0.5f).toInt()
					val pad_lr = (activity.density * 8f + 0.5f).toInt()
					val pad_tb = (activity.density * 4f + 0.5f).toInt()
					b.setPaddingRelative(pad_lr, pad_tb, pad_lr, pad_tb)
					b.text = caption
					b.allCaps = false
					b.setOnClickListener {
						dialog.dismissSafe()
						span.onClick(contentTextView)
					}
					llLinks.addView(b, insPos ++)
				}
				
				val dc = status.decoded_content
				for(tag in dc.getSpans(0, dc.length, MyClickableSpan::class.java)) {
					val start = dc.getSpanStart(tag)
					val end = dc.getSpanEnd(tag)
					val href = tag?.url ?: continue
					val caption = dc.substring(start, end)
					val head = caption[0]
					if(head == '@' || head == '#')
						addLinkButton(tag, caption)
					else
						addLinkButton(tag, href)
				}
			}
			vg(llLinks, llLinks.childCount > 1)
			
			btnDelete.visibility = if(status_by_me) View.VISIBLE else View.GONE
			btnRedraft.visibility = if(status_by_me) View.VISIBLE else View.GONE
			
			vg(btnBoostWithVisibility, ! access_info.isPseudo && ! access_info.isMisskey)
			
			btnReport.visibility =
				if(status_by_me || access_info.isPseudo) View.GONE else View.VISIBLE
			
			val application_name = status.application?.name
			if(status_by_me || application_name == null || application_name.isEmpty()) {
				btnMuteApp.visibility = View.GONE
			} else {
				btnMuteApp.text = activity.getString(R.string.mute_app_of, application_name)
			}
			
			val btnBoostedBy = viewRoot.findViewById<View>(R.id.btnBoostedBy)
			val btnFavouritedBy = viewRoot.findViewById<View>(R.id.btnFavouritedBy)
			btnBoostedBy.setOnClickListener(this)
			btnFavouritedBy.setOnClickListener(this)
			val isNA = access_info.isNA
			btnBoostedBy.visibility = if(isNA) View.GONE else View.VISIBLE
			btnFavouritedBy.visibility = if(isNA) View.GONE else View.VISIBLE
			
			val btnProfilePin = viewRoot.findViewById<View>(R.id.btnProfilePin)
			val btnProfileUnpin = viewRoot.findViewById<View>(R.id.btnProfileUnpin)
			btnProfilePin.setOnClickListener(this)
			btnProfileUnpin.setOnClickListener(this)
			val canPin = status.canPin(access_info)
			btnProfileUnpin.visibility = if(canPin && status.pinned) View.VISIBLE else View.GONE
			btnProfilePin.visibility = if(canPin && ! status.pinned) View.VISIBLE else View.GONE
		}
		
		var bShowConversationMute = false
		if(status != null) {
			if(access_info.isMe(status.account)) {
				bShowConversationMute = true
			} else if(notification != null && TootNotification.TYPE_MENTION == notification.type) {
				bShowConversationMute = true
			}
		}
		
		if(! bShowConversationMute) {
			btnConversationMute.visibility = View.GONE
		} else {
			val muted = status?.muted ?: false
			btnConversationMute.setText(if(muted) R.string.unmute_this_conversation else R.string.mute_this_conversation)
		}
		
		llNotification.visibility = if(notification == null) View.GONE else View.VISIBLE
		
		
		fun showRelation(relation : UserRelation) {
			// 被フォロー状態
			// Styler.setFollowIconとは異なり細かい状態を表示しない
			vg(ivFollowedBy, relation.followed_by)
			
			// フォロー状態
			// Styler.setFollowIconとは異なりミュートやブロックを表示しない
			btnFollow.setImageResource(
				when {
					relation.getRequested(who) -> R.drawable.ic_follow_wait
					relation.getFollowing(who) -> R.drawable.ic_follow_cross
					else -> R.drawable.ic_follow_plus
				}
			)
			btnFollow.imageTintList = ColorStateList.valueOf(
				getAttributeColor(
					activity,
					when {
						relation.getRequested(who) -> R.attr.colorRegexFilterError
						relation.getFollowing(who) -> R.attr.colorImageButtonAccent
						else -> R.attr.colorImageButton
					}
				)
			)
			
			// ミュート状態
			btnMute.imageTintList = ColorStateList.valueOf(
				getAttributeColor(
					activity,
					when(relation.muting) {
						true -> R.attr.colorImageButtonAccent
						else -> R.attr.colorImageButton
					}
				)
			)
			
			// ブロック状態
			btnBlock.imageTintList = ColorStateList.valueOf(
				getAttributeColor(
					activity,
					when(relation.blocking) {
						true -> R.attr.colorImageButtonAccent
						else -> R.attr.colorImageButton
					}
				)
			)
		}
		
		if(access_info.isPseudo) {
			// 議事アカミュートができたのでアカウントアクションを表示する
			showRelation(UserRelation())
			llAccountActionBar.visibility = View.VISIBLE
			vg(ivFollowedBy, false)
			btnFollow.setImageResource(R.drawable.ic_follow_plus)
			btnFollow.imageTintList =
				ColorStateList.valueOf(getAttributeColor(activity, R.attr.colorImageButton))
			
			
			
			btnNotificationFrom.visibility = View.GONE
		} else {
			showRelation(relation)
		}
		
		if(who == null) {
			btnInstanceInformation.visibility = View.GONE
			btnProfileDirectory.visibility = View.GONE
			btnDomainBlock.visibility = View.GONE
			btnCopyAccountId.visibility = View.GONE
			btnOpenAccountInAdminWebUi.visibility = View.GONE
			btnOpenInstanceInAdminWebUi.visibility = View.GONE
		} else {
			val who_host = getUserHost()
			btnInstanceInformation.visibility = View.VISIBLE
			btnInstanceInformation.text =
				activity.getString(R.string.instance_information_of, who_host)

			btnProfileDirectory.visibility = View.VISIBLE
			btnProfileDirectory.text =
				activity.getString(R.string.profile_directory_of, who_host)
			
			if(access_info.isPseudo || access_info.host.equals(who_host, ignoreCase = true)) {
				// 疑似アカウントではドメインブロックできない
				// 自ドメインはブロックできない
				btnDomainBlock.visibility = View.GONE
			} else {
				btnDomainBlock.visibility = View.VISIBLE
				btnDomainBlock.text = activity.getString(R.string.block_domain_that, who_host)
			}
			
			btnCopyAccountId.visibility = View.VISIBLE
			btnCopyAccountId.text = activity.getString(R.string.copy_account_id, who.id.toString())
			
			vg(btnOpenAccountInAdminWebUi, ! access_info.isPseudo)
			vg(btnOpenInstanceInAdminWebUi, ! access_info.isPseudo)
		}
		
		viewRoot.findViewById<View>(R.id.btnAccountText).setOnClickListener(this)
		
		if(access_info.isPseudo) {
			btnProfile.visibility = View.GONE
			btnSendMessage.visibility = View.GONE
			btnEndorse.visibility = View.GONE
		}
		
		btnEndorse.text = when(relation.endorsed) {
			false -> activity.getString(R.string.endorse_set)
			else -> activity.getString(R.string.endorse_unset)
		}
		
		if(columnType != ColumnType.FOLLOW_REQUESTS) {
			btnFollowRequestOK.visibility = View.GONE
			btnFollowRequestNG.visibility = View.GONE
		}
		
		if(columnType != ColumnType.FOLLOW_SUGGESTION) {
			btnDeleteSuggestion.visibility = View.GONE
		}
		
		if(account_list_non_pseudo.isEmpty()) {
			btnFollowFromAnotherAccount.visibility = View.GONE
			btnSendMessageFromAnotherAccount.visibility = View.GONE
		}
		
		viewRoot.findViewById<View>(R.id.btnNickname).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnAccountQrCode).setOnClickListener(this)
		
		if(access_info.isPseudo
			|| who == null
			|| ! relation.getFollowing(who)
			|| relation.following_reblogs == UserRelation.REBLOG_UNKNOWN) {
			btnHideBoost.visibility = View.GONE
			btnShowBoost.visibility = View.GONE
		} else if(relation.following_reblogs == UserRelation.REBLOG_SHOW) {
			btnHideBoost.visibility = View.VISIBLE
			btnShowBoost.visibility = View.GONE
		} else {
			btnHideBoost.visibility = View.GONE
			btnShowBoost.visibility = View.VISIBLE
		}
		
		when {
			who == null -> {
				btnHideFavourite.visibility = View.GONE
				btnShowFavourite.visibility = View.GONE
			}
			
			FavMute.contains(access_info.getFullAcct(who)) -> {
				btnHideFavourite.visibility = View.GONE
				btnShowFavourite.visibility = View.VISIBLE
			}
			
			else -> {
				btnHideFavourite.visibility = View.VISIBLE
				btnShowFavourite.visibility = View.GONE
			}
		}
		
		val who_host = who?.host
		if(who_host == null || who_host.isEmpty() || who_host == "?") {
			btnOpenTimeline.visibility = View.GONE
		} else {
			btnOpenTimeline.text = activity.getString(R.string.open_local_timeline_for, who_host)
		}
		
		btnListMemberAddRemove.visibility = View.VISIBLE
	}
	
	fun show() {
		val window = dialog.window
		if(window != null) {
			val lp = window.attributes
			lp.width = (0.5f + 280f * activity.density).toInt()
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT
			window.attributes = lp
		}
		dialog.show()
	}
	
	fun getUserHost():String {
		val who_host = whoRef?.get()?.host
		return when(who_host) {
			"?" -> column.instance_uri
			null,"" -> access_info.host
			else -> who_host
		}
	}
	
	override fun onClick(v : View) {
		dialog.dismissSafe()
		
		val pos = activity.nextPosition(column)
		
		val whoRef = this.whoRef
		val who = whoRef?.get()
		
		if(whoRef != null && who != null) {
			when(v.id) {
				R.id.btnReport -> if(status is TootStatus) {
					Action_User.reportForm(activity, access_info, who, status)
				}
				
				R.id.btnFollow ->
					when {
						
						access_info.isPseudo -> Action_Follow.followFromAnotherAccount(
							activity,
							pos,
							access_info,
							who
						)
						
						access_info.isMisskey && relation.getRequested(who) && ! relation.getFollowing(
							who
						) -> Action_Follow.deleteFollowRequest(
							activity, pos, access_info, whoRef,
							callback = activity.cancel_follow_request_complete_callback
						)
						
						else -> {
							val bSet = ! (relation.getRequested(who) || relation.getFollowing(who))
							Action_Follow.follow(
								activity, pos, access_info, whoRef,
								bFollow = bSet,
								callback = when(bSet) {
									true -> activity.follow_complete_callback
									else -> activity.unfollow_complete_callback
								}
							)
						}
					}
				
				R.id.btnAccountText ->
					ActText.open(activity, ActMain.REQUEST_CODE_TEXT, access_info, who)
				
				R.id.btnMute ->
					when {
						
						//解除
						relation.muting ->
							Action_User.mute(
								activity,
								access_info,
								who,
								bMute = false
							)
						
						else -> {
							@SuppressLint("InflateParams")
							val view =
								activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
							
							val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
							val cbMuteNotification = view.findViewById<CheckBox>(R.id.cbSkipNext)
							
							// Misskey には「このユーザからの通知もミュート」オプションはない
							// 疑似アカウントにもない
							val hasMuteNotification =
								!access_info.isMisskey && ! access_info.isPseudo
							if(hasMuteNotification) {
								tvMessage.text =
									activity.getString(R.string.confirm_mute_user, who.username)
								cbMuteNotification.setText(R.string.confirm_mute_notification_for_user)
								cbMuteNotification.isChecked = true
								// オプション指定つきでミュート
							} else {
								tvMessage.text =
									activity.getString(R.string.confirm_mute_user, who.username)
								cbMuteNotification.visibility = View.GONE
								cbMuteNotification.isChecked = false
							}
							AlertDialog.Builder(activity)
								.setView(view)
								.setNegativeButton(R.string.cancel, null)
								.setPositiveButton(R.string.ok) { _, _ ->
									Action_User.mute(
										activity,
										access_info,
										who,
										bMuteNotification = cbMuteNotification.isChecked
									)
								}
								.show()
							
						}
					}
				
				R.id.btnBlock ->
					when {
						relation.blocking -> Action_User.block(
							activity,
							access_info,
							who,
							false
						)
						
						else -> AlertDialog.Builder(activity)
							.setMessage(
								activity.getString(
									R.string.confirm_block_user,
									who.username
								)
							)
							.setNegativeButton(R.string.cancel, null)
							.setPositiveButton(R.string.ok) { _, _ ->
								Action_User.block(
									activity,
									access_info,
									who,
									true
								)
							}
							.show()
					}
				
				R.id.btnProfile ->
					Action_User.profileLocal(activity, pos, access_info, who)
				
				R.id.btnSendMessage ->
					Action_User.mention(activity, access_info, who)
				
				R.id.btnAccountWebPage -> who.url?.let { url ->
					App1.openCustomTab(activity, url)
				}
				
				R.id.btnFollowRequestOK ->
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, true)
				
				R.id.btnDeleteSuggestion ->
					Action_User.deleteSuggestion(activity, access_info, who)
				
				R.id.btnFollowRequestNG ->
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, false)
				
				R.id.btnFollowFromAnotherAccount ->
					Action_Follow.followFromAnotherAccount(activity, pos, access_info, who)
				
				R.id.btnSendMessageFromAnotherAccount ->
					Action_User.mentionFromAnotherAccount(activity, access_info, who)
				
				R.id.btnOpenProfileFromAnotherAccount ->
					Action_User.profileFromAnotherAccount(activity, pos, access_info, who)
				
				R.id.btnNickname ->
					ActNickname.open(
						activity,
						access_info.getFullAcct(who),
						true,
						ActMain.REQUEST_CODE_NICKNAME
					)
				
				R.id.btnAccountQrCode ->
					DlgQRCode.open(
						activity,
						whoRef.decoded_display_name,
						access_info.getUserUrl(who.acct)
					)
				
				R.id.btnDomainBlock ->
					if(access_info.isPseudo) {
						// 疑似アカウントではドメインブロックできない
						showToast(activity, false, R.string.domain_block_from_pseudo)
						return
					} else {
						val who_host = who.host
						
						// 自分のドメインではブロックできない
						if(access_info.host.equals(who_host, ignoreCase = true)) {
							showToast(activity, false, R.string.domain_block_from_local)
							return
						}
						AlertDialog.Builder(activity)
							.setMessage(activity.getString(R.string.confirm_block_domain, who_host))
							.setNegativeButton(R.string.cancel, null)
							.setPositiveButton(R.string.ok) { _, _ ->
								Action_Instance.blockDomain(activity, access_info, who_host, true)
							}
							.show()
					}
				
				R.id.btnOpenTimeline -> {
					val who_host = who.host
					if(who_host.isEmpty() || who_host == "?") {
						// 何もしない
					} else {
						Action_Instance.timelineLocal(activity, pos, who_host)
					}
				}
				
				R.id.btnAvatarImage -> {
					val url = if(! who.avatar.isNullOrEmpty()) who.avatar else who.avatar_static
					if(url != null && url.isNotEmpty()) App1.openCustomTab(activity, url)
					// XXX: 設定によっては内蔵メディアビューアで開けないか？
				}
				
				R.id.btnQuoteName -> {
					var sv = who.display_name
					try {
						val fmt = Pref.spQuoteNameFormat(activity.pref)
						if(fmt.contains("%1\$s")) {
							sv = String.format(fmt, sv)
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					Action_Account.openPost(activity, sv)
				}
				
				R.id.btnHideBoost ->
					Action_User.showBoosts(activity, access_info, who, false)
				
				R.id.btnShowBoost ->
					Action_User.showBoosts(activity, access_info, who, true)
				
				R.id.btnHideFavourite -> {
					val acct = access_info.getFullAcct(who)
					FavMute.save(acct)
					showToast(activity, false, R.string.changed)
					for(column in activity.app_state.column_list) {
						column.onHideFavouriteNotification(acct)
					}
				}
				
				R.id.btnShowFavourite -> {
					FavMute.delete(access_info.getFullAcct(who))
					showToast(activity, false, R.string.changed)
				}
				
				R.id.btnListMemberAddRemove ->
					DlgListMember(activity, who, access_info).show()
				
				R.id.btnInstanceInformation ->{
					Action_Instance.information(activity, pos, getUserHost())
				}

				R.id.btnProfileDirectory->{
					Action_Instance.profileDirectory(activity,column,getUserHost())
				}
				
				R.id.btnEndorse -> Action_Account.endorse(
					activity,
					access_info,
					who,
					! relation.endorsed
				)
				
				R.id.btnAroundAccountTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.host,
					status,
					ColumnType.ACCOUNT_AROUND
					, allowPseudo = false
				)
				
				R.id.btnAroundLTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.host,
					status,
					ColumnType.LOCAL_AROUND
				)
				
				R.id.btnAroundFTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.host,
					status,
					ColumnType.FEDERATED_AROUND
				)
				
				R.id.btnCopyAccountId -> who.id.toString().copyToClipboard(activity)
				
				R.id.btnOpenAccountInAdminWebUi ->
					App1.openBrowser(
						activity,
						"https://${access_info.host}/admin/accounts/${who.id}"
					)
				
				R.id.btnOpenInstanceInAdminWebUi ->
					App1.openBrowser(
						activity,
						"https://${access_info.host}/admin/instances/${who.host}"
					)
				
				R.id.btnBoostWithVisibility -> {
					val status = this.status ?: return
					val list = if(access_info.isMisskey) {
						arrayOf(
							TootVisibility.Public,
							TootVisibility.UnlistedHome,
							TootVisibility.PrivateFollowers,
							TootVisibility.LocalPublic,
							TootVisibility.LocalHome,
							TootVisibility.LocalFollowers,
							TootVisibility.DirectSpecified,
							TootVisibility.DirectPrivate
						)
					} else {
						arrayOf(
							TootVisibility.Public,
							TootVisibility.UnlistedHome,
							TootVisibility.PrivateFollowers
						)
					}
					val caption_list = list
						.map { Styler.getVisibilityCaption(activity, access_info.isMisskey, it) }
						.toTypedArray()
					
					AlertDialog.Builder(activity)
						.setTitle(R.string.choose_visibility)
						.setItems(caption_list) { _, which ->
							if(which in 0 until list.size) {
								Action_Toot.boost(
									activity,
									access_info,
									status,
									access_info.getFullAcct(status.account),
									NOT_CROSS_ACCOUNT,
									activity.boost_complete_callback,
									visibility = list[which]
								)
							}
						}
						.setNegativeButton(R.string.cancel, null)
						.show()
				}
				
				R.id.btnNotificationFrom -> {
					if(access_info.isMisskey) {
						showToast(activity, false, R.string.misskey_account_not_supported)
					} else {
						val acct = access_info.getFullAcct(who)
						if(acct.isNotEmpty() && ! acct.contains('?')) {
							activity.addColumn(
								pos,
								access_info,
								ColumnType.NOTIFICATION_FROM_ACCT,
								acct
							)
						}
					}
				}
			}
		}
		
		when(v.id) {
			
			R.id.btnStatusWebPage -> status?.url?.let { url ->
				App1.openCustomTab(activity, url)
			}
			
			R.id.btnText -> if(status != null) {
				ActText.open(activity, ActMain.REQUEST_CODE_TEXT, access_info, status)
			}
			
			R.id.btnFavouriteAnotherAccount -> Action_Toot.favouriteFromAnotherAccount(
				activity,
				access_info,
				status
			)
			
			R.id.btnBoostAnotherAccount -> Action_Toot.boostFromAnotherAccount(
				activity,
				access_info,
				status
			)
			R.id.btnReactionAnotherAccount -> Action_Toot.reactionFromAnotherAccount(
				activity,
				access_info,
				status
			)
			
			R.id.btnReplyAnotherAccount -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status
			)
			R.id.btnQuotedRenote -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status,
				quotedRenote = true
			)
			R.id.btnConversationAnotherAccount -> status?.let { status ->
				Action_Toot.conversationOtherInstance(activity, pos, status)
			}
			
			R.id.btnDelete -> status?.let { status ->
				AlertDialog.Builder(activity)
					.setMessage(activity.getString(R.string.confirm_delete_status))
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ ->
						Action_Toot.delete(
							activity,
							access_info,
							status.id
						)
					}
					.show()
			}
			R.id.btnRedraft -> status?.let { status ->
				Action_Toot.redraft(activity, access_info, status)
			}
			
			R.id.btnMuteApp -> status?.application?.let {
				Action_App.muteApp(activity, it)
			}
			
			R.id.btnBoostedBy -> status?.let {
				activity.addColumn(false, pos, access_info, ColumnType.BOOSTED_BY, it.id)
			}
			
			R.id.btnFavouritedBy -> status?.let {
				activity.addColumn(false, pos, access_info, ColumnType.FAVOURITED_BY, it.id)
			}
			
			R.id.btnCancel -> dialog.cancel()
			
			R.id.btnTranslate -> CustomShare.invoke(
				activity,
				access_info,
				status,
				CustomShareTarget.Translate
			)
			
			R.id.btnQuoteUrlStatus -> status?.url?.let { url ->
				if(url.isNotEmpty()) Action_Account.openPost(activity, url)
			}
			
			R.id.btnQuoteUrlAccount -> who?.url?.let { url ->
				if(url.isNotEmpty()) Action_Account.openPost(activity, url)
			}
			R.id.btnShareUrlStatus -> status?.url?.let { url ->
				if(url.isNotEmpty()) shareText(activity, url)
			}
			
			R.id.btnShareUrlAccount -> who?.url?.let { url ->
				if(url.isNotEmpty()) shareText(activity, url)
			}
			R.id.btnNotificationDelete -> notification?.let { notification ->
				Action_Notification.deleteOne(activity, access_info, notification)
			}
			
			R.id.btnConversationMute -> status?.let { status ->
				Action_Toot.muteConversation(activity, access_info, status)
			}
			
			R.id.btnProfilePin -> status?.let { status ->
				Action_Toot.pin(activity, access_info, status, true)
			}
			
			R.id.btnProfileUnpin -> status?.let { status ->
				Action_Toot.pin(activity, access_info, status, false)
			}
			
		}
	}
	
	private fun shareText(activity : ActMain, text : String) {
		ShareCompat.IntentBuilder.from(activity)
			.setText(text)
			.setType("text/plain")
			.startChooser()
	}
	
	override fun onLongClick(v : View) : Boolean {
		val who = whoRef?.get()
		
		when(v.id) {
			R.id.btnFollow -> {
				dialog.dismissSafe()
				Action_Follow.followFromAnotherAccount(
					activity,
					activity.nextPosition(column),
					access_info,
					who
				)
				return true
			}
		}
		return false
	}
	
}
