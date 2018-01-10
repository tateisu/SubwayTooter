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
	viewRoot : View,
	private val bSimpleList : Boolean
) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		val log = LogCategory("StatusButtons")
	}
	
	private val btnBoost : Button
	private val btnFavourite : Button
	private val btnFollow2 : ImageButton
	private val ivFollowedBy2 : ImageView
	private val llFollow2 : View
	
	private val access_info : SavedAccount
	private var relation : UserRelation? = null
	private var status : TootStatus? = null
	private var notification : TootNotification? = null
	
	var close_window : PopupWindow? = null
	
	init {
		this.access_info = column.access_info
		
		btnBoost = viewRoot.findViewById(R.id.btnBoost)
		btnFavourite = viewRoot.findViewById(R.id.btnFavourite)
		btnFollow2 = viewRoot.findViewById(R.id.btnFollow2)
		ivFollowedBy2 = viewRoot.findViewById(R.id.ivFollowedBy2)
		llFollow2 = viewRoot.findViewById(R.id.llFollow2)
		
		val listener = this
		
		btnBoost.setOnClickListener(listener)
		btnBoost.setOnLongClickListener(listener)
		btnFavourite.setOnClickListener(listener)
		btnFavourite.setOnLongClickListener(listener)
		btnFollow2.setOnClickListener(listener)
		btnFollow2.setOnLongClickListener(listener)
		
		with(viewRoot.findViewById<View>(R.id.btnMore)) {
			setOnClickListener(listener)
		}
		
		with(viewRoot.findViewById<View>(R.id.btnConversation)) {
			setOnClickListener(listener)
			setOnLongClickListener(listener)
		}
		
		with(viewRoot.findViewById<View>(R.id.btnReply)) {
			setOnClickListener(listener)
			setOnLongClickListener(listener)
		}
	}
	
	fun bind(status : TootStatus, notification : TootNotification?) {
		this.status = status
		this.notification = notification
		
		val color_normal = Styler.getAttributeColor(activity, R.attr.colorImageButton)
		val color_accent = Styler.getAttributeColor(activity, R.attr.colorImageButtonAccent)
		val fav_icon_attr = if(access_info.isNicoru(status.account)) R.attr.ic_nicoru else R.attr.btn_favourite
		
		when {
			TootStatus.VISIBILITY_DIRECT == status.visibility -> setButton(btnBoost, false, color_accent, R.attr.ic_mail, "")
			TootStatus.VISIBILITY_PRIVATE == status.visibility -> setButton(btnBoost, false, color_accent, R.attr.ic_lock, "")
			activity.app_state.isBusyBoost(access_info, status) -> setButton(btnBoost, false, color_normal, R.attr.btn_refresh, "?")
			
			else -> {
				val color = if(status.reblogged) color_accent else color_normal
				setButton(btnBoost, true, color, R.attr.btn_boost, status.reblogs_count?.toString() ?: "")
			}
		}
		
		when {
			activity.app_state.isBusyFav(access_info, status) -> setButton(btnFavourite, false, color_normal, R.attr.btn_refresh, "?")
			
			else -> {
				val color = if(status.favourited) color_accent else color_normal
				setButton(btnFavourite, true, color, fav_icon_attr, status.favourites_count?.toString() ?: "")
			}
		}
		
		val account = status.account
		
		this.relation = if( ! activity.pref.getBoolean(Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, false)) {
			llFollow2.visibility = View.GONE
			null
		} else {
			llFollow2.visibility = View.VISIBLE
			val relation = UserRelation.load(access_info.db_id, account.id)
			Styler.setFollowIcon(activity, btnFollow2, ivFollowedBy2, relation, account)
			relation
		}
		
	}
	
	private fun setButton(b : Button, enabled : Boolean, color : Int, icon_attr : Int, text : String) {
		val d = Styler.getAttributeDrawable(activity, icon_attr).mutate()
		d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
		b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
		b.text = text
		b.setTextColor(color)
		b.isEnabled = enabled
	}
	
	override fun onClick(v : View) {
		
		close_window?.dismiss()
		close_window = null
		
		val status = this.status ?: return
		
		when(v.id) {
			
			R.id.btnConversation -> Action_Toot.conversation(activity, activity.nextPosition(column), access_info, status)
			
			R.id.btnReply -> if( ! access_info.isPseudo) {
				Action_Toot.reply(activity, access_info, status)
			} else {
				Action_Toot.replyFromAnotherAccount(activity, access_info, status)
			}
			
			R.id.btnBoost -> if(access_info.isPseudo) {
				Action_Toot.boostFromAnotherAccount(activity, access_info, status)
			} else {
				
				// トグル動作
				val willRoost = ! status.reblogged
				
				// 簡略表示なら結果をトースト表示
				val callback = when {
					! bSimpleList -> null
					willRoost -> activity.boost_complete_callback
					else -> activity.unboost_complete_callback
				}
				
				Action_Toot.boost(
					activity,
					access_info,
					status,
					NOT_CROSS_ACCOUNT,
					willRoost,
					false,
					callback
				)
			}
			
			R.id.btnFavourite -> if(access_info.isPseudo) {
				Action_Toot.favouriteFromAnotherAccount(activity, access_info, status)
			} else {
				
				// トグル動作
				val willFavourite = ! status.favourited
				
				// 簡略表示なら結果をトースト表示
				val callback = when {
					! bSimpleList -> null
					status.favourited -> activity.unfavourite_complete_callback
					else -> activity.favourite_complete_callback
				}
				
				Action_Toot.favourite(
					activity,
					access_info,
					status,
					NOT_CROSS_ACCOUNT,
					willFavourite,
					callback
				)
			}
			
			R.id.btnFollow2 -> {
				val account = status.account
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
							account,
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
							account,
							bFollow =true,
							callback =activity.follow_complete_callback
						)
					}
				}
			}
			
			R.id.btnMore -> DlgContextMenu(activity, column, status.account, status, notification).show()
		}
	}
	
	override fun onLongClick(v : View) : Boolean {
		
		close_window?.dismiss()
		close_window = null
		
		val status = this.status ?: return true
		
		when(v.id) {
			R.id.btnConversation -> Action_Toot.conversationOtherInstance(activity, activity.nextPosition(column), status)
			R.id.btnBoost -> Action_Toot.boostFromAnotherAccount(activity, access_info, status)
			R.id.btnFavourite -> Action_Toot.favouriteFromAnotherAccount(activity, access_info, status)
			R.id.btnReply -> Action_Toot.replyFromAnotherAccount(activity, access_info, status)
			
			R.id.btnFollow2 -> {
				Action_Follow.followFromAnotherAccount(activity, activity.nextPosition(column), access_info, status.account)
			}
		}
		return true
	}
	
}
