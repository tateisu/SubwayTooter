package jp.juggler.subwaytooter

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.*

object Styler {
	
	fun defaultColorIcon(context : Context, iconId : Int) : Drawable? =
		ContextCompat.getDrawable(context, iconId)?.also {
			it.setTint(getAttributeColor(context, R.attr.colorVectorDrawable))
			it.setTintMode(PorterDuff.Mode.SRC_IN)
		}
	
	fun getVisibilityIconId(isMisskeyData : Boolean, visibility : TootVisibility) : Int {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)) {
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY -> true
			else -> isMisskeyData
		}
		return when {
			isMisskey -> when(visibility) {
				TootVisibility.Public -> R.drawable.ic_public
				TootVisibility.UnlistedHome -> R.drawable.ic_home
				TootVisibility.PrivateFollowers -> R.drawable.ic_lock_open
				TootVisibility.DirectSpecified -> R.drawable.ic_mail
				TootVisibility.DirectPrivate -> R.drawable.ic_lock
				TootVisibility.WebSetting -> R.drawable.ic_question
				
				TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
				TootVisibility.LocalHome -> R.drawable.ic_local_home
				TootVisibility.LocalFollowers -> R.drawable.ic_local_lock_open
				
			}
			else -> when(visibility) {
				TootVisibility.Public -> R.drawable.ic_public
				TootVisibility.UnlistedHome -> R.drawable.ic_lock_open
				TootVisibility.PrivateFollowers -> R.drawable.ic_lock
				TootVisibility.DirectSpecified -> R.drawable.ic_mail
				TootVisibility.DirectPrivate -> R.drawable.ic_mail
				TootVisibility.WebSetting -> R.drawable.ic_question
				
				TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
				TootVisibility.LocalHome -> R.drawable.ic_local_lock_open
				TootVisibility.LocalFollowers -> R.drawable.ic_local_lock
				
			}
		}
	}
	
	fun getVisibilityString(
		context : Context,
		isMisskeyData : Boolean,
		visibility : TootVisibility
	) : String {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)) {
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY -> true
			else -> isMisskeyData
		}
		return context.getString(
			when {
				isMisskey -> when(visibility) {
					TootVisibility.Public -> R.string.visibility_public
					TootVisibility.UnlistedHome -> R.string.visibility_home
					TootVisibility.PrivateFollowers -> R.string.visibility_followers
					TootVisibility.DirectSpecified -> R.string.visibility_direct
					TootVisibility.DirectPrivate -> R.string.visibility_private
					TootVisibility.WebSetting -> R.string.visibility_web_setting
					
					TootVisibility.LocalPublic -> R.string.visibility_local_public
					TootVisibility.LocalHome -> R.string.visibility_local_home
					TootVisibility.LocalFollowers -> R.string.visibility_local_followers
				}
				else -> when(visibility) {
					TootVisibility.Public -> R.string.visibility_public
					TootVisibility.UnlistedHome -> R.string.visibility_unlisted
					TootVisibility.PrivateFollowers -> R.string.visibility_followers
					TootVisibility.DirectSpecified -> R.string.visibility_direct
					TootVisibility.DirectPrivate -> R.string.visibility_direct
					TootVisibility.WebSetting -> R.string.visibility_web_setting
					
					TootVisibility.LocalPublic -> R.string.visibility_local_public
					TootVisibility.LocalHome -> R.string.visibility_local_unlisted
					TootVisibility.LocalFollowers -> R.string.visibility_local_followers
				}
			}
		)
	}
	
	// アイコン付きの装飾テキストを返す
	fun getVisibilityCaption(
		context : Context,
		isMisskeyData : Boolean,
		visibility : TootVisibility
	) : CharSequence {
		
		val icon_id = getVisibilityIconId(isMisskeyData, visibility)
		val sv = getVisibilityString(context, isMisskeyData, visibility)
		val color = getAttributeColor(context, R.attr.colorVectorDrawable)
		val sb = SpannableStringBuilder()
		
		// アイコン部分
		val start = sb.length
		sb.append(" ")
		val end = sb.length
		sb.setSpan(
			EmojiImageSpan(
				context,
				icon_id,
				useColorShader = true,
				color = color
			),
			start,
			end,
			Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		)
		
		// 文字列部分
		sb.append(' ')
		sb.append(sv)
		
		return sb
	}
	
	fun setFollowIcon(
		context : Context
		, ibFollow : ImageButton
		, ivDot : ImageView
		, relation : UserRelation
		, who : TootAccount
		, defaultColor : Int
		, alphaMultiplier : Float
	) {
		
		fun colorError() = getAttributeColor(context, R.attr.colorRegexFilterError)
		fun colorAccent() = getAttributeColor(context, R.attr.colorImageButtonAccent)
		
		// 被フォロー状態
		when {
			
			relation.blocked_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_blocked_by,
					color = colorError(),
					alphaMultiplier = alphaMultiplier
				)
			}
			
			relation.requested_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_requested_by,
					color = colorError(),
					alphaMultiplier = alphaMultiplier
				)
			}
			
			relation.followed_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_followed_by,
					color = colorAccent(),
					alphaMultiplier = alphaMultiplier
				)
				// 被フォローリクエスト状態の時に followed_by が 真と偽の両方がありえるようなので
				// Relationshipだけを見ても被フォローリクエスト状態は分からないっぽい
				// 仕方ないので馬鹿正直に「 followed_byが真ならバッジをつける」しかできない
			}
			
			else -> {
				ivDot.visibility = View.GONE
			}
		}
		
		// フォローボタン
		// follow button
		val color : Int
		val iconId : Int
		val contentDescription : String
		
		when {
			relation.blocking -> {
				iconId = R.drawable.ic_block
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.muting -> {
				iconId = R.drawable.ic_volume_off
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.getFollowing(who) -> {
				iconId = R.drawable.ic_follow_cross
				color = colorAccent()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			relation.getRequested(who) -> {
				iconId = R.drawable.ic_follow_wait
				color = colorError()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			else -> {
				iconId = R.drawable.ic_follow_plus
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
		}
		
		setIconDrawableId(
			context,
			ibFollow,
			iconId,
			color = color,
			alphaMultiplier = alphaMultiplier
		)
		ibFollow.contentDescription = contentDescription
	}
	
	private fun getHorizontalPadding(v : View, delta_dp : Float) : Int {
		val form_width_max = 420f
		val dm = v.resources.displayMetrics
		val screen_w = dm.widthPixels
		val content_w = (0.5f + form_width_max * dm.density).toInt()
		val pad_lr = (screen_w - content_w) / 2
		return (if(pad_lr < 0) 0 else pad_lr) + (0.5f + delta_dp * dm.density).toInt()
	}
	
	fun fixHorizontalPadding(v : View) {
		val pad_lr = getHorizontalPadding(v, 12f)
		val pad_t = v.paddingTop
		val pad_b = v.paddingBottom
		v.setPaddingRelative(pad_lr, pad_t, pad_lr, pad_b)
	}
	
	fun fixHorizontalPadding2(v : View) {
		val pad_lr = getHorizontalPadding(v, 0f)
		val pad_t = v.paddingTop
		val pad_b = v.paddingBottom
		v.setPaddingRelative(pad_lr, pad_t, pad_lr, pad_b)
	}
	
	fun fixHorizontalMargin(v : View) {
		val pad_lr = getHorizontalPadding(v, 0f)
		val lp = v.layoutParams
		if(lp is ViewGroup.MarginLayoutParams) {
			lp.leftMargin = pad_lr
			lp.rightMargin = pad_lr
		}
	}
	
	// ActMainの初期化時に更新される
	var round_ratio : Float = 0.33f * 0.5f
	var boost_alpha : Float = 1f
	
	fun calcIconRound(wh : Int) = wh.toFloat() * round_ratio
	
	fun calcIconRound(lp : ViewGroup.LayoutParams) =
		Math.min(lp.width, lp.height).toFloat() * round_ratio
	
}

fun SpannableStringBuilder.appendColorShadeIcon(
	context : Context,
	drawable_id : Int,
	text : String,
	color : Int? = null
) : SpannableStringBuilder {
	val start = this.length
	this.append(text)
	val end = this.length
	this.setSpan(
		EmojiImageSpan(context, drawable_id, useColorShader = true, color = color),
		start,
		end,
		Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
	)
	return this
}

fun SpannableStringBuilder.appendDrawableIcon(
	context : Context,
	drawable_id : Int,
	text : String
) : SpannableStringBuilder {
	val start = this.length
	this.append(text)
	val end = this.length
	this.setSpan(
		EmojiImageSpan(context, drawable_id),
		start,
		end,
		Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
	)
	return this
}
