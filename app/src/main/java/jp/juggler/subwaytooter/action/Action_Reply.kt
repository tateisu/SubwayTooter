package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.SavedAccountCallback
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.showToast
import java.util.*

object Action_Reply {


    /////////////////////////////////////////////////////////////////////////////////
    // reply

    fun reply(
        activity: ActMain,
        access_info: SavedAccount,
        status: TootStatus,
        quote: Boolean = false
    ) {
        activity.launchActPost(
            ActPost.createIntent(
                activity,
                access_info.db_id,
                reply_status = status,
                quote = quote
            )
        )
    }

    private fun replyRemote(
        activity: ActMain,
        access_info: SavedAccount,
        remote_status_url: String?,
        quote: Boolean = false
    ) {
        if (remote_status_url == null || remote_status_url.isEmpty()) return

        TootTaskRunner(activity)
            .progressPrefix(activity.getString(R.string.progress_synchronize_toot))

            .run(access_info, object : TootTask {

                var local_status: TootStatus? = null
                override suspend fun background(client: TootApiClient): TootApiResult? {
                    val (result, status) = client.syncStatus(access_info, remote_status_url)
                    local_status = status
                    return result
                }

                override suspend fun handleResult(result: TootApiResult?) {

                    result ?: return // cancelled.

                    val ls = local_status
                    if (ls != null) {
                        reply(activity, access_info, ls, quote = quote)
                    } else {
                        activity.showToast(true, result.error)
                    }
                }
            })
    }

    fun replyFromAnotherAccount(
        activity: ActMain,
        timeline_account: SavedAccount,
        status: TootStatus?,
        quote: Boolean = false
    ) {
        status ?: return

        val accountCallback: SavedAccountCallback = { ai ->
            if (ai.matchHost(status.readerApDomain)) {
                // アクセス元ホストが同じならステータスIDを使って返信できる
                reply(activity, ai, status, quote = quote)
            } else {
                // それ以外の場合、ステータスのURLを検索APIに投げることで返信できる
                replyRemote(activity, ai, status.url, quote = quote)
            }
        }

        if (quote) {
            AccountPicker.pick(
                activity,
                bAllowPseudo = false,
                bAllowMisskey = true,
                bAllowMastodon = true,
                bAuto = true,
                message = activity.getString(R.string.account_picker_quote_toot),
                callback = accountCallback
            )
        } else {
            AccountPicker.pick(
                activity,
                bAllowPseudo = false,
                bAuto = false,
                message = activity.getString(R.string.account_picker_reply),
                accountListArg = makeAccountListNonPseudo(activity, timeline_account.apDomain),
                callback = accountCallback
            )
        }
    }

    //////////////////////////////////////////////////

    // tootsearch APIは投稿の返信元を示すreplyの情報がない。
    // in_reply_to_idを参照するしかない
    // ところがtootsearchでは投稿をどのタンスから読んだか分からないので、IDは全面的に信用できない。
    // 疑似ではないアカウントを選んだ後に表示中の投稿を検索APIで調べて、そのリプライのIDを取得しなおす
    fun showReplyTootsearch(
        activity: ActMain,
        pos: Int,
        statusArg: TootStatus?
    ) {
        statusArg ?: return

        // step2: 選択したアカウントで投稿を検索して返信元の投稿のIDを調べる
        fun step2(a: SavedAccount) = TootTaskRunner(activity).run(a, object : TootTask {
            var tmp: TootStatus? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {
                val (result, status) = client.syncStatus(a, statusArg)
                this.tmp = status
                return result
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return
                val status = tmp
                val replyId = status?.in_reply_to_id
                when {
                    status == null -> activity.showToast(true, result.error ?: "?")
                    replyId == null -> activity.showToast(
                        true,
                        "showReplyTootsearch: in_reply_to_id is null"
                    )
                    else -> Action_Conversation.conversationLocal(activity, pos, a, replyId)
                }
            }
        })

        // step 1: choose account

        val host = statusArg.account.apDomain
        val local_account_list = ArrayList<SavedAccount>()
        val other_account_list = ArrayList<SavedAccount>()

        for (a in SavedAccount.loadAccountList(activity)) {

            // 検索APIはログイン必須なので疑似アカウントは使えない
            if (a.isPseudo) continue

            if (a.matchHost(host)) {
                local_account_list.add(a)
            } else {
                other_account_list.add(a)
            }
        }

        val dialog = ActionsDialog()

        SavedAccount.sort(local_account_list)
        for (a in local_account_list) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { step2(a) }
        }

        SavedAccount.sort(other_account_list)
        for (a in other_account_list) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { step2(a) }
        }

        dialog.show(activity, activity.getString(R.string.open_status_from))
    }

}