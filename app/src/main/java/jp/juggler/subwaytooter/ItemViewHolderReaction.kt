package jp.juggler.subwaytooter

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.endMargin
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.util.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.padding

fun ItemViewHolder.canReaction() = when {
    access_info.isPseudo -> false
    access_info.isMisskey -> true
    TootInstance.getCached(access_info.apiHost)?.fedibird_capabilities?.contains("emoji_reaction") == true -> true
    else -> false
}

fun ItemViewHolder.makeReactionsView(status: TootStatus) {
    if (!canReaction() && status.reactionSet == null) return

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

    // +/- ボタン
    box.addView(ImageButton(act).also { b ->
        b.layoutParams = FlexboxLayout.LayoutParams(
            buttonHeight,
            buttonHeight
        ).apply {
            endMargin = marginBetween
        }

        b.background = ContextCompat.getDrawable(
            activity,
            R.drawable.btn_bg_transparent_round6dp
        )

        val myReaction = status.reactionSet?.myReaction

        b.contentDescription = activity.getString(
            if (myReaction != null)
                R.string.reaction_remove
            else
                R.string.reaction_add
        )
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.padding = paddingV

        b.setOnClickListener {
            if (!canReaction()) {
                Action_Toot.reactionFromAnotherAccount(
                    activity,
                    access_info,
                    status_showing
                )
            } else if (myReaction != null) {
                removeReaction(status, false)
            } else {
                addReaction(status, null)
            }
        }

        b.setOnLongClickListener {
            Action_Toot.reactionFromAnotherAccount(
                activity,
                access_info,
                status_showing
            )
            true
        }

        setIconDrawableId(
            act,
            b,
            if (myReaction != null)
                R.drawable.ic_remove
            else
                R.drawable.ic_add,
            color = content_color,
            alphaMultiplier = Styler.boost_alpha
        )
    })

    val reactionSet = status.reactionSet
    if (reactionSet != null) {

        var lastButton: View? = null

        val options = DecodeOptions(
            act,
            access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )

        for (reaction in reactionSet) {
            if (reaction.count <= 0L) continue

            val ssb = reaction.toSpannableStringBuilder(options, status)
                .also { it.append(" ${reaction.count}") }

            val b = Button(act).apply {
                layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    buttonHeight
                ).apply {
                    endMargin = marginBetween
                }
                minWidthCompat = buttonHeight

                background = if (reaction == reactionSet.myReaction) {
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
                        removeReaction(status, false)
                    } else {
                        addReaction(status, taggedReaction?.name)
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
    }

    llExtra.addView(box)
}

// code は code@dmain のような形式かもしれない
private fun ItemViewHolder.addReaction(status: TootStatus, code: String?) {
    if (status.reactionSet?.myReaction != null) {
        activity.showToast(false, R.string.already_reactioned)
        return
    }

    if (!canReaction()) return

    if (code == null) {
        EmojiPicker(activity, access_info, closeOnSelected = true) { result ->
            addReaction(
                status, when (val emoji = result.emoji) {
                    is UnicodeEmoji -> emoji.unifiedCode
                    is CustomEmoji -> if (access_info.isMisskey) {
                        ":${emoji.shortcode}:"
                    } else {
                        emoji.shortcode
                    }
                    else -> error("unknown emoji type")
                }
            )
        }.show()
        return
    }else if( access_info.isMisskey && code.contains("@")){
        val cols = code.replace(":","").split("@")
        when( /* val domain = */ cols.elementAtOrNull(1)){
            null,"",".",access_info.apDomain.ascii -> {
                val name = cols.elementAtOrNull(0)
                addReaction(status,":$name:")
                return
            }
            /*
            #misskey のリアクションAPIはリモートのカスタム絵文字のコードをフォールバック絵文字に変更して、
            何の追加情報もなしに204 no contentを返す。
            よってクライアントはAPI応答からフォールバックが発生したことを認識できず、
            後から投稿をリロードするまで気が付かない。
            この挙動はこの挙動は多くのユーザにとって受け入れられないと判断するので、
            クライアント側で事前にエラー扱いにする方が良い。
            */
            else -> {
                activity.showToast(true,R.string.cant_reaction_remote_custom_emoji,code)
                return
            }
        }
    }

    TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
        object : TootTask {

            var newStatus: TootStatus? = null

            override suspend fun background(client: TootApiClient): TootApiResult? {
                return if (access_info.isMisskey) {
                    client.request("/api/notes/reactions/create", access_info.putMisskeyApiToken().apply {
                        put("noteId", status.id.toString())
                        put("reaction", code)
                    }.toPostRequestBuilder())
                    // 成功すると204 no content
                } else {
                    client.request(
                        "/api/v1/statuses/${status.id}/emoji_reactions/${code.encodePercent("@")}",
                        "".toFormRequestBody().toPut()
                    )
                        // 成功すると新しいステータス
                        ?.also { result ->
                            newStatus = TootParser(activity, access_info).status(result.jsonObject)
                        }
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return

                val error = result.error
                if (error != null) {
                    activity.showToast(false, error)
                    return
                }
                when (val resCode = result.response?.code) {
                    in 200 until 300 -> {
                        if (newStatus != null) {
                            activity.app_state.columnList.forEach { column ->
                                if (column.access_info.acct == access_info.acct)
                                    column.updateEmojiReactionByApiResponse(newStatus)
                            }
                        } else {
                            if (status.increaseReactionMisskey(code, true, caller = "addReaction")) {
                                // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                                list_adapter.notifyChange(reason = "addReaction complete", reset = true)
                            }
                        }
                    }
                    else -> activity.showToast(false, "HTTP error $resCode")
                }
            }
        })
}

private fun ItemViewHolder.removeReaction(status: TootStatus, confirmed: Boolean = false) {

    val myReaction = status.reactionSet?.myReaction

    if (myReaction == null) {
        activity.showToast(false, R.string.not_reactioned)
        return
    }

    if (!canReaction()) return

    if (!confirmed) {
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.reaction_remove_confirm, myReaction.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                removeReaction(status, confirmed = true)
            }
            .show()
        return
    }

    TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
        object : TootTask {

            var newStatus: TootStatus? = null

            override suspend fun background(client: TootApiClient): TootApiResult? =
                if (access_info.isMisskey) {
                    client.request(
                        "/api/notes/reactions/delete",
                        access_info.putMisskeyApiToken().apply {
                            put("noteId", status.id.toString())
                        }
                            .toPostRequestBuilder()
                    )
                    // 成功すると204 no content
                } else {
                    client.request(
                        "/api/v1/statuses/${status.id}/emoji_unreaction",
                        "".toFormRequestBody().toPost()
                    )
                        // 成功すると新しいステータス
                        ?.also { result ->
                            newStatus = TootParser(activity, access_info).status(result.jsonObject)
                        }
                }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return

                val error = result.error
                if (error != null) {
                    activity.showToast(false, error)
                    return
                }

                if ((result.response?.code ?: -1) in 200 until 300) {
                    if (newStatus != null) {
                        activity.app_state.columnList.forEach { column ->
                            if (column.access_info.acct == access_info.acct)
                                column.updateEmojiReactionByApiResponse(newStatus)
                        }
                    } else {
                        if (status.decreaseReactionMisskey(
                                myReaction.name,
                                true,
                                "removeReaction"
                            )
                        ) {
                            // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                            list_adapter.notifyChange(
                                reason = "removeReaction complete",
                                reset = true
                            )
                        }
                    }
                }
            }
        })
}
