package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.PorterDuff
import android.support.v4.app.ShareCompat
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

import java.util.ArrayList

import jp.juggler.subwaytooter.action.Action_Account
import jp.juggler.subwaytooter.action.Action_App
import jp.juggler.subwaytooter.action.Action_Follow
import jp.juggler.subwaytooter.action.Action_Instance
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.action.Action_User
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.DlgQRCode
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.showToast

@SuppressLint("InflateParams")
internal class DlgContextMenu(
	val activity : ActMain,
	private val column : Column,
	private val whoRef : TootAccountRef?,
	private val status : TootStatus?,
	private val notification : TootNotification?
) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		private val log = LogCategory("DlgContextMenu")
	}
	
	private val access_info : SavedAccount
	private val relation : UserRelation
	
	private val dialog : Dialog
	
	init {
		this.access_info = column.access_info
		
		val column_type = column.column_type
		
		val who = whoRef?.get()
		val status = this.status
		
		this.relation = when {
			who != null -> UserRelation.load(access_info.db_id, who.id)
			else -> UserRelation()
		}
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_context_menu, null, false)
		this.dialog = Dialog(activity)
		dialog.setContentView(viewRoot)
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(true)
		
		val llStatus = viewRoot.findViewById<View>(R.id.llStatus)
		val btnStatusWebPage = viewRoot.findViewById<View>(R.id.btnStatusWebPage)
		val btnText = viewRoot.findViewById<View>(R.id.btnText)
		val btnFavouriteAnotherAccount =
			viewRoot.findViewById<View>(R.id.btnFavouriteAnotherAccount)
		val btnBoostAnotherAccount = viewRoot.findViewById<View>(R.id.btnBoostAnotherAccount)
		val btnReplyAnotherAccount = viewRoot.findViewById<View>(R.id.btnReplyAnotherAccount)
		val btnDelete = viewRoot.findViewById<View>(R.id.btnDelete)
		val btnRedraft = viewRoot.findViewById<View>(R.id.btnRedraft)
		
		val btnReport = viewRoot.findViewById<View>(R.id.btnReport)
		val btnMuteApp = viewRoot.findViewById<Button>(R.id.btnMuteApp)
		val llAccountActionBar = viewRoot.findViewById<View>(R.id.llAccountActionBar)
		val btnFollow = viewRoot.findViewById<ImageView>(R.id.btnFollow)
		
		val btnMute = viewRoot.findViewById<ImageView>(R.id.btnMute)
		val btnBlock = viewRoot.findViewById<ImageView>(R.id.btnBlock)
		val btnProfile = viewRoot.findViewById<View>(R.id.btnProfile)
		val btnSendMessage = viewRoot.findViewById<View>(R.id.btnSendMessage)
		val btnAccountWebPage = viewRoot.findViewById<View>(R.id.btnAccountWebPage)
		val btnFollowRequestOK = viewRoot.findViewById<View>(R.id.btnFollowRequestOK)
		val btnFollowRequestNG = viewRoot.findViewById<View>(R.id.btnFollowRequestNG)
		val btnDeleteSuggestion = viewRoot.findViewById<View>(R.id.btnDeleteSuggestion)
		val btnFollowFromAnotherAccount =
			viewRoot.findViewById<View>(R.id.btnFollowFromAnotherAccount)
		val btnSendMessageFromAnotherAccount =
			viewRoot.findViewById<View>(R.id.btnSendMessageFromAnotherAccount)
		val btnOpenProfileFromAnotherAccount =
			viewRoot.findViewById<View>(R.id.btnOpenProfileFromAnotherAccount)
		val btnDomainBlock = viewRoot.findViewById<Button>(R.id.btnDomainBlock)
		val btnInstanceInformation = viewRoot.findViewById<Button>(R.id.btnInstanceInformation)
		val ivFollowedBy = viewRoot.findViewById<ImageView>(R.id.ivFollowedBy)
		val btnOpenTimeline = viewRoot.findViewById<Button>(R.id.btnOpenTimeline)
		val btnConversationAnotherAccount =
			viewRoot.findViewById<View>(R.id.btnConversationAnotherAccount)
		val btnAvatarImage = viewRoot.findViewById<View>(R.id.btnAvatarImage)
		
		val llNotification = viewRoot.findViewById<View>(R.id.llNotification)
		val btnNotificationDelete = viewRoot.findViewById<View>(R.id.btnNotificationDelete)
		val btnConversationMute = viewRoot.findViewById<Button>(R.id.btnConversationMute)
		
		val btnHideBoost = viewRoot.findViewById<View>(R.id.btnHideBoost)
		val btnShowBoost = viewRoot.findViewById<View>(R.id.btnShowBoost)
		val btnHideFavourite = viewRoot.findViewById<View>(R.id.btnHideFavourite)
		val btnShowFavourite = viewRoot.findViewById<View>(R.id.btnShowFavourite)
		
		val btnListMemberAddRemove = viewRoot.findViewById<View>(R.id.btnListMemberAddRemove)
		
		btnStatusWebPage.setOnClickListener(this)
		btnText.setOnClickListener(this)
		btnFavouriteAnotherAccount.setOnClickListener(this)
		btnBoostAnotherAccount.setOnClickListener(this)
		btnReplyAnotherAccount.setOnClickListener(this)
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
		btnDomainBlock.setOnClickListener(this)
		
		viewRoot.findViewById<View>(R.id.btnQuoteUrlStatus).setOnClickListener(this)
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
		} else {
			val status_by_me = access_info.isMe(status.account)
			
			btnDelete.visibility = if(status_by_me) View.VISIBLE else View.GONE
			btnRedraft.visibility = if(status_by_me) View.VISIBLE else View.GONE
			
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
		
		if(access_info.isPseudo) {
			llAccountActionBar.visibility = View.GONE
		} else {
			
			// 被フォロー状態
			if(! relation.followed_by) {
				ivFollowedBy.visibility = View.GONE
			} else {
				ivFollowedBy.visibility = View.VISIBLE
				ivFollowedBy.setImageResource(
					Styler.getAttributeResourceId(
						activity,
						R.attr.ic_followed_by
					)
				)
			}
			
			// follow button
			var icon_attr = when {
				relation.getRequested(who) -> R.attr.ic_follow_wait
				relation.getFollowing(who) -> R.attr.ic_follow_cross
				else -> R.attr.ic_follow_plus
			}
			var color_attr = when {
				relation.getRequested(who) -> R.attr.colorRegexFilterError
				relation.getFollowing(who) -> R.attr.colorImageButtonAccent
				else -> R.attr.colorImageButton
			}
			var color = Styler.getAttributeColor(activity, color_attr)
			var d = Styler.getAttributeDrawable(activity, icon_attr).mutate()
			d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
			btnFollow.setImageDrawable(d)
			
			// mute button
			icon_attr = R.attr.ic_mute
			color_attr =
				if(relation.muting) R.attr.colorImageButtonAccent else R.attr.colorImageButton
			color = Styler.getAttributeColor(activity, color_attr)
			d = Styler.getAttributeDrawable(activity, icon_attr).mutate()
			d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
			btnMute.setImageDrawable(d)
			
			// block button
			icon_attr = R.attr.ic_block
			color_attr =
				if(relation.blocking) R.attr.colorImageButtonAccent else R.attr.colorImageButton
			color = Styler.getAttributeColor(activity, color_attr)
			d = Styler.getAttributeDrawable(activity, icon_attr).mutate()
			d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
			btnBlock.setImageDrawable(d)
			
		}
		
		if(who == null) {
			btnInstanceInformation.visibility = View.GONE
			btnDomainBlock.visibility = View.GONE
		} else {
			val who_host = who.host
			btnInstanceInformation.visibility = View.VISIBLE
			btnInstanceInformation.text =
				activity.getString(R.string.instance_information_of, who_host)
			if(access_info.isPseudo || access_info.host.equals(who_host, ignoreCase = true)) {
				// 疑似アカウントではドメインブロックできない
				// 自ドメインはブロックできない
				btnDomainBlock.visibility = View.GONE
			} else {
				btnDomainBlock.visibility = View.VISIBLE
				btnDomainBlock.text = activity.getString(R.string.block_domain_that, who_host)
			}
		}
		
		viewRoot.findViewById<View>(R.id.btnAccountText).setOnClickListener(this)
		
		if(access_info.isPseudo) {
			btnProfile.visibility = View.GONE
			btnSendMessage.visibility = View.GONE
		}
		
		if(column_type != Column.TYPE_FOLLOW_REQUESTS) {
			btnFollowRequestOK.visibility = View.GONE
			btnFollowRequestNG.visibility = View.GONE
		}
		
		if(column_type != Column.TYPE_FOLLOW_SUGGESTION) {
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
	
	override fun onClick(v : View) {
		
		try {
			dialog.dismiss()
		} catch(ignored : Throwable) {
			// IllegalArgumentException がたまに出る
		}
		
		val pos = activity.nextPosition(column)
		
		val whoRef = this.whoRef
		val who = whoRef?.get()
		
		if(whoRef != null && who != null) {
			when(v.id) {
				R.id.btnReport -> if(status is TootStatus) {
					Action_User.reportForm(activity, access_info, who, status)
				}
				
				R.id.btnFollow ->
					if(access_info.isPseudo) {
						Action_Follow.followFromAnotherAccount(activity, pos, access_info, who)
					} else {
						val bSet = ! (relation.getFollowing(who) || relation.getRequested(who))
						Action_Follow.follow(
							activity, pos, access_info, whoRef,
							bFollow = bSet,
							callback = if(bSet) activity.follow_complete_callback else activity.unfollow_complete_callback
						)
					}
				
				R.id.btnAccountText ->
					ActText.open(activity, ActMain.REQUEST_CODE_TEXT, access_info, who)
				
				R.id.btnMute ->
					if(relation.muting) {
						//解除
						Action_User.mute(
							activity,
							access_info,
							who,
							bMute = false
						)
					} else {
						@SuppressLint("InflateParams")
						val view =
							activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)
						val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
						tvMessage.text =
							activity.getString(R.string.confirm_mute_user, who.username)
						val cbMuteNotification = view.findViewById<CheckBox>(R.id.cbSkipNext)
						cbMuteNotification.setText(R.string.confirm_mute_notification_for_user)
						cbMuteNotification.isChecked = true
						// オプション指定つきでミュート
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
				
				R.id.btnBlock ->
					if(relation.blocking) {
						Action_User.block(activity, access_info, who, false)
					} else {
						AlertDialog.Builder(activity)
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
				
				R.id.btnInstanceInformation ->
					Action_Instance.information(activity, pos, who.host)
				
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
			
			R.id.btnReplyAnotherAccount -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status
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
				activity.addColumn(false, pos, access_info, Column.TYPE_BOOSTED_BY, it.id)
			}
			
			R.id.btnFavouritedBy -> status?.let {
				activity.addColumn(false, pos, access_info, Column.TYPE_FAVOURITED_BY, it.id)
			}
			
			R.id.btnCancel -> dialog.cancel()
			
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
				try {
					dialog.dismiss()
				} catch(ignored : Throwable) {
					// IllegalArgumentException がたまに出る
				}
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
