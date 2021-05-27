package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.*

private val rePleromaStatusUrl = """/objects/""".toRegex()

private fun String.likePleromaStatusUrl(): Boolean {
    return rePleromaStatusUrl.find(this) != null
}

// 長押しでない普通のリアクション操作
fun ActMain.reactionAdd(
    column: Column,
    status: TootStatus,

    // Unicode絵文字、 :name: :name@.: :name@domain: name name@domain 等
    codeArg: String? = null,

    urlArg: String? = null,

    // 確認済みなら真
    isConfirmed: Boolean = false
) {
    val activity = this@reactionAdd
    val access_info = column.access_info

    val canMultipleReaction = InstanceCapability.canMultipleReaction(access_info)
    val hasMyReaction = status.reactionSet?.hasMyReaction() == true
    if (hasMyReaction && !canMultipleReaction) {
        showToast(false, R.string.already_reactioned)
        return
    }

    var code = codeArg
    if (code == null) {
        EmojiPicker(activity, access_info, closeOnSelected = true) { result ->
            var newUrl: String? = null
            val newCode: String = when (val emoji = result.emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> {
                    newUrl = emoji.static_url
                    if (access_info.isMisskey) {
                        ":${emoji.shortcode}:"
                    } else {
                        emoji.shortcode
                    }
                }
            }
            reactionAdd(column, status, newCode, newUrl)
        }.show()
        return
    }

    if (access_info.isMisskey) {
        val pair = TootReaction.splitEmojiDomain(code)
        when (/* val domain = */ pair.second) {
            null, "", ".", access_info.apDomain.ascii -> {
                // normalize to local custom emoji
                code = ":${pair.first}:"
            }
            else -> {
                /*
                #misskey のリアクションAPIはリモートのカスタム絵文字のコードをフォールバック絵文字に変更して、
                何の追加情報もなしに204 no contentを返す。
                よってクライアントはAPI応答からフォールバックが発生したことを認識できず、
                後から投稿をリロードするまで気が付かない。
                この挙動はこの挙動は多くのユーザにとって受け入れられないと判断するので、
                クライアント側で事前にエラー扱いにする方が良い。
                */
                showToast(true, R.string.cant_reaction_remote_custom_emoji, code)
                return
            }
        }
    }

    if (canMultipleReaction && TootReaction.isCustomEmoji(code)) {
        showToast(false, "can't reaction with custom emoji from this account")
        return
    }

    if (!isConfirmed) {
        val options = DecodeOptions(
            activity,
            access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )
        val emojiSpan = TootReaction.toSpannableStringBuilder(options, code, urlArg)
        DlgConfirm.open(
            activity,
            getString(R.string.confirm_reaction, emojiSpan, AcctColor.getNickname(access_info)),
            object : DlgConfirm.Callback {
                override var isConfirmEnabled: Boolean
                    get() = access_info.confirm_reaction
                    set(bv) {
                        access_info.confirm_reaction = bv
                        access_info.saveSetting()
                    }

                override fun onOK() {
                    reactionAdd(
                        column,
                        status,
                        codeArg = code,
                        urlArg = urlArg,
                        isConfirmed = true
                    )
                }
            })
        return
    }

    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(access_info) { client ->
            when {
                access_info.isMisskey -> client.request(
                    "/api/notes/reactions/create",
                    access_info.putMisskeyApiToken().apply {
                        put("noteId", status.id.toString())
                        put("reaction", code)
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content

                canMultipleReaction -> client.request(
                    "/api/v1/pleroma/statuses/${status.id}/reactions/${code.encodePercent("@")}",
                    "".toFormRequestBody().toPut()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, access_info).status(result.jsonObject)
                }

                else -> client.request(
                    "/api/v1/statuses/${status.id}/emoji_reactions/${code.encodePercent("@")}",
                    "".toFormRequestBody().toPut()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, access_info).status(result.jsonObject)
                }
            }
        }?.let { result ->

            val error = result.error
            if (error != null) {
                activity.showToast(false, error)
                return@launchMain
            }

            when (val resCode = result.response?.code) {
                in 200 until 300 -> when (val newStatus = resultStatus) {
                    null ->
                        if (status.increaseReactionMisskey(code, true, caller = "addReaction")) {
                            // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                            column.fireShowContent(
                                reason = "addReaction complete",
                                reset = true
                            )
                        }

                    else ->
                        activity.app_state.columnList.forEach { column ->
                            if (column.access_info.acct == access_info.acct)
                                column.updateEmojiReactionByApiResponse(newStatus)
                        }

                }

                else -> showToast(false, "HTTP error $resCode")
            }
        }
    }
}

