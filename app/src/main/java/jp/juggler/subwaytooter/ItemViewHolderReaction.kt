package jp.juggler.subwaytooter

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import org.jetbrains.anko.allCaps

fun ItemViewHolder.makeReactionsView(status: TootStatus) {
    val myReaction = status.reactionSet?.myReaction
    val reactionSet = status.reactionSet?.filter { it.count > 0 }

    if (reactionSet?.isEmpty() != false){
        if( !TootReaction.canReaction(access_info) || !Pref.bpKeepReactionSpace(activity.pref) ) return
    }

    val density = activity.density

    val buttonHeight = ActMain.boostButtonSize
    val marginBetween = (buttonHeight.toFloat() * 0.05f + 0.5f).toInt()

    val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

    val act = this@makeReactionsView.activity // not Button(View).getActivity()

    val box = FlexboxLayout(activity).apply {
        flexWrap = FlexWrap.WRAP
        justifyContent = JustifyContent.FLEX_START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (0.5f + density * 3f).toInt()
        }
    }

//    // +/- ボタン
//    box.addView(ImageButton(act).also { b ->
//        b.layoutParams = FlexboxLayout.LayoutParams(
//            buttonHeight,
//            buttonHeight
//        ).apply {
//            endMargin = marginBetween
//        }
//
//        b.background = ContextCompat.getDrawable(
//            activity,
//            R.drawable.btn_bg_transparent_round6dp
//        )
//
//        val myReaction = status.reactionSet?.myReaction
//
//        b.contentDescription = activity.getString(
//            if (myReaction != null)
//                R.string.reaction_remove
//            else
//                R.string.reaction_add
//        )
//        b.scaleType = ImageView.ScaleType.FIT_CENTER
//        b.padding = paddingV
//
//        b.setOnClickListener {
//            if (!TootReaction.canReaction(access_info)) {
//                Action_Toot.reactionFromAnotherAccount(
//                    activity,
//                    access_info,
//                    status_showing
//                )
//            } else if (myReaction != null) {
//                Action_Toot.removeReaction(activity, column, status)
//            } else {
//                Action_Toot.addReaction(activity, column, status)
//            }
//        }
//
//        b.setOnLongClickListener {
//            Action_Toot.reactionFromAnotherAccount(
//                activity,
//                access_info,
//                status_showing
//            )
//            true
//        }
//
//        setIconDrawableId(
//            act,
//            b,
//            if (myReaction != null)
//                R.drawable.ic_remove
//            else
//                R.drawable.ic_add,
//            color = content_color,
//            alphaMultiplier = Styler.boost_alpha
//        )
//    })

    if( reactionSet?.isEmpty() != false){
        val v = View(act).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                buttonHeight,
                buttonHeight
            )
            setPadding(paddingH, paddingV, paddingH, paddingV)
        }
        box.addView(v)
    }

    val options = DecodeOptions(
        act,
        access_info,
        decodeEmoji = true,
        enlargeEmoji = 1.5f,
        enlargeCustomEmoji = 1.5f
    )

    var lastButton: View? = null

    reactionSet?.forEachIndexed { index, reaction ->
        val ssb = reaction.toSpannableStringBuilder(options, status)
            .also { it.append(" ${reaction.count}") }

        val b = Button(act).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                if(index >0 ) startMargin = marginBetween
            }
            minWidthCompat = buttonHeight

            background = if (reaction == myReaction) {
                // 自分がリアクションしたやつは背景を変える
                getAdaptiveRippleDrawableRound(
                    act,
                    Pref.ipButtonReactionedColor(act.pref).notZero() ?: act.attrColor(R.attr.colorImageButtonAccent),
                    act.attrColor(R.attr.colorRippleEffect),
                    roundNormal = true
                )
            } else {
                ContextCompat.getDrawable(
                    act,
                    R.drawable.btn_bg_transparent_round6dp
                )
            }

            setTextColor(content_color)
            setPadding(paddingH, paddingV, paddingH, paddingV)

            text = ssb

            allCaps = false
            tag = reaction
            setOnClickListener {
                val taggedReaction = it.tag as? TootReaction
                if (taggedReaction == status.reactionSet?.myReaction) {
                    Action_Toot. removeReaction(act, column, status)
                } else {
                    Action_Toot. addReaction(act, column, status, taggedReaction?.name)
                }
            }

            setOnLongClickListener {
                val taggedReaction = it.tag as? TootReaction
                Action_Toot.reactionFromAnotherAccount(
                    this@makeReactionsView.activity,
                    access_info,
                    status_showing,
                    taggedReaction
                )
                true
            }
            // カスタム絵文字の場合、アニメーション等のコールバックを処理する必要がある
            val invalidator = NetworkEmojiInvalidator(act.handler, this)
            invalidator.register(ssb)
            extra_invalidator_list.add(invalidator)
        }
        box.addView(b)
        lastButton = b
    }

    lastButton
        ?.layoutParams
        ?.cast<ViewGroup.MarginLayoutParams>()
        ?.endMargin = 0

    llExtra.addView(box)
}

