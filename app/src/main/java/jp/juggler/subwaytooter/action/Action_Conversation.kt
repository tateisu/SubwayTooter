package jp.juggler.subwaytooter.action

import android.content.Context
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootConversationSummary
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.findStatus
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import java.util.*


private val log = LogCategory("Action_Conversation")


/////////////////////////////////////////////////////////////////////////////////////
// open conversation

// returns true if unread flag will be cleared.
internal fun ActMain.conversationUnreadClear(
    access_info: SavedAccount,
    conversationSummary: TootConversationSummary?
): Boolean {

    // サマリがない
    conversationSummary ?: return false

    // 更新の必要がない
    if (!conversationSummary.unread) return false

    // 変数を更新
    conversationSummary.unread = false

    // 未読フラグのクリアをサーバに送る
    launchMain {
        runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            client.request(
                "/api/v1/conversations/${conversationSummary.id}/read",
                "".toFormRequestBody().toPost()
            )
        }
        // 応答の内容は見ない
    }

    return true // 表示も更新されるべき
}

// ローカルかリモートか判断する
fun ActMain.conversation(
    pos: Int,
    access_info: SavedAccount,
    status: TootStatus
) {
    if (access_info.isNA || !access_info.matchHost(status.readerApDomain)) {
        conversationOtherInstance(pos, status)
    } else {

        conversationLocal(pos, access_info, status.id)
    }
}

// ローカルから見える会話の流れを表示する
fun ActMain.conversationLocal(
    pos: Int,
    access_info: SavedAccount,
    status_id: EntityId
) = addColumn(pos, access_info, ColumnType.CONVERSATION, status_id)


private val reDetailedStatusTime =
    """<a\b[^>]*?\bdetailed-status__datetime\b[^>]*href="https://[^/]+/@[^/]+/([^\s?#/"]+)"""
        .toRegex()

private val reHeaderOgUrl = """<meta\s+content="https://[^/"]+/notice/([^/"]+)"\s+property="og:url"/?>"""
    .toRegex()

// 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
suspend fun guessStatusIdFromPseudoAccount(
    context: Context, // for string resource
    client: TootApiClient, // for get HTML
    remote_status_url: String
): Pair<TootApiResult?, EntityId?> {

    val result = client.getHttp(remote_status_url)

    result?.string?.let { html ->

        reDetailedStatusTime.find(html)
            ?.groupValues?.elementAtOrNull(1)
            ?.let { return Pair(result, EntityId(it)) }

        reHeaderOgUrl.find(html)
            ?.groupValues?.elementAtOrNull(1)
            ?.let { return Pair(result, EntityId(it)) }
    }

    return Pair(
        result?.setError(context.getString(R.string.status_id_conversion_failed)),
        null
    )
}

private fun ActMain.conversationRemote(
    pos: Int,
    access_info: SavedAccount,
    remote_status_url: String
) {

    launchMain {
        var local_status_id: EntityId? = null

        runApiTask(
            access_info,
            progressPrefix = getString(R.string.progress_synchronize_toot)
        ) { client ->
            if (access_info.isPseudo) {
                // 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
                val pair = guessStatusIdFromPseudoAccount(applicationContext, client, remote_status_url)
                local_status_id = pair.second
                pair.first
            } else {
                // 実アカウントでは検索APIを使える
                val (result, status) = client.syncStatus(access_info, remote_status_url)
                if (status != null) {
                    local_status_id = status.id
                    log.d("status id conversion ${remote_status_url}=>${status.id}")
                }
                result
            }
        }?.let { result ->
            when (val statusId = local_status_id) {
                null -> showToast(true, result.error)
                else -> conversationLocal(pos, access_info, statusId)
            }
        }
    }
}


// アプリ外部からURLを渡された場合に呼ばれる
fun ActMain.conversationOtherInstance(
    pos: Int,
    url: String,
    status_id_original: EntityId? = null,
    host_access: Host? = null,
    status_id_access: EntityId? = null
) {
    val activity = this

    val dialog = ActionsDialog()

    val host_original = Host.parse(url.toUri().authority ?: "")

    // 選択肢：ブラウザで表示する
    dialog.addAction(getString(R.string.open_web_on_host, host_original.pretty)) { openCustomTab(url) }

    // トゥートの投稿元タンスにあるアカウント
    val local_account_list = ArrayList<SavedAccount>()

    // TLを読んだタンスにあるアカウント
    val access_account_list = ArrayList<SavedAccount>()

    // その他のタンスにあるアカウント
    val other_account_list = ArrayList<SavedAccount>()

    for (a in SavedAccount.loadAccountList(applicationContext)) {

        // 疑似アカウントは後でまとめて処理する
        if (a.isPseudo) continue

        if (status_id_original != null && a.matchHost(host_original)) {
            // アクセス情報＋ステータスID でアクセスできるなら
            // 同タンスのアカウントならステータスIDの変換なしに表示できる
            local_account_list.add(a)
        } else if (status_id_access != null && a.matchHost(host_access)) {
            // 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
            access_account_list.add(a)
        } else {
            // 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
            other_account_list.add(a)
        }
    }

    // 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
    if (local_account_list.isEmpty()) {
        if (status_id_original != null) {
            dialog.addAction(
                getString(R.string.open_in_pseudo_account, "?@${host_original.pretty}")
            ) {
                launchMain {
                    addPseudoAccount(host_original)?.let { sa ->
                        conversationLocal(pos, sa, status_id_original)
                    }
                }
            }
        } else {
            dialog.addAction(
                getString(R.string.open_in_pseudo_account, "?@${host_original.pretty}")
            ) {
                launchMain {
                    addPseudoAccount(host_original)?.let { sa ->
                        conversationRemote(pos, sa, url)
                    }
                }
            }
        }
    }

    // ローカルアカウント
    if (status_id_original != null) {
        SavedAccount.sort(local_account_list)
        for (a in local_account_list) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { conversationLocal(pos, a, status_id_original) }
        }
    }

    // アクセスしたアカウント
    if (status_id_access != null) {
        SavedAccount.sort(access_account_list)
        for (a in access_account_list) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { conversationLocal(pos, a, status_id_access) }
        }
    }

    // その他の実アカウント
    SavedAccount.sort(other_account_list)
    for (a in other_account_list) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                activity,
                R.string.open_in_account,
                a.acct
            )
        ) { conversationRemote(pos, a, url) }
    }

    dialog.show(activity, activity.getString(R.string.open_status_from))
}

