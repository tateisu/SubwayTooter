package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.*

object Action_Reaction {

    // 長押しでない普通のリアクション操作
    fun addReaction(
        activity: ActMain,
        column: Column,
        status: TootStatus,

        // Unicode絵文字、 :name: :name@.: :name@domain: name name@domain 等
        codeArg: String? = null,

        urlArg: String? = null,

        // 確認済みなら真
        isConfirmed: Boolean = false
    ) {
        if (status.reactionSet?.myReaction != null) {
            activity.showToast(false, R.string.already_reactioned)
            return
        }

        val access_info = column.access_info

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
                addReaction(activity, column, status, newCode, newUrl)
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
                    activity.showToast(true, R.string.cant_reaction_remote_custom_emoji, code)
                    return
                }
            }
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
                activity.getString(R.string.confirm_reaction, emojiSpan, AcctColor.getNickname(access_info)),
                object : DlgConfirm.Callback {
                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_reaction
                        set(bv) {
                            access_info.confirm_reaction = bv
                            access_info.saveSetting()
                        }

                    override fun onOK() {
                        addReaction(
                            activity,
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
                                    column.fireShowContent(
                                        reason = "addReaction complete",
                                        reset = true
                                    )
                                }
                            }
                        }
                        else -> activity.showToast(false, "HTTP error $resCode")
                    }
                }
            })
    }

    // 長押しでない普通のリアクション操作
    fun removeReaction(
        activity: ActMain,
        column: Column,
        status: TootStatus,
        confirmed: Boolean = false
    ) {
        val access_info = column.access_info

        val myReaction = status.reactionSet?.myReaction

        if (myReaction == null) {
            activity.showToast(false, R.string.not_reactioned)
            return
        }


        if (!confirmed) {
            AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.reaction_remove_confirm, myReaction.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    removeReaction(activity, column, status, confirmed = true)
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

                    result.error?.let {
                        activity.showToast(false, it)
                        return
                    }

                    val resCode = result.response?.code ?: -1
                    if (resCode !in 200 until 300) {
                        activity.showToast(false, "HTTP error $resCode")
                        return
                    }

                    if (newStatus != null) {
                        activity.app_state.columnList.forEach { column ->
                            if (column.access_info.acct == access_info.acct)
                                column.updateEmojiReactionByApiResponse(newStatus)
                        }
                    } else if (status.decreaseReactionMisskey(myReaction.name, true, "removeReaction")) {
                        // 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
                        column.fireShowContent(
                            reason = "removeReaction complete",
                            reset = true
                        )
                    }
                }
            })
    }

    // リアクションの別アカ操作で使う
    // 選択済みのアカウントと同期済みのステータスにリアクションを行う
    private fun reactionWithoutUi(
        activity: ActMain,
        access_info: SavedAccount,
        resolvedStatus: TootStatus,
        reactionCode: String? = null,
        reactionImage: String? = null,
        isConfirmed: Boolean = false,
        callback: () -> Unit,
    ) {
        if (reactionCode == null) {
            EmojiPicker(activity, access_info, closeOnSelected = true) { result ->
                var newUrl :String? = null
                val newCode = when (val emoji = result.emoji) {
                    is UnicodeEmoji -> emoji.unifiedCode
                    is CustomEmoji ->{
                        newUrl = emoji.static_url
                        if (access_info.isMisskey) {
                            ":${emoji.shortcode}:"
                        } else {
                            emoji.shortcode
                        }
                    }
                }
                reactionWithoutUi(
                    activity = activity,
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

        if (!isConfirmed) {
            val options = DecodeOptions(
                activity,
                access_info,
                decodeEmoji = true,
                enlargeEmoji = 1.5f,
                enlargeCustomEmoji = 1.5f
            )
            val emojiSpan = TootReaction.toSpannableStringBuilder(options, reactionCode, reactionImage)
            DlgConfirm.open(
                activity,
                activity.getString(R.string.confirm_reaction, emojiSpan, AcctColor.getNickname(access_info)),
                object : DlgConfirm.Callback {
                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_reaction
                        set(bv) {
                            access_info.confirm_reaction = bv
                            access_info.saveSetting()
                        }

                    override fun onOK() {
                        reactionWithoutUi(
                            activity = activity,
                            access_info = access_info,
                            resolvedStatus = resolvedStatus,
                            reactionCode = reactionCode,
                            reactionImage = reactionImage,
                            isConfirmed = true,
                            callback = callback
                        )
                    }
                })
            return
        }

        TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info,
            object : TootTask {

                var newStatus: TootStatus? = null

                override suspend fun background(client: TootApiClient): TootApiResult? {
                    return if (access_info.isMisskey) {
                        client.request(
                            "/api/notes/reactions/create",
                            access_info.putMisskeyApiToken().apply {
                                put("noteId", resolvedStatus.id.toString())
                                put("reaction", reactionCode)

                            }
                                .toPostRequestBuilder()
                        )
                        // 成功すると204 no content
                    } else {
                        client.request(
                            "/api/v1/statuses/${resolvedStatus.id}/emoji_reactions/${reactionCode.encodePercent()}",
                            "".toFormRequestBody().toPut()
                        )?.also { result ->
                            // 成功すると更新された投稿
                            newStatus = TootParser(activity, access_info).status(result.jsonObject)
                        }
                    }
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    result ?: return
                    result.error?.let {
                        activity.showToast(true, it)
                        return
                    }
                    callback()
                }
            })
    }

    // リアクションの別アカ操作で使う
    // 選択済みのアカウントと同期済みのステータスと同期まえのリアクションから、同期後のリアクションコードを計算する
    // 解決できなかった場合はnullを返す
    private fun fixReactionCode(
        activity: ActMain,
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
        activity.showToast(true, R.string.cant_reaction_remote_custom_emoji, newName)
        return null
    }

    fun reactionFromAnotherAccount(
        activity: ActMain,
        timeline_account: SavedAccount,
        statusArg: TootStatus?,
        reaction: TootReaction? = null,
    ) {
        statusArg ?: return

        fun afterResolveStatus(actionAccount: SavedAccount, resolvedStatus: TootStatus) {
            val code = if (reaction == null) {
                null // あとで選択する
            } else {
                fixReactionCode(
                    activity = activity,
                    timelineAccount = timeline_account,
                    actionAccount = actionAccount,
                    reaction = reaction,
                ) ?: return // エラー終了の場合がある
            }

            reactionWithoutUi(
                activity = activity,
                access_info = actionAccount,
                resolvedStatus = resolvedStatus,
                reactionCode = code,
                callback = activity.reaction_complete_callback,
            )
        }

        Action_Account.listAccountsReactionable(activity) { list ->

            if (list.isEmpty()) {
                activity.showToast(false, R.string.not_available_for_current_accounts)
                return@listAccountsReactionable
            }

            AccountPicker.pick(
                activity,
                accountListArg = list,
                bAuto = false,
                message = activity.getString(R.string.account_picker_reaction)
            ) { action_account ->
                if (calcCrossAccountMode(timeline_account, action_account).isNotRemote) {
                    afterResolveStatus(action_account, statusArg)
                } else {
                    TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(action_account,
                        object : TootTask {
                            var newStatus: TootStatus? = null
                            override suspend fun background(client: TootApiClient): TootApiResult? {
                                val (result, status) = client.syncStatus(action_account, statusArg)
                                if (status?.reactionSet?.myReaction != null) {
                                    return TootApiResult(activity.getString(R.string.already_reactioned))
                                }
                                newStatus = status
                                return result
                            }

                            override suspend fun handleResult(result: TootApiResult?) {
                                result?.error?.let {
                                    activity.showToast(true, it)
                                    return
                                }
                                newStatus?.let { afterResolveStatus(action_account, it) }
                            }
                        })
                }
            }
        }
    }
}