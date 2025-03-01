package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.createSpan
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.fixColor
import jp.juggler.util.ui.mixColor
import jp.juggler.util.ui.scan
import jp.juggler.util.ui.setIconDrawableId
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max
import kotlin.math.min

private val log = LogCategory("Styler")

fun defaultColorIcon(context: Context, iconId: Int): Drawable? =
    ContextCompat.getDrawable(context, iconId)?.also {
        it.setTint(context.attrColor(R.attr.colorTextContent))
        it.setTintMode(PorterDuff.Mode.SRC_IN)
    }

fun TootVisibility.getVisibilityIconId(isMisskeyData: Boolean): Int {
    val isMisskey = when (PrefI.ipVisibilityStyle.value) {
        PrefI.VS_MASTODON -> false
        PrefI.VS_MISSKEY -> true
        else -> isMisskeyData
    }
    return when {
        isMisskey -> when (this) {
            TootVisibility.Public -> R.drawable.ic_public
            TootVisibility.UnlistedHome -> R.drawable.ic_home
            TootVisibility.PrivateFollowers -> R.drawable.ic_lock_open
            TootVisibility.DirectSpecified -> R.drawable.ic_mail
            TootVisibility.DirectPrivate -> R.drawable.ic_lock
            TootVisibility.WebSetting -> R.drawable.ic_question
            TootVisibility.AccountSetting -> R.drawable.ic_question

            TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
            TootVisibility.LocalHome -> R.drawable.ic_local_home
            TootVisibility.LocalFollowers -> R.drawable.ic_local_lock_open

            TootVisibility.Unknown -> R.drawable.ic_question
            TootVisibility.Limited -> R.drawable.ic_account_circle
            TootVisibility.Mutual -> R.drawable.ic_bidirectional
        }

        else -> when (this) {
            TootVisibility.Public -> R.drawable.ic_public
            TootVisibility.UnlistedHome -> R.drawable.ic_lock_open
            TootVisibility.PrivateFollowers -> R.drawable.ic_lock
            TootVisibility.DirectSpecified -> R.drawable.ic_mail
            TootVisibility.DirectPrivate -> R.drawable.ic_mail
            TootVisibility.WebSetting -> R.drawable.ic_question
            TootVisibility.AccountSetting -> R.drawable.ic_question

            TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
            TootVisibility.LocalHome -> R.drawable.ic_local_lock_open
            TootVisibility.LocalFollowers -> R.drawable.ic_local_lock

            TootVisibility.Unknown -> R.drawable.ic_question
            TootVisibility.Limited -> R.drawable.ic_account_circle
            TootVisibility.Mutual -> R.drawable.ic_bidirectional
        }
    }
}

fun TootVisibility.getVisibilityString(isMisskeyData: Boolean): String {
    val isMisskey = when (PrefI.ipVisibilityStyle.value) {
        PrefI.VS_MASTODON -> false
        PrefI.VS_MISSKEY -> true
        else -> isMisskeyData
    }
    return lazyContext.getString(
        when {
            isMisskey -> when (this) {
                TootVisibility.Public -> R.string.visibility_public
                TootVisibility.UnlistedHome -> R.string.visibility_home
                TootVisibility.PrivateFollowers -> R.string.visibility_followers
                TootVisibility.DirectSpecified -> R.string.visibility_direct
                TootVisibility.DirectPrivate -> R.string.visibility_private
                TootVisibility.WebSetting -> R.string.visibility_web_setting
                TootVisibility.AccountSetting -> R.string.visibility_account_setting

                TootVisibility.LocalPublic -> R.string.visibility_local_public
                TootVisibility.LocalHome -> R.string.visibility_local_home
                TootVisibility.LocalFollowers -> R.string.visibility_local_followers

                TootVisibility.Unknown -> R.string.visibility_unknown
                TootVisibility.Limited -> R.string.visibility_limited
                TootVisibility.Mutual -> R.string.visibility_mutual
            }

            else -> when (this) {
                TootVisibility.Public -> R.string.visibility_public
                TootVisibility.UnlistedHome -> R.string.visibility_unlisted
                TootVisibility.PrivateFollowers -> R.string.visibility_followers
                TootVisibility.DirectSpecified -> R.string.visibility_direct
                TootVisibility.DirectPrivate -> R.string.visibility_direct
                TootVisibility.WebSetting -> R.string.visibility_web_setting
                TootVisibility.AccountSetting -> R.string.visibility_account_setting

                TootVisibility.LocalPublic -> R.string.visibility_local_public
                TootVisibility.LocalHome -> R.string.visibility_local_unlisted
                TootVisibility.LocalFollowers -> R.string.visibility_local_followers

                TootVisibility.Unknown -> R.string.visibility_unknown
                TootVisibility.Limited -> R.string.visibility_limited
                TootVisibility.Mutual -> R.string.visibility_mutual
            }
        }
    )
}

