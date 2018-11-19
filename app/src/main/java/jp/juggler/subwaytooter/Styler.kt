package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.clipRange
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
	
	/////////////////////////////////////////////////////////
	
	private class ColorFilterCacheValue(
		val filter : ColorFilter,
		var lastUsed : Long
	)
	
	private val colorFilterCache = SparseArray<ColorFilterCacheValue>()
	private var colorFilterCacheLastSweep = 0L
	
	private fun createColorFilter(rgb : Int) : ColorFilter? {
		synchronized(colorFilterCache) {
			val now = SystemClock.elapsedRealtime()
			val cacheValue = colorFilterCache[rgb]
			if(cacheValue != null) {
				cacheValue.lastUsed = now
				return cacheValue.filter
			}
			
			val size = colorFilterCache.size()
			if(now - colorFilterCacheLastSweep >= 10000L && size >= 64) {
				colorFilterCacheLastSweep = now
				for(i in size - 1 downTo 0) {
					val v = colorFilterCache.valueAt(i)
					if(now - v.lastUsed >= 10000L) {
						colorFilterCache.removeAt(i)
					}
				}
			}
			
			val f = PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_ATOP)
			colorFilterCache.put(rgb, ColorFilterCacheValue(f, now))
			return f
		}
	}
	
	/////////////////////////////////////////////////////////
	
	private class ColoredDrawableCacheKey(
		val drawableId : Int,
		val rgb : Int,
		val alpha : Int
	) {
		
		override fun equals(other : Any?) : Boolean {
			return this === other || (
				other is ColoredDrawableCacheKey
					&& drawableId == other.drawableId
					&& rgb == other.rgb
					&& alpha == other.alpha
				)
		}
		
		override fun hashCode() : Int {
			return drawableId xor (rgb or (alpha shl 24))
		}
	}
	
	private class ColoredDrawableCacheValue(
		val drawable : Drawable,
		var lastUsed : Long
	)
	
	private val coloredDrawableCache = HashMap<ColoredDrawableCacheKey, ColoredDrawableCacheValue>()
	private var coloredDrawableCacheLastSweep = 0L
	
	fun createColoredDrawable(
		context : Context,
		drawableId : Int,
		color : Int,
		alphaMultiplier : Float? = null
	) : Drawable {
		val rgb = (color and 0xffffff) or Color.BLACK
		val alpha = if(alphaMultiplier == null) {
			(color ushr 24)
		} else {
			clipRange(0, 255, ((color ushr 24).toFloat() * alphaMultiplier + 0.5f).toInt())
		}
		
		val cacheKey = ColoredDrawableCacheKey(drawableId, rgb, alpha)
		synchronized(coloredDrawableCache) {
			val now = SystemClock.elapsedRealtime()
			val cacheValue = coloredDrawableCache[cacheKey]
			if(cacheValue != null) {
				cacheValue.lastUsed = now
				return cacheValue.drawable
			}
			
			if(now - coloredDrawableCacheLastSweep >= 10000L && coloredDrawableCache.size >= 64) {
				coloredDrawableCacheLastSweep = now
				val it = coloredDrawableCache.entries.iterator()
				while(it.hasNext()) {
					val (_, v) = it.next()
					if(now - v.lastUsed >= 10000L) {
						it.remove()
					}
				}
			}
			
			// 色指定が他のアイコンに影響しないようにする
			// カラーフィルターとアルファ値を設定する
			val d = ContextCompat.getDrawable(context, drawableId) !!.mutate()
			d.colorFilter = createColorFilter(rgb)
			d.alpha = alpha
			coloredDrawableCache[cacheKey] = ColoredDrawableCacheValue(d, now)
			return d
		}
	}
	
	//////////////////////////////////////////////////////////////////
	
	fun setIconDrawableId(
		context : Context,
		imageView : ImageView,
		drawableId : Int,
		color : Int? = null,
		alphaMultiplier : Float? = null
	) {
		if(color == null) {
			// ImageViewにアイコンを設定する。デフォルトの色
			imageView.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
		} else {
			imageView.setImageDrawable(
				createColoredDrawable(
					context,
					drawableId,
					color,
					alphaMultiplier
				)
			)
		}
	}
	
	fun setIconAttr(
		context : Context,
		imageView : ImageView,
		iconAttrId : Int,
		color : Int? = null,
		alphaMultiplier : Float? = null
	) {
		setIconDrawableId(
			context,
			imageView,
			getAttributeResourceId(context, iconAttrId),
			color,
			alphaMultiplier
		)
	}
	
	fun getVisibilityIconAttr(isMisskeyData : Boolean, visibility : TootVisibility) : Int {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)) {
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY -> true
			else -> isMisskeyData
		}
		return when {
			isMisskey -> when(visibility) {
				TootVisibility.Public -> R.attr.ic_public
				TootVisibility.UnlistedHome -> R.attr.btn_home
				TootVisibility.PrivateFollowers -> R.attr.ic_lock_open
				TootVisibility.DirectSpecified -> R.attr.ic_mail
				TootVisibility.DirectPrivate -> R.attr.ic_lock
				TootVisibility.WebSetting -> R.attr.ic_question
				
				TootVisibility.LocalPublic -> R.attr.ic_local_ltl
				TootVisibility.LocalHome -> R.attr.ic_local_home
				TootVisibility.LocalFollowers -> R.attr.ic_local_lock_open
				
			}
			else -> when(visibility) {
				TootVisibility.Public -> R.attr.ic_public
				TootVisibility.UnlistedHome -> R.attr.ic_lock_open
				TootVisibility.PrivateFollowers -> R.attr.ic_lock
				TootVisibility.DirectSpecified -> R.attr.ic_mail
				TootVisibility.DirectPrivate -> R.attr.ic_mail
				TootVisibility.WebSetting -> R.attr.ic_question
				
				TootVisibility.LocalPublic -> R.attr.ic_local_ltl
				TootVisibility.LocalHome -> R.attr.ic_local_lock_open
				TootVisibility.LocalFollowers -> R.attr.ic_local_lock
				
			}
		}
	}
	
	fun getVisibilityIcon(
		context : Context,
		isMisskeyData : Boolean,
		visibility : TootVisibility
	) : Int {
		return getAttributeResourceId(context, getVisibilityIconAttr(isMisskeyData, visibility))
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
		
		val icon_id = getVisibilityIcon(context, isMisskeyData, visibility)
		val sv = getVisibilityString(context, isMisskeyData, visibility)
		
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
		, defaultColor : Int
		, alphaMultiplier : Float? = null
	) {
		
		fun colorError() = Styler.getAttributeColor(context, R.attr.colorRegexFilterError)
		fun colorAccent() = Styler.getAttributeColor(context, R.attr.colorImageButtonAccent)
		
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
				setIconAttr(
					context,
					ivDot,
					R.attr.ic_followed_by,
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
		val icon_attr : Int
		val contentDescription : String
		
		when {
			relation.blocking -> {
				icon_attr = R.attr.ic_block
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.muting -> {
				icon_attr = R.attr.ic_mute
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.getFollowing(who) -> {
				icon_attr = R.attr.ic_follow_cross
				color = colorAccent()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			relation.getRequested(who) -> {
				icon_attr = R.attr.ic_follow_wait
				color = colorError()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			else -> {
				icon_attr = R.attr.ic_follow_plus
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
		}
		
		setIconAttr(context, ibFollow, icon_attr, color = color, alphaMultiplier = alphaMultiplier)
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
	var boost_alpha : Float? = null
	
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
