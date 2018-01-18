package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.support.v4.content.ContextCompat
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView

import java.util.Locale

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.span.EmojiImageSpan

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
		return ContextCompat.getDrawable(context, drawableId)
			?: throw RuntimeException(
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
	
	fun getVisibilityIconAttr(visibility : String?) : Int {
		return when(visibility) {
			null -> R.attr.ic_public
			TootStatus.VISIBILITY_PUBLIC -> R.attr.ic_public
			TootStatus.VISIBILITY_UNLISTED -> R.attr.ic_lock_open
			TootStatus.VISIBILITY_PRIVATE -> R.attr.ic_lock
			TootStatus.VISIBILITY_DIRECT -> R.attr.ic_mail
			TootStatus.VISIBILITY_WEB_SETTING -> R.attr.ic_question
			else -> R.attr.ic_question
		}
	}
	
	fun getVisibilityIcon(context : Context, visibility : String?) : Int {
		return getAttributeResourceId(context, getVisibilityIconAttr(visibility))
	}
	
	fun getVisibilityString(context : Context, visibility : String) : String {
		return when(visibility) {
			TootStatus.VISIBILITY_PUBLIC -> context.getString(R.string.visibility_public)
			TootStatus.VISIBILITY_UNLISTED -> context.getString(R.string.visibility_unlisted)
			TootStatus.VISIBILITY_PRIVATE -> context.getString(R.string.visibility_private)
			TootStatus.VISIBILITY_DIRECT -> context.getString(R.string.visibility_direct)
			TootStatus.VISIBILITY_WEB_SETTING -> context.getString(R.string.visibility_web_setting)
			else -> "?"
		}
	}
	
	// アイコン付きの装飾テキストを返す
	fun getVisibilityCaption(context : Context, visibility : String) : CharSequence {
		
		val sb = SpannableStringBuilder()
		
		// アイコン部分
		val start = sb.length
		sb.append(visibility)
		val end = sb.length
		val icon_id = getVisibilityIcon(context, visibility)
		sb.setSpan(EmojiImageSpan(context, icon_id), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		
		// 文字列部分
		sb.append(' ')
		sb.append(getVisibilityString(context, visibility))
		if(TootStatus.VISIBILITY_WEB_SETTING == visibility) {
			sb.append("\n　　(")
			sb.append(context.getString(R.string.mastodon_1_6_later))
			sb.append(")")
		}
		
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
		
		when {
			relation.blocking -> {
				icon_attr = R.attr.ic_block
				color_attr = R.attr.colorImageButton
			}
			
			relation.muting -> {
				icon_attr = R.attr.ic_mute
				color_attr = R.attr.colorImageButton
			}
			
			relation.getFollowing(who) -> {
				icon_attr = R.attr.ic_follow_cross
				color_attr = R.attr.colorImageButtonAccent
			}
			
			relation.getRequested(who) -> {
				icon_attr = R.attr.ic_follow_wait
				color_attr = R.attr.colorRegexFilterError
			}
			
			else -> {
				icon_attr = R.attr.ic_follow_plus
				color_attr = R.attr.colorImageButton
			}
		}
		
		val color = Styler.getAttributeColor(context, color_attr)
		setIconCustomColor(context, ibFollow, color, icon_attr)
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
	var round_ratio :Float = 0.33f * 0.5f
	
	fun calcIconRound(wh:Int) =wh.toFloat() * round_ratio

	fun calcIconRound(lp:ViewGroup.LayoutParams)
		=Math.min(lp.width,lp.height).toFloat() * round_ratio
	
}