// アイコン付きの装飾テキストを返す
fun getVisibilityCaption(
    context: Context,
    isMisskeyData: Boolean,
    visibility: TootVisibility,
): CharSequence {

    val iconId = visibility.getVisibilityIconId(isMisskeyData)
    val sv = visibility.getVisibilityString(isMisskeyData)
    val color = context.attrColor(R.attr.colorTextContent)
    val sb = SpannableStringBuilder()

    // アイコン部分
    val start = sb.length
    sb.append(" ")
    val end = sb.length
    sb.setSpan(
        EmojiImageSpan(
            context,
            iconId,
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
    context: Context,
    ibFollow: ImageButton,
    ivDot: ImageView,
    relation: UserRelation,
    who: TootAccount,
    defaultColor: Int,
    alphaMultiplier: Float,
) {
    val colorFollowed =
        PrefI.ipButtonFollowingColor.value.notZero()
            ?: context.attrColor(R.attr.colorButtonAccentFollow)

    val colorFollowRequest =
        PrefI.ipButtonFollowRequestColor.value.notZero()
            ?: context.attrColor(R.attr.colorButtonAccentFollowRequest)

    val colorError = context.attrColor(R.attr.colorRegexFilterError)

    // 被フォロー状態
    when {

        relation.blocked_by -> {
            ivDot.visibility = View.VISIBLE
            setIconDrawableId(
                context,
                ivDot,
                R.drawable.ic_blocked_by,
                color = colorError,
                alphaMultiplier = alphaMultiplier
            )
        }

        relation.requested_by -> {
            ivDot.visibility = View.VISIBLE
            setIconDrawableId(
                context,
                ivDot,
                R.drawable.ic_requested_by,
                color = colorFollowRequest,
                alphaMultiplier = alphaMultiplier
            )
        }

        relation.followed_by -> {
            ivDot.visibility = View.VISIBLE
            setIconDrawableId(
                context,
                ivDot,
                R.drawable.ic_follow_dot,
                color = colorFollowed,
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
    val color: Int
    val iconId: Int
    val contentDescription: String

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
            color = colorFollowed
            contentDescription = context.getString(R.string.unfollow)
        }

        relation.getRequested(who) -> {
            iconId = R.drawable.ic_follow_wait
            color = colorFollowRequest
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

private fun getHorizontalPadding(v: View, dpDelta: Float): Int {
    // Essential Phone PH-1は 短辺439dp
    val formWidthMax = 460f
    val dm = v.resources.displayMetrics
    val screenW = dm.widthPixels
    val contentW = (0.5f + formWidthMax * dm.density).toInt()
    val padW = max(0, (screenW - contentW) / 2)
    return padW + (0.5f + dpDelta * dm.density).toInt()
}

private fun getOrientationString(orientation: Int?) = when (orientation) {
    null -> "null"
    Configuration.ORIENTATION_LANDSCAPE -> "landscape"
    Configuration.ORIENTATION_PORTRAIT -> "portrait"
    Configuration.ORIENTATION_UNDEFINED -> "undefined"
    else -> orientation.toString()
}

fun fixHorizontalPadding(v: View, dpDelta: Float = 12f) {
    val padT = v.paddingTop
    val padB = v.paddingBottom

    val dm = v.resources.displayMetrics
    val widthDp = dm.widthPixels / dm.density
    if (widthDp >= 640f && v.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val padLr = (0.5f + dpDelta * dm.density).toInt()
        when (PrefI.ipJustifyWindowContentPortrait.value) {
            PrefI.JWCP_START -> {
                v.setPaddingRelative(padLr, padT, padLr + dm.widthPixels / 2, padB)
                return
            }

            PrefI.JWCP_END -> {
                v.setPaddingRelative(padLr + dm.widthPixels / 2, padT, padLr, padB)
                return
            }

            else -> Unit
        }
    }

    val padLr = getHorizontalPadding(v, dpDelta)
    v.setPaddingRelative(padLr, padT, padLr, padB)
}

fun fixHorizontalMargin(v: View) {
    val lp = v.layoutParams
    if (lp is ViewGroup.MarginLayoutParams) {

        val dm = v.resources.displayMetrics
        val orientationString = getOrientationString(v.resources?.configuration?.orientation)
        val widthDp = dm.widthPixels / dm.density
        log.d("fixHorizontalMargin: orientation=$orientationString, w=${widthDp}dp, h=${dm.heightPixels / dm.density}")

        if (widthDp >= 640f && v.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            when (PrefI.ipJustifyWindowContentPortrait.value) {
                PrefI.JWCP_START -> {
                    lp.marginStart = 0
                    lp.marginEnd = dm.widthPixels / 2
                    return
                }

                PrefI.JWCP_END -> {
                    lp.marginStart = dm.widthPixels / 2
                    lp.marginEnd = 0
                    return
                }
            }
        }

        val padLr = getHorizontalPadding(v, 0f)
        lp.leftMargin = padLr
        lp.rightMargin = padLr
    }
}

// ActMainの初期化時に更新される
var stylerRoundRatio: Float = 0.33f * 0.5f
var stylerBoostAlpha: Float = 1f

fun calcIconRound(wh: Int) = wh.toFloat() * stylerRoundRatio

fun calcIconRound(lp: ViewGroup.LayoutParams) =
    min(lp.width, lp.height).toFloat() * stylerRoundRatio

fun SpannableStringBuilder.appendColorShadeIcon(
    context: Context,
    @DrawableRes drawableId: Int,
    text: String,
    color: Int? = null,
): SpannableStringBuilder {
    val start = this.length
    this.append(text)
    val end = this.length
    this.setSpan(
        EmojiImageSpan(context, drawableId, useColorShader = true, color = color),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return this
}

fun SpannableStringBuilder.appendMisskeyReaction(
    context: Context,
    emojiUtf16: String,
    text: String,
): SpannableStringBuilder {

    val emoji = EmojiMap.unicodeMap[emojiUtf16]
    when {
        emoji == null ->
            append("text")

        PrefB.bpUseTwemoji.value -> {
            val start = this.length
            append(text)
            val end = this.length
            this.setSpan(
                emoji.createSpan(context),
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        else ->
            this.append(emoji.unifiedCode)
    }
    return this
}

fun Context.setSwitchColor(root: View?) {
    root ?: return
    val colorBg = attrColor(R.attr.colorMainBackground)
    val colorOff = attrColor(R.attr.colorSwitchOff)
    val colorOn = PrefI.ipSwitchOnColor.value

    val colorDisabled = mixColor(colorBg, colorOff)

    val colorTrackDisabled = mixColor(colorBg, colorDisabled)
    val colorTrackOn = mixColor(colorBg, colorOn)
    val colorTrackOff = mixColor(colorBg, colorOff)

    // https://stackoverflow.com/a/25635526/9134243
    val thumbStates = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        ),
        intArrayOf(
            colorDisabled,
            colorOn,
            colorOff
        )
    )

    val trackStates = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        ),
        intArrayOf(
            colorTrackDisabled,
            colorTrackOn,
            colorTrackOff
        )
    )

    root.scan {
        (it as? SwitchCompat)?.apply {
            thumbTintList = thumbStates
            trackTintList = trackStates
        }
    }
}

fun ViewGroup.generateLayoutParamsEx(): ViewGroup.LayoutParams? =
    try {
        val parser = resources.getLayout(R.layout.generate_params)
        // Skip everything until the view tag.
        while (true) {
            val token = parser.nextToken()
            if (token == XmlPullParser.START_TAG) break
        }
        generateLayoutParams(parser)
    } catch (ex: Throwable) {
        log.e(ex, "generateLayoutParamsEx failed")
        null
    }


fun ComponentActivity.enableEdgeToEdgeEx(forceDark: Boolean) {
    var nTheme = PrefI.ipUiTheme.value
    if (forceDark && nTheme == 0) nTheme = 1
    val isLightTheme = when (nTheme) {
        2 -> false // R.style.AppTheme_Mastodon
        1 -> false // R.style.AppTheme_Dark
        /* 0 */ else -> true // R.style.AppTheme_Light
    }
    val colorBarBg = when{
        forceDark -> Color.BLACK
        // API 28以下では色を指定できる
        else -> PrefI.ipWindowInsetsColor.value.notZero()
            ?: attrColor(R.attr.colorWindowInsetsBg)
    }

    val barStyle =when {
        isLightTheme -> SystemBarStyle.light(
            // API 28以下では色を指定できる
            // bg color used when light theme that have dark icon, should use light color.
            scrim = fixColor(src = colorBarBg, lExpect = 1f),
            // bg color used when light theme that have LIGHT icon, should use dark color.
            darkScrim = fixColor(src = colorBarBg, lExpect = 0f),
        )
        else -> SystemBarStyle.dark(
            // API 28以下では色を指定できる
            // bg color used when dark theme that have LIGHT icon, should use dark color.
            scrim = fixColor(src = colorBarBg, lExpect = 0f),
        )
    }
    enableEdgeToEdge(
        statusBarStyle = barStyle,
        navigationBarStyle = barStyle,
    )

    window?.apply{
        // API29以降でinsets部分の色を指定するのは基本的にコレ
        setBackgroundDrawable(ColorDrawable(colorBarBg))

        // 3ボタンナビゲーションで80% 不透明の背景が追加される挙動を無効化する
        if(Build.VERSION.SDK_INT >= 29){
            isNavigationBarContrastEnforced = false
        }
        // ステータスバーに80% 不透明の背景が追加される挙動を無効化する
        if(Build.VERSION.SDK_INT in 29..34 ){
            @Suppress("DEPRECATION")
            isStatusBarContrastEnforced = false
        }
    }
}