// 長押しでない普通のリアクション操作
fun ActMain.reactionRemove(
    column: Column,
    status: TootStatus,
    reactionArg: TootReaction? = null,
    confirmed: Boolean = false
) {
    val activity = this
    val access_info = column.access_info

    val canMultipleReaction = InstanceCapability.canMultipleReaction(access_info)

    // 指定されたリアクションまたは自分がリアクションした最初のもの
    val reaction = reactionArg ?: status.reactionSet?.find { it.count > 0 && it.me }
    if (reaction == null) {
        showToast(false, R.string.not_reactioned)
        return
    }

    if (!confirmed) {
        val options = DecodeOptions(
            activity,
            access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )
        val emojiSpan = reaction.toSpannableStringBuilder(options, status)
        AlertDialog.Builder(activity)
            .setMessage(getString(R.string.reaction_remove_confirm, emojiSpan))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                reactionRemove(column, status, reaction, confirmed = true)
            }
            .show()
        return
    }

    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(access_info) { client ->
            when {
                access_info.isMisskey -> client.request(
                    "/api/notes/reactions/delete",
                    access_info.putMisskeyApiToken().apply {
                        put("noteId", status.id.toString())
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content

                canMultipleReaction -> client.request(
                    "/api/v1/pleroma/statuses/${status.id}/reactions/${reaction.name.encodePercent("@")}",
                    "".toFormRequestBody().toDelete()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, access_info).status(result.jsonObject)
                }

                else -> client.request(
                    "/api/v1/statuses/${status.id}/emoji_unreaction",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, access_info).status(result.jsonObject)
                }
            }
        }?.let { result ->
            val resCode = result.response?.code
            when {
                result.error != null ->
                    activity.showToast(false, result.error)

                resCode !in 200 until 300 -> showToast(false, "HTTP error $resCode")
                else -> {
                    when (val newStatus = resultStatus) {
                        null ->
                            if (status.decreaseReactionMisskey(reaction.name, true, "removeReaction")) {
                                // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                                column.fireShowContent(reason = "removeReaction complete", reset = true)
                            }

                        else ->
                            activity.app_state.columnList.forEach { column ->
                                if (column.access_info.acct == access_info.acct)
                                    column.updateEmojiReactionByApiResponse(newStatus)
                            }
                    }
                }
            }
        }
    }
}