// リモートかもしれない会話の流れを表示する
fun ActMain.conversationOtherInstance(
    pos: Int,
    status: TootStatus?
) {
    if (status == null) return
    val url = status.url

    if (url == null || url.isEmpty()) {
        // URLが不明なトゥートというのはreblogの外側のアレ
        return
    }

    when {

        // 検索サービスではステータスTLをどのタンスから読んだのか分からない
        status.readerApDomain == null ->
            conversationOtherInstance(
                pos,
                url,
                TootStatus.validStatusId(status.id)
                    ?: TootStatus.findStatusIdFromUri(
                        status.uri,
                        status.url
                    )
            )

        // TLアカウントのホストとトゥートのアカウントのホストが同じ
        status.originalApDomain == status.readerApDomain ->
            conversationOtherInstance(
                pos,
                url,
                TootStatus.validStatusId(status.id)
                    ?: TootStatus.findStatusIdFromUri(
                        status.uri,
                        status.url
                    )
            )

        else -> {
            // トゥートを取得したタンスと投稿元タンスが異なる場合
            // status.id はトゥートを取得したタンスでのIDである
            // 投稿元タンスでのIDはuriやURLから調べる
            // pleromaではIDがuuidなので失敗する(その時はURLを検索してIDを見つける)
            conversationOtherInstance(
                pos, url, TootStatus.findStatusIdFromUri(
                    status.uri,
                    status.url
                ), status.readerApDomain, TootStatus.validStatusId(status.id)
            )
        }
    }
}

////////////////////////////////////////

fun ActMain.conversationMute(
    access_info: SavedAccount,
    status: TootStatus
) {
    // toggle change
    val bMute = !status.muted

    launchMain {
        var local_status: TootStatus? = null
        runApiTask(access_info) { client ->
            client.request(
                "/api/v1/statuses/${status.id}/${if (bMute) "mute" else "unmute"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                local_status = TootParser(this, access_info).status(result.jsonObject)
            }
        }?.let { result ->
            when (val ls = local_status) {
                null -> showToast(true, result.error)

                else -> {
                    for (column in app_state.columnList) {
                        if (access_info == column.access_info) {
                            column.findStatus(access_info.apDomain, ls.id) { _, status ->
                                status.muted = bMute
                                true
                            }
                        }
                    }
                    showToast(
                        true,
                        if (bMute) R.string.mute_succeeded else R.string.unmute_succeeded
                    )
                }
            }
        }
    }
}


//////////////////////////////////////////////////

// tootsearch APIは投稿の返信元を示すreplyの情報がない。
// in_reply_to_idを参照するしかない
// ところがtootsearchでは投稿をどのタンスから読んだか分からないので、IDは全面的に信用できない。
// 疑似ではないアカウントを選んだ後に表示中の投稿を検索APIで調べて、そのリプライのIDを取得しなおす
fun ActMain.conversationFromTootsearch(
    pos: Int,
    statusArg: TootStatus?
) {
    statusArg ?: return

    // step2: 選択したアカウントで投稿を検索して返信元の投稿のIDを調べる
    fun step2(a: SavedAccount) = launchMain {
        var tmp: TootStatus? = null
        runApiTask(a) { client ->
            val (result, status) = client.syncStatus(a, statusArg)
            tmp = status
            result
        }?.let { result ->
            val status = tmp
            val replyId = status?.in_reply_to_id
            when {
                status == null -> showToast(true, result.error ?: "?")
                replyId == null -> showToast(true, "showReplyTootsearch: in_reply_to_id is null")
                else -> conversationLocal(pos, a, replyId)
            }
        }
    }

    // step 1: choose account

    val host = statusArg.account.apDomain
    val local_account_list = ArrayList<SavedAccount>()
    val other_account_list = ArrayList<SavedAccount>()

    for (a in SavedAccount.loadAccountList(this)) {

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
                this,
                R.string.open_in_account,
                a.acct
            )
        ) { step2(a) }
    }

    SavedAccount.sort(other_account_list)
    for (a in other_account_list) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                this,
                R.string.open_in_account,
                a.acct
            )
        ) { step2(a) }
    }

    dialog.show(this, getString(R.string.open_status_from))
}
