package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.fireShowContent
import jp.juggler.subwaytooter.column.updateEmojiReactionByApiResponse
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.launchEmojiPicker
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.accountListCanReaction
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toDelete
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.network.toPut
import okhttp3.Request

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
    isConfirmed: Boolean = false,
) {
    val activity = this@reactionAdd
    val accessInfo = column.accessInfo
    val ti = TootInstance.getCached(accessInfo)

    val maxReactionPerAccount = InstanceCapability.maxReactionPerAccount(accessInfo, ti)
    val myReactionCount = status.reactionSet?.myReactionCount ?: 0

    if (maxReactionPerAccount <= 0) {
        return
    } else if (myReactionCount >= maxReactionPerAccount) {
        showToast(false, R.string.already_reactioned)
        return
    }

    if (codeArg == null) {
        launchEmojiPicker(
            activity,
            accessInfo,
            closeOnSelected = true
        ) { emoji, _ ->
            var newUrl: String? = null
            val newCode: String = when (emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> {
                    newUrl = emoji.staticUrl
                    if (accessInfo.isMisskey) {
                        ":${emoji.shortcode}:"
                    } else {
                        emoji.shortcode
                    }
                }
            }
            reactionAdd(column, status, newCode, newUrl)
        }
        return
    }
    var code = codeArg

    if (accessInfo.isMisskey) {
        val (name, domain) = TootReaction.splitEmojiDomain(code)
        if (name == null) {
            // unicode emoji
        } else when (domain) {
            null, "", ".", accessInfo.apDomain.ascii -> {
                // normalize to local custom emoji
                code = ":$name:"
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

    if (TootReaction.isCustomEmoji(code) && !InstanceCapability.canPostCustomEmojiReaction(
            accessInfo,
            ti
        )
    ) {
        showToast(false, "can't reaction with custom emoji from this account")
        return
    }

    activity.launchAndShowError {

        if (!isConfirmed) {
            val options = DecodeOptions(
                activity,
                accessInfo,
                decodeEmoji = true,
                enlargeEmoji = DecodeOptions.emojiScaleReaction,
                enlargeCustomEmoji = DecodeOptions.emojiScaleReaction,
                emojiSizeMode = accessInfo.emojiSizeMode(),
            )
            val emojiSpan = TootReaction.toSpannableStringBuilder(options, code, urlArg)
            confirm(
                getString(
                    R.string.confirm_reaction,
                    emojiSpan,
                    daoAcctColor.getNickname(accessInfo)
                ),
                accessInfo.confirmReaction,
            ) { newConfirmEnabled ->
                accessInfo.confirmReaction = newConfirmEnabled
                daoSavedAccount.save(accessInfo)
            }
        }

        var resultStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            when {
                accessInfo.isMisskey -> client.request(
                    "/api/notes/reactions/create",
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", status.id.toString())
                        put("reaction", code)
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content

                ti?.pleromaFeatures != null -> client.request(
                    "/api/v1/pleroma/statuses/${status.id}/reactions/${code.encodePercent("@")}",
                    "".toFormRequestBody().toPut()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, accessInfo).status(result.jsonObject)
                }

                else -> client.request(
                    "/api/v1/statuses/${status.id}/emoji_reactions/${code.encodePercent("@")}",
                    "".toFormRequestBody().toPut()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, accessInfo).status(result.jsonObject)
                }
            }
        }?.let { result ->
            result.error?.let { error(it) }

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
                        activity.appState.columnList.forEach { column ->
                            if (column.accessInfo.acct == accessInfo.acct) {
                                column.updateEmojiReactionByApiResponse(newStatus)
                            }
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
) {
    val activity = this
    val accessInfo = column.accessInfo

    val ti = TootInstance.getCached(accessInfo)

    // 指定されたリアクションまたは自分がリアクションした最初のもの
    val reaction = reactionArg ?: status.reactionSet?.find { it.count > 0 && it.me }
    if (reaction == null) {
        showToast(false, R.string.not_reactioned)
        return
    }

    launchAndShowError {
        // 確認
        val options = DecodeOptions(
            activity,
            accessInfo,
            decodeEmoji = true,
            enlargeEmoji = DecodeOptions.emojiScaleReaction,
            enlargeCustomEmoji = DecodeOptions.emojiScaleReaction,
            emojiSizeMode = accessInfo.emojiSizeMode(),
        )
        val emojiSpan = reaction.toSpannableStringBuilder(options, status)
        confirm(R.string.reaction_remove_confirm, emojiSpan)
        // 削除
        var resultStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            when {
                accessInfo.isMisskey -> client.request(
                    "/api/notes/reactions/delete",
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", status.id.toString())
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content

                ti?.pleromaFeatures != null -> client.request(
                    "/api/v1/pleroma/statuses/${status.id}/reactions/${reaction.name.encodePercent("@")}",
                    "".toFormRequestBody().toDelete()
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, accessInfo).status(result.jsonObject)
                }

                else -> client.request(
                    "/api/v1/statuses/${status.id}/emoji_reactions/${reaction.name.encodePercent("@")}",
                    Request.Builder().delete(),
                )?.also { result ->
                    // 成功すると新しいステータス
                    resultStatus = TootParser(activity, accessInfo).status(result.jsonObject)
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
                            if (status.decreaseReactionMisskey(
                                    reaction.name,
                                    true,
                                    "removeReaction"
                                )
                            ) {
                                // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                                column.fireShowContent(
                                    reason = "removeReaction complete",
                                    reset = true
                                )
                            }

                        else ->
                            activity.appState.columnList.forEach { column ->
                                if (column.accessInfo.acct == accessInfo.acct) {
                                    column.updateEmojiReactionByApiResponse(newStatus)
                                }
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
    accessInfo: SavedAccount,
    resolvedStatus: TootStatus,
    reactionCode: String? = null,
    reactionImage: String? = null,
    callback: () -> Unit,
) {
    val activity = this

    if (reactionCode == null) {
        launchEmojiPicker(activity, accessInfo, closeOnSelected = true) { emoji, _ ->
            var newUrl: String? = null
            val newCode = when (emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> {
                    newUrl = emoji.staticUrl
                    if (accessInfo.isMisskey) {
                        ":${emoji.shortcode}:"
                    } else {
                        emoji.shortcode
                    }
                }
            }
            reactionWithoutUi(
                accessInfo = accessInfo,
                resolvedStatus = resolvedStatus,
                reactionCode = newCode,
                reactionImage = newUrl,
                callback = callback
            )
        }
        return
    }

    val ti = TootInstance.getCached(accessInfo)

    val options = DecodeOptions(
        activity,
        accessInfo,
        decodeEmoji = true,
        enlargeEmoji = DecodeOptions.emojiScaleReaction,
        enlargeCustomEmoji = DecodeOptions.emojiScaleReaction,
        emojiSizeMode = accessInfo.emojiSizeMode(),
    )
    val emojiSpan = TootReaction.toSpannableStringBuilder(options, reactionCode, reactionImage)
    val isCustomEmoji = TootReaction.isCustomEmoji(reactionCode)
    val url = resolvedStatus.url

    launchAndShowError {
        when {
            isCustomEmoji && !InstanceCapability.canPostCustomEmojiReaction(accessInfo, ti) ->
                error("can't use custom emoji for reaction from this account.")

            isCustomEmoji && url?.likePleromaStatusUrl() == true -> confirm(
                R.string.confirm_reaction_to_pleroma,
                emojiSpan,
                daoAcctColor.getNickname(accessInfo),
                resolvedStatus.account.acct.host?.pretty ?: "(null)"
            )

            else -> confirm(
                getString(
                    R.string.confirm_reaction,
                    emojiSpan,
                    daoAcctColor.getNickname(accessInfo)
                ),
                accessInfo.confirmReaction,
            ) { newConfirmEnabled ->
                accessInfo.confirmReaction = newConfirmEnabled
                daoSavedAccount.save(accessInfo)
            }
        }

        // var resultStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            when {
                accessInfo.isMisskey -> client.request(
                    "/api/notes/reactions/create",
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", resolvedStatus.id.toString())
                        put("reaction", reactionCode)
                    }.toPostRequestBuilder()
                ) // 成功すると204 no content

                ti?.pleromaFeatures != null -> client.request(
                    "/api/v1/pleroma/statuses/${resolvedStatus.id}/reactions/${
                        reactionCode.encodePercent("@")
                    }",
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
    val newName = "${pair.first}@$srcDomain"

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
    timelineAccount: SavedAccount,
    statusArg: TootStatus?,
    reaction: TootReaction? = null,
) {
    statusArg ?: return
    val activity = this

    launchMain {

        fun afterResolveStatus(
            actionAccount: SavedAccount,
            resolvedStatus: TootStatus,
        ) {
            val code = if (reaction == null) {
                null // あとで選択する
            } else {
                reactionFixCode(
                    timelineAccount = timelineAccount,
                    actionAccount = actionAccount,
                    reaction = reaction,
                ) ?: return // エラー終了の場合がある
            }

            reactionWithoutUi(
                accessInfo = actionAccount,
                resolvedStatus = resolvedStatus,
                reactionCode = code,
                callback = reactionCompleteCallback,
            )
        }

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
            if (calcCrossAccountMode(timelineAccount, action_account).isNotRemote) {
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

fun ActMain.clickReaction(accessInfo: SavedAccount, column: Column, status: TootStatus) {
    val activity = this
    launchMain {
        try {
            val myReactionCount = status.reactionSet?.myReactionCount ?: 0
            val maxReactionPerAccount = InstanceCapability.maxReactionPerAccount(accessInfo)
            when {
                !TootReaction.canReaction(accessInfo) ->
                    reactionFromAnotherAccount(
                        accessInfo,
                        status
                    )

                myReactionCount >= maxReactionPerAccount ->
                    activity.showToast(
                        true,
                        getString(R.string.exceed_reaction_per_account, maxReactionPerAccount)
                    )

                else ->
                    reactionAdd(column, status)
            }
        } catch (ex: Throwable) {
            showToast(ex, "clickReaction failed.")
        }
    }
}