// リアクションの別アカ操作で使う
// 選択済みのアカウントと同期済みのステータスにリアクションを行う
private fun ActMain.reactionWithoutUi(
    access_info: SavedAccount,
    resolvedStatus: TootStatus,
    reactionCode: String? = null,
    reactionImage: String? = null,
    isConfirmed: Boolean = false,
    callback: () -> Unit,
) {
    val activity = this

    if (reactionCode == null) {
        EmojiPicker(activity, access_info, closeOnSelected = true) { result ->
            var newUrl: String? = null
            val newCode = when (val emoji = result.emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> {
                    newUrl = emoji.static_url
                    if (access_info.isMisskey) {
                        ":${emoji.shortcode}:"
                    } else {
                        emoji.shortcode
                    }
                }
            }
            reactionWithoutUi(
                access_info = access_info,
                resolvedStatus = resolvedStatus,
                reactionCode = newCode,
                reactionImage = newUrl,
                isConfirmed = isConfirmed,
                callback = callback
            )
        }.show()
        return
    }

    val canMultipleReaction = InstanceCapability.canMultipleReaction(access_info)


    if (!isConfirmed) {
        val options = DecodeOptions(
            activity,
            access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )
        val emojiSpan = TootReaction.toSpannableStringBuilder(options, reactionCode, reactionImage)

        val isCustomEmoji = TootReaction.isCustomEmoji(reactionCode)
        val url = resolvedStatus.url
        when {
            isCustomEmoji && canMultipleReaction -> {
                showToast(false, "can't reaction with custom emoji from this account")
                return
            }
            isCustomEmoji && url?.likePleromaStatusUrl() == true -> DlgConfirm.openSimple(
                activity,
                getString(
                    R.string.confirm_reaction_to_pleroma,
                    emojiSpan,
                    AcctColor.getNickname(access_info),
                    resolvedStatus.account.acct.host?.pretty ?: "(null)"
                ),
            ) {
                reactionWithoutUi(
                    access_info = access_info,
                    resolvedStatus = resolvedStatus,
                    reactionCode = reactionCode,
                    reactionImage = reactionImage,
                    isConfirmed = true,
                    callback = callback
                )
            }

            else -> DlgConfirm.open(
                activity,
                getString(R.string.confirm_reaction, emojiSpan, AcctColor.getNickname(access_info)),
                object : DlgConfirm.Callback {
                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_reaction
                        set(bv) {
                            access_info.confirm_reaction = bv
                            access_info.saveSetting()
                        }

                    override fun onOK() {
                        reactionWithoutUi(
                            access_info = access_info,
                            resolvedStatus = resolvedStatus,
                            reactionCode = reactionCode,
                            reactionImage = reactionImage,
                            isConfirmed = true,
                            callback = callback
                        )
                    }
                })
        }
        return
    }

    launchMain {
        // var resultStatus: TootStatus? = null
        runApiTask(access_info) { client ->
            when {
                access_info.isMisskey -> client.request(
                    "/api/notes/reactions/create",
                    access_info.putMisskeyApiToken().apply {
                        put("noteId", resolvedStatus.id.toString())
                        put("reaction", reactionCode)
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content


                canMultipleReaction -> client.request(
                    "/api/v1/pleroma/statuses/${resolvedStatus.id}/reactions/${reactionCode.encodePercent("@")}",
                    "".toFormRequestBody().toPut()
                ) // 成功すると更新された投稿

                else -> client.request(
                    "/api/v1/statuses/${resolvedStatus.id}/emoji_reactions/${reactionCode.encodePercent()}",
                    "".toFormRequestBody().toPut()
                ) // 成功すると更新された投稿
            }
        }?.let { result ->
            when (val error = result.error) {
                null -> callback()
                else -> showToast(true, error)
            }
        }
    }
}

// リアクションの別アカ操作で使う
// 選択済みのアカウントと同期済みのステータスと同期まえのリアクションから、同期後のリアクションコードを計算する
// 解決できなかった場合はnullを返す
private fun ActMain.reactionFixCode(
    timelineAccount: SavedAccount,
    actionAccount: SavedAccount,
    reaction: TootReaction,
): String? {
    val pair = reaction.splitEmojiDomain()
    pair.first ?: return reaction.name // null または Unicode絵文字

    val srcDomain = when (val d = pair.second) {
        null, ".", "" -> timelineAccount.apDomain
        else -> Host.parse(d)
    }
    // リアクション者から見てローカルな絵文字
    if (srcDomain == actionAccount.apDomain) {
        return when {
            actionAccount.isMisskey -> ":${pair.first}:"
            else -> pair.first
        }
    }
    // リアクション者からみてリモートの絵文字
    val newName = "${pair.first}@${srcDomain}"

    if (actionAccount.isMisskey) {
        /*
        Misskey のリアクションAPIはリモートのカスタム絵文字のコードをフォールバック絵文字に変更して、
        何の追加情報もなしに204 no contentを返す。
        よってクライアントはAPI応答からフォールバックが発生したことを認識できず、
        後から投稿をリロードするまで気が付かない。
        この挙動はこの挙動は多くのユーザにとって受け入れられないと判断するので、
        クライアント側で事前にエラー扱いにする方が良い。
        */
    } else {
        // Fedibirdの場合、ステータスを同期した時点で絵文字も同期されてると期待できるのだろうか？
        // 実際に試してみると
        // nightly.fedibird.comの投稿にローカルな絵文字を付けた後、
        // その投稿のURLをfedibird.comの検索欄にいれてローカルに同期すると、
        // すでにインポート済みの投稿だとリアクション集合は古いままなのだった。
        //
        // if (resolvedStatus.reactionSet?.any { it.name == newName } == true)

        return newName
    }

    // エラー
    showToast(true, R.string.cant_reaction_remote_custom_emoji, newName)
    return null
}

fun ActMain.reactionFromAnotherAccount(
    timeline_account: SavedAccount,
    statusArg: TootStatus?,
    reaction: TootReaction? = null,
) {
    statusArg ?: return
    val activity = this

    fun afterResolveStatus(actionAccount: SavedAccount, resolvedStatus: TootStatus) {
        val code = if (reaction == null) {
            null // あとで選択する
        } else {
            reactionFixCode(
                timelineAccount = timeline_account,
                actionAccount = actionAccount,
                reaction = reaction,
            ) ?: return // エラー終了の場合がある
        }

        reactionWithoutUi(
            access_info = actionAccount,
            resolvedStatus = resolvedStatus,
            reactionCode = code,
            callback = reaction_complete_callback,
        )
    }

    launchMain {

        val list = accountListCanReaction() ?: return@launchMain
        if (list.isEmpty()) {
            showToast(false, R.string.not_available_for_current_accounts)
            return@launchMain
        }

        pickAccount(
            accountListArg = list.toMutableList(),
            bAuto = false,
            message = activity.getString(R.string.account_picker_reaction)
        )?.let { action_account ->
            if (calcCrossAccountMode(timeline_account, action_account).isNotRemote) {
                afterResolveStatus(action_account, statusArg)
            } else {
                var newStatus: TootStatus? = null
                runApiTask(action_account, progressStyle = ApiTask.PROGRESS_NONE) { client ->
                    val (result, status) = client.syncStatus(action_account, statusArg)
                    newStatus = status
                    result
                }?.let { result ->
                    result.error?.let {
                        activity.showToast(true, it)
                        return@launchMain
                    }
                    newStatus?.let { afterResolveStatus(action_account, it) }
                }
            }
        }
    }
}
