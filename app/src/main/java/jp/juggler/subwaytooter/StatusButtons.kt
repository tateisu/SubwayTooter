package jp.juggler.subwaytooter

import android.graphics.PorterDuff
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow

import jp.juggler.subwaytooter.action.Action_Follow
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.action.NOT_CROSS_ACCOUNT
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.LogCategory

internal class StatusButtons(
	private val activity : ActMain,
	private val column : Column,
	private val bSimpleList : Boolean,
	
	private val btnConversation : ImageButton,
	private val btnReply : Button,
	private val btnBoost : Button,
	private val btnFavourite : Button,
	private val llFollow2 : View,
	private val btnFollow2 : ImageButton,
	private val ivFollowedBy2 : ImageView,
	private val btnMore : ImageButton

) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		val log = LogCategory("StatusButtons")
	}
	
	private val access_info : SavedAccount
	private var relation : UserRelation? = null
	private var status : TootStatus? = null
	private var notification : TootNotification? = null
	
	var close_window : PopupWindow? = null
	
	init {
		this.access_info = column.access_info
		
		btnBoost.setOnClickListener(this)
		btnBoost.setOnLongClickListener(this)
		btnFavourite.setOnClickListener(this)
		btnFavourite.setOnLongClickListener(this)
		btnFollow2.setOnClickListener(this)
		btnFollow2.setOnLongClickListener(this)
		btnMore.setOnClickListener(this)
		btnConversation.setOnClickListener(this)
		btnConversation.setOnLongClickListener(this)
		btnReply.setOnClickListener(this)
		btnReply.setOnLongClickListener(this)
		
	}
	
	fun bind(status : TootStatus, notification : TootNotification?) {
		this.status = status
		this.notification = notification
		
		val color_normal = Styler.getAttributeColor(activity, R.attr.colorImageButton)
		val color_accent = Styler.getAttributeColor(activity, R.attr.colorImageButtonAccent)
		val fav_icon_attr =
			if(access_info.isNicoru(status.account)) R.attr.ic_nicoru else R.attr.btn_favourite
		
		val replies_count = status.replies_count

		setButton(
			btnReply,
			true,
			color_normal,
			R.attr.btn_reply,
			when {
				replies_count == null || status.visibility == TootStatus.VISIBILITY_DIRECT -> ""
				else -> when(Pref.ipRepliesCount(activity.pref)) {
					Pref.RC_SIMPLE -> when {
						replies_count >= 2L -> "1+"
						replies_count == 1L -> "1"
						else -> ""
					}
					Pref.RC_ACTUAL -> replies_count.toString()
					else -> ""
				}
			},
			activity.getString(R.string.reply)
		)
		
		// ブーストボタン
		when {
			TootStatus.VISIBILITY_DIRECT == status.visibility -> setButton(
				btnBoost,
				false,
				color_accent,
				R.attr.ic_mail,
				"",
				activity.getString(R.string.boost)
			)
			
			activity.app_state.isBusyBoost(access_info, status) -> setButton(
				btnBoost,
				false,
				color_normal,
				R.attr.btn_refresh,
				"?",
				activity.getString(R.string.boost)
			)
			
			else -> setButton(
				btnBoost,
				true,
				if(status.reblogged) color_accent else color_normal,
				R.attr.btn_boost,
				status.reblogs_count?.toString() ?: "",
				activity.getString(R.string.boost)
			)
		}
		
		when {
			activity.app_state.isBusyFav(access_info, status) -> setButton(
				btnFavourite,
				false,
				color_normal,
				R.attr.btn_refresh,
				"?",
				activity.getString(R.string.favourite)
			)
			
			else -> setButton(
				btnFavourite,
				true,
				if(status.favourited) color_accent else color_normal,
				fav_icon_attr,
				status.favourites_count?.toString() ?: "",
				activity.getString(R.string.favourite)
			)
		}
		
		val account = status.account
		
		this.relation = if(! Pref.bpShowFollowButtonInButtonBar(activity.pref)) {
			llFollow2.visibility = View.GONE
			null
		} else {
			llFollow2.visibility = View.VISIBLE
			val relation = UserRelation.load(access_info.db_id, account.id)
			Styler.setFollowIcon(activity, btnFollow2, ivFollowedBy2, relation, account)
			relation
		}
		
	}
	
	private fun setButton(
		b : Button,
		enabled : Boolean,
		color : Int,
		icon_attr : Int,
		text : String,
		contentDescription : String
	) {
		val d = Styler.getAttributeDrawable(activity, icon_attr).mutate()
		d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
		b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
		b.text = text
		b.contentDescription = contentDescription + text
		b.setTextColor(color)
		b.isEnabled = enabled
	}
	
	override fun onClick(v : View) {
		
		close_window?.dismiss()
		close_window = null
		
		val status = this.status ?: return
		
		when(v) {
			
			btnConversation -> Action_Toot.conversation(
				activity,
				activity.nextPosition(column),
				access_info,
				status
			)
			
			btnReply -> if(! access_info.isPseudo) {
				Action_Toot.reply(activity, access_info, status)
			} else {
				Action_Toot.replyFromAnotherAccount(activity, access_info, status)
			}
			
			btnBoost -> {
				if(access_info.isPseudo) {
					Action_Toot.boostFromAnotherAccount(activity, access_info, status)
				} else {
					
					// トグル動作
					val bSet = ! status.reblogged
					
					Action_Toot.boost(
						activity,
						access_info,
						status,
						access_info.getFullAcct(status.account),
						NOT_CROSS_ACCOUNT,
						when {
							! bSimpleList -> null
							// 簡略表示なら結果をトースト表示
							bSet -> activity.boost_complete_callback
							else -> activity.unboost_complete_callback
						},
						bSet = bSet
					)
				}
			}
			
			btnFavourite -> {
				if(access_info.isPseudo) {
					Action_Toot.favouriteFromAnotherAccount(activity, access_info, status)
				} else {
					
					// トグル動作
					val bSet = ! status.favourited
					
					Action_Toot.favourite(
						activity,
						access_info,
						status,
						NOT_CROSS_ACCOUNT,
						when {
							! bSimpleList -> null
							// 簡略表示なら結果をトースト表示
							bSet -> activity.favourite_complete_callback
							else -> activity.unfavourite_complete_callback
						},
						bSet = bSet
					)
				}
			}
			
			btnFollow2 -> {
				val accountRef = status.accountRef
				val account = accountRef.get()
				val relation = this.relation ?: return
				
				when {
					access_info.isPseudo -> {
						// 別アカでフォロー
						Action_Follow.followFromAnotherAccount(
							activity,
							activity.nextPosition(column),
							access_info,
							account
						)
					}
					
					relation.blocking || relation.muting -> {
						// 何もしない
					}
					
					relation.getFollowing(account) || relation.getRequested(account) -> {
						// フォロー解除
						Action_Follow.follow(
							activity,
							activity.nextPosition(column),
							access_info,
							accountRef,
							bFollow = false,
							callback = activity.unfollow_complete_callback
						)
					}
					
					else -> {
						// フォロー
						Action_Follow.follow(
							activity,
							activity.nextPosition(column),
							access_info,
							accountRef,
							bFollow = true,
							callback = activity.follow_complete_callback
						)
					}
				}
			}
			
			btnMore -> DlgContextMenu(
				activity,
				column,
				status.accountRef,
				status,
				notification
			).show()
		}
	}
	
	override fun onLongClick(v : View) : Boolean {
		
		close_window?.dismiss()
		close_window = null
		
		val status = this.status ?: return true
		
		when(v) {
			btnConversation -> Action_Toot.conversationOtherInstance(
				activity, activity.nextPosition(column), status
			)
			
			btnBoost -> Action_Toot.boostFromAnotherAccount(
				activity, access_info, status
			)
			
			btnFavourite -> Action_Toot.favouriteFromAnotherAccount(
				activity, access_info, status
			)
			
			btnReply -> Action_Toot.replyFromAnotherAccount(
				activity, access_info, status
			)
			
			btnFollow2 -> Action_Follow.followFromAnotherAccount(
				activity, activity.nextPosition(column), access_info, status.account
			)
			
		}
		return true
	}
	
}
