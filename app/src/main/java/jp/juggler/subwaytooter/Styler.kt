package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.support.v4.content.ContextCompat
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
import java.util.*

object Styler {
	
	fun getAttributeColor(context : Context, attrId : Int) : Int {
		val theme = context.theme
		val a = theme.obtainStyledAttributes(intArrayOf(attrId))
		val color = a.getColor(0, Color.BLACK)
		a.recycle()
		return color
	}
	
	fun getAttributeResourceId(context : Context, attrId : Int) : Int {
		val theme = context.theme
		val a = theme.obtainStyledAttributes(intArrayOf(attrId))
		val resourceId = a.getResourceId(0, 0)
		a.recycle()
		if(resourceId == 0)
			throw RuntimeException(
				String.format(
					Locale.JAPAN,
					"attr not defined.attr_id=0x%x",
					attrId
				)
			)
		return resourceId
	}
	
	fun getAttributeDrawable(context : Context, attrId : Int) : Drawable {
		val drawableId = getAttributeResourceId(context, attrId)
		val d = ContextCompat.getDrawable(context, drawableId)
		return d ?: throw RuntimeException(
			String.format(
				Locale.JAPAN,
				"getDrawable failed. drawableId=0x%x",
				drawableId
			)
		)
	}
	
	// ImageViewにアイコンを設定する
	fun setIconDefaultColor(context : Context, imageView : ImageView, iconAttrId : Int) {
		imageView.setImageResource(getAttributeResourceId(context, iconAttrId))
	}
	
	// ImageViewにアイコンを設定する。色を変えてしまう
	fun setIconCustomColor(
		context : Context,
		imageView : ImageView,
		color : Int,
		iconAttrId : Int
	) {
		val d = getAttributeDrawable(context, iconAttrId)
		d.mutate() // 色指定が他のアイコンに影響しないようにする
		d.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
		imageView.setImageDrawable(d)
	}
	
	fun getVisibilityIconAttr(isMisskeyData:Boolean ,visibility : TootVisibility):Int {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)){
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY ->true
			else-> isMisskeyData
		}
		return when{
			isMisskey -> when(visibility) {
				TootVisibility.Public -> R.attr.ic_public
				TootVisibility.UnlistedHome -> R.attr.btn_home
				TootVisibility.PrivateFollowers -> R.attr.ic_lock_open
				TootVisibility.DirectSpecified -> R.attr.ic_mail
				TootVisibility.DirectPrivate -> R.attr.ic_lock
				TootVisibility.WebSetting -> R.attr.ic_question
			}
			else-> when(visibility) {
				TootVisibility.Public -> R.attr.ic_public
				TootVisibility.UnlistedHome -> R.attr.ic_lock_open
				TootVisibility.PrivateFollowers -> R.attr.ic_lock
				TootVisibility.DirectSpecified -> R.attr.ic_mail
				TootVisibility.DirectPrivate -> R.attr.ic_mail
				TootVisibility.WebSetting -> R.attr.ic_question
			}
		}
	}
	
	fun getVisibilityIcon(context : Context, isMisskeyData:Boolean ,visibility : TootVisibility) : Int {
		return getAttributeResourceId(context, getVisibilityIconAttr(isMisskeyData,visibility))
	}
	
	fun getVisibilityString(context : Context,isMisskeyData:Boolean ,visibility : TootVisibility):String {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)){
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY ->true
			else-> isMisskeyData
		}
		return when{
			isMisskey -> when(visibility) {
				TootVisibility.Public ->context.getString(R.string.visibility_public)
				TootVisibility.UnlistedHome -> context.getString(R.string.visibility_home)
				TootVisibility.PrivateFollowers -> context.getString(R.string.visibility_followers)
				TootVisibility.DirectSpecified -> context.getString(R.string.visibility_direct)
				TootVisibility.DirectPrivate -> context.getString(R.string.visibility_private)
				TootVisibility.WebSetting -> context.getString(R.string.visibility_web_setting)
			}
			else-> when(visibility) {
				TootVisibility.Public -> context.getString(R.string.visibility_public)
				TootVisibility.UnlistedHome -> context.getString(R.string.visibility_unlisted)
				TootVisibility.PrivateFollowers -> context.getString(R.string.visibility_followers)
				TootVisibility.DirectSpecified -> context.getString(R.string.visibility_direct)
				TootVisibility.DirectPrivate -> context.getString(R.string.visibility_direct)
				TootVisibility.WebSetting -> context.getString(R.string.visibility_web_setting)
			}
		}
	}
	
	// アイコン付きの装飾テキストを返す
	fun getVisibilityCaption(context : Context, isMisskeyData:Boolean ,visibility : TootVisibility) : CharSequence {
		
		val icon_id = getVisibilityIcon(context, isMisskeyData,visibility)
		val sv = getVisibilityString(context, isMisskeyData,visibility)

		val sb = SpannableStringBuilder()

		// アイコン部分
		val start = sb.length
		sb.append(" ")
		val end = sb.length
		sb.setSpan(EmojiImageSpan(context, icon_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		
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
	) {
		
		// 被フォロー状態
		if(! relation.followed_by) {
			ivDot.visibility = View.GONE
		} else {
			ivDot.visibility = View.VISIBLE
			setIconDefaultColor(context, ivDot, R.attr.ic_followed_by)
			
			// 被フォローリクエスト状態の時に followed_by が 真と偽の両方がありえるようなので
			// Relationshipだけを見ても被フォローリクエスト状態は分からないっぽい
			// 仕方ないので馬鹿正直に「 followed_byが真ならバッジをつける」しかできない
		}
		
		// フォローボタン
		// follow button
		val color_attr : Int
		val icon_attr : Int
		val contentDescription : String
		
		when {
			relation.blocking -> {
				icon_attr = R.attr.ic_block
				color_attr = R.attr.colorImageButton
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.muting -> {
				icon_attr = R.attr.ic_mute
				color_attr = R.attr.colorImageButton
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.getFollowing(who) -> {
				icon_attr = R.attr.ic_follow_cross
				color_attr = R.attr.colorImageButtonAccent
				contentDescription = context.getString(R.string.unfollow)
			}
			
			relation.getRequested(who) -> {
				icon_attr = R.attr.ic_follow_wait
				color_attr = R.attr.colorRegexFilterError
				contentDescription = context.getString(R.string.unfollow)
			}
			
			else -> {
				icon_attr = R.attr.ic_follow_plus
				color_attr = R.attr.colorImageButton
				contentDescription = context.getString(R.string.follow)
			}
		}
		
		val color = Styler.getAttributeColor(context, color_attr)
		setIconCustomColor(context, ibFollow, color, icon_attr)
		ibFollow.contentDescription = contentDescription
	}
	
	// 色を指定してRippleDrawableを生成する
	fun getAdaptiveRippleDrawable(normalColor : Int, pressedColor : Int) : Drawable {
		return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			RippleDrawable(
				ColorStateList.valueOf(pressedColor), getRectShape(normalColor), null
			)
		} else {
			getStateListDrawable(normalColor, pressedColor)
		}
	}
	
	// 色を指定してRectShapeを生成する
	private fun getRectShape(color : Int) : Drawable {
		val r = RectShape()
		val shapeDrawable = ShapeDrawable(r)
		shapeDrawable.paint.color = color
		return shapeDrawable
	}
	
	// 後方互換用にボタン背景Drawableを生成する
	private fun getStateListDrawable(normalColor : Int, pressedColor : Int) : StateListDrawable {
		val states = StateListDrawable()
		states.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(pressedColor))
		states.addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(pressedColor))
		states.addState(intArrayOf(android.R.attr.state_activated), ColorDrawable(pressedColor))
		states.addState(intArrayOf(), ColorDrawable(normalColor))
		return states
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
	
	fun calcIconRound(wh : Int) = wh.toFloat() * round_ratio
	
	fun calcIconRound(lp : ViewGroup.LayoutParams) =
		Math.min(lp.width, lp.height).toFloat() * round_ratio
	
}

fun SpannableStringBuilder.appendColorShadeIcon(
	context:Context,
	drawable_id:Int,
	text:String
):SpannableStringBuilder{
	val start = this.length
	this.append(text)
	val end = this.length
	this.setSpan(
		EmojiImageSpan(context, drawable_id,useColorShader=true),
		start,
		end,
		Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
	)
	return this
}

fun SpannableStringBuilder.appendDrawableIcon(
	context:Context,
	drawable_id:Int,
	text:String
):SpannableStringBuilder{
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
